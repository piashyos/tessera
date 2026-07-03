package app.tessera.ui.notes.components

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.TesseraTheme
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.Trash2
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines for [TesseraSwipeableNoteCard]'s revealed states (swipe-to-reveal, Task 4).
 *
 * The card's [androidx.compose.foundation.gestures.AnchoredDraggableState] is INTERNAL: callers
 * cannot set the offset, so the only honest way to reach a revealed state is to perform the swipe
 * gesture and let the snap animation settle. [androidx.compose.ui.test.ComposeContentTestRule.waitForIdle]
 * drives Robolectric's test clock forward until the snap tween settles to its anchor, so the capture
 * is taken with the card resting on an anchor rather than mid-flight — deterministic, not flaky.
 *
 * We assert the revealed button exists before capturing so a baseline can never silently degrade
 * into a Settled (unrevealed) capture masquerading as "revealed". This is an existence guard, not a
 * bounds assertion — this repo's M3 touch targets can't be measured via boundsInRoot (known gotcha).
 *
 * Action lists mirror how NotesBody builds them: left = single Archive (secondaryContainer);
 * right = Pin + Delete (secondaryContainer + errorContainer).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class TesseraSwipeableNoteCardScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val noteId = "screenshot-note"

    private fun capture(
        name: String,
        darkTheme: Boolean,
        gesture: (composeRule: androidx.compose.ui.test.junit4.ComposeContentTestRule) -> Unit,
        revealedActionKey: String,
    ) {
        composeRule.setContent {
            TesseraTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface {
                    SampleCard()
                }
            }
        }
        gesture(composeRule)
        composeRule.waitForIdle()
        // Guard: the revealed button must actually be laid out, otherwise we'd bake a Settled
        // capture as if it were revealed. captureRoboImage grabs the whole composition root.
        composeRule.onNodeWithTag(UiTestTags.swipeAction(noteId, revealedActionKey)).assertExists()
        composeRule.onRoot().captureRoboImage("ui/notes/components/$name.png")
    }

    @Composable
    private fun SampleCard() {
        var isOpen by remember { mutableStateOf(false) }
        TesseraSwipeableNoteCard(
            noteId = noteId,
            title = "Material You redesign",
            preview = "Pinned cards, search overlay, and folder drawer now drive the list.",
            dateLabel = "today",
            tags = listOf("android", "compose"),
            isPinned = false,
            isOpen = isOpen,
            onOpenChange = { isOpen = it },
            onClick = {},
            onLongClick = {},
            leftActions = listOf(
                SwipeAction(
                    key = "archive",
                    icon = Lucide.Archive,
                    label = "Archive",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = {},
                ),
            ),
            rightActions = listOf(
                SwipeAction(
                    key = "pin",
                    icon = Lucide.Pin,
                    label = "Pin",
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = {},
                ),
                SwipeAction(
                    key = "delete",
                    icon = Lucide.Trash2,
                    label = "Delete",
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = {},
                ),
            ),
            modifier = Modifier.width(411.dp),
        )
    }

    @Test
    fun rightRevealedLight() = capture(
        name = "swipeable_card_right_revealed_light",
        darkTheme = false,
        gesture = { it.onNodeWithTag(UiTestTags.noteCard(noteId)).performTouchInput { swipeLeft() } },
        revealedActionKey = "delete",
    )

    @Test
    fun rightRevealedDark() = capture(
        name = "swipeable_card_right_revealed_dark",
        darkTheme = true,
        gesture = { it.onNodeWithTag(UiTestTags.noteCard(noteId)).performTouchInput { swipeLeft() } },
        revealedActionKey = "delete",
    )

    @Test
    fun leftRevealedLight() = capture(
        name = "swipeable_card_left_revealed_light",
        darkTheme = false,
        gesture = { it.onNodeWithTag(UiTestTags.noteCard(noteId)).performTouchInput { swipeRight() } },
        revealedActionKey = "archive",
    )

    @Test
    fun leftRevealedDark() = capture(
        name = "swipeable_card_left_revealed_dark",
        darkTheme = true,
        gesture = { it.onNodeWithTag(UiTestTags.noteCard(noteId)).performTouchInput { swipeRight() } },
        revealedActionKey = "archive",
    )
}
