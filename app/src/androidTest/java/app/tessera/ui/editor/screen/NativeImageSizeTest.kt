package app.tessera.ui.editor.screen

import android.graphics.Rect
import android.util.Log
import android.util.Size
import android.view.ViewTreeObserver
import androidx.test.platform.app.InstrumentationRegistry
import app.tessera.data.images.NoteImageDecoder
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.domain.model.parseImageRef
import app.tessera.ui.editor.components.NoteObjectImageSource
import app.tessera.ui.editor.engine.DeviceEditor
import app.tessera.ui.editor.engine.TesseraEditText
import app.tessera.ui.editor.engine.boundsNear
import app.tessera.ui.editor.engine.savePng
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Phase 2 spec §3.1 on the device, with real WebP files and the real `BitmapFactory` (Robolectric fakes the bounds-only read): a picture
 * takes the size Aztec gave it (turned first, then `min(turned width, text width) × w%`, never stretched, one file pixel = one screen pixel),
 * and that size is known before the decode — the text under a picture does not move when the picture arrives. A file that cannot be read
 * is "unavailable", and a download from Drive heals it. `adb logcat -s NativeImageSize` has every size.
 */
@HiltAndroidTest
class NativeImageSizeTest : NativeAppTest() {
    @Inject lateinit var decoder: NoteImageDecoder
    @Inject lateinit var imageDao: NoteImageDao

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private val appIds = ArrayList<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Before
    fun dirs() {
        dir = File(ctx.cacheDir, "qa-img-size").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun clean() {
        scope.cancel()
        dir.deleteRecursively()
        ImageFixtures.removeApp(ctx, appIds)
    }

    private class Case(val name: String, val w: Int, val h: Int, query: String = "") {
        val id = "qa-size-$name"
        val src = "tessera-img://$id$query"
        val ref = parseImageRef(src)!!
    }

    /** Odd sizes on purpose: a codec that samples down rounds them, and the size known in advance must round the same way. */
    private val cases = listOf(
        Case("wide", 1001, 563),
        Case("narrow", 301, 401),
        Case("w50", 1001, 563, "?w=50"),
        Case("rot90", 1001, 563, "?rot=90"),
        Case("rot270", 451, 1203, "?rot=270"),
        Case("rot180-w30", 1001, 563, "?w=30&rot=180"),
        Case("sample2", 2047, 1153),
        Case("sample2-rot90", 2047, 1153, "?rot=90"),
        Case("sample2-w50-rot270", 2047, 1153, "?w=50&rot=270"),
        Case("sample4", 2989, 1601),
        Case("sample4-rot90", 2989, 1601, "?rot=90"),
    )

    private fun log(msg: String) = Log.i(TAG, msg)

    private fun testDecoder(files: File, cache: File, drive: FakeDrive = FakeDrive()) = NoteImageDecoder(MemoryImageDao(), files, cache, drive)

    private fun textWidth(e: TesseraEditText) = e.width - e.totalPaddingLeft - e.totalPaddingRight

    private fun awaitPictures(e: DeviceEditor, n: Int) {
        val until = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < until) {
            if (e.ui { shownImages(e.view).let { s -> s.size == n && s.all { it.loaded || it.failed } } }) return
            Thread.sleep(50)
        }
        error("pictures did not arrive: ${e.ui { shownImages(e.view) }}")
    }

    @Test
    fun theSizeKnownBeforeTheDecodeIsTheSizeAfterIt() {
        val files = File(dir, "files")
        val cache = File(dir, "cache")
        for (c in cases) ImageFixtures.webp(File(files, "images/${c.id}.webp"), c.w, c.h)
        val bad = ArrayList<String>()
        DeviceEditor("").use { e ->
            val textW = e.ui { textWidth(e.view) }
            // the decoder alone, at the width of this editor and of other screens: the size read from the bounds (before any decode) is the
            // size of the decoded picture
            for (w in listOf(textW, 640, 1080).distinct()) {
                val d = testDecoder(files, cache)
                runBlocking { d.prefetchSizes(cases.map { it.src }) }
                val planned = cases.map { d.cachedSize(it.src, w) }
                cases.forEachIndexed { i, c ->
                    val got = (runBlocking { d.decode(c.src, w) } as? NoteImageDecoder.Result.Loaded)?.bitmap?.let { Size(it.width, it.height) }
                    log("${c.name} ${c.w}x${c.h} @ $w: known before ${planned[i]}, decoded $got")
                    if (planned[i] == null || planned[i] != got) bad += "${c.name} @ $w: known before ${planned[i]}, decoded $got"
                }
            }

            // the editor: the box of a picture before its decode is the box after it, and nothing below it moves
            val d = testDecoder(files, cache)
            runBlocking { d.prefetchSizes(cases.map { it.src }) }
            val gate = ImageFixtures.hold(d)
            val md = cases.indices.joinToString("\n") { "line-%02d".format(it) + "\n![](${cases[it].src})" } + "\nline-end"
            val markers = cases.indices.map { "line-%02d".format(it) } + "line-end"
            e.ui { e.view.imageSource = NoteObjectImageSource(d, scope); e.view.setMarkdown(md) }
            e.settleFrames()
            val before = e.ui { shownImages(e.view) }
            val topsBefore = e.ui { markers.map { lineTopOf(e.view, it) } }
            assertTrue("the decodes are held: nothing is loaded yet ($before)", before.none { it.loaded })
            gate.countDown()
            awaitPictures(e, cases.size)
            e.settleFrames()
            val after = e.ui { shownImages(e.view) }
            val topsAfter = e.ui { markers.map { lineTopOf(e.view, it) } }
            cases.forEachIndexed { i, c ->
                val b = before[i].rect
                val a = after[i]
                log("editor ${c.name}: box before ${b.width()}x${b.height()}, after ${a.rect.width()}x${a.rect.height()}, picture ${a.pictureW}x${a.pictureH}")
                if (!a.loaded) bad += "${c.name}: not loaded in the editor"
                if (b.width() != a.rect.width() || b.height() != a.rect.height()) bad += "${c.name}: box before the decode ${b.width()}x${b.height()} != after ${a.rect.width()}x${a.rect.height()}"
                if (a.pictureW != a.rect.width() || a.pictureH != a.rect.height()) bad += "${c.name}: picture ${a.pictureW}x${a.pictureH} drawn in a box ${a.rect.width()}x${a.rect.height()}"
            }
            if (topsBefore != topsAfter) bad += "the text moved when the pictures came: $topsBefore -> $topsAfter"
        }
        assertEquals("sizes that change with the decode (${bad.size})", emptyList<String>(), bad)
    }

    /**
     * What the user is promised (spec §3.1, as Aztec): the picture is turned first, then it is as wide as its turned width allows, up to the text
     * width, times `w`%; the aspect is kept. A big photo turned by 90° is as wide as the text, like any photo wider than the text.
     */
    @Test
    fun aPictureIsAsWideAsItsTurnedWidthAllows() {
        val files = File(dir, "files")
        for (c in cases) ImageFixtures.webp(File(files, "images/${c.id}.webp"), c.w, c.h)
        val bad = ArrayList<String>()
        for (textW in listOf(640, 720, 1080)) {
            val d = testDecoder(files, File(dir, "cache"))
            for (c in cases) {
                val got = (runBlocking { d.decode(c.src, textW) } as NoteImageDecoder.Result.Loaded).bitmap
                val turned = c.ref.rotDegrees % 180 != 0
                val tw = if (turned) c.h else c.w
                val th = if (turned) c.w else c.h
                val width = Math.round(c.ref.wPercent / 100f * minOf(tw, textW))
                val height = width * th.toFloat() / tw
                log("promise ${c.name} @ $textW: ${width}x${"%.1f".format(height)}, decoded ${got.width}x${got.height}")
                if (got.width != width || abs(got.height - height) > 1.5f) bad += "${c.name} ${c.w}x${c.h} @ $textW: promised ${width}x${"%.1f".format(height)}, shown ${got.width}x${got.height}"
            }
        }
        assertEquals("pictures not in the promised size (${bad.size})", emptyList<String>(), bad)
    }

    /**
     * The decoder of API 28+ (`ImageDecoder`) gives the colors and the alpha `BitmapFactory` gave (the decode of stage 2 before 78a9868): the same
     * file, the same size, pixel against pixel. A lossy photo as the app stores it, and a lossless picture with transparent and half-transparent
     * parts (a PNG with alpha from another app).
     */
    @Test
    fun colorsAndAlphaAreWhatBitmapFactoryGave() {
        val files = File(dir, "files")
        ImageFixtures.webp(File(files, "images/qa-color-lossy.webp"), 1001, 563)
        File(files, "images/qa-color-alpha.webp").let { f ->
            val b = android.graphics.Bitmap.createBitmap(400, 300, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(b)
            c.drawColor(ImageFixtures.FILL)
            c.drawRect(0f, 0f, 200f, 300f, android.graphics.Paint().apply { color = 0; xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) })
            c.drawRect(100f, 100f, 300f, 200f, android.graphics.Paint().apply { color = 0x80FF0000.toInt(); xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC) })
            java.io.FileOutputStream(f).use { b.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it) }
        }
        val d = testDecoder(files, File(dir, "cache"))
        val bad = ArrayList<String>()
        for ((id, w) in listOf("qa-color-lossy" to 704, "qa-color-alpha" to 704)) {
            val path = File(files, "images/$id.webp").absolutePath
            val now = (runBlocking { d.decode("tessera-img://$id", w) } as NoteImageDecoder.Result.Loaded).bitmap
            // what BitmapFactory gave: the same sample, then scaled to the same size
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { android.graphics.BitmapFactory.decodeFile(path, it) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= w) sample *= 2
            var before = android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })!!
            if (before.width != now.width || before.height != now.height) before = android.graphics.Bitmap.createScaledBitmap(before, now.width, now.height, true)
            var maxDiff = 0
            var alphaDiff = 0
            var at = ""
            for (y in 0 until now.height step 5) for (x in 0 until now.width step 5) {
                val a = now.getPixel(x, y)
                val b = before.getPixel(x, y)
                val diff = maxOf(abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)), abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)), abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)))
                val ad = abs(android.graphics.Color.alpha(a) - android.graphics.Color.alpha(b))
                if (diff > maxDiff) { maxDiff = diff; at = "($x,$y) %08x vs %08x".format(a, b) }
                alphaDiff = maxOf(alphaDiff, ad)
            }
            log("colors $id: ${now.width}x${now.height} ${now.config} alpha ${now.hasAlpha()} premultiplied ${now.isPremultiplied} vs BitmapFactory ${before.config} alpha ${before.hasAlpha()}; max color diff $maxDiff $at, max alpha diff $alphaDiff")
            if (maxDiff > 3 || alphaDiff > 0) bad += "$id: max color diff $maxDiff $at, max alpha diff $alphaDiff"
            if (now.hasAlpha() != before.hasAlpha() || now.config != before.config) bad += "$id: ${now.config}/alpha ${now.hasAlpha()} vs ${before.config}/alpha ${before.hasAlpha()}"
        }
        assertEquals("pictures that changed with the decoder", emptyList<String>(), bad)
    }

    /** `DENSITY_NONE`: a picture narrower than the text keeps its width (no stretch to the text), and one pixel of it is one pixel of the screen. */
    @Test
    fun aNarrowPictureIsNotStretchedAndAFilePixelIsAScreenPixel() {
        val files = File(dir, "files")
        ImageFixtures.webp(File(files, "images/qa-px-narrow.webp"), 301, 201, mark = false)
        ImageFixtures.webp(File(files, "images/qa-px-wide.webp"), 1001, 563, mark = false)
        DeviceEditor("").use { e ->
            val d = testDecoder(files, File(dir, "cache"))
            e.ui { e.view.imageSource = NoteObjectImageSource(d, scope); e.view.setMarkdown("top\n![](tessera-img://qa-px-narrow)\nmiddle\n![](tessera-img://qa-px-wide)\nend") }
            awaitPictures(e, 2)
            val textW = e.ui { textWidth(e.view) }
            val shown = e.ui { shownImages(e.view) }
            val pixels = e.capture()
            val density = ctx.resources.displayMetrics.densityDpi
            val bad = ArrayList<String>()
            for ((s, want) in shown.zip(listOf(Size(301, 201), Size(textW, Math.round(textW * 563f / 1001))))) {
                val box = boundsNear(pixels, Rect(s.rect).apply { inset(-6, -6) }, ImageFixtures.FILL)
                log("${s.src}: box ${s.rect.width()}x${s.rect.height()}, on screen ${box?.width()}x${box?.height()} at density $density, text width $textW")
                if (box == null || abs(box.width() - want.width) > 1 || abs(box.height() - want.height) > 1) bad += "${s.src}: on screen ${box?.width()}x${box?.height()}, want ${want.width}x${want.height}"
            }
            assertTrue("a narrow picture is narrower than the text: ${shown[0].rect.width()} < $textW", shown[0].rect.width() < textW)
            assertEquals("pictures on screen not at their pixel size", emptyList<String>(), bad)
        }
    }

    @Test
    fun aBrokenFileIsUnavailableAndADownloadHealsIt() {
        val good = ImageFixtures.webp(File(dir, "good.webp"), 1001, 563)
        val cache = File(dir, "cache")
        val files = File(dir, "files")
        fun broken(id: String, how: (File) -> Unit) = ImageFixtures.webp(File(cache, "images/$id.webp"), 1001, 563).also(how)
        broken("qa-broken-half") { ImageFixtures.truncate(it, (good.length() / 2).toInt()) }
        broken("qa-broken-tail") { ImageFixtures.truncate(it, (good.length() - 64).toInt()) }
        broken("qa-broken-header") { ImageFixtures.truncate(it, 30) }
        broken("qa-broken-garbage") { f -> f.writeBytes(ByteArray(5_000) { (it * 31).toByte() }) }
        val ids = listOf("qa-broken-half", "qa-broken-tail", "qa-broken-header", "qa-broken-garbage")
        val bad = ArrayList<String>()

        // nothing on Drive: each is "unavailable", none is shown as a part of a picture
        DeviceEditor("").use { e ->
            val d = testDecoder(files, cache)
            runBlocking { d.prefetchSizes(ids.map { "tessera-img://$it" }) }
            e.ui { e.view.imageSource = NoteObjectImageSource(d, scope); e.view.setMarkdown(ids.joinToString("\n") { "$it\n![](tessera-img://$it)" }) }
            awaitPictures(e, ids.size)
            e.ui { e.view.scrollTo(0, shownImages(e.view)[1].rect.top - 40) }
            savePng(ctx, "p2-broken-tail", e.capture()) // what a file without its last bytes looks like
            e.ui { e.view.scrollTo(0, 0) }
            for (s in e.ui { shownImages(e.view) }) {
                log("broken ${s.src}: loaded ${s.loaded}, failed ${s.failed}, box ${s.rect.width()}x${s.rect.height()}, picture ${s.pictureW}x${s.pictureH}")
                if (!s.failed) bad += "${s.src}: shown as a picture ${s.pictureW}x${s.pictureH}, not \"unavailable\""
            }
        }

        // Drive has the picture: the broken file is replaced by the download and the picture is shown; the next open needs no download
        val healCache = File(dir, "heal-cache")
        ImageFixtures.webp(File(healCache, "images/qa-heal.webp"), 1001, 563).also { ImageFixtures.truncate(it, (good.length() / 2).toInt()) }
        val drive = FakeDrive { id ->
            val target = File(healCache, "images/$id.webp")
            val tmp = File(healCache, "images/$id.webp.tmp").apply { writeBytes(good.readBytes()) }
            tmp.renameTo(target)
            target
        }
        DeviceEditor("").use { e ->
            val d = testDecoder(files, healCache, drive)
            e.ui { e.view.imageSource = NoteObjectImageSource(d, scope); e.view.setMarkdown("heal\n![](tessera-img://qa-heal)\nend") }
            awaitPictures(e, 1)
            val s = e.ui { shownImages(e.view) }.single()
            log("heal: loaded ${s.loaded}, failed ${s.failed}, picture ${s.pictureW}x${s.pictureH}, downloads ${drive.calls}")
            if (!s.loaded) bad += "a broken file with the picture on Drive: not healed (failed ${s.failed}, downloads ${drive.calls})"
            if (drive.calls != 1) bad += "a broken file: ${drive.calls} downloads, want 1"
        }
        val again = FakeDrive()
        val second = runBlocking { testDecoder(files, healCache, again).decode("tessera-img://qa-heal", 720) }
        if (second !is NoteImageDecoder.Result.Loaded || again.calls != 0) bad += "after the heal the file on the device is good: $second, downloads ${again.calls}"
        assertEquals("broken files", emptyList<String>(), bad)
    }

    /** The app: a note with pictures is opened from the list; from its first frame the text stands where it stays when the pictures come. */
    @Test
    fun openingANoteTheTextDoesNotMoveWhenThePicturesArrive() {
        val pics = listOf(
            Case("open-wide", 1001, 563), Case("open-narrow", 301, 401), Case("open-w50", 1001, 563, "?w=50"),
            Case("open-rot90", 1001, 563, "?rot=90"), Case("open-sample2-rot270", 2047, 1153, "?w=70&rot=270"),
        )
        for (c in pics) {
            val f = ImageFixtures.webp(ImageFixtures.appFile(ctx, c.id), c.w, c.h)
            appIds += c.id
            runBlocking { imageDao.upsert(ImageFixtures.row(c.id, f, c.w, c.h, noteId = "qa-size-open")) }
        }
        val markers = pics.indices.map { "under %02d".format(it) } + "the end"
        val md = "# Pictures\n" + pics.indices.joinToString("\n") { "![](${pics[it].src})\n" + markers[it] } + "\nthe end"
        app.launch()
        app.seed("qa-size-open", md, BASE)

        class Frame(val tops: List<Int>, val boxes: List<Pair<Int, Int>>, val loaded: Int)
        val frames = CopyOnWriteArrayList<Frame>()
        val listener = ViewTreeObserver.OnPreDrawListener {
            val e = findView(app.activity().window.decorView, TesseraEditText::class.java)?.takeIf { it.layout != null && it.text.toString().contains("the end") }
            if (e != null) {
                val imgs = shownImages(e)
                frames += Frame(markers.map { lineTopOf(e, it) }, imgs.map { it.rect.width() to it.rect.height() }, imgs.count { it.loaded })
            }
            true
        }
        val original = decoder.fileDecoder
        val gate = ImageFixtures.hold(decoder)
        try {
            app.ui { app.activity().window.decorView.viewTreeObserver.addOnPreDrawListener(listener) }
            val e = app.open("qa-size-open")
            app.settle(500)
            val held = frames.size
            gate.countDown()
            val until = System.currentTimeMillis() + 15_000
            while (app.ui { shownImages(e).count { it.loaded } } < pics.size && System.currentTimeMillis() < until) Thread.sleep(50)
            app.settle(800)
            app.ui { app.activity().window.decorView.viewTreeObserver.removeOnPreDrawListener(listener) }
            val distinct = frames.map { it.tops to it.boxes }.distinct()
            log("open: ${frames.size} frames ($held with the decodes held), loaded first ${frames.firstOrNull()?.loaded} last ${frames.lastOrNull()?.loaded}")
            distinct.forEach { log("open: tops ${it.first}, boxes ${it.second}") }
            assertTrue("frames were drawn before the pictures came: $held", held > 0 && frames.first().loaded == 0)
            assertEquals("every picture came", pics.size, frames.last().loaded)
            assertEquals("the text and the boxes from the first frame to the last (${distinct.size} states)", 1, distinct.size)
            app.back()
            assertEquals("opened and closed: the note is as it was", md, app.entity("qa-size-open")!!.content)
        } finally {
            gate.countDown()
            decoder.fileDecoder = original
            app.ui { app.activity().window.decorView.viewTreeObserver.removeOnPreDrawListener(listener) }
        }
    }

    /**
     * A long note, scrolled down, a picture above the screen arrives late: the note stays where the user scrolled it (it does not jump to
     * the caret). With its size known in advance and without; the caret at the top, the editor without and with the focus.
     */
    @Test
    fun aPictureThatArrivesLateDoesNotScrollTheNote() {
        val files = File(dir, "files")
        ImageFixtures.webp(File(files, "images/qa-late.webp"), 1001, 563)
        val md = "top line\n![](tessera-img://qa-late)\n" + (1..80).joinToString("\n") { "line $it of a long note" }
        val bad = ArrayList<String>()
        for (known in listOf(true, false)) for (focus in listOf(false, true)) {
            val d = testDecoder(files, File(dir, "cache-$known-$focus"))
            if (known) runBlocking { d.prefetchSizes(listOf("tessera-img://qa-late")) }
            val gate = ImageFixtures.hold(d)
            DeviceEditor("", focus = focus).use { e ->
                e.ui { e.view.imageSource = NoteObjectImageSource(d, scope); e.view.setMarkdown(md); e.view.setSelection(3) }
                e.settleFrames()
                val bottom = e.ui { e.view.layout.height + e.view.totalPaddingTop + e.view.totalPaddingBottom - e.view.height }
                e.ui { e.view.scrollTo(0, bottom) }
                e.settleFrames()
                val s0 = e.ui { e.view.scrollY }
                val box0 = e.ui { shownImages(e.view).single().rect.height() }
                gate.countDown()
                awaitPictures(e, 1)
                e.settleFrames()
                val s1 = e.ui { e.view.scrollY }
                val box1 = e.ui { shownImages(e.view).single().rect.height() }
                val focused = e.ui { e.view.isFocused }
                log("late picture, size known $known, focus $focused: scrollY $s0 -> $s1, picture height $box0 -> $box1")
                if (s1 != s0) bad += "size known $known, focus $focused: scrollY $s0 -> $s1 (picture height $box0 -> $box1)"
            }
        }
        assertEquals("the note scrolled when a picture came", emptyList<String>(), bad)
    }

    private companion object {
        const val TAG = "NativeImageSize"
        const val BASE = 1_700_000_000_000L
    }
}
