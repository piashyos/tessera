package app.tessera.ui.editor.components

import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.ui.editor.engine.FormatState
import app.tessera.ui.editor.engine.HostFixture
import app.tessera.ui.editor.engine.ParagraphFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The `RichBodyActions` → engine mapping of spec §7, over a real [app.tessera.ui.editor.engine.TesseraEditText]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteBodyActionsTest {

    /** Runs [act] with the caret in "abc" of a one-paragraph note and returns the Markdown that results. */
    private fun apply(md: String = "abc", act: RichBodyActions.() -> Unit): String {
        val h = HostFixture(md)
        h.caret(1)
        NoteBodyActions(h.view).act()
        return h.md()
    }

    @Test fun heading1_2_3_are_title_heading_subheading() {
        assertEquals("# abc", apply { toggleHeading1() })
        assertEquals("## abc", apply { toggleHeading2() })
        assertEquals("### abc", apply { toggleHeading3() })
    }

    @Test fun heading4_to_6_and_subheading_are_subheading() {
        assertEquals("### abc", apply { toggleHeading4() })
        assertEquals("### abc", apply { toggleHeading5() })
        assertEquals("### abc", apply { toggleHeading6() })
        assertEquals("### abc", apply { toggleSubheading() })
    }

    @Test fun code_is_mono_and_paragraph_is_body() {
        assertEquals("```\nabc\n```", apply { toggleCode() })
        assertEquals("abc", apply("# abc") { toggleParagraph() })
        assertEquals("abc", apply("## abc") { toggleParagraph() })
    }

    @Test fun a_kind_button_pressed_twice_goes_back_to_body() {
        assertEquals("abc", apply { toggleHeading1(); toggleHeading1() })
        assertEquals("abc", apply { toggleCode(); toggleCode() })
    }

    @Test fun lists_and_quote() {
        assertEquals("- abc", apply { toggleBulletList() })
        assertEquals("+ abc", apply { toggleDashList() })
        assertEquals("1. abc", apply { toggleOrderedList() })
        assertEquals("- [ ] abc", apply { toggleTaskList() })
        assertEquals("> abc", apply { toggleQuote() })
    }

    @Test fun marks_over_a_selection() {
        val h = HostFixture("abc")
        h.select(0, 3)
        val a = NoteBodyActions(h.view)
        a.toggleBold()
        assertEquals("**abc**", h.md())
        a.toggleBold()
        assertEquals("abc", h.md())
        a.toggleItalic()
        assertEquals("*abc*", h.md())
        a.toggleItalic()
        a.toggleUnderline()
        assertEquals("<u>abc</u>", h.md())
        a.toggleUnderline()
        a.toggleStrikethrough()
        assertEquals("~~abc~~", h.md())
    }

    @Test fun insertHorizontalRule_splits_the_caret_paragraph_around_a_divider() {
        val h = HostFixture("abc")
        h.caret(1)
        NoteBodyActions(h.view).insertHorizontalRule()
        val blocks = NoteMarkdown.parse(h.md()).blocks
        assertEquals("a", (blocks.first() as NoteBlock.Text).content.text)
        assertTrue("a divider follows: $blocks", blocks[1] == NoteBlock.Divider)
        assertEquals("bc", (blocks[2] as NoteBlock.Text).content.text)
    }

    @Test fun indent_and_outdent_move_list_items() {
        assertEquals("    - a", apply("- a") { indent() })
        assertEquals("- a", apply("    - a") { outdent() })
        assertEquals("- a", apply("- a") { outdent() }) // already at level 0
        assertEquals("plain", apply("plain") { indent() }) // not a list item
    }

    @Test fun undo_and_redo_travel_the_history() {
        val h = HostFixture("abc")
        h.caret(1)
        val a = NoteBodyActions(h.view)
        a.toggleHeading1()
        assertEquals("# abc", h.md())
        a.undo()
        assertEquals("abc", h.md())
        a.redo()
        assertEquals("# abc", h.md())
    }

    @Test fun formatState_reflects_the_caret_and_the_history() {
        val h = HostFixture("# abc")
        h.caret(1)
        val a = NoteBodyActions(h.view)
        val before = a.formatState()
        assertTrue(before.paragraph is ParagraphFormat.Single)
        assertEquals(false, before.canUndo)
        a.toggleParagraph()
        assertEquals(true, a.formatState().canUndo)
        assertEquals(FormatState.Empty.marks, a.formatState().marks)
    }

    @Test fun activeFormats_is_empty_because_the_bar_reads_formatState() {
        assertTrue(NoteBodyActions(HostFixture("abc").view).activeFormats().isEmpty())
    }
}
