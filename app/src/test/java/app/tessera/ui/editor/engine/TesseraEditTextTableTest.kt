package app.tessera.ui.editor.engine

import android.os.SystemClock
import android.view.MotionEvent
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.core.text.notedoc.NoteBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The table of the native editor (phase 2 spec §4, variant A): the engine's side — the command that inserts one, the commit of an edited cell as
 * one step, the height the overlay reports (a quiet change), the tap in the margin of the span. The overlay itself is a view and is tested apart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TesseraEditTextTableTest {
    private val table = "| a | b |\n| --- | --- |\n| 1 | 2 |"
    private val md = "top\n$table\nbottom"

    private fun host(markdown: String = md) = HostFixture(markdown).also { it.layout() }
    private fun HostFixture.ps() = at("￼")
    private fun HostFixture.version() = (text as EngineEditable).version
    private fun HostFixture.tables() = DocBinder.read(text).blocks.filterIsInstance<NoteBlock.Table>()
    private fun HostFixture.tap(px: Float, py: Float) {
        val t = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, px, py, 0))
        view.dispatchTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_UP, px, py, 0))
    }

    @Test
    fun insertTableAddsAnEmpty2x2TableAfterTheCaretsParagraphAndTheCaretGoesBelowIt() {
        val h = host("one\ntwo")
        h.caret(3)
        h.view.insertTable()
        val g = h.tables().single().grid
        assertEquals(2 to 2, g.columnCount to g.rowCount)
        assertTrue("the caret is in the paragraph after the table", h.text.substring(h.view.selectionStart).startsWith("two"))
    }

    @Test
    fun aCommittedCellIsOneStepOfTheHistoryAndUndoGivesTheOldTableBack() {
        val h = host()
        val ps = h.ps()
        h.caret(1)
        val before = h.md()
        assertTrue(h.view.replaceTable(ps, TableGrid(listOf("a", "b"), listOf(listOf("changed", "2")))))
        assertTrue(h.md().contains("changed"))
        assertEquals("the selection is not moved onto the table", 1 to 1, h.view.selectionStart to h.view.selectionEnd)
        h.view.undo()
        assertEquals(before, h.md())
        h.view.redo()
        assertTrue(h.md().contains("changed"))
    }

    @Test
    fun theSameGridIsNoEditAndANonTableParagraphIsNone() {
        val h = host()
        val v = h.version()
        assertFalse(h.view.replaceTable(h.ps(), TableGrid(listOf("a", "b"), listOf(listOf("1", "2")))))
        assertFalse(h.view.replaceTable(0, TableGrid(listOf("x"), emptyList())))
        assertEquals(v, h.version())
        assertFalse("no step", h.view.formatState().canUndo)
    }

    @Test
    fun aReadOnlyEditorDoesNotCommit() {
        val h = host()
        h.view.editable = false
        assertFalse(h.view.replaceTable(h.ps(), TableGrid(listOf("a", "b"), listOf(listOf("x", "2")))))
        assertFalse(h.md().contains("| x |"))
    }

    @Test
    fun theHeightTheOverlayReportsIsReservedAtOnceAndIsNoChangeOfTheDocument() {
        val h = host()
        val v = h.version()
        val before = h.md()
        h.view.reportTableHeight(0, 500)
        h.layout()
        assertEquals(500, h.view.tableZones().single().rect.height())
        assertEquals("no new version", v, h.version())
        assertEquals(before, h.md())
        assertFalse("no step", h.view.formatState().canUndo)
        h.pass(1000)
        assertTrue("nothing emitted", h.emitted.isEmpty())
    }

    @Test
    fun aTableThatIsRebuiltStartsAtTheHeightItsGridHad() {
        val h = host()
        h.view.reportTableHeight(0, 333)
        h.view.setMarkdown("x\n$table\ny")
        h.layout()
        assertEquals(333, h.view.tableZones().single().rect.height())
    }

    @Test
    fun theTableMovesUpAndDownAsOneStep() {
        val h = host()
        assertTrue(h.view.moveTable(h.ps(), -1))
        assertTrue(h.md().startsWith("|"))
        h.view.undo()
        assertTrue(h.md().startsWith("top"))
        assertTrue(h.view.moveTable(h.ps(), +1))
        assertTrue("the table is after `bottom`", h.md().indexOf("bottom") < h.md().indexOf("| a"))
        assertFalse("the first paragraph has nothing above it", host("$table\nz").view.moveTable(0, -1))
    }

    @Test
    fun deleteRemovesTheTableParagraph() {
        val h = host()
        assertTrue(h.view.deleteObjectAt(h.ps()))
        assertTrue(h.tables().isEmpty())
        assertEquals("top\nbottom", h.md())
    }

    @Test
    fun aTapInTheMarginAboveATableDoesNotPutTheCaretInItsParagraph() {
        val h = host()
        val z = h.view.tableZones().single()
        h.caret(0)
        h.tap(10f, z.rect.top - 2f)
        val s = h.view.selectionStart
        assertTrue("the caret is not on the table paragraph (at $s, table ${h.ps()})", s != h.ps() && s != h.ps() + 1)
    }

    @Test
    fun aTapInTheMarginBelowATableGoesToTheParagraphAfterIt() {
        val h = host()
        val z = h.view.tableZones().single()
        h.tap(10f, z.rect.bottom + 2f)
        val s = h.view.selectionStart
        assertTrue("the caret is in the paragraph after the table (at $s)", s > h.ps() + 1)
    }

    @Test
    fun aLineBreakInACellIsFlattenedOnTheWayToTheNote() {
        val h = host()
        h.view.replaceTable(h.ps(), TableGrid(listOf("a\nb", "c"), listOf(listOf("1\r\n2", "3"))))
        assertFalse(h.tables().single().grid.headers.any { '\n' in it })
        assertEquals("a b", h.tables().single().grid.headers[0])
    }
}
