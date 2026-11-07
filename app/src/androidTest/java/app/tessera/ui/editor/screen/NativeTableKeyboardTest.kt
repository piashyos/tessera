package app.tessera.ui.editor.screen

import android.util.Log
import android.view.KeyEvent
import androidx.compose.ui.test.onAllNodesWithContentDescription
import app.tessera.R
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.ui.editor.engine.TesseraEditText
import app.tessera.ui.editor.engine.savePng
import app.tessera.ui.editor.engine.tableZones
import dagger.hilt.android.testing.HiltAndroidTest
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/**
 * Plan E1 (criterion 5) and the review finding about `bringPointIntoView`, in the real app with the device's keyboard: a cell of the bottom row
 * at the bottom edge goes up above the keyboard (and above the table bar on it); a cell that grows with a long text pushes the text under the
 * table down without covering it and stays above the keyboard; the keyboard coming up does not throw the note to its caret.
 */
@HiltAndroidTest
class NativeTableKeyboardTest : NativeAppTest() {
    /** A wait that never ends fails the test (with the stack of the stuck thread) instead of holding the device and the shared Gradle lock. */
    @get:Rule(order = 2)
    val timeout: Timeout = Timeout.builder().withTimeout(if (javaClass.simpleName.contains("Shots")) 12L else 4L, TimeUnit.MINUTES).withLookingForStuckThread(true).build()

    private lateinit var t: Tables
    private val ctx get() = app.instrumentation.targetContext
    private fun log(msg: String) = Log.i(TAG, msg)
    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private val grid = TableGrid(listOf("H1", "H2"), listOf(listOf("a1", "b1"), listOf("a2", "b2"), listOf("a3", "b3")))

    private fun open(id: String, md: String): TesseraEditText {
        app.launch()
        app.seed(id, md, System.currentTimeMillis())
        val e = app.open(id)
        t = Tables(app)
        t.awaitOverlays(e, tablesOf(md).size)
        return e
    }

    /** Where the focused cell must end: above the keyboard and above the table bar that stands on it. */
    private fun limit(): Int {
        val ime = t.imeTop() ?: error("no keyboard")
        val bar = compose.onAllNodesWithContentDescription(ctx.getString(R.string.table_add_row)).fetchSemanticsNodes().firstOrNull()?.boundsInWindow?.top?.toInt()
        return minOf(ime, bar ?: ime)
    }

    private fun bottomRowAtTheBottomEdge(e: TesseraEditText) {
        val (h, th) = app.ui { e.height to e.tableZones().single().rect.height() }
        t.scrollTableTo(e, h - th - dp(12))
    }

    @Test
    fun aCellOfTheBottomRowAtTheBottomEdgeGoesAboveTheKeyboard() {
        val md = note(*(1..20).map(::lorem).toTypedArray(), grid, *(21..30).map(::lorem).toTypedArray())
        val e = open("qa-table-kb-lift", md)
        bottomRowAtTheBottomEdge(e)
        val before = t.cell("a3")
        t.tapCell("a3")
        t.awaitIme(e)
        val field = t.focused()
        val limit = limit()
        val editorTop = app.ui { IntArray(2).also { e.getLocationOnScreen(it) }[1] }
        log("bottom row: cell a3 at $before before the tap; focused ${field?.first} at ${field?.second}; keyboard/bar top $limit; editor top $editorTop")
        assertEquals("the cell has the focus", "a3", field?.first)
        assertTrue("the cell is above the keyboard and the bar (${field!!.second.bottom} <= $limit)", field.second.bottom <= limit)
        assertTrue("and below the top of the editor (${field.second.top} >= $editorTop)", field.second.top >= editorTop)
    }

    @Test
    fun aGrowingCellPushesTheTextBelowDownAndStaysAboveTheKeyboard() {
        val md = note(*(1..20).map(::lorem).toTypedArray(), grid, *(21..30).map(::lorem).toTypedArray())
        val e = open("qa-table-kb-grow", md)
        bottomRowAtTheBottomEdge(e)
        t.tapCell("a3")
        t.awaitIme(e)
        app.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MOVE_END)
        val h0 = t.focused()!!.second.height()
        val zone0 = app.ui { e.tableZones().single().rect.height() }
        t.typeKeys(" and a long text that wraps over several lines of this narrow cell")
        app.settle(1_200)
        val field = t.focused()!!
        val (zone, overlayH, under) = app.ui {
            val z = e.tableZones().single().rect
            val l = e.layout
            val line = l.getLineForOffset(e.text.toString().indexOf("Paragraph 21"))
            Triple(z, t.host()!!.getChildAt(1).height, l.getLineTop(line) + e.totalPaddingTop - e.scrollY)
        }
        val limit = limit()
        val saved = tablesOf(app.md(e)).single().rows[2][0]
        t.ua.takeScreenshot()?.let { savePng(ctx, "p2t-grow", it) }
        log("grow: cell height $h0 -> ${field.second.height()}, table $zone0 -> ${zone.height()} (overlay $overlayH), paragraph under the table at $under (table bottom ${zone.bottom}), cell bottom ${field.second.bottom}, keyboard/bar top $limit; cell \"$saved\"")
        assertTrue("the cell grew: $h0 -> ${field.second.height()}", field.second.height() > h0 + dp(16))
        assertEquals("the span reserves what the overlay takes", overlayH, zone.height())
        // the cell's node is clipped to what is seen of the table (clipBounds, 8fc6aa2): the table grows at least by what is seen of the cell's growth
        assertTrue("the table grew with the cell: $zone0 -> ${zone.height()}, the cell (seen) $h0 -> ${field.second.height()}", zone.height() - zone0 >= field.second.height() - h0 - dp(4))
        assertTrue("the text under the table is not covered: paragraph at $under, table bottom ${zone.bottom}", under >= zone.bottom)
        assertTrue("the cell is above the keyboard and the bar (${field.second.bottom} <= $limit)", field.second.bottom <= limit)
        assertTrue("the text is in the cell: \"$saved\"", saved.startsWith("a3 and a long text"))
    }

    /** Typing at the end of a cell appends there: a space and a letter, one key at a time, from a hardware keyboard and from Gboard. */
    @Test
    fun typingAtTheEndOfACellAppendsThere() {
        val md = note("Append", grid, "tail line")
        val e = open("qa-table-kb-append", md)
        val trail = ArrayList<String>()
        t.tapCell("a2")
        t.awaitIme(e)
        trail += "tap: ${t.focused()} sel ${t.focusedSelection()}"
        for ((name, code) in listOf("space" to KeyEvent.KEYCODE_SPACE, "b" to KeyEvent.KEYCODE_B, "c" to KeyEvent.KEYCODE_C)) {
            app.instrumentation.sendKeyDownUpSync(code)
            app.settle(250)
            trail += "hw $name: ${t.focused()?.first} sel ${t.focusedSelection()}"
        }
        app.settle(800)
        val hw = tablesOf(app.md(e)).single().rows[1][0]
        val kb = Gboard(app)
        t.tapCell("b2")
        t.awaitIme(e)
        kb.awaitShown()
        for (ch in listOf(" ", "d", "e")) {
            kb.type(ch)
            trail += "gboard \"$ch\": ${t.focused()?.first} sel ${t.focusedSelection()}"
        }
        app.settle(800)
        val gb = tablesOf(app.md(e)).single().rows[1][1]
        Log.i(TAG, "append at the end of a cell: ${trail.joinToString(" | ")}; saved \"$hw\" and \"$gb\"")
        assertEquals("hardware keys at the end of a2", "a2 bc", hw)
        assertEquals("Gboard keys at the end of b2", "b2 de", gb)
    }

    /** Finding T3 without the keyboard: a table taller than the editor is shown whole (the note scrolls), not cut to the editor's height. */
    @Test
    fun aTableTallerThanTheEditorIsNotCutWithoutTheKeyboard() {
        val tall = TableGrid(listOf("H1", "H2"), (1..14).map { listOf("a$it", "b$it") })
        val md = note("Tall", tall, "after the tall table")
        val e = open("qa-table-kb-tall", md)
        val (editorH, zoneH, overlayH) = app.ui { Triple(e.height, e.tableZones().single().rect.height(), t.host()!!.getChildAt(1).height) }
        val minimum = 15 * dp(40) // fifteen rows of at least 40dp each
        t.ua.takeScreenshot()?.let { savePng(ctx, "p2t-tall", it) }
        Log.i(TAG, "tall table without the keyboard: editor height $editorH, span $zoneH, overlay $overlayH, at least $minimum for 15 rows")
        assertTrue("the table is not cut to the editor's height: span $zoneH, overlay $overlayH, editor $editorH, at least $minimum", zoneH >= minimum && overlayH >= minimum)
    }

    @Test
    fun theKeyboardComingUpDoesNotThrowTheNoteToItsCaret() {
        val md = note(*(1..40).map(::lorem).toTypedArray(), grid, *(41..50).map(::lorem).toTypedArray())
        val e = open("qa-table-kb-jump", md)
        app.ui { e.setSelection(0) } // the caret where the note was opened: at its start
        t.scrollTableTo(e, dp(120))
        val s0 = app.ui { e.scrollY }
        val frames = t.Frames { app.editorOrNull()?.scrollY }.start()
        t.tapCell("a1")
        t.awaitIme(e)
        app.settle(1_000)
        frames.stop()
        val field = t.focused()
        val limit = limit()
        val ys = frames.list.toList()
        log("keyboard up over a cell: scrollY $s0 -> ${ys.lastOrNull()} (min ${ys.minOrNull()}, ${ys.size} frames: ${ys.distinct().take(20)}); focused ${field?.first} at ${field?.second}, keyboard/bar top $limit")
        assertTrue("the note never went back to its start: min scrollY ${ys.minOrNull()} vs $s0", ys.isNotEmpty() && ys.min() >= s0 - dp(40))
        assertEquals("the cell has the focus", "a1", field?.first)
        assertTrue("the cell is above the keyboard and the bar", field!!.second.bottom <= limit)
    }

    private companion object {
        const val TAG = "NativeTableKeyboard"
    }
}
