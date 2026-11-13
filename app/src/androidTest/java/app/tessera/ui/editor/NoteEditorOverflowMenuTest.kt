package app.tessera.ui.editor

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import app.tessera.R
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.domain.model.ShareRole
import app.tessera.ui.editor.engine.savePng
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The "⋯" menu of the editor's bar on the device, per note and role (spec §2.5): which items, in which order, delete last — and a
 * screenshot of each menu in light and dark for the divider and the red delete (Downloads/native-editor-qa, `hdr-menu-<case>-<theme>.png`).
 * The roles come from the screen state, as the view model sets them: no account and no Firestore needed.
 */
class NoteEditorOverflowMenuTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var dark by mutableStateOf(false)
    private fun s(id: Int) = composeRule.activity.getString(id)

    /** Opens the menu of [state] and returns its item labels from the top down; shoots it in light and dark as [name]. */
    private fun menu(name: String, state: NoteEditorState): List<String> {
        composeRule.setContent {
            TesseraTheme(darkTheme = dark, dynamicColor = false) {
                NoteEditorScreen(
                    state = state.copy(isLoading = false),
                    onContentChange = {}, onAddTag = {}, onRemoveTag = {}, onTogglePin = {}, onBack = {}, onDelete = {},
                )
            }
        }
        composeRule.onNodeWithTag(UiTestTags.EditorOverflow).performClick()
        composeRule.waitForIdle()
        val labels = listOf(
            R.string.editor_make_shared, R.string.editor_manage_access, R.string.editor_make_private,
            R.string.editor_leave, R.string.editor_delete, R.string.editor_delete_for_all,
        ).map(::s)
        val order = labels.mapNotNull { label ->
            composeRule.onAllNodes(hasText(label), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { label to it.boundsInRoot.top }
        }.sortedBy { it.second }.map { it.first }
        for (theme in listOf("light", "dark")) {
            dark = theme == "dark"
            composeRule.waitForIdle()
            Thread.sleep(400)
            val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: error("no screenshot")
            savePng(composeRule.activity, "hdr-menu-$name-$theme", shot)
        }
        return order
    }

    @Test
    fun aPrivateNoteOfASignedInUserIsShareThenDelete() {
        assertEquals(
            listOf(s(R.string.editor_make_shared), s(R.string.editor_delete)),
            menu("private-signed-in", NoteEditorState(isOwner = true)),
        )
    }

    @Test
    fun theOwnerOfASharedNoteHasAccessPrivateThenDeleteForAll() {
        assertEquals(
            listOf(s(R.string.editor_manage_access), s(R.string.editor_make_private), s(R.string.editor_delete_for_all)),
            menu("shared-owner", NoteEditorState(isShared = true, myRole = ShareRole.OWNER)),
        )
    }

    @Test
    fun aViewerOfASharedNoteCanOnlyLeave() {
        assertEquals(listOf(s(R.string.editor_leave)), menu("shared-viewer", NoteEditorState(isShared = true, myRole = ShareRole.VIEWER)))
    }
}
