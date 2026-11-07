package app.tessera.ui.editor.screen

import android.os.SystemClock
import android.view.MotionEvent
import app.tessera.ui.editor.engine.TesseraEditText
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec §9 item 2: with the native editor on, opening a note and leaving it without an edit changes nothing — the same `content`,
 * byte for byte, no new `updatedAt`, no pending sync. Every legacy form (the Aztec-era corpus, non-canonical `* x`, `1. 1.`, tabs,
 * tags, tables, images, scripts) goes through the real app: the list, a tap on the card, the editor, and out by the Back arrow, the
 * system Back, or after a tap in the text (focus, a caret, the keyboard binding to the word under it).
 */
@HiltAndroidTest
class NativeEditorOpenCloseTest : NativeAppTest() {

    private fun tapInText(e: TesseraEditText) {
        val (x, y) = app.ui {
            val loc = IntArray(2).also { e.getLocationOnScreen(it) }
            val l = e.layout
            val line = 0
            val x = loc[0] + e.totalPaddingLeft + l.getLineRight(line).coerceAtMost(200f) / 2 + 1
            val y = loc[1] + e.totalPaddingTop - e.scrollY + (l.getLineTop(line) + l.getLineBottom(line)) / 2f
            x to y
        }
        val t = SystemClock.uptimeMillis()
        app.instrumentation.sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        app.instrumentation.sendPointerSync(MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0))
        app.settle(800)
    }

    @Test
    fun legacyNotesOpenAndCloseWithoutAnyChange() {
        app.launch()
        val changed = ArrayList<String>()
        LegacyNotes.all.forEachIndexed { i, md ->
            val id = "qa-open-close-$i"
            val ts = BASE + i * 1_000L
            app.seed(id, md, ts)
            val e = app.open(id)
            val way = when (i % 3) {
                0 -> "Back arrow".also { app.back() }
                1 -> "system Back".also { app.systemBack() }
                else -> "tap in the text, Back arrow".also { tapInText(e); app.back() }
            }
            val after = app.entity(id)
            if (after == null || after.content != md || after.updatedAt != ts || after.syncedAt != ts) {
                changed += "#$i (${way}) ${quote(md)} -> " +
                    (after?.let { "content ${if (it.content == md) "same" else quote(it.content)}, updatedAt +${it.updatedAt - ts}, syncedAt ${it.syncedAt}" } ?: "gone")
            }
        }
        assertEquals("notes changed by opening and closing them (${changed.size} of ${LegacyNotes.all.size})", emptyList<String>(), changed)
    }

    private fun quote(s: String) = "\"" + s.take(80).replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""

    private companion object {
        const val BASE = 1_700_000_000_000L
    }
}
