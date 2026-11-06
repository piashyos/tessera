package app.tessera.ui.editor.engine

import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import app.tessera.core.text.notedoc.Inline
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.Mark
import app.tessera.core.text.notedoc.MarkRange
import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.NoteDoc
import app.tessera.core.text.notedoc.NoteDocFixtures
import app.tessera.core.text.notedoc.NoteMarkdown
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.core.text.notedoc.normalize
import app.tessera.ui.editor.engine.spans.BLOCK_SPAN_FLAGS
import app.tessera.ui.editor.engine.spans.BlockSpan
import app.tessera.ui.editor.engine.spans.ListItemSpan
import app.tessera.ui.editor.engine.spans.MarkSpan
import app.tessera.ui.editor.engine.spans.ObjectSpan
import app.tessera.ui.editor.engine.spans.SentinelSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocBinderTest {
    private val style = EngineTestStyle.create()

    private fun roundTrip(doc: NoteDoc) = DocBinder.read(DocBinder.build(doc, style))

    @Test
    fun sampleDocsRoundTrip() {
        NoteDocFixtures.sampleDocs.forEachIndexed { i, doc ->
            assertEquals("sample #$i", normalize(doc), roundTrip(doc))
        }
    }

    @Test
    fun randomDocsRoundTrip() {
        val docs = NoteDocFixtures.randomDocs(seed = 20261106, count = 500)
        docs.forEachIndexed { i, doc -> assertEquals("random #$i", normalize(doc), roundTrip(doc)) }
        // and through `bind` into one editable that already holds the previous document
        val editable = SpannableStringBuilder()
        docs.forEachIndexed { i, doc ->
            DocBinder.bind(doc, editable, style)
            assertEquals("random #$i via bind", normalize(doc), DocBinder.read(editable))
            EngineAssertions.assertInvariant1(editable, "random #$i")
        }
    }

    private val everyNonBodyKind: List<ParagraphKind> = listOf(
        ParagraphKind.Title, ParagraphKind.Heading, ParagraphKind.Subheading, ParagraphKind.Quote, ParagraphKind.Mono,
    ) + ListType.entries.flatMap { t -> listOf(0, 2).flatMap { l -> listOf(false, true).map { c -> ParagraphKind.ListItem(t, l, c) } } }

    @Test
    fun emptyParagraphOfEveryKindAloneAndAfterBody() {
        for (kind in everyNonBodyKind) {
            for (blocks in listOf(
                listOf(NoteBlock.Text(kind, Inline(""))),
                listOf(NoteBlock.Text(ParagraphKind.Body, Inline("a")), NoteBlock.Text(kind, Inline(""))),
                listOf(NoteBlock.Text(kind, Inline("")), NoteBlock.Text(ParagraphKind.Body, Inline("a"))),
            )) {
                val doc = NoteDoc(blocks)
                assertEquals("$kind $blocks", normalize(doc), roundTrip(doc))
                EngineAssertions.assertInvariant1(DocBinder.build(doc, style), "$kind")
            }
        }
    }

    @Test
    fun theEmptyDocumentIsOneEmptyBody() {
        assertEquals(NoteDoc(emptyList()).let(::normalize), roundTrip(NoteDoc(emptyList())))
        assertEquals("", DocBinder.build(NoteDoc(emptyList()), style).toString())
    }

    @Test
    fun legacyCorpusRoundTrip() {
        NoteDocFixtures.legacyCorpus.forEach { md ->
            val doc = NoteMarkdown.parse(md)
            assertEquals(md, doc, roundTrip(doc))
        }
    }

    @Test
    fun bindReplacesContentAndSpansOfAnEditableInPlace() {
        val editable = SpannableStringBuilder()
        DocBinder.bind(NoteMarkdown.parse("# T\n- [ ] a\n- "), editable, style)
        val doc2 = NoteMarkdown.parse("x **b**\n> q")
        DocBinder.bind(doc2, editable, style)
        assertEquals(doc2, DocBinder.read(editable))
        assertEquals(1, editable.getSpans(0, editable.length, MarkSpan::class.java).size)
        assertEquals(0, editable.getSpans(0, editable.length, SentinelSpan::class.java).size)
    }

    @Test
    fun blockSpanCoversItsParagraphWithTheLineBreakAndBodyHasNone() {
        val doc = NoteMarkdown.parse("# h\nbody\n\n- i")
        val t = DocBinder.build(doc, style)
        assertEquals("h\nbody\n\ni", t.toString())
        val spans = t.getSpans(0, t.length, BlockSpan::class.java).sortedBy { t.getSpanStart(it) }
        assertEquals(listOf(ParagraphKind.Title, ParagraphKind.ListItem(ListType.Bullet, 0)), spans.map { it.kind })
        assertEquals(0 to 2, t.getSpanStart(spans[0]) to t.getSpanEnd(spans[0]))
        assertEquals(8 to 9, t.getSpanStart(spans[1]) to t.getSpanEnd(spans[1]))
        assertTrue(spans.all { t.getSpanFlags(it) == BLOCK_SPAN_FLAGS })
    }

    @Test
    fun emptyLastNonBodyParagraphHoldsAMarkedSentinel() {
        val t = DocBinder.build(NoteMarkdown.parse("- [ ] a\n- [ ] "), style)
        assertEquals("a\n​", t.toString())
        val sentinel = t.getSpans(0, t.length, SentinelSpan::class.java).single()
        assertEquals(2 to 3, t.getSpanStart(sentinel) to t.getSpanEnd(sentinel))
        val block = t.getSpans(2, 3, BlockSpan::class.java).single()
        assertEquals(ParagraphKind.ListItem(ListType.Check, 0), block.kind)
        assertEquals(2 to 3, t.getSpanStart(block) to t.getSpanEnd(block))
        assertEquals(NoteMarkdown.parse("- [ ] a\n- [ ] "), DocBinder.read(t))
    }

    @Test
    fun emptyLastBodyParagraphHasNeitherSentinelNorSpan() {
        val t = DocBinder.build(NoteMarkdown.parse("a\n"), style)
        assertEquals("a\n", t.toString())
        assertEquals(0, t.getSpans(0, t.length, SentinelSpan::class.java).size)
    }

    @Test
    fun userZeroWidthSpaceIsText() {
        val doc = NoteDoc(listOf(NoteBlock.Text(ParagraphKind.Body, Inline("a​b"))))
        assertEquals(doc, roundTrip(doc))
    }

    @Test
    fun objectsAreOneReplacementCharKeepingTheSourceBlock() {
        val doc = NoteMarkdown.parse("a\n![](tessera-img://p-1?w=30&rot=90)\n---\n| A |\n| --- |\n| 1 |\nb")
        val t = DocBinder.build(doc, style)
        assertEquals("a\n￼\n￼\n￼\nb", t.toString())
        val objs = t.getSpans(0, t.length, ObjectSpan::class.java).sortedBy { t.getSpanStart(it) }
        assertEquals(doc.blocks.subList(1, 4), objs.map { it.block })
        assertEquals(0, t.getSpans(2, 3, BlockSpan::class.java).size)
    }

    @Test
    fun marksKeepTheirRangesInEveryParagraph() {
        val doc = NoteDoc(
            listOf(
                NoteBlock.Text(ParagraphKind.Body, Inline("ab cd", listOf(MarkRange(0, 2, Mark.Bold), MarkRange(3, 5, Mark.Code)))),
                NoteBlock.Text(ParagraphKind.Quote, Inline("xy", listOf(MarkRange(0, 2, Mark.Italic)))),
            ),
        )
        assertEquals(doc, roundTrip(doc))
    }

    @Test
    fun reapplyThemeRecreatesSpansWithoutTouchingTheText() {
        val md = "# T\n- [x] a\n> q\n```\nm\n```\n---\nz `c`"
        val editable: Editable = SpannableStringBuilder()
        DocBinder.bind(NoteMarkdown.parse(md), editable, style)
        val textBefore = editable.toString()
        val rangesBefore = editable.getSpans(0, editable.length, Any::class.java)
            .filter { DocBinder.isEngineSpan(it) }.map { Triple(it.javaClass, editable.getSpanStart(it), editable.getSpanEnd(it)) }
        var textEvents = 0
        editable.setSpan(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) { textEvents++ }
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { textEvents++ }
            override fun afterTextChanged(s: Editable?) { textEvents++ }
        }, 0, editable.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        val oldBlock = editable.getSpans(0, editable.length, BlockSpan::class.java).first()

        val dark = style.copy(textColor = 0xFFEEEEEE.toInt(), accentColor = 0xFF00FFFF.toInt())
        DocBinder.reapplyTheme(editable, dark)

        assertEquals(textBefore, editable.toString())
        assertEquals(0, textEvents)
        val rangesAfter = editable.getSpans(0, editable.length, Any::class.java)
            .filter { DocBinder.isEngineSpan(it) }.map { Triple(it.javaClass, editable.getSpanStart(it), editable.getSpanEnd(it)) }
        assertEquals(rangesBefore.toSet(), rangesAfter.toSet())
        val newBlock = editable.getSpans(0, editable.length, BlockSpan::class.java).first { it.javaClass == oldBlock.javaClass }
        assertNotSame(oldBlock, newBlock)
        assertSame(dark, newBlock.style)
        assertEquals(NoteMarkdown.parse(md), DocBinder.read(editable))
        assertTrue(editable.getSpans(0, editable.length, ListItemSpan::class.java).all { it.style === dark })
    }
}
