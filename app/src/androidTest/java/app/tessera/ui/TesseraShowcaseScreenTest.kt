package app.tessera.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.activity.ComponentActivity
import app.tessera.core.ui.UiTestTags
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag
import app.tessera.ui.notes.NotesListScreen
import app.tessera.ui.notes.NotesListState
import app.tessera.ui.notes.NotesViewMode
import org.junit.Rule
import org.junit.Test

class TesseraShowcaseScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showcaseRendersKeyCapsulesAndFab() {
        composeRule.setContent {
            NotesListScreen(
                state =
                    NotesListState.Content(
                        notes =
                            listOf(
                                previewNote(
                                    id = "note-1",
                                    title = "Smoke note",
                                    content = "Notes list smoke test content",
                                    tags = listOf("smoke"),
                                ),
                            ),
                        pinnedCount = 0,
                        query = "",
                        selection = emptySet(),
                        availableTags = listOf("smoke"),
                        selectedTags = emptySet(),
                        viewMode = NotesViewMode.Tiles,
                    ),
                snackbarHostState = SnackbarHostState(),
                onQueryChange = {},
                onTagToggle = {},
                onCreateNote = {},
            )
        }

        composeRule.onNodeWithText("Tessera").assertIsDisplayed()
        composeRule.onNodeWithText("Notes list smoke test content").assertIsDisplayed()
        composeRule
            .onNodeWithTag(UiTestTags.CreateNoteFab)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
    }
}

private fun previewNote(
    id: String,
    title: String,
    content: String,
    tags: List<String> = emptyList(),
): Note =
    Note(
        id = NoteId(id),
        title = title,
        content = content,
        tags = tags.map(::Tag),
        isPinned = false,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = null,
        syncedAt = null,
        ownerId = null,
    )
