package app.tessera.ui.editor.components

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.Mark
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.engine.FormatState
import app.tessera.ui.editor.engine.ParagraphFormat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `EditorFormatBar` / `MarkdownFormatMenu` with `nativeEngine = true` (spec §7, D15) and the unchanged Aztec default. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorFormatBarNativeTest {
    @get:Rule val rule = createComposeRule()

    private class Recording : RichBodyActions {
        val calls = mutableListOf<String>()
        override fun toggleBold() { calls += "bold" }
        override fun toggleItalic() { calls += "italic" }
        override fun toggleStrikethrough() { calls += "strike" }
        override fun toggleParagraph() { calls += "paragraph" }
        override fun toggleHeading1() { calls += "h1" }
        override fun toggleHeading2() { calls += "h2" }
        override fun toggleHeading3() { calls += "h3" }
        override fun toggleHeading4() {}
        override fun toggleHeading5() {}
        override fun toggleHeading6() {}
        override fun toggleBulletList() { calls += "bullet" }
        override fun toggleTaskList() { calls += "task" }
        override fun toggleQuote() { calls += "quote" }
        override fun toggleCode() { calls += "code" }
        override fun toggleSubheading() { calls += "subheading" }
        override fun toggleDashList() { calls += "dash" }
        override fun indent() { calls += "indent" }
        override fun outdent() { calls += "outdent" }
        override fun undo() { calls += "undo" }
        override fun redo() { calls += "redo" }
        override fun focusBody() {}
        override fun activeFormats() = emptySet<org.wordpress.aztec.AztecTextFormat>()
    }

    private fun bar(
        fs: FormatState?,
        native: Boolean = true,
        actions: RichBodyActions = Recording(),
    ) = rule.setContent {
        TesseraTheme(darkTheme = false, dynamicColor = false) {
            EditorFormatBar(actions = actions, activeFormats = emptySet(), nativeEngine = native, formatState = fs)
        }
    }

    @Test fun native_bar_has_table_image_and_camera_and_dash_and_indents() {
        bar(FormatState.Empty)
        listOf("Insert table", "Insert image", "Take photo", "Dashed list", "Indent", "Outdent", "Bullet list", "Numbered list", "Checklist item", "Divider", "Quote", "Code")
            .forEach { rule.onNodeWithContentDescription(it).assertExists() }
    }

    @Config(qualifiers = "w411dp-h800dp")
    @Test fun native_lists_come_before_the_marks_and_are_in_the_frame_of_a_phone() {
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(411.dp)) {
                    EditorFormatBar(actions = Recording(), activeFormats = emptySet(), nativeEngine = true, formatState = FormatState.Empty)
                }
            }
        }
        fun node(d: String) = rule.onNodeWithContentDescription(d).fetchSemanticsNode()
        fun left(d: String) = node(d).positionInRoot.x // not clipped by the scroll viewport
        fun right(d: String) = node(d).positionInRoot.x + node(d).size.width
        val frame = with(rule.density) { 411.dp.toPx() }
        val lists = listOf("Checklist item", "Bullet list", "Dashed list", "Numbered list")
        assertEquals("order of the lists", lists, lists.sortedBy { left(it) })
        lists.forEach {
            assertTrue("$it starts before Bold", left(it) < left("Bold"))
            assertTrue("$it is in the frame: ${right(it)} of $frame", right(it) <= frame)
            assertTrue("$it is left of the pinned more button", right(it) <= left("More formats"))
        }
        // the scrolling groups (⋯ is pinned: the groups that do not fit run behind it in the layout)
        val order = lists + listOf("Bold", "Italic", "Underline", "Strikethrough", "Heading 1", "Heading 2", "Indent", "Outdent", "Quote", "Code", "Divider")
        assertEquals("the order of the scrolling groups", order, order.sortedBy { left(it) })
        assertTrue("Undo and Redo are pinned before the lists", right("Redo") <= left("Checklist item"))
    }

    @Test fun native_buttons_take_their_activity_from_the_format_state() {
        bar(FormatState(paragraph = ParagraphFormat.Single(ParagraphKind.Title), marks = setOf(Mark.Bold, Mark.Strike)))
        rule.onNodeWithContentDescription("Bold").assertIsSelected()
        rule.onNodeWithContentDescription("Strikethrough").assertIsSelected()
        rule.onNodeWithContentDescription("Italic").assertIsNotSelected()
        rule.onNodeWithContentDescription("Underline").assertIsNotSelected()
        rule.onNodeWithContentDescription("Heading 1").assertIsSelected()
        rule.onNodeWithContentDescription("Heading 2").assertIsNotSelected()
        rule.onNodeWithContentDescription("Bullet list").assertIsNotSelected()
    }

    @Test fun native_list_buttons_follow_the_list_type_whatever_the_level() {
        val item = { type: ListType -> FormatState(paragraph = ParagraphFormat.Single(ParagraphKind.ListItem(type, 2))) }
        bar(item(ListType.Dash))
        rule.onNodeWithContentDescription("Dashed list").assertIsSelected()
        rule.onNodeWithContentDescription("Bullet list").assertIsNotSelected()
        rule.onNodeWithContentDescription("Checklist item").assertIsNotSelected()
    }

    @Test fun native_mixed_paragraphs_light_no_kind_button() {
        bar(FormatState(paragraph = ParagraphFormat.Mixed))
        listOf("Heading 1", "Heading 2", "Bullet list", "Quote", "Code").forEach {
            rule.onNodeWithContentDescription(it).assertIsNotSelected()
        }
    }

    @Test fun native_quote_and_code_buttons_follow_the_kind() {
        bar(FormatState(paragraph = ParagraphFormat.Single(ParagraphKind.Mono)))
        rule.onNodeWithContentDescription("Code").assertIsSelected()
        rule.onNodeWithContentDescription("Quote").assertIsNotSelected()
    }

    @Test fun undo_redo_are_disabled_without_history_and_enabled_with_it() {
        bar(FormatState(canUndo = false, canRedo = false))
        rule.onNodeWithContentDescription("Undo").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Redo").assertIsNotEnabled()
    }

    @Test fun undo_redo_enabled_by_the_state_and_call_the_actions() {
        val actions = Recording()
        bar(FormatState(canUndo = true, canRedo = true), actions = actions)
        rule.onNodeWithContentDescription("Undo").assertIsEnabled().performClick()
        rule.onNodeWithContentDescription("Redo").assertIsEnabled().performClick()
        assertEquals(listOf("undo", "redo"), actions.calls)
    }

    @Test fun only_one_of_undo_redo_can_be_enabled() {
        bar(FormatState(canUndo = true, canRedo = false))
        rule.onNodeWithContentDescription("Undo").assertIsEnabled()
        rule.onNodeWithContentDescription("Redo").assertIsNotEnabled()
    }

    @Test fun indent_buttons_follow_canIndent_and_canOutdent() {
        bar(FormatState(canIndent = true, canOutdent = false))
        rule.onNodeWithContentDescription("Indent").assertIsEnabled()
        rule.onNodeWithContentDescription("Outdent").assertIsNotEnabled()
    }

    @Test fun new_buttons_call_their_actions() {
        val actions = Recording()
        bar(FormatState(canIndent = true, canOutdent = true), actions = actions)
        rule.onNodeWithContentDescription("Dashed list").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Indent").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Outdent").performScrollTo().performClick()
        assertEquals(listOf("dash", "indent", "outdent"), actions.calls)
    }

    @Test fun native_menu_lists_title_heading_subheading_body_mono_quote_only() {
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                MarkdownFormatMenu(
                    expanded = true,
                    activeFormats = emptySet(),
                    actions = Recording(),
                    onDismiss = {},
                    nativeEngine = true,
                    formatState = FormatState.Empty,
                )
            }
        }
        listOf("Title", "Heading", "Subheading", "Body", "Monospaced", "Quote")
            .forEach { rule.onNodeWithText(it).performScrollTo().assertExists() }
        listOf("Paragraph", "Heading 1", "Heading 6", "Code", "Divider", "Insert table")
            .forEach { rule.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test fun native_menu_entries_call_the_actions() {
        val actions = Recording()
        var dismissed = 0
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                MarkdownFormatMenu(
                    expanded = true,
                    activeFormats = emptySet(),
                    actions = actions,
                    onDismiss = { dismissed++ },
                    nativeEngine = true,
                    formatState = FormatState.Empty,
                )
            }
        }
        listOf("Title", "Heading", "Subheading", "Body", "Monospaced", "Quote").forEach {
            rule.onNodeWithText(it).performScrollTo().performClick()
        }
        assertEquals(listOf("h1", "h2", "subheading", "paragraph", "code", "quote"), actions.calls)
        assertEquals(6, dismissed)
    }

    @Test fun aztec_default_keeps_the_old_set() {
        bar(fs = null, native = false)
        listOf("Insert table", "Insert image", "Take photo", "Bullet list", "Divider").forEach {
            rule.onNodeWithContentDescription(it).performScrollTo().assertExists()
        }
        listOf("Dashed list", "Indent", "Outdent").forEach { rule.onNodeWithContentDescription(it).assertDoesNotExist() }
        // undo / redo stay enabled on this path (the history lives in Aztec)
        rule.onNodeWithContentDescription("Undo").assertIsEnabled()
        rule.onNodeWithContentDescription("Redo").assertIsEnabled()
    }

    @Test fun aztec_default_menu_keeps_the_old_entries() {
        rule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                MarkdownFormatMenu(expanded = true, activeFormats = emptySet(), actions = Recording(), onDismiss = {})
            }
        }
        listOf("Paragraph", "Heading 1", "Heading 6", "Quote", "Code", "Insert table").forEach {
            rule.onNodeWithText(it).performScrollTo().assertExists()
        }
        rule.onNodeWithText("Monospaced").assertDoesNotExist()
    }
}
