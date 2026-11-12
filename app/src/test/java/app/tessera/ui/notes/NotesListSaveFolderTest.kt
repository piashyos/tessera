package app.tessera.ui.notes

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderSaveResult
import app.tessera.domain.model.FolderTreeNode
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The screen's glue between the appearance popup and the folder save result (NameTaken / Saved / a second tap). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class NotesListSaveFolderTest {
    @get:Rule
    val rule = createComposeRule()

    private val work = Folder(FolderId("work"), "Work", null, 1L)
    private val taken = "A folder with this name already exists"

    private fun show(onUpdateFolder: suspend (FolderId, String, String?, String?) -> FolderSaveResult) {
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                NotesListScreen(
                    state = NotesListState.Empty(
                        query = "", availableTags = emptyList(), selectedTags = emptySet(), viewMode = NotesViewMode.List,
                        folders = listOf(work), folderTree = listOf(FolderTreeNode(work, emptyList())),
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onQueryChange = {}, onTagToggle = {}, onToggleViewMode = {}, onCreateNote = {},
                    onUpdateFolder = onUpdateFolder,
                )
            }
        }
        // Drawer -> "⋮" of the first folder -> Edit.
        rule.onAllNodesWithContentDescription("Folders")[0].performClick()
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription("Folder actions")[0].performClick()
        rule.onNodeWithText("Edit").performClick()
        rule.waitForIdle()
    }

    @Test
    fun nameTakenShowsTheErrorAndKeepsThePopup() {
        show { _, _, _, _ -> FolderSaveResult.NameTaken }
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(taken).assertExists()
        rule.onNodeWithText("Save").assertExists()
        // Editing the name takes the error away.
        rule.onAllNodesWithTextField()[0].performTextInput("2")
        rule.onNodeWithText(taken).assertDoesNotExist()
    }

    @Test
    fun savedClosesThePopup() {
        show { id, name, color, icon -> FolderSaveResult.Saved(Folder(id, name, null, 1L, color, icon)) }
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test
    fun aSecondTapWhileSavingDoesNotSaveTwice() {
        val gate = CompletableDeferred<FolderSaveResult>()
        var calls = 0
        show { _, _, _, _ -> calls++; gate.await() }
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()
        assertEquals(1, calls)
        gate.complete(FolderSaveResult.Saved(work))
        rule.waitForIdle()
        rule.onNodeWithText("Save").assertDoesNotExist()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextField() = onAllNodes(hasSetTextAction())
}
