package app.tessera.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag
import app.tessera.ui.notes.NotesListScreen
import app.tessera.ui.notes.NotesListState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class TesseraShowcaseScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recordLightShowcaseBaseline() {
        composeRule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NotesListScreen(
                        state =
                            NotesListState.Content(
                                notes =
                                    listOf(
                                        previewNote(
                                            id = "note-1",
                                            title = "Chunk 5 baseline",
                                            content = "Search, cards, and list navigation now replace the old showcase.",
                                            tags = listOf("android", "compose"),
                                        ),
                                        previewNote(
                                            id = "note-2",
                                            title = "Pinned note",
                                            content = "Swipe actions come next.",
                                            pinned = true,
                                            tags = listOf("pinned"),
                                        ),
                                    ),
                                pinnedCount = 1,
                                query = "",
                                selection = emptySet(),
                                availableTags = listOf("android", "compose", "pinned"),
                                selectedTags = emptySet(),
                            ),
                        snackbarHostState = SnackbarHostState(),
                        onQueryChange = {},
                        onTagToggle = {},
                        onCreateNote = {},
                    )
                }
            }
        }

        composeRule.onRoot().captureRoboImage("ui/tessera-showcase-light.png")
    }
}

private fun previewNote(
    id: String,
    title: String,
    content: String,
    pinned: Boolean = false,
    tags: List<String> = emptyList(),
): Note =
    Note(
        id = NoteId(id),
        title = title,
        content = content,
        tags = tags.map(::Tag),
        isPinned = pinned,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = null,
        syncedAt = null,
        ownerId = null,
    )
