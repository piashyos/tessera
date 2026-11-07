package app.tessera.ui.editor.screen

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import app.tessera.R
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.ui.editor.engine.TesseraEditText
import app.tessera.ui.editor.engine.savePng
import app.tessera.ui.editor.engine.tableZones
import dagger.hilt.android.testing.HiltAndroidTest
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/**
 * Phase 2 spec §2 and §4 on the screen: the "Table" button of the native bar puts an empty 2×2 table as a paragraph of its own (after the caret's
 * paragraph, splitting it in the middle, before it at its start, in an empty note, twice in a row); every button of the table bar is one step of
 * the history, undone by the bar's Undo; a selection through tables and Delete empties the note, one Undo brings it back; with several tables each
 * overlay stays on its own table while the note scrolls.
 */
@HiltAndroidTest
class NativeTableEditTest : NativeAppTest() {
    /** A wait that never ends fails the test (with the stack of the stuck thread) instead of holding the device and the shared Gradle lock. */
    @get:Rule(order = 2)
    val timeout: Timeout = Timeout.builder().withTimeout(if (javaClass.simpleName.contains("Shots")) 12L else 4L, TimeUnit.MINUTES).withLookingForStuckThread(true).build()

    private lateinit var t: Tables
    private val ctx get() = app.instrumentation.targetContext
    private fun log(msg: String) = Log.i(TAG, msg)
    private fun s(id: Int) = ctx.getString(id)

    private fun open(id: String, md: String): TesseraEditText {
        app.launch()
        app.seed(id, md, System.currentTimeMillis())
        val e = app.open(id)
        t = Tables(app)
        t.awaitOverlays(e, tablesOf(md).size)
        return e
    }

    /** The editor focused with the caret at [at], the keyboard and the format bar up. */
    private fun caret(e: TesseraEditText, at: Int) {
        app.ui {
            e.requestFocus()
            e.setSelection(at)
            e.context.getSystemService(InputMethodManager::class.java).showSoftInput(e, 0)
        }
        t.awaitIme(e)
        compose.waitUntil(5_000) { compose.has(s(R.string.toolbar_table)) }
    }

    private fun shape(md: String): List<String> = NoteMarkdown.parse(md).blocks.map { b ->
        when (b) {
            is NoteBlock.Table -> "T${b.grid.columnCount}x${b.grid.rowCount + 1}"
            is NoteBlock.Text -> b.content.text
            else -> b.toString()
        }
    }

    @Test
    fun theTableButtonPutsAnEmptyTableAsAParagraphOfItsOwn() {
        class Case(val name: String, val seed: String, val at: (String) -> Int, val presses: Int, val want: List<String>)
        val cases = listOf(
            Case("end of a paragraph", "first line\nsecond line", { it.indexOf(" line") + 5 }, 1, listOf("first line", "T2x3", "second line")),
            Case("middle of a paragraph", "first line\nsecond line", { it.indexOf(" line") }, 1, listOf("first", "T2x3", " line", "second line")),
            Case("start of a paragraph", "first line\nsecond line", { it.indexOf("second") }, 1, listOf("first line", "T2x3", "second line")),
            Case("empty note", "x", { -1 }, 1, listOf("T2x3", "")), // the note is emptied in the editor (an empty note has no card to open)
            // Two objects in a row keep an empty paragraph between them (normalize rule 9: a place for the caret) — the lead's decision on Т7.
            Case("twice in a row", "first line\nsecond line", { it.indexOf(" line") + 5 }, 2, listOf("first line", "T2x3", "", "T2x3", "second line")),
        )
        val bad = ArrayList<String>()
        app.launch()
        cases.forEachIndexed { i, c ->
            val id = "qa-table-insert-$i"
            app.seed(id, c.seed, System.currentTimeMillis())
            val e = app.open(id)
            t = Tables(app)
            val at = c.at(app.text(e))
            if (at < 0) {
                caret(e, 0)
                app.ui { e.setSelection(0, e.text!!.length) }
                app.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DEL)
                app.settle(400)
                check(app.text(e).isEmpty()) { "not emptied: \"${app.text(e)}\"" }
            } else {
                caret(e, at)
            }
            repeat(c.presses) { compose.press(s(R.string.toolbar_table)); app.settle(500) }
            t.awaitOverlays(e, c.presses)
            val md = app.md(e)
            val got = shape(md).let { if (it.lastOrNull() == "" && c.want.lastOrNull() != "") it.dropLast(1) else it }
            val caretAt = app.ui { e.selectionStart }
            val afterLast = app.ui { e.tableZones().last().let { z -> app.engineParagraphEndPlusOne(e, z.ps) } }
            log("${c.name}: ${md.replace("\n", "\\n")} -> $got; caret $caretAt (start of the paragraph after the last table: $afterLast)")
            if (got != c.want) bad += "${c.name}: $got, want ${c.want}"
            if (caretAt != afterLast) bad += "${c.name}: caret $caretAt, want the start of the paragraph after the table ($afterLast)"
            app.back()
            if (app.entity(id)?.content != md) bad += "${c.name}: saved ${app.entity(id)?.content?.replace("\n", "\\n")}"
        }
        assertEquals("table inserts", emptyList<String>(), bad)
    }

    /**
     * A note emptied (select all, Delete), left alone past the autosave, then written again — new text, or a table from the bar — keeps what was
     * written. The ViewModel deletes a note that is blank when a write runs; the autosave runs half a second after an edit.
     */
    @Test
    fun aNoteEmptiedAndWrittenAgainKeepsTheNewContent() {
        app.launch()
        val bad = ArrayList<String>()
        for (how in listOf("text", "table")) {
            val id = "qa-table-emptied-$how"
            app.seed(id, "old content", System.currentTimeMillis())
            val e = app.open(id)
            t = Tables(app)
            caret(e, 0)
            app.ui { e.setSelection(0, e.text!!.length) }
            app.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DEL)
            app.settle(1_500) // past the 300 ms emit and the 500 ms autosave
            val whileEmpty = app.entity(id)
            if (how == "text") app.ime(e) { commitText("new content", 1) } else compose.press(s(R.string.toolbar_table))
            app.settle(1_200)
            val shown = app.md(e)
            app.back()
            val saved = app.entity(id)
            log("emptied, then $how: in the database while empty ${whileEmpty?.let { "\"${it.content}\" deletedAt ${it.deletedAt}" } ?: "nothing"}; shown ${shown.replace("\n", "\\n")}; saved ${saved?.content?.replace("\n", "\\n")}")
            if (saved?.content != shown) bad += "$how: shown \"${shown.replace("\n", "\\n")}\", saved ${saved?.content?.let { "\"$it\"" } ?: "nothing (the row is gone)"}"
        }
        assertEquals("what was written after emptying the note is lost", emptyList<String>(), bad)
    }

    /** T6 on the Aztec path (the flag off): the same emptied note, written again after the autosave, is kept (the fix of 91708fe is for both). */
    @Test
    fun aNoteEmptiedAndWrittenAgainInAztecKeepsTheNewContent() {
        runBlocking { prefs.setUseNativeEditor(false) }
        app.launch()
        val id = "qa-aztec-emptied"
        app.seed(id, "old content", System.currentTimeMillis())
        app.tapCard(id)
        compose.waitUntil(15_000) { app.aztecOrNull() != null }
        app.settle(1_000)
        app.ui { app.aztecOrNull()!!.let { a -> a.requestFocus(); a.setSelection(0, a.length()) } }
        app.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DEL)
        app.settle(1_500) // past the autosave
        val whileEmpty = app.entity(id)
        app.ui { app.aztecOrNull()!!.let { a -> a.setSelection(a.length()); a.onCreateInputConnection(android.view.inputmethod.EditorInfo())!!.commitText("new content", 1) } }
        app.settle(1_200)
        val shown = app.ui { app.aztecOrNull()!!.text.toString() }
        app.back()
        val saved = app.entity(id)
        log("Aztec, emptied then text: in the database while empty ${whileEmpty?.let { "\"${it.content}\" deletedAt ${it.deletedAt}" } ?: "nothing"}; shown \"$shown\"; saved ${saved?.content?.let { "\"$it\"" } ?: "nothing"}")
        runBlocking { prefs.setUseNativeEditor(true) }
        assertTrue("Aztec: the note is there while it is empty and being edited", whileEmpty != null)
        assertEquals("Aztec: what was written after emptying is saved", "new content", saved?.content?.trim())
    }

    @Test
    fun everyButtonOfTheTableBarIsOneUndoStep() {
        val md0 = note("Top line", TableGrid(listOf("A", "B"), listOf(listOf("1", "2"), listOf("3", "4"))), "Bottom line")
        val e = open("qa-table-bar", md0)
        class Action(val name: String, val desc: Int, val check: (String) -> Boolean)
        val actions = listOf(
            Action("add row", R.string.table_add_row) { tablesOf(it).single().rowCount == 3 },
            Action("remove row", R.string.table_remove_row) { tablesOf(it).single().rowCount == 1 },
            Action("add column", R.string.table_add_column) { tablesOf(it).single().columnCount == 3 },
            Action("remove column", R.string.table_remove_column) { tablesOf(it).single().columnCount == 1 },
            Action("move up", R.string.table_move_up) { shape(it).take(2) == listOf("T2x3", "Top line") },
            Action("move down", R.string.table_move_down) { shape(it).filter { b -> b.isNotEmpty() } == listOf("Top line", "Bottom line", "T2x3") },
            Action("delete", R.string.table_delete) { tablesOf(it).isEmpty() },
        )
        val bad = ArrayList<String>()
        for ((i, a) in actions.withIndex()) {
            // each button on a note opened afresh: one step of the history, undone by the format bar's Undo
            val ee = if (i == 0) e else { app.back(); app.seed("qa-table-bar-$i", md0, System.currentTimeMillis()); app.open("qa-table-bar-$i").also { t.awaitOverlays(it, 1) } }
            t.tapCell("1")
            t.awaitIme(ee)
            val bar = runCatching { compose.waitUntil(5_000) { compose.has(s(R.string.table_add_row)) } }.isSuccess
            if (!bar) {
                bad += "${a.name}: no table bar after a tap on the cell"
                continue
            }
            compose.press(s(a.desc))
            app.settle(700)
            val after = app.md(ee)
            t.awaitOverlays(ee, tablesOf(after).size)
            t.tapText(ee, "Bottom line")
            compose.press(s(R.string.toolbar_undo))
            app.settle(700)
            val undone = app.md(ee)
            t.awaitOverlays(ee, 1)
            log("${a.name}: ${after.replace("\n", "\\n")} -> undo ${undone.replace("\n", "\\n")}")
            if (!a.check(after)) bad += "${a.name}: did not do it: ${after.replace("\n", "\\n")}"
            if (undone != md0) bad += "${a.name}: one undo gave ${undone.replace("\n", "\\n")}"
        }
        assertEquals("table bar buttons", emptyList<String>(), bad)
    }

    /** The table bar is there whenever a cell has the focus: after leaving to the text and coming back, again and again, also around an edit undone. */
    @Test
    fun theTableBarComesBackEveryTimeACellGetsTheFocus() {
        val md0 = note("Top line", TableGrid(listOf("A", "B"), listOf(listOf("1", "2"), listOf("3", "4"))), "Bottom line")
        val e = open("qa-table-bar-again", md0)
        val seen = ArrayList<String>()
        fun focusAndLook(step: String) {
            t.tapCell("1")
            t.awaitIme(e)
            val bar = runCatching { compose.waitUntil(3_000) { compose.has(s(R.string.table_add_row)) } }.isSuccess
            seen += "$step: bar $bar (cell ${t.focused()?.first})"
            if (!bar) t.ua.takeScreenshot()?.let { savePng(ctx, "p2t-bar-gone-${seen.size}", it) }
        }
        repeat(3) { focusAndLook("focus ${it + 1}"); t.tapText(e, "Bottom line") }
        focusAndLook("before add column")
        compose.press(s(R.string.table_add_column))
        app.settle(600)
        t.tapText(e, "Bottom line")
        compose.press(s(R.string.toolbar_undo))
        app.settle(600)
        repeat(2) { focusAndLook("after add column and undo ${it + 1}"); t.tapText(e, "Bottom line") }
        log("table bar on each focus: ${seen.joinToString(" | ")}")
        assertTrue("the table bar shows on every focus: $seen", seen.all { "bar true" in it })
    }

    /** The chain of the first QA runs (finding T5): several structure edits, each undone from the format bar, then the focus in a cell again. */
    @Test
    fun theTableBarComesBackAfterAChainOfStructureEditsAndUndos() {
        val md0 = note("Top line", TableGrid(listOf("A", "B"), listOf(listOf("1", "2"), listOf("3", "4"))), "Bottom line")
        val e = open("qa-table-bar-chain", md0)
        val seen = ArrayList<String>()
        for (desc in listOf(R.string.table_add_row, R.string.table_remove_row, R.string.table_add_column, R.string.table_remove_column)) {
            t.tapCell("1")
            t.awaitIme(e)
            val bar = runCatching { compose.waitUntil(3_000) { compose.has(s(R.string.table_add_row)) } }.isSuccess
            seen += "before ${s(desc)}: bar $bar"
            if (!bar) { t.ua.takeScreenshot()?.let { savePng(ctx, "p2t-bar-chain-gone", it) }; break }
            compose.press(s(desc))
            app.settle(600)
            t.tapText(e, "Bottom line")
            compose.press(s(R.string.toolbar_undo))
            app.settle(600)
        }
        log("table bar along the chain: ${seen.joinToString(" | ")}")
        assertTrue("the table bar shows each time a cell gets the focus: $seen", seen.all { it.endsWith("bar true") })
    }

    private fun ctrl(code: Int) {
        val t0 = SystemClock.uptimeMillis()
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        app.instrumentation.sendKeySync(KeyEvent(t0, t0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, 0, meta))
        app.instrumentation.sendKeySync(KeyEvent(t0, t0, KeyEvent.ACTION_DOWN, code, 0, meta))
        app.instrumentation.sendKeySync(KeyEvent(t0, t0, KeyEvent.ACTION_UP, code, 0, meta))
        app.instrumentation.sendKeySync(KeyEvent(t0, t0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, 0, 0))
        app.instrumentation.waitForIdleSync()
    }

    @Test
    fun selectAllThroughTablesAndDeleteEmptiesTheNoteAndOneUndoBringsItBack() {
        val g = TableGrid(listOf("A", "B"), listOf(listOf("1", "2")))
        val md0 = note("Top line", g, "Middle line", g.setCell(0, 0, "9"), "Bottom line")
        val e = open("qa-table-selectall", md0)
        t.tapText(e, "Middle line")
        t.awaitIme(e)
        ctrl(KeyEvent.KEYCODE_A)
        val sel = app.ui { e.selectionStart to e.selectionEnd }
        val len = app.ui { e.text!!.length }
        app.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DEL)
        app.settle(600)
        val emptied = app.md(e)
        val overlaysLeft = app.ui { t.host()!!.childCount - 1 }
        compose.press(s(R.string.toolbar_undo))
        app.settle(700)
        val back = app.md(e)
        t.awaitOverlays(e, 2)
        log("select all $sel of $len, Delete -> \"${emptied.replace("\n", "\\n")}\" ($overlaysLeft overlays left); undo -> ${back.replace("\n", "\\n")}")
        assertEquals("Ctrl+A selects the whole note", 0 to len, sel)
        assertTrue("Delete empties the note: \"$emptied\"", emptied.isBlank())
        assertEquals("no overlay is left", 0, overlaysLeft)
        assertEquals("one undo brings the note back", md0, back)
    }

    @Test
    fun withSeveralTablesEachOverlayStaysOnItsTableWhileTheNoteScrolls() {
        val g = TableGrid(listOf("A", "B", "C"), listOf(listOf("1", "2", "3"), listOf("4", "5", "6")))
        val md = note(*(1..6).map(::lorem).toTypedArray(), g, *(7..12).map(::lorem).toTypedArray(), g.setCell(0, 0, "x"), *(13..18).map(::lorem).toTypedArray(), g.addColumn().addColumn().addColumn(), *(19..24).map(::lorem).toTypedArray())
        val e = open("qa-table-multi", md)
        val host = t.host()!!
        var frames = 0
        var off = 0
        var maxOff = 0f
        val listener = ViewTreeObserver.OnDrawListener {
            val zones = e.tableZones()
            if (zones.size != 3) return@OnDrawListener
            frames++
            var bad = false
            zones.forEachIndexed { i, z ->
                val o = host.getChildAt(i + 1)
                val d = maxOf(abs(o.translationY - z.rect.top), abs(o.translationX - z.rect.left))
                if (d > 0.5f) { bad = true; maxOff = maxOf(maxOff, d) }
            }
            if (bad) off++
        }
        app.ui { host.viewTreeObserver.addOnDrawListener(listener) }
        val loc = app.ui { IntArray(2).also { host.getLocationOnScreen(it) } }
        val h = app.ui { host.height }
        val d = (48 * ctx.resources.displayMetrics.density).toInt()
        repeat(3) { t.drag(loc[0] + d / 2, loc[1] + h - d, 0, -(h - 2 * d), steps = 30) }
        repeat(3) { t.drag(loc[0] + d / 2, loc[1] + d, 0, h - 2 * d, steps = 30) }
        app.ui { host.viewTreeObserver.removeOnDrawListener(listener) }
        log("three tables, scrolled down and up: $frames frames, overlays off their tables in $off (max %.1f px)".format(maxOff))
        assertTrue("frames were drawn while scrolling", frames > 20)
        assertEquals("every overlay stays on its table", 0, off)
    }

    private companion object {
        const val TAG = "NativeTableEdit"
    }
}

/** The start of the paragraph after the one at [ps]. Main thread. */
internal fun AppDriver.engineParagraphEndPlusOne(e: TesseraEditText, ps: Int): Int =
    (app.tessera.ui.editor.engine.paragraphEnd(e.text!!, ps) + 1).coerceAtMost(e.text!!.length)
