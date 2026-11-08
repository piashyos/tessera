package app.tessera.ui.editor.engine

import android.widget.FrameLayout
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.ui.editor.screen.note
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * An object that is measured again without an edit (a table whose overlay grew because a cell wrapped or a row came, a picture shown at another
 * size) moves the lines under it. The text of a `TextView` is drawn from recorded `RenderNode`s that keep their old place on a hardware canvas, so
 * the lines under the object must be recorded again (`repaintBelow` in `deliverImage`). What the window shows afterwards must be what a fresh
 * editor of the same text shows. The live editor is drawn once before the change, so its nodes exist.
 */
class NativeEditorQuietResizeLayoutTest {
    private val grid = TableGrid(listOf("A", "B"), listOf(listOf("1", "2"), listOf("3", "4")))
    private val md = note("Title", *(1..4).map { "above $it" }.toTypedArray(), grid, *(1..60).map { "below line $it" }.toTypedArray())

    private fun asInTheApp(v: TesseraEditText) {
        val d = v.resources.displayMetrics.density
        v.setPadding(0, (8 * d).toInt(), 0, (24 * d).toInt())
        v.includeFontPadding = false
        v.setLineSpacing(4 * d, 1f)
    }

    @Test
    fun aTableThatGrowsMovesTheLinesBelowItOnTheScreen() {
        DeviceEditor(md, focus = false).use { e ->
            val (w, h) = e.ui {
                asInTheApp(e.view)
                val w = e.container.width / 2
                val h = e.container.height / 2
                e.view.layoutParams = FrameLayout.LayoutParams(w, h)
                w to h
            }
            e.idle()
            e.capture(e.view) // recorded once at the planned height
            val planned = e.ui { e.view.layout.height }
            val tall = e.ui { (e.view.resources.displayMetrics.density * 300).toInt() }
            e.ui { e.view.reportTableHeight(0, tall) } // the overlay measured itself taller: no edit, no history
            e.idle()
            val grown = e.ui { e.view.layout.height }
            check(grown > planned) { "the table did not change the height of the layout ($planned -> $grown)" }
            val fresh = e.ui {
                e.newEditor(md).also {
                    asInTheApp(it)
                    it.reportTableHeight(0, tall)
                    e.container.addView(it, FrameLayout.LayoutParams(w, h).apply { leftMargin = w })
                }
            }
            e.idle()
            val live = e.capture(e.view)
            val freshShot = e.capture(fresh)
            assertEquals("the layouts differ", e.ui { fresh.layout.height }, grown)
            pixelDiff(live, freshShot)?.let {
                savePng(e.activity, "QuietResize-table-live", live)
                savePng(e.activity, "QuietResize-table-fresh", freshShot)
                fail("table grew: on screen (hardware), live vs fresh: $it")
            }
        }
    }
}
