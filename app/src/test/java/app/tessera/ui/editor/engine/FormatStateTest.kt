package app.tessera.ui.editor.engine

import android.text.SpannableStringBuilder
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.Mark
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.core.text.notedoc.ParagraphKind
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FormatStateTest {
    private val style = EngineTestStyle.create()
    private val tp = TypingAttributes(style)

    /** The state at the markers of [md] (a caret or a selection). */
    private fun state(md: String, inSel: String = ""): FormatState {
        val f = EditFixture(md, inSel, style)
        return FormatState.of(f.e, f.selStart, f.selEnd, tp)
    }

    private fun single(k: ParagraphKind) = ParagraphFormat.Single(k)

    @Test
    fun theKindAtTheCaret() {
        assertEquals(single(ParagraphKind.Heading), state("## a‸b").paragraph)
        assertEquals(single(ParagraphKind.Body), state("a‸b").paragraph)
        assertEquals(single(ParagraphKind.Mono), state("```\na‸\n```").paragraph)
        assertEquals(single(ParagraphKind.ListItem(ListType.Check, 1, true)), state("    - [x] a‸").paragraph)
    }

    @Test
    fun anEmptyLastItemHasItsKind() {
        assertEquals(single(ParagraphKind.ListItem(ListType.Bullet, 0)), state("- a\n- ‸").paragraph)
    }

    @Test
    fun differentKindsUnderASelectionAreMixed() {
        assertEquals(ParagraphFormat.Mixed, state("## «a\nb»").paragraph)
        assertEquals(ParagraphFormat.Mixed, state("- «a\n1. b»").paragraph)
    }

    @Test
    fun listItemsOfOneTypeAreOneKindWhateverTheirLevel() {
        assertEquals(single(ParagraphKind.ListItem(ListType.Bullet, 0)), state("- «a\n    - b»").paragraph)
    }

    @Test
    fun aSelectionEndingAtTheStartOfAParagraphDoesNotTouchIt() {
        assertEquals(single(ParagraphKind.Heading), state("## «a\n»b").paragraph)
    }

    @Test
    fun objectsAreLeftOutOfTheKind() {
        assertEquals(single(ParagraphKind.Title), state("# a\n---\n# b", "0-5").paragraph)
        assertEquals(single(ParagraphKind.Body), state("---", "0").paragraph)
    }

    @Test
    fun marksAreActiveWhenTheyCoverTheWholeSelection() {
        assertEquals(setOf(Mark.Bold), state("«**ab**»").marks)
        assertEquals(emptySet<Mark>(), state("«**a**b»").marks)
        assertEquals(setOf(Mark.Bold, Mark.Italic), state("«***ab***»").marks)
    }

    @Test
    fun lineBreaksMonoLinesAndObjectsDoNotBreakAMultiParagraphSelection() {
        assertEquals(setOf(Mark.Bold), state("«**a**\n**b**»").marks)
        assertEquals(setOf(Mark.Bold), state("«**a**\n```\nm»\n```").marks)
        assertEquals(setOf(Mark.Bold), state("**a**\n---\n**b**", "0-5").marks)
    }

    @Test
    fun withoutASelectionTheMarksAreTheTypingAttributes() {
        val f = EditFixture("**ab**‸", "", style)
        assertEquals(setOf(Mark.Bold), FormatState.of(f.e, f.selStart, f.selEnd, tp).marks)
        tp.toggle(f.e, f.selStart, Mark.Bold)
        tp.toggle(f.e, f.selStart, Mark.Italic)
        assertEquals(setOf(Mark.Italic), FormatState.of(f.e, f.selStart, f.selEnd, tp).marks)
        tp.onSelectionChanged(0, 0)
        assertEquals(setOf(Mark.Bold), FormatState.of(f.e, f.selStart, f.selEnd, tp).marks)
    }

    @Test
    fun legacyCodeIsNotATypingAttribute() {
        assertEquals(emptySet<Mark>(), state("`a`‸").marks)
        assertEquals(setOf(Mark.Code), state("«`a`»").marks)
    }

    @Test
    fun indentAndOutdentFollowTheLevels() {
        assertEquals(true to false, state("- a‸").let { it.canIndent to it.canOutdent })
        assertEquals(true to true, state("    - a‸").let { it.canIndent to it.canOutdent })
        assertEquals(false to true, state("                - a‸").let { it.canIndent to it.canOutdent })
        assertEquals(false to false, state("a‸").let { it.canIndent to it.canOutdent })
        assertEquals(true to true, state("«- a\n                - b»").let { it.canIndent to it.canOutdent })
    }

    @Test
    fun undoRedoAreFilledByTheHostAndEmptyIsTheDefault() {
        val s = state("a‸")
        assertEquals(false to false, s.canUndo to s.canRedo)
        assertEquals(FormatState(), FormatState.Empty)
    }
}
