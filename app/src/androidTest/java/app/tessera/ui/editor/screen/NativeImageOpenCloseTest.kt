package app.tessera.ui.editor.screen

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.ui.test.onAllNodesWithTag
import app.tessera.core.ui.UiTestTags
import app.tessera.data.images.NoteImageDecoder
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.ui.editor.engine.TesseraEditText
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2 acceptance §8 item 1 on the device: old notes with pictures (saved by the Aztec path, with `w` and `rot`), whose pictures are on
 * the device, open in the native editor, show their pictures, and leave without a change — the same `content`, `updatedAt`, `syncedAt`.
 * And a Back right after the tap on the card, while the sizes of the pictures are still being read, does not delete the note.
 */
@HiltAndroidTest
class NativeImageOpenCloseTest : NativeAppTest() {
    @Inject lateinit var imageDao: NoteImageDao
    @Inject lateinit var decoder: NoteImageDecoder

    private val ctx get() = app.instrumentation.targetContext
    private val ids = HashSet<String>()

    @After
    fun clean() {
        ImageFixtures.removeApp(ctx, ids)
    }

    private fun srcIds(md: String) = Regex("tessera-img://([^?)\\s]+)").findAll(md).map { it.groupValues[1] }.toList()

    /** The picture [id] on the device: a file of this device with its row (every other one), or a picture downloaded earlier (no row, in the cache). */
    private fun putPicture(id: String, i: Int) {
        val (w, h) = listOf(1001 to 563, 301 to 401, 2047 to 1153)[i % 3]
        ids += id
        if (i % 2 == 0) {
            val f = ImageFixtures.webp(ImageFixtures.appFile(ctx, id), w, h)
            runBlocking { imageDao.upsert(ImageFixtures.row(id, f, w, h)) }
        } else {
            ImageFixtures.webp(ImageFixtures.cacheFile(ctx, id), w, h)
        }
    }

    private fun awaitPictures(e: TesseraEditText, n: Int): List<Shown> {
        val until = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < until) {
            val s = app.ui { shownImages(e) }
            if (s.size == n && s.all { it.loaded || it.failed }) return s
            Thread.sleep(50)
        }
        error("pictures did not come: ${app.ui { shownImages(e) }}")
    }

    private fun tapLastLine(e: TesseraEditText) {
        val (x, y) = app.ui {
            val loc = IntArray(2).also { e.getLocationOnScreen(it) }
            val l = e.layout
            val line = l.lineCount - 1
            val top = l.getLineTop(line) + e.totalPaddingTop - e.scrollY
            loc[0] + e.totalPaddingLeft + 20f to loc[1] + (top + l.getLineBottom(line) + e.totalPaddingTop - e.scrollY) / 2f
        }
        val t = SystemClock.uptimeMillis()
        app.instrumentation.sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        app.instrumentation.sendPointerSync(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0))
        app.settle(800)
    }

    @Test
    fun oldNotesWithPicturesOpenAndCloseWithoutAnyChange() {
        val notes = LegacyNotes.all.filter { it.contains("tessera-img://") } + listOf(
            "# Trip\nDay one\n![](tessera-img://trip-1)\n![](tessera-img://trip-2?w=50)\nDay two\n![](tessera-img://trip-3?rot=90)\n- [ ] pack\n![](tessera-img://trip-4?w=70&rot=270)",
            "![](tessera-img://only-1)",
            "text\n\n![](tessera-img://blank-1?w=30)\n\n\nmore",
            "> quote\n![](tessera-img://q-1?rot=180)\n```\ncode\n```\n![](tessera-img://q-2?w=10)",
            "![](tessera-img://broken-1)\nafter a broken file",
            "![](tessera-img://missing-1)\nafter a picture that is nowhere",
        )
        var k = 0
        for (id in notes.flatMap(::srcIds).distinct()) if (id != "broken-1" && id != "missing-1") putPicture(id, k++)
        ImageFixtures.webp(ImageFixtures.appFile(ctx, "broken-1"), 1001, 563).also { ImageFixtures.truncate(it, 500) }
        ids += "broken-1"
        app.launch()
        val changed = ArrayList<String>()
        val notShown = ArrayList<String>()
        notes.forEachIndexed { i, md ->
            val id = "qa-img-open-close-$i"
            val ts = BASE + i * 1_000L
            app.seed(id, md, ts)
            val e = app.open(id)
            val shown = awaitPictures(e, srcIds(md).size)
            app.settle(800)
            shown.forEach { s ->
                val missing = s.src.contains("broken-1") || s.src.contains("missing-1")
                if (s.loaded == missing) notShown += "#$i ${s.src}: loaded ${s.loaded}, failed ${s.failed}"
            }
            val way = when (i % 3) {
                0 -> "Back arrow".also { app.back() }
                1 -> "system Back".also { app.systemBack() }
                else -> "tap in the text, Back arrow".also { tapLastLine(e); app.back() }
            }
            val after = app.entity(id)
            Log.i(TAG, "#$i ($way): ${shown.size} pictures ${shown.map { "${it.rect.width()}x${it.rect.height()}${if (it.failed) " unavailable" else ""}" }}, content same ${after?.content == md}, updatedAt +${after?.let { it.updatedAt - ts }}")
            if (after == null || after.content != md || after.updatedAt != ts || after.syncedAt != ts) {
                changed += "#$i ($way) ${q(md)} -> " + (after?.let { "content ${if (it.content == md) "same" else q(it.content)}, updatedAt +${it.updatedAt - ts}, syncedAt ${it.syncedAt}" } ?: "gone")
            }
        }
        assertEquals("pictures not shown as they should be", emptyList<String>(), notShown)
        assertEquals("notes with pictures changed by opening and closing them (${changed.size} of ${notes.size})", emptyList<String>(), changed)
    }

    /**
     * The review blocker of P2-1b: the sizes of the pictures are read before the note reaches the screen; a Back in that window used to find
     * an empty text and delete the note as blank. Many pictures make the window wide; a run counts only when the Back came before the editor.
     */
    @Test
    fun aBackWhileThePicturesAreReadDoesNotDeleteTheNote() {
        // the window is measured, not guessed: the decoder is the ViewModel's (a singleton); how many of the note's pictures it has read the
        // bounds of when the Back comes says where the prefetch was. 0 < read < 80: in the middle of it, where the old code deleted the note.
        val n = 80
        fun md(k: Int) = "# Many pictures\n" + (1..n).joinToString("\n") { "![](tessera-img://qa-many-$k-$it)" } + "\nthe end"
        val delays = listOf(0L, 0L, 0L, 5L, 10L, 20L, 40L, 60L, 90L, 130L)
        for (k in delays.indices) for (i in 1..n) {
            val id = "qa-many-$k-$i"
            val f = ImageFixtures.webp(ImageFixtures.appFile(ctx, id), 64, 48)
            ids += id
            runBlocking { imageDao.upsert(ImageFixtures.row(id, f, 64, 48)) }
        }
        app.launch()
        val runs = ArrayList<String>()
        val lost = ArrayList<String>()
        var inTheMiddle = 0
        for ((k, delay) in delays.withIndex()) {
            val id = "qa-img-back-$k"
            val ts = BASE + k * 1_000L
            val note = md(k)
            app.seed(id, note, ts)
            val srcs = (1..n).map { "tessera-img://qa-many-$k-$it" }
            app.tapCard(id)
            // the editor's screen is there (its Back arrow): a Back before it would leave the list, not the editor
            compose.waitUntil(5_000) { compose.onAllNodesWithTag(UiTestTags.EditorBack).fetchSemanticsNodes().isNotEmpty() }
            if (delay > 0) Thread.sleep(delay)
            val read = srcs.count { decoder.cachedSize(it, 640) != null }
            app.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(15_000) { app.editorOrNull() == null && compose.onAllNodesWithTag(UiTestTags.EditorBack).fetchSemanticsNodes().isEmpty() }
            app.settle(1_500)
            if (read in 1 until n) inTheMiddle++
            val after = app.entity(id)
            runs += "delay $delay ms: $read of $n sizes read at the Back, note ${if (after == null) "gone" else if (after.deletedAt != null) "deleted" else "kept"}"
            if (after == null || after.deletedAt != null || after.content != note || after.updatedAt != ts) {
                lost += "delay $delay ms ($read of $n read): " + (after?.let { "deletedAt ${it.deletedAt}, content same ${it.content == note}, updatedAt +${it.updatedAt - ts}" } ?: "gone")
            }
        }
        runs.forEach { Log.i(TAG, "back: $it") }
        assertTrue("a Back came in the middle of the prefetch at least once: $runs", inTheMiddle > 0)
        assertEquals("notes lost by a Back while the pictures were read", emptyList<String>(), lost)
        // and the note opens as it was (the newest card: the top of the list)
        val e = app.open("qa-img-back-${delays.lastIndex}")
        assertEquals(md(delays.lastIndex), app.md(e))
        app.back()
    }

    private fun q(s: String) = "\"" + s.take(80).replace("\n", "\\n") + "\""

    private companion object {
        const val TAG = "NativeImageOpenClose"
        const val BASE = 1_700_000_000_000L
    }
}
