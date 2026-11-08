package app.tessera.ui.editor.screen

import android.app.UiAutomation
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.Lifecycle
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.ui.editor.engine.TesseraEditText
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

/**
 * Phase 2 acceptance §8 items 1 and 3 on the device: old notes with tables (written by the Aztec path, with the `<!-- tessera:… -->` sizes, and
 * the legacy forms the corpus has) open in the native editor with their overlays and leave without a change; an edit in a cell is kept by a
 * Back right after it, by the app going to the background with the focus in the cell, and by a turn of the screen.
 */
@HiltAndroidTest
class NativeTableOpenCloseTest : NativeAppTest() {
    /** A wait that never ends fails the test (with the stack of the stuck thread) instead of holding the device and the shared Gradle lock. */
    @get:Rule(order = 2)
    val timeout: Timeout = Timeout.builder().withTimeout(if (javaClass.simpleName.contains("Shots")) 12L else 4L, TimeUnit.MINUTES).withLookingForStuckThread(true).build()

    private lateinit var t: Tables
    private lateinit var kb: Gboard
    private fun log(msg: String) = Log.i(TAG, msg)

    private val budget = TableGrid(
        listOf("Item", "Cost", "Note"), listOf(listOf("Rent", "1200", "monthly"), listOf("Food", "300", "")),
        colWidths = listOf(120f, 80f, 200f), rowHeights = listOf(44f, 30f, 30f),
    )
    private val wide = TableGrid(listOf("a", "b", "c", "d", "e", "f"), listOf(listOf("1", "2", "3", "4", "5", "6")), colWidths = List(6) { 100f })

    /** Notes as the Aztec path saved them (its join puts a blank line between blocks and the sizes in a comment) and legacy table forms. */
    private val notes = LegacyNotes.all.filter { it.contains('|') } + listOf(
        legacyNote("# Budget\nIntro line", budget, "after the table"),
        legacyNote(budget.copy(rowHeights = null), "between", TableGrid(listOf("a | b", "c"), listOf(listOf("1", "2")))),
        legacyNote("wide", wide),
        legacyNote("- [ ] task", TableGrid(listOf("", ""), listOf(listOf("", ""))), "> quote"),
        "| A | B |\n|---|---|\n| 1 | 2 |\n\ntext after",
        "# T\n| x | y |\n| :--- | ---: |\n| 1 | 2 |",
        "text\n| one |\n| --- |\n| 1 |\n| 2 |\n| 3 |",
    )

    private fun tapLastLine(e: TesseraEditText) {
        val (x, y) = app.ui {
            val loc = IntArray(2).also { e.getLocationOnScreen(it) }
            val l = e.layout
            val line = l.lineCount - 1
            loc[0] + e.totalPaddingLeft + 20f to loc[1] + e.totalPaddingTop - e.scrollY + (l.getLineTop(line) + l.getLineBottom(line)) / 2f
        }
        val t0 = SystemClock.uptimeMillis()
        app.instrumentation.sendPointerSync(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x, y, 0))
        app.instrumentation.sendPointerSync(MotionEvent.obtain(t0, t0 + 60, MotionEvent.ACTION_UP, x, y, 0))
        app.settle(800)
    }

    @Test
    fun oldNotesWithTablesOpenAndCloseWithoutAnyChange() {
        app.launch()
        t = Tables(app)
        val changed = ArrayList<String>()
        notes.forEachIndexed { i, md ->
            val id = "qa-table-open-close-$i"
            val ts = BASE + i * 1_000L
            app.seed(id, md, ts)
            val e = app.open(id)
            val n = tablesOf(md).size
            t.awaitOverlays(e, n)
            app.settle(600)
            val way = when (i % 3) {
                0 -> "Back arrow".also { app.back() }
                1 -> "system Back".also { app.systemBack() }
                else -> "tap in the text, Back arrow".also { tapLastLine(e); app.back() }
            }
            val after = app.entity(id)
            log("#$i ($way): $n tables, content same ${after?.content == md}, updatedAt +${after?.let { it.updatedAt - ts }}")
            if (after == null || after.content != md || after.updatedAt != ts || after.syncedAt != ts) {
                changed += "#$i ($way) ${q(md)} -> " + (after?.let { "content ${if (it.content == md) "same" else q(it.content)}, updatedAt +${it.updatedAt - ts}" } ?: "gone")
            }
        }
        assertEquals("notes with tables changed by opening and closing them (${changed.size} of ${notes.size})", emptyList<String>(), changed)
    }

    /** "apple" with the typed "s" in it (where the caret put the letter is another test's business: `typingAtTheEndOfACellAppendsThere`). */
    private fun edited(cell: String?) = cell != null && cell.length == 6 && cell.replaceFirst("s", "") == "apple"

    private fun typeInApple(id: String): TesseraEditText {
        val md = note("Keep", TableGrid(listOf("Fruit", "Qty"), listOf(listOf("apple", "3"))), "tail line")
        app.launch()
        app.seed(id, md, System.currentTimeMillis())
        val e = app.open(id)
        t = Tables(app)
        kb = Gboard(app)
        t.awaitOverlays(e, 1)
        t.tapCell("apple")
        t.awaitIme(e)
        kb.awaitShown()
        app.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MOVE_END)
        kb.type("s") // inside the cell's 500 ms pause: not committed yet
        return e
    }

    private fun savedCell(id: String) = app.entity(id)?.content?.let { tablesOf(it).firstOrNull()?.rows?.getOrNull(0)?.getOrNull(0) }

    @Test
    fun aBackRightAfterTypingInACellKeepsTheEdit() {
        typeInApple("qa-table-back-arrow")
        app.back()
        val arrow = savedCell("qa-table-back-arrow")
        typeInApple("qa-table-back-system")
        app.instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK) // the first Back takes the keyboard away
        app.settle(400)
        app.systemBack()
        val system = savedCell("qa-table-back-system")
        log("Back right after a key in a cell: arrow -> \"$arrow\", system Back twice -> \"$system\"")
        assertTrue("the Back arrow right after the key keeps it: \"$arrow\"", edited(arrow))
        assertTrue("the system Back (keyboard, then the note) right after the key keeps it: \"$system\"", edited(system))
    }

    @Test
    fun goingToTheBackgroundWithTheFocusInACellKeepsTheEdit() {
        typeInApple("qa-table-stop")
        app.scenario!!.moveToState(Lifecycle.State.CREATED)
        app.settle(300)
        val away = savedCell("qa-table-stop")
        app.scenario!!.moveToState(Lifecycle.State.RESUMED)
        val e = app.awaitEditor()
        Tables(app).awaitOverlays(e, 1)
        val shown = tablesOf(app.md(e)).single().rows[0][0]
        app.back()
        val saved = savedCell("qa-table-stop")
        log("ON_STOP with the focus in a cell: saved while away \"$away\", shown when back \"$shown\", saved at the end \"$saved\"")
        assertTrue("saved while away (the process may die there): \"$away\"", edited(away))
        assertEquals("shown once when back", away, shown)
        assertEquals("saved once", away, saved)
    }

    @Test
    fun turningTheScreenWithTheFocusInACellKeepsTheEdit() {
        typeInApple("qa-table-rotate")
        val ua = app.instrumentation.uiAutomation
        try {
            ua.setRotation(UiAutomation.ROTATION_FREEZE_90)
            app.settle(2_500)
            val e = app.awaitEditor()
            Tables(app).awaitOverlays(e, 1)
            val landscape = tablesOf(app.md(e)).single().rows[0][0]
            val cells = Tables(app).cells().map { it.first }
            ua.setRotation(UiAutomation.ROTATION_FREEZE_0)
            app.settle(2_500)
            val e2 = app.awaitEditor()
            Tables(app).awaitOverlays(e2, 1)
            val portrait = tablesOf(app.md(e2)).single().rows[0][0]
            app.back()
            val saved = savedCell("qa-table-rotate")
            log("turned with the focus in a cell: landscape \"$landscape\" (cells $cells), portrait again \"$portrait\", saved \"$saved\"")
            assertTrue("after the turn the edit is in the note: \"$landscape\"", edited(landscape))
            assertTrue("and in its cell on screen: $cells", landscape in cells)
            assertEquals("turned back", landscape, portrait)
            assertEquals("saved", landscape, saved)
        } finally {
            ua.setRotation(UiAutomation.ROTATION_FREEZE_0)
            ua.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }

    private fun q(s: String) = "\"" + s.take(80).replace("\n", "\\n") + "\""

    private companion object {
        const val TAG = "NativeTableOpenClose"
        const val BASE = 1_700_000_000_000L
    }
}
