package app.tessera.ui.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.UiTestTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NoteEditorScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingBackInvokesOnBack() {
        var backInvoked = false
        composeRule.setContent {
            NoteEditorScreen(
                state = editorState(),
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

    @Test
    fun tappingPinControlInvokesOnTogglePin() {
        var pinToggled = false
        composeRule.setContent {
            NoteEditorScreen(
                state = editorState(),
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

    @Test
    fun tappingDeleteControlInvokesOnDelete() {
        var deleteInvoked = false
        composeRule.setContent {
            NoteEditorScreen(
                state = editorState(),
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

    @Test
    fun markdownToolbarRendersAllFormattingButtons() {
        composeRule.setContent {
            NoteEditorScreen(
                state = editorState(content = "hello"),
                onContentChange = {},
                onAddTag = {},
                onRemoveTag = {},
                onTogglePin = {},
                onBack = {},
                onDelete = {},
            )
        }

        listOf("Bold", "Italic", "Strikethrough", "Bullet list", "Checklist item")
            .forEach { description ->
                composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
            }
    }
}

private fun editorState(
    content: String = "",
    tags: List<String> = emptyList(),
    isPinned: Boolean = false,
    availableTags: List<String> = emptyList(),
) = NoteEditorState(
    content = content,
    tags = tags,
    isPinned = isPinned,
    availableTags = availableTags,
    isLoading = false,
)
