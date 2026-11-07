package app.tessera.ui.editor.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextReplacement
import app.tessera.core.text.markdownblocks.TableGrid
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TableBlockEditorTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `composes with initial grid`() {
        var grid by mutableStateOf(TableGrid(listOf("A", "B"), listOf(listOf("1", "2"))))
        composeRule.setContent {
            TableBlockEditor(grid = grid, onGridChange = { grid = it })
        }
        composeRule.onRoot().assertExists()
    }

    @Test fun `a line break typed in a cell is Enter, never text, when Enter moves on`() {
        var grid by mutableStateOf(TableGrid(listOf("A", "B"), listOf(listOf("1", "2"))))
        var last = 0
        composeRule.setContent {
            TableBlockEditor(grid = grid, onGridChange = { grid = it }, enterMovesOn = true, onEnterInLastCell = { last++ })
        }
        composeRule.onNodeWithText("A").performTextReplacement("A\n") // what the keyboard's Enter does: a line break at the end
        composeRule.waitForIdle()
        assertEquals("the cell keeps its text", "A", grid.headers[0])
        composeRule.onNodeWithText("2").performTextReplacement("2\n") // the last cell
        composeRule.waitForIdle()
        assertEquals("2", grid.rows[0][1])
        assertEquals("the last cell hands the caret on", 1, last)
    }

    @Test fun `a paste with line breaks is flattened to one line`() {
        var grid by mutableStateOf(TableGrid(listOf("A", "B"), listOf(listOf("1", "2"))))
        composeRule.setContent {
            TableBlockEditor(grid = grid, onGridChange = { grid = it }, enterMovesOn = true)
        }
        composeRule.onNodeWithText("A").performTextReplacement("x\ny\r\nz")
        composeRule.waitForIdle()
        assertEquals("x y z", grid.headers[0])
    }

    @Test fun `without enterMovesOn the cell takes the line break as before`() {
        var grid by mutableStateOf(TableGrid(listOf("A", "B"), listOf(listOf("1", "2"))))
        composeRule.setContent { TableBlockEditor(grid = grid, onGridChange = { grid = it }) }
        composeRule.onNodeWithText("A").performTextReplacement("A\n")
        composeRule.waitForIdle()
        assertEquals("A\n", grid.headers[0])
    }

    @Test fun `a read-only table has disabled cells`() {
        composeRule.setContent { TableBlockEditor(grid = TableGrid(listOf("A", "B"), listOf(listOf("1", "2"))), onGridChange = {}, readOnly = true) }
        composeRule.onNodeWithText("A").assertIsNotEnabled()
    }

    @Test fun `Enter that comes with the composing word in one edit moves on and keeps the word`() { // a keyboard commits both together
        var grid by mutableStateOf(TableGrid(listOf("A", "B"), listOf(listOf("1", "2"))))
        composeRule.setContent { TableBlockEditor(grid = grid, onGridChange = { grid = it }, enterMovesOn = true) }
        composeRule.onNodeWithText("A").performTextInput("milk")
        composeRule.onNodeWithText("Amilk").performTextInput("\n") // the break at the caret, the text before it is not yet the grid's
        composeRule.waitForIdle()
        assertEquals("Amilk", grid.headers[0])
        composeRule.onNodeWithText("B").assertIsFocused()
    }

    @Test fun `the IME action Next moves to the next cell and from the last one hands the caret on`() {
        var grid by mutableStateOf(TableGrid(listOf("A", "B"), listOf(listOf("1", "2"))))
        var last = 0
        composeRule.setContent { TableBlockEditor(grid = grid, onGridChange = { grid = it }, enterMovesOn = true, onEnterInLastCell = { last++ }) }
        composeRule.onNodeWithText("A").performClick()
        composeRule.onNodeWithText("A").performImeAction()
        composeRule.onNodeWithText("B").assertIsFocused()
        composeRule.onNodeWithText("B").performImeAction()
        composeRule.onNodeWithText("1").assertIsFocused()
        composeRule.onNodeWithText("1").performImeAction()
        composeRule.onNodeWithText("2").assertIsFocused()
        composeRule.onNodeWithText("2").performImeAction()
        composeRule.waitForIdle()
        assertEquals(1, last)
    }

    @Test fun `a hardware Enter moves on and puts nothing into the cell`() {
        var grid by mutableStateOf(TableGrid(listOf("A", "B"), listOf(listOf("1", "2"))))
        composeRule.setContent { TableBlockEditor(grid = grid, onGridChange = { grid = it }, enterMovesOn = true) }
        composeRule.onNodeWithText("A").performClick()
        composeRule.onNodeWithText("A").performKeyInput { pressKey(androidx.compose.ui.input.key.Key.Enter) }
        composeRule.waitForIdle()
        assertEquals("A", grid.headers[0])
        composeRule.onNodeWithText("B").assertIsFocused()
    }
}
