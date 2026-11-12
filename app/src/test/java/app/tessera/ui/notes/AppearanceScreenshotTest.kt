package app.tessera.ui.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import app.tessera.R
import app.tessera.core.ui.tessera.LocalTagAppearances
import app.tessera.core.ui.tessera.TagStyle
import app.tessera.core.ui.tessera.TesseraChip
import app.tessera.core.ui.tessera.TesseraChipRow
import app.tessera.core.ui.tessera.parseEntityIcon
import app.tessera.core.ui.theme.AccentColor
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderTreeNode
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag
import app.tessera.ui.notes.components.AppearancePopup
import app.tessera.ui.notes.components.TesseraNotesDrawer
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of tag / folder appearance on the notes screens (spec §6.3), light + dark.
 * New, no goldens yet: the orchestrator records them after looking at the `_actual` images.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class AppearanceScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        composeRule.setContent {
            TesseraTheme(darkTheme = dark, dynamicColor = false) {
                CompositionLocalProvider(LocalTagAppearances provides tagStyles) {
                    Surface(modifier = Modifier.fillMaxSize()) { content() }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("ui/notes/$name.png")
    }

    @Composable
    private fun screen(state: NotesListState) {
        NotesListScreen(state = state, snackbarHostState = SnackbarHostState(), onQueryChange = {}, onTagToggle = {}, onToggleViewMode = {}, onCreateNote = {})
    }

    /** The popup over the notes screen, as it opens from a long press / the "⋮" menu. */
    @Composable
    private fun popupOverList(popup: @Composable (HazeState) -> Unit) {
        val haze = remember { HazeState() }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) { screen(listState()) }
            popup(haze)
        }
    }

    @Test fun popupTagLight() = capture("appearance_popup_tag_light", false) { tagPopup() }

    @Test fun popupTagDark() = capture("appearance_popup_tag_dark", true) { tagPopup() }

    @Test fun popupFolderNameTakenLight() = capture("appearance_popup_folder_name_taken_light", false) { folderPopup() }

    @Test fun popupFolderNameTakenDark() = capture("appearance_popup_folder_name_taken_dark", true) { folderPopup() }

    @Composable
    private fun tagPopup() = popupOverList { haze ->
        AppearancePopup(
            visible = true,
            title = "#compose",
            name = null,
            initialColor = "indigo",
            initialIcon = "lucide:lightbulb",
            showReset = true,
            onSave = { _, _, _ -> },
            onDismiss = {},
            hazeState = haze,
        )
    }

    @Composable
    private fun folderPopup() = popupOverList { haze ->
        AppearancePopup(
            visible = true,
            title = stringResource(R.string.folders_edit_title),
            name = "Personal",
            initialColor = "pink",
            initialIcon = "emoji:🏠",
            showReset = true,
            nameTaken = true,
            onSave = { _, _, _ -> },
            onDismiss = {},
            hazeState = haze,
        )
    }

    @Test fun drawerLight() = capture("appearance_drawer_light", false) { drawer() }

    @Test fun drawerDark() = capture("appearance_drawer_dark", true) { drawer() }

    @Test fun drawerMenuLight() = captureMenu("appearance_drawer_menu_light", false)

    @Test fun drawerMenuDark() = captureMenu("appearance_drawer_menu_dark", true)

    // The "⋮" menu is a popup window: open it, then grab the whole screen.
    private fun captureMenu(name: String, dark: Boolean) {
        composeRule.setContent {
            TesseraTheme(darkTheme = dark, dynamicColor = false) { Surface(modifier = Modifier.fillMaxSize()) { drawer() } }
        }
        val more = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.folders_more)
        composeRule.onAllNodesWithContentDescription(more)[0].performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("ui/notes/$name.png")
    }

    @Composable
    private fun drawer() {
        TesseraNotesDrawer(
            drawerState = rememberDrawerState(DrawerValue.Open),
            folderTree = coloredTree(),
            selectedFolderId = null,
            selectedSystemKey = "all",
            showSharedSection = false,
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

    @Test fun listTintedLight() = capture("appearance_list_tinted_light", false) { screen(listState()) }

    @Test fun listTintedDark() = capture("appearance_list_tinted_dark", true) { screen(listState()) }

    @Test fun tilesTintedLight() = capture("appearance_tiles_tinted_light", false) { screen(listState().copy(viewMode = NotesViewMode.Tiles)) }

    @Test fun tilesTintedDark() = capture("appearance_tiles_tinted_dark", true) { screen(listState().copy(viewMode = NotesViewMode.Tiles)) }

    // The same folder opened: its list is not tinted.
    @Test fun listInFolderLight() = capture("appearance_list_in_folder_light", false) { screen(inFolderState()) }

    @Test fun listInFolderDark() = capture("appearance_list_in_folder_dark", true) { screen(inFolderState()) }

    @Test fun filterRowLight() = capture("appearance_filter_row_light", false) { filterRow() }

    @Test fun filterRowDark() = capture("appearance_filter_row_dark", true) { filterRow() }

    @Composable
    private fun filterRow() {
        TesseraChipRow(
            chips = listOf(
                TesseraChip("android", "#android", selected = false),
                TesseraChip("compose", "#compose", selected = true),
                TesseraChip("home", "#home", selected = false),
                TesseraChip("books", "#books", selected = false),
            ),
            onChipClick = {},
            onChipLongClick = {},
        )
    }
}

private val tagStyles = mapOf(
    "home" to TagStyle(AccentColor.Blue, null),
    "android" to TagStyle(AccentColor.Green, parseEntityIcon("lucide:code")),
    "compose" to TagStyle(AccentColor.Orange, parseEntityIcon("emoji:🏠")),
)

private val work = Folder(FolderId("work"), "Work", null, 1L, color = "blue", icon = "lucide:briefcase")

private fun coloredTree() = listOf(
    FolderTreeNode(
        folder = work,
        // "Projects" has no colour of its own: the drawer shows it in Work's blue.
        children = listOf(Folder(FolderId("projects"), "Projects", work.id, 3L)),
    ),
    FolderTreeNode(Folder(FolderId("personal"), "Personal", null, 2L, color = "pink", icon = "emoji:🏠"), emptyList()),
    FolderTreeNode(Folder(FolderId("misc"), "Misc", null, 4L), emptyList()),
)

/** "All": note-1 selected (selection wins over the tint), note-2 blue with a tag of the same blue, note-3 green. */
private fun listState() = NotesListState.Content(
    notes = listOf(
        shotNote("note-1", "Material You redesign", "Pinned cards, search overlay, and folder drawer.", tags = listOf("android")),
        shotNote("note-2", "Grocery list", "Milk, eggs, bread.", tags = listOf("home")),
        shotNote("note-3", "Reading list", "Effective reading, Compose internals.", tags = listOf("books")),
        shotNote("note-4", "Loose note", "No folder, no tint."),
    ),
    pinnedCount = 0,
    query = "",
    selection = setOf(NoteId("note-1")),
    selectionMode = true,
    availableTags = listOf("android", "home", "books"),
    selectedTags = emptySet(),
    viewMode = NotesViewMode.List,
    noteTints = mapOf(NoteId("note-1") to "blue", NoteId("note-2") to "blue", NoteId("note-3") to "green"),
    folders = listOf(work),
    folderTree = coloredTree(),
)

private fun inFolderState() = listState().copy(
    selectionMode = false,
    selection = emptySet(),
    selectedFolderId = work.id,
    noteTints = emptyMap(),
)

private fun shotNote(id: String, title: String, content: String, tags: List<String> = emptyList()) = Note(
    id = NoteId(id), title = title, content = content, tags = tags.map(::Tag), isPinned = false,
    createdAt = 1L, updatedAt = 1L, deletedAt = null, syncedAt = null, ownerId = null,
)
