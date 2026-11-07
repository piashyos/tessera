package app.tessera.ui.editor.screen

import android.util.Log
import android.view.KeyEvent
import app.tessera.R
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.ui.editor.engine.TesseraEditText
import app.tessera.ui.editor.engine.savePng
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/**
 * Phase 2 spec §4 (variant A) and plan E1, with the device's own keyboard (Gboard, pressed at its keys): typing in a cell takes Gboard's
 * autocorrection and Backspace; Enter goes on to the next cell and from the last one into the paragraph after the table, the keyboard staying
 * up, and no line break gets into a cell; an edit of a cell is one step of the history once the cell pauses, undone and redone whole.
 * `adb logcat -s NativeTableIme` has the details.
 */
@HiltAndroidTest
class NativeTableImeTest : NativeAppTest() {
    /** A wait that never ends fails the test (with the stack of the stuck thread) instead of holding the device and the shared Gradle lock. */
    @get:Rule(order = 2)
    val timeout: Timeout = Timeout.builder().withTimeout(if (javaClass.simpleName.contains("Shots")) 12L else 4L, TimeUnit.MINUTES).withLookingForStuckThread(true).build()

    private lateinit var t: Tables
    private lateinit var kb: Gboard
    private val ctx get() = app.instrumentation.targetContext

    private fun log(msg: String) = Log.i(TAG, msg)

    private fun open(id: String, md: String): TesseraEditText {
        app.launch()
        app.seed(id, md, System.currentTimeMillis())
        val e = app.open(id)
        t = Tables(app)
        kb = Gboard(app)
        t.awaitOverlays(e, tablesOf(md).size)
        return e
    }

    /** A finger in the cell [text], the caret at its end (the End key of a hardware keyboard), Gboard up. */
    private fun focusCell(e: TesseraEditText, text: String) {
        t.tapCell(text)
        t.awaitIme(e)
        kb.awaitShown()
        app.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MOVE_END)
        app.settle(300)
    }

    @Test
    fun typingInACellTakesAutocorrectionAndBackspace() {
        val md = note("Groceries", TableGrid(listOf("Fruit", "Note"), listOf(listOf("apple", "red"))), "after the table")
        val e = open("qa-table-ime-type", md)
        // the same keys in the note's own text first: what this keyboard does with them (its autocorrection is a setting of the device)
        t.tapText(e, "after the table")
        t.awaitIme(e)
        kb.awaitShown()
        kb.type(" teh ")
        val suggestText = kb.suggestions().map { it.first }
        kb.type("zx")
        kb.press("Delete")
        app.settle(600)
        val inText = app.md(e).lines().last()
        // then in the cell
        focusCell(e, "red")
        kb.type(" teh")
        val suggestCell = kb.suggestions().map { it.first }
        kb.type(" zx")
        kb.press("Delete")
        app.settle(1_000) // past the cell's pause: committed
        val shown = t.awaitCells { cells -> cells.any { it.startsWith("red") && it.endsWith("z") } }
        val cell = tablesOf(app.md(e)).single().rows[0][1]
        log("the same keys \" teh zx\" + Delete: in the text -> \"$inText\" (suggestions $suggestText); in the cell -> \"$cell\" (suggestions $suggestCell); cells on screen $shown")
        assertEquals("the cell takes the keys as the text does (autocorrection, Backspace)", inText.removePrefix("after the table").trim(), cell.removePrefix("red").trim())
        assertTrue("the cell shows it: $shown", cell in shown)
        app.back()
        assertEquals("saved", cell, tablesOf(app.entity("qa-table-ime-type")!!.content).single().rows[0][1])
    }

    /** Enter after each word, as one types a table; [space]: the word is finished by a space first (no word in composition when Enter comes). */
    private fun enterThroughTheCells(id: String, space: Boolean) {
        val md = note("Shopping", TableGrid.empty(columns = 2, rows = 2))
        val e = open(id, md)
        val first = t.cells(t.onScreen(e)).first().second // the first header cell, empty
        t.tap(first.left + 30, first.centerY())
        t.awaitIme(e)
        kb.awaitShown()
        val words = listOf("milk", "two", "eggs", "six", "tea", "one")
        val ime = t.Frames { t.imeShown(e) }.start()
        val steps = ArrayList<String>()
        for (w in words) {
            kb.type(if (space) "$w " else w)
            kb.tap(kb.enterKey())
            app.settle(300)
            steps += t.cells(t.onScreen(e)).map { it.first }.toString()
        }
        val editorFocused = app.ui { e.isFocused }
        val caret = app.ui { e.selectionStart }
        kb.type("ok")
        app.settle(1_000)
        ime.stop()
        val now = app.md(e)
        val grid = tablesOf(now).single()
        val keyboard = ime.list.joinToString("") { if (it) "K" else "-" }
        log("Enter through the cells (word then ${if (space) "space, " else ""}Enter): cells after each Enter $steps; after the last Enter the editor focused $editorFocused, caret $caret; md ${now.replace("\n", "\\n")}")
        log("keyboard per frame from the first word to the end (${ime.list.size} frames): $keyboard")
        assertEquals("each word in its cell, in reading order, no line break in a cell", TableGrid(listOf("milk", "two"), listOf(listOf("eggs", "six"), listOf("tea", "one"))), grid.let { g -> g.copy(headers = g.headers.map { it.trim() }, rows = g.rows.map { r -> r.map { it.trim() } }) })
        assertTrue("after the last cell the editor has the focus", editorFocused)
        assertTrue("the paragraph after the table got the typing: ${now.replace("\n", "\\n")}", now.lines().last().equals("ok", ignoreCase = true))
        assertTrue("the keyboard stayed up in every frame: $keyboard", ime.list.isNotEmpty() && ime.list.all { it })
    }

    @Test
    fun enterGoesToTheNextCellAndFromTheLastOneToTheParagraphAfter() = enterThroughTheCells("qa-table-ime-enter", space = false)

    @Test
    fun enterAfterASpaceGoesToTheNextCell() = enterThroughTheCells("qa-table-ime-enter-space", space = true)

    /** The same table filled from a hardware keyboard: the characters as key events, Enter as the Enter key. */
    @Test
    fun enterOfAHardwareKeyboardGoesToTheNextCell() {
        val md = note("Shopping", TableGrid.empty(columns = 2, rows = 2))
        val e = open("qa-table-ime-enter-hw", md)
        val first = t.cells(t.onScreen(e)).first().second
        t.tap(first.left + 30, first.centerY())
        t.awaitIme(e)
        val steps = ArrayList<String>()
        for (w in listOf("milk", "two", "eggs", "six", "tea", "one")) {
            t.typeKeys(w)
            app.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
            app.settle(300)
            steps += t.cells(t.onScreen(e)).map { it.first }.toString()
        }
        val editorFocused = app.ui { e.isFocused }
        app.settle(800)
        val grid = tablesOf(app.md(e)).single()
        log("hardware Enter through the cells: cells after each Enter $steps; editor focused after the last $editorFocused; grid $grid")
        assertEquals("each word in its cell", TableGrid(listOf("milk", "two"), listOf(listOf("eggs", "six"), listOf("tea", "one"))), grid)
        assertTrue("after the last cell the editor has the focus", editorFocused)
    }

    @Test
    fun anEditOfACellIsOneUndoStepAfterItsPause() {
        val md = note("Undo", TableGrid(listOf("Fruit", "Qty"), listOf(listOf("apple", "3"))), "tail line")
        val e = open("qa-table-ime-undo", md)
        focusCell(e, "apple")
        t.typeKeys("xy") // two keys well inside the 500 ms pause
        app.settle(1_200)
        val edited = app.md(e)
        assertEquals("the edit is in the note", "applexy", tablesOf(edited).single().rows[0][0])
        t.tapText(e, "tail line") // the focus leaves the cell: the format bar with Undo / Redo comes back
        compose.press(ctx.getString(R.string.toolbar_undo))
        app.settle(600)
        val undone = app.md(e)
        val shownUndone = t.awaitCells { "apple" in it }
        compose.press(ctx.getString(R.string.toolbar_redo))
        app.settle(600)
        val redone = app.md(e)
        val cachedRedone = t.cells(fresh = false).map { it.first } // what a screen reader keeps from the events
        val shownRedone = t.awaitCells { "applexy" in it }
        log("redo: the tree kept from the events $cachedRedone, the tree read again $shownRedone")
        t.ua.takeScreenshot()?.let { savePng(ctx, "p2t-undo-redo", it) }
        log("undo: ${undone.replace("\n", "\\n")} cells $shownUndone; redo: ${redone.replace("\n", "\\n")} cells $shownRedone")
        assertEquals("one undo gives the table back whole", md, undone)
        assertTrue("the overlay shows the table of the note: $shownUndone", "apple" in shownUndone && "applexy" !in shownUndone)
        assertEquals("redo gives the edit back", edited, redone)
        assertTrue("the overlay shows the edit again: $shownRedone", "applexy" in shownRedone)
    }

    private companion object {
        const val TAG = "NativeTableIme"
    }
}
