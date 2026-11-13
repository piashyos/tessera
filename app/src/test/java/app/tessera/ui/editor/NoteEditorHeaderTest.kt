package app.tessera.ui.editor

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.tessera.R
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.domain.model.ShareRole
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The editor's top bar: path and date as the title, `‹ · 📌 · ⋯`, and the ⋯ menu's items per role (spec §2.4, §2.5). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteEditorHeaderTest {
    @get:Rule val rule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun s(id: Int) = context.getString(id)

    private fun show(state: NoteEditorState) = rule.setContent {
        TesseraTheme(darkTheme = false, dynamicColor = false) {
            NoteEditorScreen(
                state = state.copy(isLoading = false),
                onContentChange = {}, onAddTag = {}, onRemoveTag = {}, onTogglePin = {}, onBack = {}, onDelete = {},
            )
        }
    }

    private fun openMenu() = rule.onNodeWithTag(UiTestTags.EditorOverflow).performClick()

    private fun assertMenu(shown: List<Int>, hidden: List<Int>) {
        shown.forEach { rule.onNodeWithText(s(it)).assertIsDisplayed() }
        hidden.forEach { rule.onAllNodesWithText(s(it)).assertCountEquals(0) }
    }

    private val all = listOf(
        R.string.editor_make_shared, R.string.editor_manage_access, R.string.editor_make_private,
        R.string.editor_leave, R.string.editor_delete, R.string.editor_delete_for_all,
    )
    private fun only(vararg shown: Int) = shown.toList() to all - shown.toSet()

    @Test fun path_and_date_are_in_the_bar() {
        show(NoteEditorState(folderBreadcrumb = listOf("Work", "Projects"), updatedAt = System.currentTimeMillis()))
        rule.onNodeWithText("Work › Projects").assertIsDisplayed()
        rule.onNodeWithText(s(R.string.note_date_today)).assertIsDisplayed()
    }

    @Test fun no_folder_shows_only_the_date() {
        show(NoteEditorState(updatedAt = System.currentTimeMillis()))
        rule.onNodeWithText(s(R.string.note_date_today)).assertIsDisplayed()
    }

    @Test fun private_note_menu_is_share_then_delete_and_the_bar_has_no_trash() {
        show(NoteEditorState(isOwner = true))
        rule.onAllNodesWithContentDescription(s(R.string.editor_delete)).assertCountEquals(0)
        rule.onNodeWithTag(UiTestTags.EditorBack).assertIsDisplayed()
        rule.onNodeWithTag(UiTestTags.EditorPin).assertIsDisplayed()
        openMenu()
        val (shown, hidden) = only(R.string.editor_make_shared, R.string.editor_delete)
        assertMenu(shown, hidden)
    }

    @Test fun private_note_without_sign_in_menu_is_delete_alone() {
        show(NoteEditorState(isOwner = false))
        openMenu()
        val (shown, hidden) = only(R.string.editor_delete)
        assertMenu(shown, hidden)
    }

    @Test fun owner_of_a_shared_note_menu() {
        show(NoteEditorState(isShared = true, myRole = ShareRole.OWNER))
        rule.onAllNodesWithContentDescription(s(R.string.editor_delete)).assertCountEquals(0)
        openMenu()
        val (shown, hidden) = only(R.string.editor_manage_access, R.string.editor_make_private, R.string.editor_delete_for_all)
        assertMenu(shown, hidden)
    }

    @Test fun viewer_of_a_shared_note_menu_is_leave() {
        show(NoteEditorState(isShared = true, myRole = ShareRole.VIEWER))
        rule.onNodeWithTag(UiTestTags.EditorOverflow).assertIsDisplayed()
        openMenu()
        val (shown, hidden) = only(R.string.editor_leave)
        assertMenu(shown, hidden)
    }

    @Test fun shared_note_with_unknown_role_has_no_overflow() {
        show(NoteEditorState(isShared = true, myRole = null))
        rule.onAllNodesWithTag(UiTestTags.EditorOverflow).assertCountEquals(0)
    }
}
