package app.tessera.ui.editor.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.tessera.core.text.markdownblocks.TableGrid
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
}
