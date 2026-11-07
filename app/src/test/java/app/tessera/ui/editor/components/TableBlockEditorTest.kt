package app.tessera.ui.editor.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
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
}
