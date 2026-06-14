package app.tessera.ui.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.tessera.core.ui.UiTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NoteEditorScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // -----------------------------------------------------------------------
    // Test 1: typing in the title field invokes onTitleChange
    // -----------------------------------------------------------------------
    @Test
    fun typingInTitleFieldInvokesOnTitleChange() {
        var capturedTitle = ""
        composeRule.setContent {
            NoteEditorScreen(
                state = editorState(),
                onTitleChange = { capturedTitle = it },
                onContentChange = {},
                onToggleChecklist = {},
                onAddTag = {},
                onRemoveTag = {},
                onTogglePin = {},
                onInsertToken = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag(UiTestTags.EditorTitleField)
            .performTextInput("Groceries")

        assertEquals("Groceries", capturedTitle)
    }

    // -----------------------------------------------------------------------
    // Test 2: tapping a checklist item invokes onToggleChecklist with correct index
    // -----------------------------------------------------------------------
    @Test
    fun tappingChecklistItemInvokesOnToggleChecklistWithCorrectIndex() {
        var capturedIndex = -1
        composeRule.setContent {
            NoteEditorScreen(
                state = editorState(content = "- [ ] milk\n- [x] eggs"),
                onTitleChange = {},
                onContentChange = {},
                onToggleChecklist = { capturedIndex = it },
                onAddTag = {},
                onRemoveTag = {},
                onTogglePin = {},
                onInsertToken = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag(UiTestTags.editorChecklistItem(1))
            .performClick()

        assertEquals(1, capturedIndex)
    }

    // -----------------------------------------------------------------------
    // Test 3: tapping back invokes onBack
    // -----------------------------------------------------------------------
    @Test
    fun tappingBackInvokesOnBack() {
        var backInvoked = false
        composeRule.setContent {
            NoteEditorScreen(
                state = editorState(),
                onTitleChange = {},
                onContentChange = {},
                onToggleChecklist = {},
                onAddTag = {},
                onRemoveTag = {},
                onTogglePin = {},
                onInsertToken = {},
                onBack = { backInvoked = true },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.EditorBack).performClick()

        assertTrue(backInvoked)
    }

    // -----------------------------------------------------------------------
    // Test 4: tapping the pin control invokes onTogglePin
    // -----------------------------------------------------------------------
    @Test
    fun tappingPinControlInvokesOnTogglePin() {
        var pinToggled = false
        composeRule.setContent {
            NoteEditorScreen(
                state = editorState(),
                onTitleChange = {},
                onContentChange = {},
                onToggleChecklist = {},
                onAddTag = {},
                onRemoveTag = {},
                onTogglePin = { pinToggled = true },
                onInsertToken = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag(UiTestTags.EditorPin).performClick()

        assertTrue(pinToggled)
    }
}

// ---------------------------------------------------------------------------
// Helper: build a NoteEditorState with isLoading = false
// ---------------------------------------------------------------------------
private fun editorState(
    title: String = "",
    content: String = "",
    tags: List<String> = emptyList(),
    isPinned: Boolean = false,
    availableTags: List<String> = emptyList(),
) = NoteEditorState(
    title = title,
    content = content,
    tags = tags,
    isPinned = isPinned,
    availableTags = availableTags,
    isLoading = false,
)
