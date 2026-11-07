package app.tessera.ui.editor.components

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.engine.TesseraEditText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * The table bar over the real path of the device (QA finding T5): a real tap on the cell, a real tap in the text, an Undo through the actions of the
 * editor. API 27: the text magnifier of Compose (API 28 and up) cannot be shown by Robolectric and throws when a cell is tapped again and again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class NoteBodyTableFocusTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val md = "top line\n| a | b |\n| --- | --- |\n| 1 | 2 |\nbottom line"
    private var actions: RichBodyActions? = null
    private var bar by mutableStateOf<TableBarState?>(null)

    private fun host(): TableOverlayHost {
        fun find(v: View): TableOverlayHost? =
            if (v is TableOverlayHost) v else (v as? ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { find(g.getChildAt(it)) } }
        return find(rule.activity.window.decorView) ?: error("no TableOverlayHost")
    }

    private fun editor(): TesseraEditText = host().editor

    private fun drawn() {
        rule.waitForIdle()
        rule.runOnUiThread { host().viewTreeObserver.dispatchOnPreDraw() }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60))
        rule.waitForIdle()
    }

    /** A tap with a finger on the editor's text (the first line). */
    private fun tapText() {
        rule.runOnUiThread {
            val t = SystemClock.uptimeMillis()
            val x = 20f
            val y = 14f
            host().dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
            host().dispatchTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, x, y, 0))
        }
        drawn()
    }

    @Test fun the_bar_comes_back_at_every_tap_in_a_cell_after_add_row_a_tap_in_the_text_and_undo() {
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                NoteBody(markdown = md, onMarkdownChange = {}, onActionsReady = { actions = it }, onFormatStateChanged = {}, editable = true, onTableBarChanged = { bar = it }, modifier = Modifier.fillMaxSize())
            }
        }
        drawn()
        repeat(5) { round ->
            rule.onNodeWithText("1").performClick()
            drawn()
            assertNotNull("round $round: the bar is there with the cell in the focus", bar)
            bar!!.onAddRow()
            drawn()
            tapText()
            assertNull("round $round: the bar is gone with the focus in the text", bar)
            rule.runOnUiThread { actions!!.undo() }
            drawn()
        }
    }

    @Test fun the_cell_that_is_tapped_after_an_undo_takes_the_focus() {
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                NoteBody(markdown = md, onMarkdownChange = {}, onActionsReady = { actions = it }, onFormatStateChanged = {}, editable = true, onTableBarChanged = { bar = it }, modifier = Modifier.fillMaxSize())
            }
        }
        drawn()
        rule.onNodeWithText("1").performClick()
        drawn()
        bar!!.onAddRow()
        drawn()
        tapText()
        rule.runOnUiThread { actions!!.undo() }
        drawn()
        rule.onNodeWithText("1").performClick()
        drawn()
        rule.onNodeWithText("1").assertIsFocused()
        assertEquals(1, host().childCount - 1)
    }

    @Test fun the_bar_comes_back_when_the_layout_is_dropped_after_the_undo_as_healLayout_does() {
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                NoteBody(markdown = md, onMarkdownChange = {}, onActionsReady = { actions = it }, onFormatStateChanged = {}, editable = true, onTableBarChanged = { bar = it }, modifier = Modifier.fillMaxSize())
            }
        }
        drawn()
        repeat(3) { round ->
            rule.onNodeWithText("1").performClick()
            drawn()
            assertNotNull("round $round: the bar is there with the cell in the focus", bar)
            bar!!.onAddRow()
            drawn()
            tapText()
            rule.runOnUiThread {
                actions!!.undo()
                val p = editor().includeFontPadding
                editor().includeFontPadding = !p // what healLayout does: the layout is dropped and made again at the next measure
                editor().includeFontPadding = p
                host().viewTreeObserver.dispatchOnPreDraw() // a pass that sees no layout
            }
            drawn()
        }
    }

    @Test fun the_bar_comes_back_when_the_buttons_are_pressed_on_the_screen_as_on_the_device() {
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                    NoteBody(markdown = md, onMarkdownChange = {}, onActionsReady = { actions = it }, onFormatStateChanged = {}, editable = true, onTableBarChanged = { bar = it }, modifier = Modifier.weight(1f))
                    val b = bar
                    // the screen's bottom bar: the table bar while a cell has the focus, else the format bar
                    if (b != null) androidx.compose.material3.Button(onClick = b.onAddRow) { androidx.compose.material3.Text("Add row") }
                    else androidx.compose.material3.Button(onClick = { actions!!.undo() }) { androidx.compose.material3.Text("Undo") }
                }
            }
        }
        drawn()
        repeat(4) { round ->
            rule.onNodeWithText("1").performClick()
            drawn()
            rule.onNodeWithText("Add row").assertExists() // the bar of round $round
            rule.onNodeWithText("Add row").performClick()
            drawn()
            tapText()
            rule.onNodeWithText("Undo").performClick()
            drawn()
        }
    }

    private fun structureKeepsTheBar(op: (TableBarState) -> Unit) {
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                NoteBody(markdown = md, onMarkdownChange = {}, onActionsReady = { actions = it }, onFormatStateChanged = {}, editable = true, onTableBarChanged = { bar = it }, modifier = Modifier.fillMaxSize())
            }
        }
        drawn()
        rule.onNodeWithText("1").performClick()
        drawn()
        assertNotNull("the bar is there with the cell in the focus", bar)
        op(bar!!) // no tap in the text: on the device the focus stays in the cell
        drawn()
        drawn()
        assertNotNull("the bar is still there: the cell that was focused keeps the focus", bar)
        assertTrue("the editor's handles stay hidden", editor().handlesHidden)
        rule.onNodeWithText("1").assertIsFocused()
    }

    @Test fun a_new_row_does_not_take_the_bar_away_from_the_cell_that_has_the_focus() = structureKeepsTheBar { it.onAddRow() }

    @Test fun a_new_column_does_not_take_the_bar_away_from_the_cell_that_has_the_focus() = structureKeepsTheBar { it.onAddColumn() }
}
