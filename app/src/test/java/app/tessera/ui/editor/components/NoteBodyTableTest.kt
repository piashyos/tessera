package app.tessera.ui.editor.components

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.engine.TesseraEditText
import app.tessera.ui.editor.engine.tableZones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** The table overlay in `NoteBody` (phase 2 spec §4, variant A): it shows the note's table, commits an edit as one step, is inactive for a viewer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteBodyTableTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val md = "top\n| a | b |\n| --- | --- |\n| 1 | 2 |\nbottom"
    private var shown by mutableStateOf(true)
    private var editable by mutableStateOf(true)
    private val emitted = mutableListOf<String>()
    private var actions: RichBodyActions? = null
    private val bars = mutableListOf<TableBarState?>()

    private fun setContent(markdown: String = md) = rule.setContent {
        TesseraTheme(darkTheme = false, dynamicColor = false) {
            if (shown) {
                NoteBody(
                    markdown = markdown,
                    onMarkdownChange = { emitted += it },
                    onActionsReady = { actions = it },
                    onFormatStateChanged = {},
                    editable = editable,
                    onTableBarChanged = { bars += it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private fun host(): TableOverlayHost {
        fun find(v: View): TableOverlayHost? =
            if (v is TableOverlayHost) v else (v as? ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { find(g.getChildAt(it)) } }
        return find(rule.activity.window.decorView) ?: error("no TableOverlayHost")
    }

    private fun editor(): TesseraEditText = host().editor

    private fun pass(ms: Long) {
        rule.runOnUiThread { editor().emitExecutor = java.util.concurrent.Executor { it.run() } }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        rule.waitForIdle()
    }

    @Test fun a_table_gets_an_overlay_that_shows_its_cells() {
        setContent()
        rule.waitForIdle()
        assertEquals("the editor and one overlay", 2, host().childCount)
        rule.onNodeWithText("a").assertExists()
        rule.onNodeWithText("2").assertExists()
    }

    @Test fun opening_and_closing_a_note_with_a_table_emits_nothing() {
        setContent()
        rule.waitForIdle()
        pass(1000)
        shown = false
        rule.waitForIdle()
        assertTrue("nothing is emitted: $emitted", emitted.isEmpty())
    }

    @Test fun a_cell_edit_is_committed_after_the_pause_as_one_step() {
        setContent()
        rule.waitForIdle()
        rule.onNodeWithText("1").performTextReplacement("one")
        rule.waitForIdle()
        assertTrue("not committed at once", editor().markdown().contains("| 1 |"))
        pass(500)
        assertTrue(editor().markdown().contains("| one |"))
        pass(400)
        assertEquals(1, emitted.size)
        rule.runOnUiThread { actions!!.undo() }
        assertTrue("one undo gives the cell back", editor().markdown().contains("| 1 |"))
    }

    @Test fun an_edit_that_waits_is_committed_when_the_note_is_left() {
        setContent()
        rule.waitForIdle()
        rule.onNodeWithText("1").performTextReplacement("kept")
        rule.waitForIdle()
        shown = false
        rule.waitForIdle()
        assertTrue("the edit is in the emitted note: $emitted", emitted.lastOrNull()?.contains("| kept |") == true)
    }

    @Test fun a_viewer_sees_the_table_and_cannot_edit_it() {
        editable = false
        setContent()
        rule.waitForIdle()
        rule.onNodeWithText("a").assertIsNotEnabled()
        assertTrue(bars.all { it == null })
    }

    @Test fun the_span_reserves_the_height_the_overlay_measured() {
        setContent()
        rule.waitForIdle()
        val overlay = host().getChildAt(1)
        assertTrue("the overlay has a height", overlay.height > 0)
        assertEquals("the span reserves it", overlay.height, editor().tableZones().single().rect.height())
    }

    @Test fun the_insert_action_adds_a_table() {
        setContent("one")
        rule.waitForIdle()
        rule.runOnUiThread { actions!!.insertTable() }
        rule.waitForIdle()
        rule.runOnUiThread { host().viewTreeObserver.dispatchOnPreDraw() } // Robolectric draws on its own schedule: this is the pass the window would make
        rule.waitForIdle()
        assertEquals("the editor and one overlay", 2, host().childCount)
        assertFalse(editor().markdown() == "one")
    }
}
