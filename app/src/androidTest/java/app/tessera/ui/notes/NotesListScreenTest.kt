package app.tessera.ui.notes

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import app.tessera.core.ui.UiTestTags
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NotesListScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fabTapInvokesCreate() {
        var created = false
        composeRule.setContent {
            NotesListScreen(
                state = contentState(listOf(note("note-1"))),
                snackbarHostState = SnackbarHostState(),
                onQueryChange = {},
                onTagToggle = {},
                onToggleViewMode = {},
                onCreateNote = { created = true },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.CreateNoteFab).performClick()

        assertTrue(created)
    }

    @Test
    fun swipeLeftRevealsPinActionAndTogglesPin() {
        var toggled: NoteId? = null
        composeRule.setContent {
            NotesListScreen(
                state = contentState(listOf(note("note-1"))),
                snackbarHostState = SnackbarHostState(),
                onQueryChange = {},
                onTagToggle = {},
                onToggleViewMode = {},
                onCreateNote = {},
                onTogglePin = { toggled = it },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.noteCard("note-1")).performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag(UiTestTags.revealPinAction("note-1")).assertIsDisplayed().performClick()

        assertEquals(NoteId("note-1"), toggled)
    }

    @Test
    fun deleteShowsUndoSnackbarAndUndoRestores() {
        var deleted: NoteId? = null
        var restored: NoteId? = null
        composeRule.setContent {
            NotesListScreen(
                state = contentState(listOf(note("note-1"))),
                snackbarHostState = SnackbarHostState(),
                onQueryChange = {},
                onTagToggle = {},
                onToggleViewMode = {},
                onCreateNote = {},
                onDeleteNote = { deleted = it.id },
                onUndoDelete = { restored = it },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.noteCard("note-1")).performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag(UiTestTags.revealDeleteAction("note-1")).performClick()

        assertEquals(NoteId("note-1"), deleted)
        composeRule.onNodeWithText("Note deleted").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        assertEquals(NoteId("note-1"), restored)
    }

    @Test
    fun rendersTodaySectionHeaderForNoteDatedToday() {
        composeRule.setContent {
            NotesListScreen(
                state = contentState(listOf(note("note-1", updatedAt = System.currentTimeMillis()))),
                snackbarHostState = SnackbarHostState(),
                onQueryChange = {},
                onTagToggle = {},
                onToggleViewMode = {},
                onCreateNote = {},
            )
        }

        composeRule.onNodeWithTag(UiTestTags.sectionHeader(NoteGroup.Today.name)).assertIsDisplayed()
    }
}

private fun contentState(notes: List<Note>) =
    NotesListState.Content(
        notes = notes,
        pinnedCount = notes.count(Note::isPinned),
        query = "",
        selection = emptySet(),
        availableTags = emptyList(),
        selectedTags = emptySet(),
        viewMode = NotesViewMode.Tiles,
    )

private fun note(id: String, updatedAt: Long = 1L): Note =
    Note(
        id = NoteId(id),
        title = "Title $id",
        content = "Body for $id",
        tags = listOf(Tag("demo")),
        isPinned = false,
        createdAt = 1L,
        updatedAt = updatedAt,
        deletedAt = null,
        syncedAt = null,
        ownerId = null,
    )
