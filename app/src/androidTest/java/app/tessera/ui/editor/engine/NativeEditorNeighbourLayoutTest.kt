package app.tessera.ui.editor.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import app.tessera.ui.editor.engine.spans.ListItemSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * `NeighbourLayoutTest` on the device. An edit changes how its neighbours look — the rounded first / last line of a mono series,
 * the numbers of the items below — and on a phone the text is drawn through the `RenderNode`s the `Editor` of a `TextView` keeps
 * per block of lines: a block that was not invalidated is shown from its old recording. So the window's own pixels (PixelCopy, the
 * hardware pipeline) of the live editor, after the edit, must be those of a fresh editor of the same text. A software `View.draw`
 * into a bitmap goes past those caches; it is compared too, as the layout check.
 *
 * The two editors stand side by side in one window, same size; the live one is never detached (that would drop its caches).
 */
class NativeEditorNeighbourLayoutTest {

    private class Board(val e: DeviceEditor, val w: Int, val h: Int) {
        var fresh: TesseraEditText? = null
    }

    private fun board(md: String): Board {
        val e = DeviceEditor(md)
        val (w, h) = e.ui {
            val w = e.container.width / 2
            val h = e.container.height / 2
            e.view.layoutParams = FrameLayout.LayoutParams(w, h)
            w to h
        }
        e.idle()
        return Board(e, w, h)
    }

    private fun InputConnection.press(code: Int) {
        sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun soft(v: TesseraEditText): Bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }

    private fun numbers(v: TesseraEditText): List<Int> {
        val t = v.text!!
        return t.getSpans(0, t.length, ListItemSpan::class.java).sortedBy { t.getSpanStart(it) }.map { it.number }
    }

    /** The live editor, as the window shows it now, against a fresh editor of its Markdown. */
    private fun assertLooksFresh(b: Board, label: String) {
        val e = b.e
        val live = e.capture(e.view)
        val fresh = e.ui {
            b.fresh?.let { e.container.removeView(it) }
            e.newEditor(e.view.markdown()).also {
                e.container.addView(it, FrameLayout.LayoutParams(b.w, b.h).apply { leftMargin = b.w })
                b.fresh = it
            }
        }
        val freshShot = e.capture(fresh)
        val (liveSoft, freshSoft) = e.ui { soft(e.view) to soft(fresh) }
        val (liveTops, freshTops) = e.ui {
            val l = e.view.layout
            val f = fresh.layout
            (0..l.lineCount).map { l.getLineTop(it) } to (0..f.lineCount).map { f.getLineTop(it) }
        }
        assertEquals("$label: text", e.ui { fresh.text.toString() }, e.text())
        assertEquals("$label: line tops", freshTops, liveTops)
        assertEquals("$label: numbers", e.ui { numbers(fresh) }, e.ui { numbers(e.view) })

        pixelDiff(live, freshShot)?.let { hw ->
            val name = "${javaClass.simpleName}-${label.replace(Regex("[^A-Za-z0-9]+"), "-")}"
            savePng(e.activity, "$name-live", live)
            savePng(e.activity, "$name-fresh", freshShot)
            fail("$label: on screen (hardware), live vs fresh: $hw, lines ${diffLines(e, live, freshShot)}\nmd: ${e.md()}")
        }
        assertNull("$label: View.draw (software), live vs fresh", pixelDiff(liveSoft, freshSoft))
    }

    /** The lines of the live layout the differing rows fall on. */
    private fun diffLines(e: DeviceEditor, a: Bitmap, b: Bitmap): List<Int> = e.ui {
        val l = e.view.layout
        val rows = (0 until a.height).filter { y -> (0 until a.width).any { a.getPixel(it, y) != b.getPixel(it, y) } }
        rows.map { l.getLineForVertical(it - e.view.totalPaddingTop + e.view.scrollY) }.distinct()
    }

    @Test
    fun enterAtTheEndOfMonoAndEditsAroundTheSeries() {
        val b = board("above\n```\none\ntwo\n```\nbelow")
        b.e.use { e ->
            assertLooksFresh(b, "start")

            e.caret(e.at("two", 3))
            e.ime { commitText("\n", 1) }
            assertEquals("Enter at the end of mono: a new mono line", "above\n```\none\ntwo\n\n```\nbelow", e.md())
            assertLooksFresh(b, "enter at the end of mono")

            e.ime { commitText("three", 1) }
            assertEquals("above\n```\none\ntwo\nthree\n```\nbelow", e.md())
            assertLooksFresh(b, "typed in the new line")

            e.select(e.at("one"), e.at("one", 4))
            e.ime { press(KeyEvent.KEYCODE_DEL) }
            assertEquals("the first line of the series deleted", "above\n```\ntwo\nthree\n```\nbelow", e.md())
            assertLooksFresh(b, "first line deleted")

            e.caret(e.at("three"))
            e.ime { press(KeyEvent.KEYCODE_DEL) }
            assertEquals("Backspace at the start of mono: Body in place", "above\n```\ntwo\n```\nthree\nbelow", e.md())
            assertLooksFresh(b, "last line left the series")
        }
    }

    @Test
    fun insertingAnItemInTheMiddleOfANumberedList() {
        val b = board("1. a\n2. b\n3. c\nx")
        b.e.use { e ->
            assertLooksFresh(b, "start")

            e.caret(e.at("a", 1))
            e.ime { commitText("\n", 1) }
            e.ime { commitText("z", 1) }
            assertEquals("1. a\n2. z\n3. b\n4. c\nx", e.md())
            assertLooksFresh(b, "item inserted")

            e.select(0, 2) // "a\n"
            e.ime { press(KeyEvent.KEYCODE_DEL) }
            assertEquals("1. z\n2. b\n3. c\nx", e.md())
            assertLooksFresh(b, "first item deleted")

            e.caret(0)
            e.ime { press(KeyEvent.KEYCODE_DEL) }
            assertEquals("the first item leaves the list, the rest renumber", "z\n1. b\n2. c\nx", e.md())
            assertLooksFresh(b, "first item became Body")
        }
    }
}
