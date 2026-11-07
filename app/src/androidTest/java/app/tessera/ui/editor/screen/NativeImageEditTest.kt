package app.tessera.ui.editor.screen

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.test.core.app.ActivityScenario
import app.tessera.R
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.data.images.NoteImageDecoder
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.ui.editor.components.NoteBody
import app.tessera.ui.editor.components.NoteObjectImageSource
import app.tessera.ui.editor.engine.TesseraEditText
import dagger.hilt.android.testing.HiltAndroidTest
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2 spec §2–§3.3 on the device, in the real app, the way a user does it: a finger on the picture, on its corner, on the turn button;
 * the hardware keys, a keyboard's calls, the clipboard. A picture is selected without the keyboard, its handles stand on its corners and go
 * with the scroll; Backspace, Delete and cut take its paragraph; typing over it gives a Body paragraph; a corner drag shows the new width at
 * once and is one undo step; a turn shows the old picture until the new one comes; an image from the clipboard or the keyboard is stored and
 * inserted; a viewer sees the picture and cannot select it. `adb logcat -s NativeImageEdit` has the details.
 */
@HiltAndroidTest
class NativeImageEditTest : NativeAppTest() {
    @Inject lateinit var decoder: NoteImageDecoder
    @Inject lateinit var imageDao: NoteImageDao

    private val ctx get() = app.instrumentation.targetContext
    private val ids = ArrayList<String>()
    private val mediaUris = ArrayList<Uri>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @After
    fun clean() {
        scope.cancel()
        ImageFixtures.removeApp(ctx, ids)
        runBlocking { imageDao.all().filter { it.noteId.startsWith("qa-") || it.imageId in ids }.forEach { imageDao.delete(it.imageId) } }
        mediaUris.forEach { runCatching { ctx.contentResolver.delete(it, null, null) } }
    }

    private fun log(msg: String) = Log.i(TAG, msg)

    private fun note(id: String) = "# Edit\nabove one\nabove two\nabove three\n![](tessera-img://$id-pic)\nbelow\n" + (1..30).joinToString("\n") { "line $it" }

    /** A note with one picture (1001×563, as stored by the app), open in the editor with the picture shown. */
    private fun openNote(id: String, md: String = note(id), picW: Int = 1001, picH: Int = 563): TesseraEditText {
        val pic = "$id-pic"
        val f = ImageFixtures.webp(ImageFixtures.appFile(ctx, pic), picW, picH)
        ids += pic
        runBlocking { imageDao.upsert(ImageFixtures.row(pic, f, picW, picH, noteId = id)) }
        app.seed(id, md, System.currentTimeMillis())
        val e = app.open(id)
        awaitPicture(e)
        return e
    }

    private fun awaitPicture(e: TesseraEditText, what: (Shown) -> Boolean = { it.loaded }) {
        val until = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < until) {
            if (app.ui { shownImages(e).firstOrNull()?.let(what) } == true) return
            Thread.sleep(50)
        }
        error("the picture is not as wanted: ${app.ui { shownImages(e) }}")
    }

    private fun picture(e: TesseraEditText) = app.ui { shownImages(e).single() }

    /** The picture's box in the window (the editor's place plus the box in the editor). */
    private fun pictureInWindow(e: TesseraEditText): Rect = app.ui {
        val loc = IntArray(2).also { e.getLocationInWindow(it) }
        Rect(shownImages(e).single().rect).apply { offset(loc[0], loc[1]) }
    }

    private fun send(action: Int, x: Float, y: Float, down: Long) {
        val ev = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
        app.instrumentation.sendPointerSync(ev)
        ev.recycle()
    }

    private fun tap(x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, x, y, t)
        Thread.sleep(60)
        send(MotionEvent.ACTION_UP, x, y, t)
        app.settle(600)
    }

    /** A finger in the middle of what is seen of the picture: inside the editor, above the keyboard. */
    private fun tapPicture(e: TesseraEditText) {
        val (x, y) = app.ui {
            val loc = IntArray(2).also { e.getLocationInWindow(it) }
            val r = Rect(shownImages(e).single().rect)
            check(r.intersect(0, 0, e.width, e.height)) { "the picture is not in the editor's view: ${shownImages(e)}" }
            r.offset(loc[0], loc[1])
            val imeTop = e.rootView.height - (e.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0)
            r.bottom = minOf(r.bottom, imeTop)
            check(r.height() > 20) { "the picture is behind the keyboard: $r" }
            r.exactCenterX() to r.exactCenterY()
        }
        tap(x, y)
    }

    /** A finger at the end of the line of [needle]: the caret there, the keyboard up. */
    private fun tapText(e: TesseraEditText, needle: String) {
        val (x, y) = app.ui {
            val loc = IntArray(2).also { e.getLocationInWindow(it) }
            val l = e.layout
            val line = l.getLineForOffset(e.text.toString().indexOf(needle))
            loc[0] + e.totalPaddingLeft + l.getLineRight(line) - 4f to loc[1] + e.totalPaddingTop - e.scrollY + (l.getLineTop(line) + l.getLineBottom(line)) / 2f
        }
        tap(x, y)
    }

    private fun imeShown(e: TesseraEditText) = app.ui { e.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }

    private fun awaitIme(e: TesseraEditText, shown: Boolean) {
        val until = SystemClock.uptimeMillis() + 10_000
        while (imeShown(e) != shown && SystemClock.uptimeMillis() < until) Thread.sleep(50)
        assertEquals("the keyboard shown", shown, imeShown(e))
        app.settle(800)
    }

    /** What the window shows in each frame, as long as it is recorded. */
    private inner class Frames<T>(private val sample: () -> T?) {
        val list = CopyOnWriteArrayList<T>()
        private val listener = ViewTreeObserver.OnPreDrawListener { sample()?.let { list += it }; true }
        fun start() = apply { app.ui { app.activity().window.decorView.viewTreeObserver.addOnPreDrawListener(listener) } }
        fun stop() = app.ui { app.activity().window.decorView.viewTreeObserver.removeOnPreDrawListener(listener) }
    }

    private fun handles(): List<SemanticsNode> =
        compose.onAllNodesWithContentDescription(ctx.getString(R.string.image_resize_handle_description)).fetchSemanticsNodes()

    private fun rotateButton(): SemanticsNode? =
        compose.onAllNodesWithContentDescription(ctx.getString(R.string.image_rotate_button_description)).fetchSemanticsNodes().firstOrNull()

    /** The four corners of the picture against the centers of the four handles: how far the farthest handle is from its corner. */
    private fun handlesOffCorners(e: TesseraEditText): Float {
        val r = pictureInWindow(e)
        val corners = listOf(PointF(r.left.toFloat(), r.top.toFloat()), PointF(r.right.toFloat(), r.top.toFloat()), PointF(r.left.toFloat(), r.bottom.toFloat()), PointF(r.right.toFloat(), r.bottom.toFloat()))
        val centers = handles().map { it.boundsInWindow.center }
        assertEquals("four handles", 4, centers.size)
        return corners.maxOf { c -> centers.minOf { maxOf(abs(it.x - c.x), abs(it.y - c.y)) } }
    }

    private fun awaitHandles() = compose.waitUntil(5_000) { handles().size == 4 }

    // ------------------------------------------------------------------ selection

    @Test
    fun aTapOnAPictureSelectsItTheKeyboardGoesAndDoesNotComeBack() {
        app.launch()
        val e = openNote("qa-edit-tap")
        tapText(e, "above two")
        awaitIme(e, shown = true)
        // a small phone: with the keyboard up the picture is below it; the user scrolls it up to the top of what is seen
        app.ui { e.scrollBy(0, shownImages(e).single().rect.top - 10) }
        app.settle(400)
        val ps = picture(e).ps
        val ime = Frames { imeShown(e) }.start()
        tapPicture(e)
        app.settle(2_000)
        ime.stop()
        val seq = ime.list.toList()
        val firstHidden = seq.indexOfFirst { !it }
        log("keyboard per frame after the tap on the picture (${seq.size} frames): ${seq.joinToString("") { if (it) "K" else "-" }}")
        assertEquals("the picture is the selection", ps to ps + 1, app.ui { e.selectionStart to e.selectionEnd })
        assertFalse("the keyboard went away", imeShown(e))
        assertTrue("after it went it did not come back: ${seq.joinToString("") { if (it) "K" else "-" }}", firstHidden >= 0 && seq.drop(firstHidden).none { it })

        // the handles stand on the corners of the picture, and follow it when the note scrolls
        awaitHandles()
        val off = handlesOffCorners(e)
        log("handles off the corners: %.1f px".format(off))
        assertTrue("the handles are on the corners: %.1f px off".format(off), off <= 2f)
        val before = pictureInWindow(e)
        app.ui { e.scrollBy(0, 150) }
        app.settle(500)
        val after = pictureInWindow(e)
        val offScrolled = handlesOffCorners(e)
        log("scrolled: picture ${before.top} -> ${after.top}, handles off the corners %.1f px".format(offScrolled))
        assertEquals("the picture moved with the scroll", before.top - 150, after.top)
        assertTrue("after the scroll the handles are on the corners: %.1f px off".format(offScrolled), offScrolled <= 2f)
    }

    @Test
    fun aTapOnAPictureWithTheKeyboardClosedDoesNotOpenIt() {
        app.launch()
        val e = openNote("qa-edit-tap-closed")
        assertFalse("no keyboard after the note opened", imeShown(e))
        val ps = picture(e).ps
        val ime = Frames { imeShown(e) }.start()
        tapPicture(e)
        app.settle(2_000)
        ime.stop()
        log("keyboard per frame (closed before): ${ime.list.joinToString("") { if (it) "K" else "-" }}")
        assertEquals("the picture is the selection", ps to ps + 1, app.ui { e.selectionStart to e.selectionEnd })
        assertTrue("the keyboard never showed", ime.list.none { it })
    }

    // ------------------------------------------------------------------ keys over a selected picture

    @Test
    fun backspaceAndDeleteOverASelectedPictureDeleteItsParagraph() {
        app.launch()
        val ways = listOf<Pair<String, (TesseraEditText) -> Unit>>(
            "hardware Backspace" to { _ -> app.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DEL) },
            "hardware Delete" to { _ -> app.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_FORWARD_DEL) },
            "keyboard Backspace key event" to { e -> app.ime(e) { sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)); sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL)) } },
            "Gboard Backspace (caret to the end, one before it)" to { e -> app.ime(e) { val end = e.selectionEnd; setSelection(end, end); deleteSurroundingText(1, 0) } },
        )
        val bad = ArrayList<String>()
        ways.forEachIndexed { i, (name, key) ->
            val id = "qa-edit-del-$i"
            val e = openNote(id)
            tapPicture(e)
            key(e)
            app.settle(500)
            val want = note(id).replace("![](tessera-img://$id-pic)\n", "")
            val shown = app.md(e)
            app.back()
            val saved = app.entity(id)?.content
            log("$name: shown ${q(shown)}, saved ${q(saved)}")
            if (shown != want || saved != want) bad += "$name: shown ${q(shown)}, saved ${q(saved)}"
        }
        assertEquals("a selected picture not deleted whole", emptyList<String>(), bad)
    }

    /** Delete at the end of the line before a picture: the first press selects the picture (it is seen what goes), the second deletes it. */
    @Test
    fun deleteBeforeAPictureSelectsItFirstThenDeletesIt() {
        app.launch()
        val id = "qa-edit-del-before"
        val e = openNote(id)
        val ps = picture(e).ps
        app.ui { e.requestFocus(); e.setSelection(ps - 1) } // the end of "above three"
        app.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_FORWARD_DEL)
        app.settle(400)
        val afterFirst = app.ui { e.selectionStart to e.selectionEnd }
        val mdFirst = app.md(e)
        app.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_FORWARD_DEL)
        app.settle(400)
        val mdSecond = app.md(e)
        log("Delete before a picture: first -> selection $afterFirst, ${q(mdFirst)}; second -> ${q(mdSecond)}")
        assertEquals("the first Delete selects the picture", ps to ps + 1, afterFirst)
        assertEquals("the first Delete changes nothing", note(id), mdFirst)
        assertEquals("the second Delete deletes it", note(id).replace("![](tessera-img://$id-pic)\n", ""), mdSecond)
    }

    @Test
    fun aCutPictureIsPastedBack() {
        app.launch()
        val id = "qa-edit-cut"
        val e = openNote(id)
        val src = "![](tessera-img://$id-pic)"
        val bad = ArrayList<String>()
        val ways = listOf<Pair<String, Pair<() -> Unit, () -> Unit>>>(
            "the menu's Cut and Paste" to ({ app.ui { e.onTextContextMenuItem(android.R.id.cut); Unit } } to { app.ui { e.onTextContextMenuItem(android.R.id.paste); Unit } }),
            "Ctrl+X and Ctrl+V" to ({ ctrl(KeyEvent.KEYCODE_X) } to { ctrl(KeyEvent.KEYCODE_V) }),
        )
        for ((name, op) in ways) {
            tapPicture(e)
            op.first()
            app.settle(400)
            val cut = app.md(e)
            val clip = app.ui { ctx.getSystemService(ClipboardManager::class.java).primaryClip?.let { c -> (0 until c.itemCount).map { c.getItemAt(it).coerceToText(ctx) } } }
            app.ui { e.setSelection(e.text.toString().indexOf("line 3") + "line 3".length) }
            op.second()
            app.settle(400)
            val pasted = app.md(e)
            log("$name: after the cut ${q(cut)}; the system clipboard $clip; after the paste ${q(pasted)}")
            if (cut.contains(src)) bad += "$name: the cut left the picture: ${q(cut)}"
            if (!pasted.contains("line 3\n$src\nline 4")) bad += "$name: the paste did not give the picture back after line 3: ${q(pasted)}"
            awaitPicture(e)
            app.ui { e.undo(); e.undo() } // the picture back where it stood
            app.settle(300)
        }
        assertEquals("cut and paste of a picture", emptyList<String>(), bad)
    }

    private fun ctrl(code: Int) {
        val t = SystemClock.uptimeMillis()
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        app.instrumentation.sendKeySync(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta))
        app.instrumentation.sendKeySync(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
        app.instrumentation.sendKeySync(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
        app.instrumentation.sendKeySync(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, 0))
        app.instrumentation.waitForIdleSync()
    }

    @Test
    fun typingOverASelectedPictureGivesABodyParagraph() {
        app.launch()
        val ways = listOf<Pair<String, (TesseraEditText) -> Unit>>(
            "keyboard commitText" to { e -> app.ime(e) { commitText("x", 1) } },
            "keyboard composing" to { e -> app.ime(e) { setComposingText("x", 1); finishComposingText() } },
            "hardware key" to { _ -> app.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_X) },
        )
        val bad = ArrayList<String>()
        ways.forEachIndexed { i, (name, type) ->
            val id = "qa-edit-type-$i"
            val e = openNote(id)
            tapPicture(e)
            type(e)
            app.settle(500)
            val want = note(id).replace("![](tessera-img://$id-pic)", "x")
            val shown = app.md(e)
            val kind = app.kinds(e)[4]
            app.back()
            val saved = app.entity(id)?.content
            log("$name: shown ${q(shown)}, kind $kind, saved ${q(saved)}")
            if (shown != want || saved != want || kind != ParagraphKind.Body) bad += "$name: shown ${q(shown)}, kind $kind, saved ${q(saved)}"
        }
        assertEquals("typing over a selected picture", emptyList<String>(), bad)
    }

    // ------------------------------------------------------------------ resize and turn

    /** A finger on the bottom-right handle, dragged left by [dx]; [whileHeld] runs before it is lifted. */
    private fun dragCorner(dx: Float, whileHeld: () -> Unit = {}) {
        awaitHandles()
        val h = handles().maxBy { it.boundsInWindow.center.x + it.boundsInWindow.center.y }.boundsInWindow.center
        val t = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, h.x, h.y, t)
        val steps = 16
        for (i in 1..steps) {
            Thread.sleep(16)
            send(MotionEvent.ACTION_MOVE, h.x - dx * i / steps, h.y, t)
        }
        app.instrumentation.waitForIdleSync()
        Thread.sleep(300)
        whileHeld()
        send(MotionEvent.ACTION_UP, h.x - dx, h.y, t)
        app.settle(600)
    }

    private fun tapRotate() {
        compose.waitUntil(5_000) { rotateButton() != null }
        val c = rotateButton()!!.boundsInWindow.center
        tap(c.x, c.y)
    }

    @Test
    fun aCornerDragResizesLiveAndOneUndoBringsTheWidthBack() {
        app.launch()
        val id = "qa-edit-resize"
        val e = openNote(id)
        val original = app.md(e)
        tapPicture(e)
        val r0 = picture(e).rect
        var live: Shown? = null
        var mdWhileHeld = ""
        dragCorner(r0.width() * 0.4f) { live = picture(e); mdWhileHeld = app.md(e) }
        val l = live!!
        log("resize: ${r0.width()}x${r0.height()} -> while held ${l.rect.width()}x${l.rect.height()} (picture shown ${l.loaded})")
        assertTrue("while dragged the picture is narrower: ${l.rect.width()} < ${r0.width()}", l.rect.width() < r0.width() - 50)
        assertTrue("while dragged the aspect stays: ${l.rect.width()}x${l.rect.height()} vs ${r0.width()}x${r0.height()}", abs(l.rect.height() * r0.width() - r0.height() * l.rect.width()) <= r0.width() * 2)
        assertTrue("while dragged the picture is shown, not a tile", l.loaded)
        assertEquals("while dragged nothing is edited", original, mdWhileHeld)
        val resized = app.md(e)
        val w = Regex("tessera-img://$id-pic\\?w=(\\d+)").find(resized)?.groupValues?.get(1)?.toInt()
        val expected = Math.round(l.rect.width() * 100f / r0.width())
        log("resize: src after the drag ${Regex("!\\[]\\([^)]*\\)").find(resized)?.value}, the width shown at the end ${l.rect.width()} of ${r0.width()} = $expected%")
        assertNotNull("the src got w: ${q(resized)}", w)
        assertTrue("w=$w is the width shown at the end ($expected%)", abs(w!! - expected) <= 2)
        awaitPicture(e) { it.loaded && abs(it.rect.width() - l.rect.width()) <= 2 }
        app.ui { e.undo() }
        app.settle(500)
        assertEquals("one undo: the width it had", original, app.md(e))
        awaitPicture(e) { it.loaded && it.rect.width() == r0.width() }
    }

    @Test
    fun aTurnShowsTheOldPictureUntilTheNewOneComes() {
        app.launch()
        val id = "qa-edit-rotate"
        val e = openNote(id)
        tapPicture(e)
        val before = picture(e)
        val held = decoder.fileDecoder
        val gate = ImageFixtures.hold(decoder)
        val frames = Frames { app.editorOrNull()?.let { shownImages(it).firstOrNull() } }
        try {
            frames.start()
            tapRotate()
            app.settle(800)
            val mdHeld = app.md(e)
            val whileHeld = frames.list.toList()
            gate.countDown()
            awaitPicture(e) { it.loaded && it.rect.height() > it.rect.width() }
            app.settle(300)
            frames.stop()
            val after = picture(e)
            log("turn: ${whileHeld.size} frames before the new picture: ${whileHeld.map { "${if (it.loaded) "P" else "T"}${it.rect.width()}x${it.rect.height()}" }.distinct()}; after ${after.rect.width()}x${after.rect.height()}")
            assertTrue("the src got rot=90 at once: ${q(mdHeld)}", mdHeld.contains("tessera-img://$id-pic?rot=90"))
            assertTrue("frames were drawn while the decode was held", whileHeld.isNotEmpty())
            assertTrue("until the new picture came, the old one was shown in every frame (no tile)", whileHeld.all { it.loaded && !it.failed })
            assertTrue("until the new picture came it stood as it was: ${whileHeld.map { it.rect.width() to it.rect.height() }.distinct()} vs ${before.rect}", whileHeld.all { it.rect.width() == before.rect.width() && it.rect.height() == before.rect.height() })
            assertTrue("the turned picture is taller than wide: ${after.rect}", after.rect.height() > after.rect.width())
        } finally {
            gate.countDown()
            decoder.fileDecoder = held
            frames.stop()
        }
    }

    // known (P2-1b-fix): the second turn and a turn after a resize (and back) lose what the first gesture did

    @Test
    fun turningTwiceGivesRot180() {
        app.launch()
        val id = "qa-edit-rot2"
        val e = openNote(id)
        tapPicture(e)
        tapRotate()
        awaitPicture(e) { it.loaded && it.rect.height() > it.rect.width() }
        app.settle(500)
        tapRotate()
        app.settle(800)
        val md = app.md(e)
        log("two turns: ${q(md)}")
        assertTrue("two turns: rot=180 — ${q(md)}", md.contains("tessera-img://$id-pic?rot=180"))
    }

    @Test
    fun aTurnAfterAResizeKeepsTheWidth() {
        app.launch()
        val id = "qa-edit-resize-rot"
        val e = openNote(id)
        tapPicture(e)
        dragCorner(picture(e).rect.width() * 0.4f)
        val w = Regex("tessera-img://$id-pic\\?w=(\\d+)").find(app.md(e))?.groupValues?.get(1)
        assertNotNull("the resize gave w: ${q(app.md(e))}", w)
        awaitPicture(e)
        app.settle(500)
        tapRotate()
        app.settle(800)
        val md = app.md(e)
        log("resize then turn: ${q(md)}")
        assertTrue("resize then turn: w=$w&rot=90 — ${q(md)}", md.contains("tessera-img://$id-pic?w=$w&rot=90"))
    }

    @Test
    fun aResizeAfterATurnKeepsTheTurn() {
        app.launch()
        val id = "qa-edit-rot-resize"
        val e = openNote(id)
        tapPicture(e)
        tapRotate()
        awaitPicture(e) { it.loaded && it.rect.height() > it.rect.width() }
        app.settle(500)
        dragCorner(picture(e).rect.width() * 0.4f)
        val md = app.md(e)
        log("turn then resize: ${q(md)}")
        assertTrue("turn then resize: w=..&rot=90 — ${q(md)}", Regex("tessera-img://$id-pic\\?w=\\d+&rot=90").containsMatchIn(md))
    }

    // ------------------------------------------------------------------ inserting

    /** A PNG in the shared storage (as a gallery or another app holds it): a `content:` URI of type `image/png`. */
    private fun sharedPng(name: String): Uri {
        val b = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(ImageFixtures.FILL) }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name-${System.currentTimeMillis()}.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/native-editor-qa")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        ctx.contentResolver.openOutputStream(uri)!!.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        mediaUris += uri
        return uri
    }

    /** After an insert: the note shows the new picture as its own paragraph after "above one", the file and the row are there, it is saved. */
    private fun checkInserted(id: String, e: TesseraEditText, how: String) {
        val until = SystemClock.uptimeMillis() + 15_000
        while (!Regex("tessera-img://[0-9a-f-]{36}").containsMatchIn(app.md(e)) && SystemClock.uptimeMillis() < until) Thread.sleep(100)
        val md = app.md(e)
        val newId = Regex("tessera-img://([0-9a-f-]{36})").find(md)?.groupValues?.get(1)
        log("$how: ${q(md)}")
        assertNotNull("$how: a picture was inserted: ${q(md)}", newId)
        ids += newId!!
        assertTrue("$how: as its own paragraph after the caret's: ${q(md)}", md.startsWith("# Insert\nabove one\n![](tessera-img://$newId)\nbelow"))
        assertTrue("$how: the file is the app's", ImageFixtures.appFile(ctx, newId).exists())
        val row = runBlocking { imageDao.byId(newId) }
        assertNotNull("$how: the row", row)
        awaitPicture(e) { it.loaded }
        log("$how: picture ${picture(e)}, row $row")
        app.back()
        assertEquals("$how: saved", md, app.entity(id)?.content)
    }

    private fun caretAfter(e: TesseraEditText, needle: String) = app.ui {
        e.requestFocus()
        e.setSelection(e.text.toString().indexOf(needle) + needle.length)
    }

    @Test
    fun anImageFromTheClipboardIsInserted() {
        app.launch()
        val id = "qa-ins-clip"
        app.seed(id, "# Insert\nabove one\nbelow", System.currentTimeMillis())
        val e = app.open(id)
        caretAfter(e, "above one")
        val uri = sharedPng("clip")
        app.ui { ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newUri(ctx.contentResolver, "picture", uri)) }
        app.ui { e.onTextContextMenuItem(android.R.id.paste) }
        checkInserted(id, e, "clipboard")
    }

    @Test
    fun anImageFromTheKeyboardIsInserted() {
        app.launch()
        val id = "qa-ins-ime"
        app.seed(id, "# Insert\nabove one\nbelow", System.currentTimeMillis())
        val e = app.open(id)
        caretAfter(e, "above one")
        val uri = sharedPng("ime")
        val (types, accepted) = app.ui {
            val info = EditorInfo()
            val ic: InputConnection = e.onCreateInputConnection(info)!!
            // what Gboard does: it reads the types the field takes, then commits the content with a read grant
            EditorInfoCompat.getContentMimeTypes(info).toList() to
                ic.commitContent(InputContentInfo(uri, ClipDescription("picture", arrayOf("image/png"))), InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
        }
        log("keyboard: the field takes $types, commitContent -> $accepted")
        assertTrue("the field says it takes images: $types", types.any { it == "image/*" || it == "image/png" })
        assertTrue("commitContent accepted", accepted)
        checkInserted(id, e, "keyboard commitContent")
    }

    // ------------------------------------------------------------------ TalkBack and the viewer

    @Test
    fun talkBackReadsAPictureAndADoubleTapSelectsIt() {
        app.launch()
        val e = openNote("qa-edit-a11y")
        val ps = picture(e).ps
        val node = app.ui { e.accessibilityNodeProvider!!.createAccessibilityNodeInfo(ps)!! }
        log("a11y node: ${node.contentDescription}, class ${node.className}, actions ${node.actionList.map { it.id }}")
        assertEquals(ctx.getString(R.string.editor_object_image_a11y), node.contentDescription?.toString())
        assertTrue("a double tap selects: the node has a click", node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
        app.ui { e.accessibilityNodeProvider!!.performAction(ps, AccessibilityNodeInfo.ACTION_CLICK, null) }
        app.settle(400)
        assertEquals("selected", ps to ps + 1, app.ui { e.selectionStart to e.selectionEnd })
    }

    /** A viewer of a shared note (the app needs a signed-in viewer, which the device has not got): the same `NoteBody(editable = false)`. */
    @Test
    fun aViewerSeesThePictureAndCannotSelectIt() {
        val pic = "qa-view-pic"
        ImageFixtures.webp(ImageFixtures.appFile(ctx, pic), 1001, 563)
        ids += pic
        val md = "# Shared\nabove\n![](tessera-img://$pic)\nbelow"
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            scenario.onActivity { a ->
                a.setContent {
                    TesseraTheme(darkTheme = false, dynamicColor = false, palette = ThemePalette.WARM) {
                        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                            NoteBody(
                                markdown = md, onMarkdownChange = {}, onActionsReady = {}, onFormatStateChanged = {}, editable = false,
                                modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp),
                                imageSource = NoteObjectImageSource(decoder, scope),
                            )
                        }
                    }
                }
            }
            compose.waitUntil(10_000) { app.editorOrNull() != null }
            val e = app.editorOrNull()!!
            awaitPicture(e)
            val ps = picture(e).ps
            tapPicture(e)
            app.settle(600)
            val sel = app.ui { e.selectionStart to e.selectionEnd }
            val node = app.ui { e.accessibilityNodeProvider!!.createAccessibilityNodeInfo(ps)!! }
            log("viewer: picture ${picture(e)}, selection after a tap $sel, handles ${handles().size}, a11y ${node.contentDescription} actions ${node.actionList.map { it.id }}")
            assertTrue("the viewer sees the picture", picture(e).loaded)
            assertFalse("a tap does not select it", sel == ps to ps + 1)
            assertEquals("no handles", 0, handles().size)
            assertEquals(ctx.getString(R.string.editor_object_image_a11y), node.contentDescription?.toString())
            assertTrue("TalkBack: no action on the picture", node.actionList.none { it.id == AccessibilityNodeInfo.ACTION_CLICK })
        } finally {
            scenario.close()
        }
    }

    private fun q(s: String?) = s?.let { "\"" + it.take(140).replace("\n", "\\n") + "\"" } ?: "null"

    private companion object {
        const val TAG = "NativeImageEdit"
    }
}
