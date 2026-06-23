package app.tessera.ui.notes

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderTreeNode
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag
import app.tessera.ui.notes.components.NoteActionsSheet
import app.tessera.ui.notes.components.TesseraNotesDrawer
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot baselines for [NotesListScreen] across its key states (Plan 07, Task 1).
 *
 * Every state is driven purely through [NotesListState] / theme parameters so the captures
 * are deterministic under Robolectric — no IME, no animations in flight, no Android Views.
 * The drawer is rendered directly with an open [DrawerState] instead of being driven by a
 * click, which keeps the open-panel capture stable.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class NotesListScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, darkTheme: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            TesseraTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) { content() }
            }
        }
        composeRule.onRoot().captureRoboImage("ui/notes/$name.png")
    }

    @androidx.compose.runtime.Composable
    private fun screen(state: NotesListState) {
        NotesListScreen(
            state = state,
            snackbarHostState = SnackbarHostState(),
            onQueryChange = {},
            onTagToggle = {},
            onToggleViewMode = {},
            onCreateNote = {},
        )
    }

    @Test fun loadingLight() = capture("notes_loading_light", darkTheme = false) { screen(NotesListState.Loading) }

    @Test fun loadingDark() = capture("notes_loading_dark", darkTheme = true) { screen(NotesListState.Loading) }

    @Test fun emptyLight() = capture("notes_empty_light", darkTheme = false) { screen(emptyState()) }

    @Test fun emptyDark() = capture("notes_empty_dark", darkTheme = true) { screen(emptyState()) }

    @Test fun contentLight() = capture("notes_content_light", darkTheme = false) { screen(contentState()) }

    @Test fun contentDark() = capture("notes_content_dark", darkTheme = true) { screen(contentState()) }

    @Test fun selectionLight() = capture("notes_selection_light", darkTheme = false) {
        screen(contentState().copy(selectionMode = true, selection = setOf(NoteId("note-1"))))
    }

    @Test fun selectionDark() = capture("notes_selection_dark", darkTheme = true) {
        screen(contentState().copy(selectionMode = true, selection = setOf(NoteId("note-1"))))
    }

    @Test fun searchExpandedLight() = capture("notes_search_expanded_light", darkTheme = false) {
        screen(contentState().copy(searchExpanded = true, query = "compose"))
    }

    @Test fun searchExpandedDark() = capture("notes_search_expanded_dark", darkTheme = true) {
        screen(contentState().copy(searchExpanded = true, query = "compose"))
    }

    @Test fun drawerLight() = capture("notes_drawer_light", darkTheme = false) { openDrawer() }

    @Test fun drawerDark() = capture("notes_drawer_dark", darkTheme = true) { openDrawer() }

    @Test fun noteActionsSheetLight() = captureSheet("notes_actions_sheet_light", darkTheme = false)

    @Test fun noteActionsSheetDark() = captureSheet("notes_actions_sheet_dark", darkTheme = true)

    // The action sheet is a ModalBottomSheet, which renders in its own window. captureScreenRoboImage
    // grabs the whole device screen (scrim + sheet) instead of only the main composition root.
    private fun captureSheet(name: String, darkTheme: Boolean) {
        composeRule.setContent {
            TesseraTheme(darkTheme = darkTheme, dynamicColor = false) {
                NoteActionsSheet(
                    noteTitle = "Material You redesign",
                    isPinned = true,
                    isArchived = false,
                    onPinToggle = {},
                    onSelect = {},
                    onMoveToFolder = {},
                    onAddTags = {},
                    onDuplicate = {},
                    onArchiveToggle = {},
                    onDelete = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("ui/notes/$name.png")
    }

    @androidx.compose.runtime.Composable
    private fun openDrawer() {
        TesseraNotesDrawer(
            drawerState = rememberDrawerState(DrawerValue.Open),
            folderTree = sampleFolderTree(),
            selectedFolderId = null,
            selectedSystemKey = "all",
            onSystemSelect = {},
            onFolderSelect = {},
            onCreateRootFolder = {},
            onCreateSubfolder = {},
            onDeleteFolder = {},
            onSettingsClick = {},
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {}
        }
    }
}

private fun emptyState() =
    NotesListState.Empty(
        query = "",
        availableTags = emptyList(),
        selectedTags = emptySet(),
        viewMode = NotesViewMode.Tiles,
    )

private fun contentState() =
    NotesListState.Content(
        notes =
            listOf(
                previewNote(
                    id = "note-1",
                    title = "Material You redesign",
                    content = "Pinned cards, search overlay, and folder drawer now drive the list.",
                    pinned = true,
                    tags = listOf("android", "compose"),
                ),
                previewNote(
                    id = "note-2",
                    title = "Grocery list",
                    content = "Milk, eggs, bread, and a fresh baseline.",
                    tags = listOf("home"),
                ),
                previewNote(
                    id = "note-3",
                    title = "Reading list",
                    content = "Effective Kotlin, Compose internals.",
                    tags = listOf("books"),
                ),
            ),
        pinnedCount = 1,
        query = "",
        selection = emptySet(),
        selectionMode = false,
        availableTags = listOf("android", "compose", "home", "books"),
        selectedTags = emptySet(),
        viewMode = NotesViewMode.Tiles,
        folders = sampleFolders(),
        folderTree = sampleFolderTree(),
    )

private fun sampleFolders() =
    listOf(
        Folder(id = FolderId("work"), name = "Work", parentId = null, createdAt = 1L),
        Folder(id = FolderId("personal"), name = "Personal", parentId = null, createdAt = 2L),
    )

private fun sampleFolderTree() =
    listOf(
        FolderTreeNode(
            folder = Folder(id = FolderId("work"), name = "Work", parentId = null, createdAt = 1L),
            children = listOf(Folder(id = FolderId("projects"), name = "Projects", parentId = FolderId("work"), createdAt = 3L)),
        ),
        FolderTreeNode(
            folder = Folder(id = FolderId("personal"), name = "Personal", parentId = null, createdAt = 2L),
            children = emptyList(),
        ),
    )

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
