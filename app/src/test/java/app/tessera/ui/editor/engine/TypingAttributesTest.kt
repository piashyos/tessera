package app.tessera.ui.editor.engine

import android.text.Editable
import android.text.SpannableStringBuilder
import app.tessera.core.text.notedoc.Mark
import app.tessera.core.text.notedoc.NoteMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TypingAttributesTest {
    private val style = EngineTestStyle.create()
    private val tp = TypingAttributes(style)

    private fun ed(md: String): Editable = SpannableStringBuilder(DocBinder.build(NoteMarkdown.parse(md), style))
    private fun md(e: Editable) = NoteMarkdown.serialize(DocBinder.read(e))

    /** An edit as the host sees it: before, the replacement, after. */
    private fun edit(e: Editable, start: Int, end: Int, text: String) {
        tp.beforeChange(e, start, end)
        e.replace(start, end, text)
        tp.afterChange(e, start + text.length)
    }

    @Test
    fun typingAtTheEndOfABoldInitialKeepsItBoldWhenTheComposingRegionIsReplacedWhole() {
        val e = ed("**Т**екст")
        edit(e, 0, 5, "Тексты")
        assertEquals("**Т**ексты", md(e))
    }

    @Test
    fun aReplacementThatChangesNothingKeepsTheMarks() {
        val e = ed("**Т**екст")
        edit(e, 0, 5, "Текст")
        assertEquals("**Т**екст", md(e))
    }

    @Test
    fun switchingBoldOffAtTheEndOfABoldWord() {
        val e = ed("**Слово**")
        assertEquals(setOf(Mark.Bold), tp.active(e, 5))
        tp.toggle(e, 5, Mark.Bold)
        assertEquals(emptySet<Mark>(), tp.active(e, 5))
        edit(e, 5, 5, "x")
        assertEquals("**Слово**x", md(e))
    }

    @Test
    fun switchingBoldOffAtTheEndOfABoldWordWhenTheWordIsReplacedWhole() {
        val e = ed("**Слово**")
        tp.toggle(e, 5, Mark.Bold)
        edit(e, 0, 5, "Словоx")
        assertEquals("**Слово**x", md(e))
    }

    @Test
    fun switchingBoldOnAtTheEndOfAPlainWordAndTheDefaultAfterTheTypedCharacter() {
        val e = ed("Слово")
        tp.toggle(e, 5, Mark.Bold)
        edit(e, 5, 5, "x")
        assertEquals("Слово**x**", md(e))
        tp.onSelectionChanged(6, 6)
        assertEquals(setOf(Mark.Bold), tp.active(e, 6)) // the default: the character before the caret
    }

    @Test
    fun typingAfterLegacyCodeIsNotCode() {
        val e = ed("`код`")
        edit(e, 3, 3, "x")
        assertEquals("`код`x", md(e))
        val f = ed("`код`")
        edit(f, 0, 3, "кодx")
        assertEquals("`код`x", md(f))
        assertEquals(emptySet<Mark>(), tp.defaultAt(ed("`код`"), 3))
    }

    @Test
    fun aToggledMarkIsDroppedWhenTheCaretMoves() {
        val e = ed("abc")
        tp.toggle(e, 3, Mark.Bold)
        tp.onSelectionChanged(1, 1)
        edit(e, 3, 3, "x")
        assertEquals("abcx", md(e))
    }

    @Test
    fun aToggledMarkStaysWhileTheCaretDoesNot() {
        val e = ed("abc")
        tp.toggle(e, 3, Mark.Italic)
        tp.onSelectionChanged(3, 3)
        edit(e, 3, 3, "x")
        assertEquals("abc*x*", md(e))
    }

    @Test
    fun typingInsideABoldWordStaysBold() {
        val e = ed("**abcd**")
        edit(e, 2, 2, "x")
        assertEquals("**abxcd**", md(e))
    }

    @Test
    fun atTheStartOfAParagraphTheFirstCharacterOfItDecides() {
        val e = ed("**ab** c")
        edit(e, 0, 0, "x")
        assertEquals("**xab** c", md(e))
        val f = ed("**a**\nb")
        edit(f, 2, 2, "x")
        assertEquals("**a**\nxb", md(f))
    }

    @Test
    fun anEmptyParagraphStartsWithoutMarks() {
        val e = ed("**a**\n")
        assertEquals(emptySet<Mark>(), tp.active(e, 2))
    }

    @Test
    fun aPasteWithLineBreaksMarksTheTextNotTheBreaks() {
        val e = ed("**a**")
        edit(e, 1, 1, "x\ny")
        assertEquals("**ax**\n**y**", md(e))
        assertTrue(e.toString() == "ax\ny")
    }

    @Test
    fun aMonoLineGetsNoMarks() {
        val e = ed("```\na\n```")
        tp.toggle(e, 1, Mark.Bold)
        edit(e, 1, 1, "x")
        assertEquals("```\nax\n```", md(e))
        assertEquals(emptySet<Mark>(), tp.active(e, 1))
    }

    @Test
    fun replacingASelectionTypesWithTheMarksAtItsStart() {
        val e = ed("**ab**c")
        edit(e, 0, 2, "z")
        assertEquals("**z**c", md(e))
        val f = ed("c**ab**") // not at offset 0: the replaced text starts after a plain character
        edit(f, 1, 3, "z")
        assertEquals("c**z**", md(f))
    }

    @Test
    fun aReplacementInTheMiddleGivesThePrefixAndTheSuffixTheirOldMarksAndTheNewPartTheTypingSet() {
        val e = ed("a**b**c")
        tp.toggle(e, 1, Mark.Italic) // typing set at 1: bold off (char before is plain 'a'), italic on
        edit(e, 0, 3, "aXbc") // "abc" -> "aXbc": prefix "a", new "X", suffix "bc" (its 'b' bold again)
        assertEquals(listOf(emptySet(), setOf(Mark.Italic), setOf(Mark.Bold), emptySet<Mark>()), (0 until 4).map { MarkOps.marksAt(e, it) })
    }

    private fun codeSpans(e: Editable) = e.getSpans(0, e.length, app.tessera.ui.editor.engine.spans.CodeMarkSpan::class.java).size

    @Test
    fun typingInTheMiddleOfCodeContinuesTheCodeInOneSpan() {
        val e = ed("`код`")
        edit(e, 1, 1, "x")
        assertEquals("`кxод`", md(e))
        assertEquals(1, codeSpans(e))
        assertEquals(setOf(Mark.Code), tp.defaultAt(ed("`код`"), 1))
    }

    @Test
    fun aComposingRegionReplacedWholeInsideCodeKeepsOneCodeSpan() {
        val e = ed("`код`")
        edit(e, 0, 3, "кxод")
        assertEquals("`кxод`", md(e))
        assertEquals(1, codeSpans(e))
        val f = ed("a `код` b") // the region is the word inside the code
        edit(f, 2, 5, "кxод")
        assertEquals("a `кxод` b", md(f))
        assertEquals(1, codeSpans(f))
    }

    @Test
    fun typingAtTheEdgesOfCodeIsNotCode() {
        val e = ed("a`код`b")
        edit(e, 1, 1, "x") // before the code
        assertEquals("ax`код`b", md(e))
        val f = ed("a`код`b")
        edit(f, 4, 4, "x") // after the code
        assertEquals("a`код`xb", md(f))
    }

    @Test
    fun aMarkTypedOverCodeIsClippedAtTheCodeBoundaries() {
        val e = ed("a`bc`d")
        tp.toggle(e, 1, Mark.Bold) // before the code: bold on
        edit(e, 1, 1, "XY")
        // XY is bold and stays outside the code
        assertEquals("a**XY**`bc`d", md(e))
    }

    @Test
    fun aWordReplacedWholeByTheKeyboardKeepsItsMarksOnTheNewFirstCharacter() {
        val e = ed("x **monday**") // Gboard capitalizes: "monday" -> "Monday"
        edit(e, 2, 8, "Monday")
        assertEquals("x **Monday**", md(e))
        val f = ed("c**ab**")
        edit(f, 1, 3, "z")
        assertEquals("c**z**", md(f))
    }

    @Test
    fun afterChangeReturnsTheReallyNewCharacters() {
        val e = ed("abcd")
        tp.beforeChange(e, 0, 4)
        e.replace(0, 4, "abXcd")
        assertEquals(2..2, tp.afterChange(e, 5))
        val f = ed("abc")
        tp.beforeChange(f, 0, 3)
        f.replace(0, 3, "abc")
        assertEquals(true, tp.afterChange(f, 3).isEmpty())
    }
}
