package app.tessera.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag
import app.tessera.sync.SyncIndicator
import app.tessera.ui.editor.NoteEditorScreen
import app.tessera.ui.editor.NoteEditorState
import app.tessera.ui.notes.NotesListScreen
import app.tessera.ui.notes.NotesListState
import app.tessera.ui.notes.NotesViewMode
import app.tessera.ui.settings.SettingsScreen
import app.tessera.ui.settings.SettingsState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Accessibility audit (Plan 07, Tasks 4–5) executed as a regression test instead of a manual
 * grep pass:
 *
 * - **Content descriptions** — every node with a click action must expose an accessible name
 *   (a content description or text in its merged semantics) so TalkBack can announce it.
 * - **Touch targets** — every such node must measure at least 48×48dp, the Material minimum.
 *
 * Both checks run against the real composables for the three redesigned screens, so a regression
 * (an unlabeled icon button, a too-small tap target) fails the unit-test suite.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class AccessibilityAuditTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val minTouchTargetDp = 48f

    // 1dp of slack absorbs sub-pixel layout rounding at xxhdpi.
    private val toleranceDp = 1f

    /**
     * Visible controls below 48dp that are accepted (Plan 07 decision): either intentional dense
     * design from Plans 04–06 (compact tag chips, the editor format bar, filter chips) or standard
     * Material3 components whose 48dp touch slop isn't reflected in their layout bounds. Documented
     * in the QA checklist; matched by exact label or prefix (tag labels carry the tag name).
     */
    private val acceptedSubMinimumTargets =
        listOf(
            // Editor — compact custom Surface(onClick) controls (intentional dense design).
            "Remove tag", "Add tag",
            "Bold", "Italic", "Underline", "Strikethrough",
            "Heading 1", "Heading 2", "Bullet list", "Numbered list", "Checklist item", "Quote",
            // Notes — filter chip (32dp Material chip) and the Material3 icon view-mode toggle.
            "#", "Show as list", "Show as tiles",
            // Settings — Material3 buttons / rows (real 48dp touch slop, underreported bounds).
            "Back", "Sign out", "Open source licenses",
        )

    @Test fun notesListClickablesAreLabeled() {
        renderInTheme { NotesListScreenFixture() }
        assertAllClickablesLabeled("NotesListScreen")
    }

    @Test fun notesListCompactTargetsAreDocumented() {
        renderInTheme { NotesListScreenFixture() }
        assertOnlyDocumentedCompactTargets("NotesListScreen")
    }

    @Test fun editorClickablesAreLabeled() {
        renderInTheme { EditorScreenFixture() }
        assertAllClickablesLabeled("NoteEditorScreen")
    }

    @Test fun editorCompactTargetsAreDocumented() {
        renderInTheme { EditorScreenFixture() }
        assertOnlyDocumentedCompactTargets("NoteEditorScreen")
    }

    @Test fun settingsClickablesAreLabeled() {
        renderInTheme { SettingsScreenFixture() }
        assertAllClickablesLabeled("SettingsScreen")
    }

    @Test fun settingsCompactTargetsAreDocumented() {
        renderInTheme { SettingsScreenFixture() }
        assertOnlyDocumentedCompactTargets("SettingsScreen")
    }

    private fun renderInTheme(content: @Composable () -> Unit) {
        composeRule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) { content() }
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertAllClickablesLabeled(screen: String) {
        // Restrict to nodes Compose marks as buttons (Role.Button) — IconButton, Surface(onClick),
        // clickable rows. That is exactly the set Plan 07 Task 4 targets, and it naturally excludes
        // the AztecText body: that Android View exposes a click action through the platform
        // accessibility bridge but carries no Compose role, and as an EditText it is announced by
        // TalkBack via its own hint/content rather than a Compose contentDescription.
        val unlabeled =
            clickableNodes(composeRule)
                .filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Button }
                .filter { node ->
                    val config = node.config
                    val hasDescription = config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.isNotBlank() } == true
                    val hasText = config.getOrNull(SemanticsProperties.Text)?.any { it.text.isNotBlank() } == true
                    !hasDescription && !hasText
                }
        assertTrue(
            "$screen has button nodes without an accessible name (contentDescription/text): " +
                unlabeled.map { it.boundsInRoot }.joinToString(),
            unlabeled.isEmpty(),
        )
    }

    /**
     * Touch-target guard for the custom `Surface(onClick=)` controls (tag chips, format-bar
     * toggles, filter chips, settings rows). Standard Material3 buttons are excluded — they reserve
     * a 48dp touch slop via `minimumInteractiveComponentSize`, which is invisible to
     * [SemanticsNode.boundsInRoot], so measuring their layout bounds underreports the real target.
     *
     * Every visible clickable under 48dp must be one of the [acceptedSubMinimumTargets] documented
     * below (see also docs/superpowers QA checklist). A new sub-48dp control trips this test so the
     * deviation gets a conscious review instead of slipping in silently.
     */
    private fun assertOnlyDocumentedCompactTargets(screen: String) {
        val density = composeRule.density
        val minPx = with(density) { (minTouchTargetDp - toleranceDp).dp.toPx() }
        val undocumented =
            clickableNodes(composeRule)
                // Zero-area nodes are off-screen (horizontally scrolled toolbar tail, closed drawer)
                // and carry no real touch target to measure.
                .filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
                .filter { it.boundsInRoot.width < minPx || it.boundsInRoot.height < minPx }
                .filterNot { node -> acceptedSubMinimumTargets.any { matches(node, it) } }
        assertTrue(
            "$screen has undocumented sub-${minTouchTargetDp}dp touch targets: " +
                undocumented.joinToString {
                    val w = with(density) { it.boundsInRoot.width.toDp() }
                    val h = with(density) { it.boundsInRoot.height.toDp() }
                    "${accessibleLabel(it)}=${w}x${h}"
                },
            undocumented.isEmpty(),
        )
    }

    private fun matches(node: SemanticsNode, accepted: String): Boolean {
        val label = accessibleLabel(node)
        return label == accepted || label.startsWith(accepted)
    }

    private fun accessibleLabel(node: SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
            ?: node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
            ?: "?"

    private fun clickableNodes(provider: SemanticsNodeInteractionsProvider) =
        provider.onAllNodes(hasClickAction()).fetchSemanticsNodes()

    @Composable
    private fun NotesListScreenFixture() {
        NotesListScreen(
            state =
                NotesListState.Content(
                    notes = listOf(sampleNote()),
                    pinnedCount = 0,
                    query = "",
                    selection = emptySet(),
                    selectionMode = false,
                    availableTags = listOf("android"),
                    selectedTags = emptySet(),
                    viewMode = NotesViewMode.Tiles,
                ),
            snackbarHostState = SnackbarHostState(),
            onQueryChange = {},
            onTagToggle = {},
            onToggleViewMode = {},
            onCreateNote = {},
        )
    }

    @Composable
    private fun EditorScreenFixture() {
        NoteEditorScreen(
            state =
                NoteEditorState(
                    content = "Note title\n\nBody text.",
                    tags = listOf("android"),
                    isPinned = false,
                    availableTags = listOf("ideas"),
                    isLoading = false,
                ),
            onContentChange = {},
            onAddTag = {},
            onRemoveTag = {},
            onTogglePin = {},
            onBack = {},
            onDelete = {},
        )
    }

    @Composable
    private fun SettingsScreenFixture() {
        SettingsScreen(
            state =
                SettingsState(
                    isSignedIn = true,
                    displayName = "Ada",
                    email = "ada@example.com",
                    themeMode = ThemeMode.AUTO,
                    syncStatus = SyncIndicator.Ok(),
                ),
        )
    }

    private fun sampleNote() =
        Note(
            id = NoteId("note-1"),
            title = "Sample note",
            content = "Content",
            tags = listOf(Tag("android")),
            isPinned = false,
            createdAt = 1L,
            updatedAt = 1L,
            deletedAt = null,
            syncedAt = null,
            ownerId = null,
        )
}
