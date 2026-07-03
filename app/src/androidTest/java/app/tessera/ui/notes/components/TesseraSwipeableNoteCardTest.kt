package app.tessera.ui.notes.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.UiTestTags
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class TesseraSwipeableNoteCardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val noteId = "n1"

    @Test
    fun swipeAloneDoesNotFireAnyAction() {
        var fired = false
        setCard(onDeleteFired = { fired = true })
        rule.onNodeWithTag(UiTestTags.noteCard(noteId)).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertFalse("swipe must not mutate; only a button tap does", fired)
    }

    @Test
    fun swipeLeftRevealsDeleteButton_tapFires() {
        var fired = false
        setCard(onDeleteFired = { fired = true })
        rule.onNodeWithTag(UiTestTags.noteCard(noteId)).performTouchInput { swipeLeft() }
        rule.onNodeWithTag(UiTestTags.swipeAction(noteId, "delete")).performClick()
        assertEquals(true, fired)
    }

    @Test
    fun swipeRightRevealsArchiveButton_tapFires() {
        var fired = false
        setCard(onArchiveFired = { fired = true })
        rule.onNodeWithTag(UiTestTags.noteCard(noteId)).performTouchInput { swipeRight() }
        rule.onNodeWithTag(UiTestTags.swipeAction(noteId, "archive")).performClick()
        assertEquals(true, fired)
    }

    @Test
    fun inertDirectionRevealsNothing() {
        // No right actions -> the end-to-start (left) swipe has no anchor and reveals nothing.
        setCard(withRightActions = false)
        rule.onNodeWithTag(UiTestTags.noteCard(noteId)).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithTag(UiTestTags.swipeAction(noteId, "delete")).assertDoesNotExist()
    }

    @Test
    fun bodyTapWhileOpenCollapses() {
        var navCount = 0
        setCard(onClick = { navCount++ })
        // Open by swiping left to reveal the delete action.
        rule.onNodeWithTag(UiTestTags.noteCard(noteId)).performTouchInput { swipeLeft() }
        rule.onNodeWithTag(UiTestTags.swipeAction(noteId, "delete")).assertExists()
        // First body tap while open must collapse, not navigate.
        rule.onNodeWithTag(UiTestTags.noteCard(noteId)).performClick()
        rule.waitForIdle()
        assertEquals("tapping an open card must collapse, not navigate", 0, navCount)
        // Collapse is confirmed by a subsequent tap now navigating (card is settled/closed).
        rule.onNodeWithTag(UiTestTags.noteCard(noteId)).performClick()
        rule.waitForIdle()
        assertEquals("tapping a closed card must navigate", 1, navCount)
    }

    private fun setCard(
        onArchiveFired: () -> Unit = {},
        onDeleteFired: () -> Unit = {},
        onClick: () -> Unit = {},
        withRightActions: Boolean = true,
    ) {
        rule.setContent {
            var isOpen by remember { mutableStateOf(false) }
            MaterialTheme {
                Surface {
                    TesseraSwipeableNoteCard(
                        noteId = noteId,
                        title = "Title",
                        preview = "Preview",
                        dateLabel = "today",
                        tags = emptyList(),
                        isPinned = false,
                        isOpen = isOpen,
                        onOpenChange = { isOpen = it },
                        onClick = onClick,
                        onLongClick = {},
                        leftActions = listOf(
                            SwipeAction("archive", Lucide.Archive, "Archive",
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.onSecondaryContainer, onArchiveFired),
                        ),
                        rightActions = if (withRightActions) listOf(
                            SwipeAction("delete", Lucide.Trash2, "Delete",
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.onErrorContainer, onDeleteFired),
                        ) else emptyList(),
                        modifier = Modifier.width(360.dp),
                    )
                }
            }
        }
    }
}
