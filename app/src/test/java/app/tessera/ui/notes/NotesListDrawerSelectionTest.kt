package app.tessera.ui.notes

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.view.drawToBitmap
import androidx.test.core.app.ApplicationProvider
import app.tessera.R
import app.tessera.core.ui.tessera.TesseraDrawerItemTags
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The drawer highlight is derived from [NotesListState], not remembered by the screen: coming back from
 * Settings recreates the screen while the ViewModel still shows e.g. Trash, and the drawer must say so.
 * The highlight is a background only (no semantics), so the test reads the row's pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class NotesListDrawerSelectionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val rows = listOf(
        "all" to R.string.folders_all_notes,
        "pinned" to R.string.drawer_pinned,
        "archive" to R.string.notes_archive,
        "trash" to R.string.drawer_trash,
        "shared" to R.string.drawer_shared,
    )

    private fun empty(
        archive: Boolean = false,
        trash: Boolean = false,
        shared: Boolean = false,
        pinned: Boolean = false,
    ) = NotesListState.Empty(
        query = "",
        availableTags = emptyList(),
        selectedTags = emptySet(),
        viewMode = NotesViewMode.List,
        isArchiveView = archive,
        isTrashView = trash,
        isSharedView = shared,
        isPinnedView = pinned,
        hasSharedNotes = true,
    )

    private fun assertHighlighted(state: NotesListState, expectedKey: String) {
        var highlight = Color.Unspecified
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                highlight = MaterialTheme.colorScheme.secondaryContainer
                Surface {
                    NotesListScreen(
                        state = state,
                        snackbarHostState = SnackbarHostState(),
                        onQueryChange = {},
                        onTagToggle = {},
                        onToggleViewMode = {},
                        onCreateNote = {},
                    )
                }
            }
        }
        // Two entry points open the drawer (header and bottom bar); either does.
        composeRule.onAllNodesWithContentDescription(context.getString(R.string.folders_open_drawer))[0].performClick()
        // The screen has endless animations, so waitForIdle never returns: step the clock past the drawer's opening.
        composeRule.mainClock.advanceTimeBy(1_000)

        // captureToImage hangs under Robolectric; draw the window into a bitmap instead.
        val window = composeRule.activity.window.decorView.drawToBitmap()
        val highlighted = rows.filter { (_, labelRes) ->
            val bounds = composeRule.onNodeWithTag(TesseraDrawerItemTags.row(context.getString(labelRes)))
                .fetchSemanticsNode().boundsInWindow
            // Right end of the row: past the label, inside the rounded background.
            window.getPixel((bounds.right - 12).toInt(), bounds.center.y.toInt()) == highlight.toArgb()
        }.map { it.first }
        assertEquals("highlighted drawer rows for $state", listOf(expectedKey), highlighted)
    }

    @Test fun allNotesByDefault() = assertHighlighted(empty(), "all")

    @Test fun trashFollowsState() = assertHighlighted(empty(trash = true), "trash")

    @Test fun archiveFollowsState() = assertHighlighted(empty(archive = true), "archive")

    @Test fun pinnedFollowsState() = assertHighlighted(empty(pinned = true), "pinned")

    @Test fun sharedFollowsState() = assertHighlighted(empty(shared = true), "shared")
}
