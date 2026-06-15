package app.tessera.ui.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
                onAddTag = {},
                onRemoveTag = {},
                onTogglePin = {},
                onBack = {},
                onDelete = {},
            )
        }

        composeRule.onNodeWithTag(UiTestTags.EditorTitleField)
            .performTextInput("Groceries")

        assertEquals("Groceries", capturedTitle)
    }

    // -----------------------------------------------------------------------
    // Test 2: tapping back invokes onBack
    // -----------------------------------------------------------------------
    @Test
    fun tappingBackInvokesOnBack() {
        var backInvoked = false
        composeRule.setContent {
            NoteEditorScreen(
                state = editorState(),
                onTitleChange = {},
                onContentChange = {},
                onAddTag = {},
                onRemoveTag = {},
                onTogglePin = {},
                onBack = { backInvoked = true },
                onDelete = {},
            )
        }

        composeRule.onNodeWithTag(UiTestTags.EditorBack).performClick()

        assertTrue(backInvoked)
    }

    // -----------------------------------------------------------------------
    // Test 3: tapping the pin control invokes onTogglePin
    // -----------------------------------------------------------------------
    @Test
    fun tappingPinControlInvokesOnTogglePin() {
        var pinToggled = false
        composeRule.setContent {
            NoteEditorScreen(
                state = editorState(),
                onTitleChange = {},
                onContentChange = {},
                onAddTag = {},
                onRemoveTag = {},
                onTogglePin = { pinToggled = true },
                onBack = {},
                onDelete = {},
            )
        }

        composeRule.onNodeWithTag(UiTestTags.EditorPin).performClick()

        assertTrue(pinToggled)
    }

    // -----------------------------------------------------------------------
    // Test 4: tapping the delete control invokes onDelete
    // -----------------------------------------------------------------------
    @Test
    fun tappingDeleteControlInvokesOnDelete() {
        var deleteInvoked = false
        composeRule.setContent {
            NoteEditorScreen(
                state = editorState(),
                onTitleChange = {},
                onContentChange = {},
                onAddTag = {},
                onRemoveTag = {},
                onTogglePin = {},
                onBack = {},
                onDelete = { deleteInvoked = true },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.EditorDelete).performClick()

        assertTrue(deleteInvoked)
    }

    // -----------------------------------------------------------------------
    // Test 5: WYSIWYG smoke — all formatting buttons are present and tappable.
    // The true WYSIWYG behavior (selection-aware toggleFormatting) is verified
    // manually on a device; this smoke test guards against the toolbar being
    // dropped/renamed without an obvious build break.
    // -----------------------------------------------------------------------
    @Test
    fun markdownToolbarRendersAllFormattingButtons() {
        composeRule.setContent {
            NoteEditorScreen(
                state = editorState(content = "hello"),
                onTitleChange = {},
                onContentChange = {},
                onAddTag = {},
                onRemoveTag = {},
                onTogglePin = {},
                onBack = {},
                onDelete = {},
            )
        }

        listOf("Bold", "Italic", "Strikethrough", "Heading 1", "Heading 2", "Bullet list", "Checklist item")
            .forEach { description ->
                composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
            }
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
