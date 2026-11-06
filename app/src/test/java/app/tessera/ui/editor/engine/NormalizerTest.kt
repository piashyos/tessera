package app.tessera.ui.editor.engine

import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.NoCopySpan
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.SuggestionSpan
import android.text.style.UnderlineSpan
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.ui.editor.engine.spans.BlockSpan
import app.tessera.ui.editor.engine.spans.ObjectSpan
import app.tessera.ui.editor.engine.spans.SentinelSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NormalizerTest {
    private val style = EngineTestStyle.create()
    private val normalizer = Normalizer(style)

    private fun editable(md: String): Editable = SpannableStringBuilder(DocBinder.build(NoteMarkdown.parse(md), style))

    /** One user edit as the host will do it: origin before, edit, normalize after. */
    private fun edit(e: Editable, start: Int, end: Int, replacement: CharSequence) {
        val origin = normalizer.originKind(e, start)
        e.replace(start, end, replacement)
        normalizer.normalize(e, start, start + replacement.length, origin)
    }

    private fun kinds(e: Spanned): List<ParagraphKind> = DocBinder.read(e).blocks.map { (it as? app.tessera.core.text.notedoc.NoteBlock.Text)?.kind ?: ParagraphKind.Body }

    private fun foreign(e: Spanned) = e.getSpans(0, e.length, Any::class.java)
        .filter { Normalizer.isForeignFormatting(it, e.getSpanFlags(it)) }

    private fun assertInvariant1(e: Spanned) = EngineAssertions.assertInvariant1(e)

    // ---- foreign spans (rule 4) ----

    @Test
    fun pastedForeignCharacterAndParagraphStylesAreRemoved() {
        val e = editable("abc")
        val pasted = SpannableString("XYZ").apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, 3, 0)
            setSpan(ForegroundColorSpan(Color.RED), 0, 2, 0)
            setSpan(BackgroundColorSpan(Color.YELLOW), 1, 3, 0)
            setSpan(AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER), 0, 3, Spanned.SPAN_PARAGRAPH)
        }
        edit(e, 1, 1, pasted)
        assertEquals("aXYZbc", e.toString())
        assertTrue(foreign(e).isEmpty())
        assertEquals(0, e.getSpans(0, e.length, StyleSpan::class.java).size)
    }

    @Test
    fun suggestionAndComposingSpansSurvive() {
        val e = editable("abc def")
        val suggestion = SuggestionSpan(org.robolectric.RuntimeEnvironment.getApplication(), arrayOf("x"), SuggestionSpan.FLAG_EASY_CORRECT)
        val composing = UnderlineSpan()
        val rangeSpan = Class.forName("android.text.style.SuggestionRangeSpan").getDeclaredConstructor().newInstance()
        val noCopy = object : NoCopySpan {}
        e.setSpan(suggestion, 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        e.setSpan(composing, 4, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING)
        e.setSpan(rangeSpan, 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        e.setSpan(noCopy, 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        e.setSpan(StyleSpan(Typeface.BOLD), 0, 7, 0) // foreign, same window: goes

        normalizer.normalize(e, 0, 7)

        assertEquals(1, e.getSpans(0, e.length, SuggestionSpan::class.java).size)
        assertNotNull(e.getSpanStart(composing).takeIf { it >= 0 })
        assertTrue(e.getSpans(0, e.length, rangeSpan.javaClass).isNotEmpty())
        assertTrue(e.getSpans(0, e.length, NoCopySpan::class.java).contains(noCopy))
        assertEquals(0, e.getSpans(0, e.length, StyleSpan::class.java).size)
    }

    @Test
    fun onlyTouchedParagraphsAreNormalized() {
        val e = editable("one\ntwo\nthree")
        val far = StyleSpan(Typeface.BOLD)
        e.setSpan(far, 8, 13, 0) // "three", not touched by the edit below
        val near = StyleSpan(Typeface.ITALIC)
        e.setSpan(near, 0, 3, 0)
        edit(e, 1, 1, "x")
        assertEquals(-1, e.getSpanStart(near))
        assertTrue(e.getSpanStart(far) >= 0)
        // invariant 3: whatever formatting stays on the text, the document only gets engine spans
        val last = DocBinder.read(e).blocks.last() as app.tessera.core.text.notedoc.NoteBlock.Text
        assertEquals(app.tessera.core.text.notedoc.Inline("three"), last.content)
    }

    @Test
    fun engineSpansAreNotForeign() {
        val e = editable("# t **b**\n- x\n> q\n---")
        assertTrue(foreign(e).isEmpty())
    }

    // ---- U+FFFC / U+200B (rule 5) ----

    @Test
    fun objectCharWithoutSpanIsDeleted() {
        val e = editable("abc")
        edit(e, 1, 1, "￼")
        assertEquals("abc", e.toString())
    }

    @Test
    fun objectWithSpanIsKept() {
        val e = editable("a\n---\nb")
        normalizer.normalize(e, 0, e.length)
        assertEquals("a\n￼\nb", e.toString())
        assertEquals(1, e.getSpans(0, e.length, ObjectSpan::class.java).size)
    }

    @Test
    fun textTypedNextToAnObjectGoesToItsOwnParagraph() {
        val e = editable("a\n---\nb")
        edit(e, 3, 3, "x") // after the object char, before its line break
        assertEquals("a\n￼\nx\nb", e.toString())
        assertEquals(listOf(ParagraphKind.Body, ParagraphKind.Body, ParagraphKind.Body, ParagraphKind.Body), kinds(e))
        edit(e, 2, 2, "y") // before it
        assertEquals("a\ny\n￼\nx\nb", e.toString())
        assertEquals(1, e.getSpans(0, e.length, ObjectSpan::class.java).size)
    }

    @Test
    fun sentinelGoesWhenTextAppearsAndStaysItsKind() {
        val e = editable("- [ ] a\n- [ ] ")
        assertEquals("a\n​", e.toString())
        edit(e, 3, 3, "x") // typed after the sentinel
        assertEquals("a\nx", e.toString())
        assertEquals(0, e.getSpans(0, e.length, SentinelSpan::class.java).size)
        assertEquals(NoteMarkdown.parse("- [ ] a\n- [ ] x"), DocBinder.read(e))
        assertInvariant1(e)
    }

    @Test
    fun userZeroWidthSpaceIsNotTouched() {
        val e = editable("a")
        edit(e, 1, 1, "​")
        assertEquals("a​", e.toString())
    }

    @Test
    fun emptyingTheLastItemPutsTheSentinelBack() {
        val e = editable("- a\n- b")
        edit(e, 2, 3, "") // "b" deleted
        assertEquals("a\n​", e.toString())
        assertEquals(1, e.getSpans(0, e.length, SentinelSpan::class.java).size)
        assertEquals(NoteMarkdown.parse("- a\n- "), DocBinder.read(e))
        assertInvariant1(e)
    }

    @Test
    fun sentinelLosesItsMeaningWhenAnotherParagraphFollows() {
        val e = editable("- a\n- ")
        edit(e, 3, 3, "\n") // line break after the sentinel
        // the old sentinel is gone; the new empty last paragraph is an empty item again, with its own sentinel
        assertEquals("a\n\n\u200B", e.toString())
        assertEquals(1, e.getSpans(0, e.length, SentinelSpan::class.java).size)
        assertEquals(NoteMarkdown.parse("- a\n- \n- "), DocBinder.read(e))
        assertInvariant1(e)
    }

    // ---- block spans (rules 1, 2) ----

    @Test
    fun editInsideAParagraphKeepsItsBlockSpan() {
        val e = editable("# Title\nbody")
        val span = e.getSpans(0, e.length, BlockSpan::class.java).single()
        edit(e, 3, 3, "zz")
        edit(e, 1, 2, "")
        assertEquals("Ttzzle\nbody", e.toString())
        assertSame(span, e.getSpans(0, e.length, BlockSpan::class.java).single())
        assertEquals(0 to 7, e.getSpanStart(span) to e.getSpanEnd(span))
    }

    @Test
    fun typingAtTheStartOfAHeadingKeepsTheKind() {
        val e = editable("# Title\nbody")
        edit(e, 0, 0, "x")
        assertEquals(listOf(ParagraphKind.Title, ParagraphKind.Body), kinds(e))
        assertInvariant1(e)
    }

    @Test
    fun imeReplacementOfAWholeParagraphKeepsTheKind() {
        // autocorrect replacing the composing word that is the whole text of a Body line under a heading
        val e = editable("# Title\nteh")
        edit(e, 6, 9, "the")
        assertEquals(listOf(ParagraphKind.Title, ParagraphKind.Body), kinds(e))
        val e2 = editable("a\n# Title")
        edit(e2, 2, 7, "Titles")
        assertEquals(listOf(ParagraphKind.Body, ParagraphKind.Title), kinds(e2))
    }

    @Test
    fun pastedLinesTakeTheKindOfTheParagraphWhereThePasteStarted() {
        val e = editable("## Head\nbody")
        edit(e, 2, 2, "x\ny\nz")
        assertEquals("Hex\ny\nzad\nbody", e.toString())
        assertEquals(listOf(ParagraphKind.Heading, ParagraphKind.Heading, ParagraphKind.Heading, ParagraphKind.Body), kinds(e))
        assertInvariant1(e)
    }

    @Test
    fun pasteAtTheStartAndEndOfAParagraphWithLineBreaks() {
        val bullet = ParagraphKind.ListItem(ListType.Bullet, 1)
        val e = editable("body\n    - item\nafter")
        // paste "p\nq" at the start of the item
        edit(e, 5, 5, "p\nq")
        assertEquals(listOf(ParagraphKind.Body, bullet, bullet, ParagraphKind.Body), kinds(e))
        // paste "r\n" at the end of "after" (body): new paragraphs stay Body
        edit(e, e.length, e.length, "r\n")
        assertEquals(ParagraphKind.Body, kinds(e).last())
        assertInvariant1(e)
    }

    @Test
    fun pastingLinesAfterTheCheckedItemGivesUncheckedItems() {
        val e = editable("- [x] done\nz")
        edit(e, 4, 4, "a\nb")
        val ks = kinds(e)
        assertEquals(ParagraphKind.ListItem(ListType.Check, 0, checked = true), ks[0])
        assertEquals(ParagraphKind.ListItem(ListType.Check, 0, checked = false), ks[1])
        assertEquals(ParagraphKind.Body, ks[2])
    }

    @Test
    fun pasteAtTheStartOfAQuoteKeepsTheQuote() {
        val e = editable("> q")
        edit(e, 0, 0, "x\ny")
        assertEquals(listOf(ParagraphKind.Quote, ParagraphKind.Quote), kinds(e))
        assertInvariant1(e)
    }

    @Test
    fun deletingAcrossParagraphsTakesTheKindOfTheFirst() {
        val e = editable("# one\n- two\nthree")
        edit(e, 1, 6, "") // from inside "one" to inside "two"
        assertEquals(listOf(ParagraphKind.Title, ParagraphKind.Body), kinds(e))
        assertEquals("oo\nthree", e.toString())
        assertInvariant1(e)
    }

    @Test
    fun joiningParagraphsKeepsTheKindOfThePrevious() {
        val e = editable("- a\nb")
        edit(e, 1, 2, "") // the line break deleted
        assertEquals(listOf(ParagraphKind.ListItem(ListType.Bullet, 0)), kinds(e))
        assertEquals("ab", e.toString())
        assertInvariant1(e)
    }

    @Test
    fun pasteIntoPlainBodyKeepsBody() {
        val e = editable("a\nb")
        edit(e, 1, 1, "x\ny")
        assertEquals(listOf(ParagraphKind.Body, ParagraphKind.Body, ParagraphKind.Body), kinds(e))
        assertEquals(0, e.getSpans(0, e.length, BlockSpan::class.java).size)
    }

    @Test
    fun stepsRunAfterTheInvariantsOnTheRepairedWindow() {
        var seen: Triple<String, Int, Int>? = null
        val n = Normalizer(style, listOf(NormalizeStep { e, s, en -> seen = Triple(e.toString(), s, en) }))
        val e = editable("one\ntwo\nthree")
        e.replace(5, 5, "X")
        n.normalize(e, 5, 6)
        assertEquals(Triple("one\ntXwo\nthree", 4, 9), seen)
    }

    @Test
    fun withoutAnOriginTheKindIsGuessedFromTheSpansAroundTheEdit() {
        val e = editable("## Head")
        e.replace(2, 2, "x\ny")
        normalizer.normalize(e, 2, 5)
        assertEquals(listOf(ParagraphKind.Heading, ParagraphKind.Heading), kinds(e))
    }
}
