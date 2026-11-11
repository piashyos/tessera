package app.tessera.ui.editor.screen

import android.util.Log
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.ui.editor.engine.TesseraEditText
import app.tessera.ui.editor.engine.savePng
import app.tessera.ui.editor.engine.paragraphEnd
import app.tessera.ui.editor.engine.tableZones
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/**
 * Plan E1 (the condition of variant A) and its notes, in the real app with real touches: a vertical swipe that starts on a table, or on its
 * focused cell, scrolls the note; a sideways swipe scrolls a wide table and not the note; a diagonal swipe leaves nothing stuck; a tap in the
 * margin above or below a table puts the caret in the paragraph next to it; a long press in a cell selects a word.
 */
@HiltAndroidTest
class NativeTableGestureTest : NativeAppTest() {
    /** A wait that never ends fails the test (with the stack of the stuck thread) instead of holding the device and the shared Gradle lock. */
    @get:Rule(order = 2)
    val timeout: Timeout = Timeout.builder().withTimeout(if (javaClass.simpleName.contains("Shots")) 12L else 4L, TimeUnit.MINUTES).withLookingForStuckThread(true).build()

    private lateinit var t: Tables
    private val ctx get() = app.instrumentation.targetContext
    private fun log(msg: String) = Log.i(TAG, msg)
    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private val wide = TableGrid(
        listOf("Name", "Qty", "Price", "Shop", "Aisle", "Note"),
        listOf(listOf("apple", "3", "1.20", "market", "4", "red"), listOf("watermelon", "1", "3.10", "corner", "1", "big")),
    )

    private fun open(id: String, md: String): TesseraEditText {
        app.launch()
        app.seed(id, md, System.currentTimeMillis())
        val e = app.open(id)
        t = Tables(app)
        t.awaitOverlays(e, tablesOf(md).size)
        return e
    }

    private fun scrollY(e: TesseraEditText) = app.ui { e.scrollY }

    /** A swipe leaves the note coasting (inertial scrolling): wait until it has come to rest, as a user would before the next touch. */
    private fun awaitRest(e: TesseraEditText) {
        var last = -1
        var stable = 0
        repeat(80) {
            val y = scrollY(e)
            if (y == last) { if (++stable >= 3) return } else { stable = 0; last = y }
            Thread.sleep(100)
        }
    }

    /** The header cell at the table's left edge: another one once the table scrolled sideways (a fling may take it far). */
    private fun leftHeader(e: TesseraEditText): String {
        val cells = t.cells(t.onScreen(e)).filter { it.second.width() > 8 }
        val top = cells.minOf { it.second.top }
        return cells.filter { it.second.top == top }.minBy { it.second.left }.first
    }

    private val longNote get() = note(*(1..12).map(::lorem).toTypedArray(), wide, *(13..24).map(::lorem).toTypedArray())

    @Test
    fun aVerticalSwipeOnATableOrOnItsFocusedCellScrollsTheNote() {
        val e = open("qa-table-swipe-v", longNote)
        t.scrollTableTo(e, dp(260))
        val table = t.onScreen(e)
        val s0 = scrollY(e)
        t.drag(table.centerX(), table.centerY(), 0, -dp(160))
        awaitRest(e)
        val s1 = scrollY(e)
        // the swipe coasted on, further than the finger went (maybe to the end of the note): bring the table back under the finger
        t.scrollTableTo(e, dp(260))
        val again = t.onScreen(e)
        val s1b = scrollY(e)
        t.drag(again.centerX(), again.centerY(), 0, dp(100), steps = 15)
        awaitRest(e)
        val s2 = scrollY(e)
        log("swipe on the table: up 160dp scrollY $s0 -> $s1, down 100dp from $s1b -> $s2")
        assertTrue("up from the table scrolls the note: $s0 -> $s1", s1 - s0 >= dp(100))
        assertTrue("down scrolls it back: $s1b -> $s2", s1b - s2 >= dp(60))

        // a cell with the focus and the keyboard up
        t.scrollTableTo(e, dp(120))
        t.tapCell("apple")
        t.awaitIme(e)
        val field = t.focused()!!
        val s3 = scrollY(e)
        t.drag(field.second.centerX(), field.second.centerY(), 0, -dp(100), steps = 15)
        awaitRest(e)
        val s4 = scrollY(e)
        val stillFocused = t.focused()?.first
        log("swipe on the focused cell \"${field.first}\": up 100dp scrollY $s3 -> $s4, focused after: $stillFocused")
        assertEquals("the cell had the focus", "apple", field.first)
        assertTrue("up from the focused cell scrolls the note: $s3 -> $s4", s4 - s3 >= dp(50))
    }

    @Test
    fun aSidewaysSwipeScrollsAWideTableAndNotTheNote() {
        val e = open("qa-table-swipe-h", longNote)
        t.scrollTableTo(e, dp(220))
        val table = t.onScreen(e)
        val r0 = leftHeader(e)
        val s0 = scrollY(e)
        t.drag(table.right - dp(40), table.centerY(), -dp(200), 0)
        val r1 = leftHeader(e)
        val s1 = scrollY(e)
        log("sideways swipe: the header at the left edge \"$r0\" -> \"$r1\", note scrollY $s0 -> $s1")
        assertTrue("the table scrolled: \"$r0\" -> \"$r1\"", r0 != r1)
        assertEquals("the note did not", s0, s1)
    }

    /**
     * A diagonal swipe that starts on the table, then a tap and a vertical swipe: nothing is left holding the touch. The gestures go through the
     * shell (`input swipe` / `input tap`): the synchronous injection of the test hung on a sideways drag here (a harness problem, not the app's).
     */
    @Test
    fun aDiagonalSwipeLeavesNothingStuck() {
        val e = open("qa-table-swipe-d", longNote)
        t.scrollTableTo(e, dp(260))
        val table = t.onScreen(e)
        val (r0, s0) = leftHeader(e) to scrollY(e)
        swipe(e, table.right - dp(60), table.centerY(), table.right - dp(60) - dp(120), table.centerY() - dp(120))
        val (r1, s1) = leftHeader(e) to scrollY(e)
        // what a 45° swipe moves is not promised (observed: sometimes the table, sometimes nothing); what follows must work
        log("diagonal swipe (shell): the header at the left edge \"$r0\" -> \"$r1\", note ${s1 - s0} px")
        // then a vertical swipe on the table scrolls the note, and a tap focuses a cell
        t.scrollTableTo(e, dp(260))
        val tv = t.onScreen(e)
        val s2 = scrollY(e)
        swipe(e, tv.centerX(), tv.centerY(), tv.centerX(), tv.centerY() - dp(140))
        val s3 = scrollY(e)
        t.scrollTableTo(e, dp(220))
        log("after the diagonal: vertical swipe scrollY $s2 -> $s3")
        val c = t.cell("3") // a cell of the second column: on screen whether the table scrolled sideways or not
        t.shell("input tap ${c.right - dp(20)} ${c.centerY()}")
        app.settle(600)
        val focused = t.focused()?.first
        log("after the diagonal: tap -> focused \"$focused\"")
        assertTrue("a vertical swipe still scrolls the note: $s2 -> $s3", s3 - s2 >= dp(90))
        assertEquals("a tap still focuses a cell", "3", focused)
    }

    private fun swipe(e: TesseraEditText, x1: Int, y1: Int, x2: Int, y2: Int) {
        t.shell("input swipe $x1 $y1 $x2 $y2 300")
        app.settle(600)
        awaitRest(e)
    }

    @Test
    fun aTapInTheMarginAboveOrBelowATablePutsTheCaretNextToIt() {
        val md = note("Before the table", TableGrid(listOf("A", "B"), listOf(listOf("1", "2"))), "After the table", *(1..6).map(::lorem).toTypedArray())
        val e = open("qa-table-margin", md)
        val (ps, pe) = app.ui { e.tableZones().single().ps.let { it to paragraphEnd(e.text!!, it) } }
        val table = t.onScreen(e)
        t.tap(table.left + dp(40), table.top - dp(3))
        val above = app.ui { Triple(e.isFocused, e.selectionStart, e.selectionEnd) }
        val focusedAbove = t.focused()
        t.tap(table.left + dp(40), table.bottom + dp(3))
        val below = app.ui { Triple(e.isFocused, e.selectionStart, e.selectionEnd) }
        val focusedBelow = t.focused()
        log("margin taps: table ps $ps..$pe at $table; above -> $above (focused field ${focusedAbove?.second}), below -> $below (focused field ${focusedBelow?.second})")
        assertEquals("above: the caret at the end of the paragraph before", Triple(true, ps - 1, ps - 1), above)
        assertEquals("below: the caret at the start of the paragraph after", Triple(true, pe + 1, pe + 1), below)
        assertEquals("no cell took the focus", null, focusedBelow)
    }

    /**
     * The test's Compose rule runs the app's Compose on its own clock: a finger held down in real time is never a long press to the field
     * unless that clock goes on too (P2-3f: without instrumentation the app selects the word; P2-3g: with the clock advanced while the finger is
     * down, so does the test). The press is held 1 s, the rule's clock advanced 100 ms at a time with it; then the rule is let settle.
     */
    @Test
    fun aLongPressInACellSelectsAWord() {
        val e = open("qa-table-longpress", longNote)
        t.scrollTableTo(e, dp(200))
        val cell = t.cell("watermelon")
        val (x, y) = (cell.left + dp(8) + dp(12)).toFloat() to cell.centerY().toFloat()
        val down = android.os.SystemClock.uptimeMillis()
        t.inject(android.view.MotionEvent.obtain(down, down, android.view.MotionEvent.ACTION_DOWN, x, y, 0))
        repeat(10) { compose.mainClock.advanceTimeBy(100); Thread.sleep(100) }
        t.inject(android.view.MotionEvent.obtain(down, android.os.SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, x, y, 0))
        compose.waitForIdle()
        app.settle(600)
        val focused = t.focused()
        val sel = t.focusedSelection()
        t.ua.takeScreenshot()?.let { savePng(ctx, "p2t-long-press", it) }
        log("long press on \"watermelon\": focused ${focused?.first}, selection $sel")
        assertEquals("the cell has the focus", "watermelon", focused?.first)
        assertEquals("the word is selected", 0 to 10, sel)
    }

    private companion object {
        const val TAG = "NativeTableGesture"
    }
}
