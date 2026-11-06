package app.tessera.core.text.notedoc

import app.tessera.core.text.markdownblocks.TableGrid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** One test group per rule of §4 "Каноничный NoteDoc". */
class NoteDocNormalizeTest {

    private fun body(t: String, vararg m: MarkRange) = NoteBlock.Text(ParagraphKind.Body, Inline(t, m.toList()))
    private fun norm(vararg b: NoteBlock) = normalize(NoteDoc(b.toList())).blocks
    private fun marksOf(vararg m: MarkRange): List<MarkRange> =
        (norm(body("0123456789", *m)).single() as NoteBlock.Text).content.marks

    private fun mr(a: Int, b: Int, m: Mark) = MarkRange(a, b, m)

    // 1
    @Test fun `overlapping and adjacent marks of one kind merge, empty ones go`() {
        assertEquals(listOf(mr(0, 6, Mark.Bold)), marksOf(mr(0, 3, Mark.Bold), mr(2, 6, Mark.Bold)))
        assertEquals(listOf(mr(0, 6, Mark.Bold)), marksOf(mr(0, 3, Mark.Bold), mr(3, 6, Mark.Bold)))
        assertEquals(listOf(mr(0, 2, Mark.Bold), mr(3, 5, Mark.Bold)), marksOf(mr(0, 2, Mark.Bold), mr(3, 5, Mark.Bold)))
        assertEquals(emptyList<MarkRange>(), marksOf(mr(4, 4, Mark.Bold), mr(6, 2, Mark.Italic)))
        assertEquals(listOf(mr(0, 10, Mark.Bold)), marksOf(mr(-5, 50, Mark.Bold)))
    }

    // 2
    @Test fun `marks do not start or end with whitespace`() {
        val b = norm(body("a b  c", mr(0, 2, Mark.Bold), mr(2, 6, Mark.Italic), mr(1, 2, Mark.Underline))).single() as NoteBlock.Text
        assertEquals(listOf(mr(0, 1, Mark.Bold), mr(2, 6, Mark.Italic)), b.content.marks)
    }

    @Test fun `whitespace-only mark disappears and nbsp counts as whitespace`() {
        assertEquals(emptyList<MarkRange>(), (norm(body("a   b", mr(1, 4, Mark.Bold))).single() as NoteBlock.Text).content.marks)
        assertEquals(listOf(mr(1, 2, Mark.Bold)), (norm(body(" x ", mr(0, 3, Mark.Bold))).single() as NoteBlock.Text).content.marks)
    }

    // 3
    @Test fun `a mark partly crossing code is cut at its boundary`() {
        assertEquals(listOf(mr(0, 3, Mark.Bold), mr(3, 6, Mark.Code)), marksOf(mr(0, 5, Mark.Bold), mr(3, 6, Mark.Code)))
        assertEquals(listOf(mr(2, 5, Mark.Code), mr(5, 8, Mark.Bold)), marksOf(mr(2, 5, Mark.Code), mr(3, 8, Mark.Bold)))
    }

    @Test fun `a mark covering code stays and one inside code goes`() {
        assertEquals(listOf(mr(0, 8, Mark.Bold), mr(2, 5, Mark.Code)), marksOf(mr(0, 8, Mark.Bold), mr(2, 5, Mark.Code)))
        assertEquals(listOf(mr(2, 5, Mark.Code)), marksOf(mr(2, 5, Mark.Code), mr(3, 4, Mark.Italic)))
    }

    @Test fun `several codes`() {
        assertEquals(
            listOf(mr(0, 5, Mark.Bold), mr(2, 4, Mark.Code), mr(4, 6, Mark.Italic), mr(6, 8, Mark.Code)),
            marksOf(mr(0, 5, Mark.Bold), mr(2, 4, Mark.Code), mr(6, 8, Mark.Code), mr(4, 7, Mark.Italic)),
        )
    }

    // 4
    @Test fun `mono paragraph has no marks`() {
        val b = norm(NoteBlock.Text(ParagraphKind.Mono, Inline("abc", listOf(mr(0, 2, Mark.Bold))))).single()
        assertEquals(NoteBlock.Text(ParagraphKind.Mono, Inline("abc")), b)
    }

    // 5
    @Test fun `checked only for check items and level is clamped`() {
        fun item(t: ListType, level: Int, c: Boolean) = NoteBlock.Text(ParagraphKind.ListItem(t, level, c), Inline("x"))
        assertEquals(
            listOf(item(ListType.Bullet, 4, false), item(ListType.Number, 0, false), item(ListType.Check, 2, true)),
            norm(item(ListType.Bullet, 9, true), item(ListType.Number, -3, true), item(ListType.Check, 2, true)),
        )
    }

    // 6
    @Test fun `marks are sorted by start end and kind order`() {
        assertEquals(
            listOf(mr(0, 2, Mark.Italic), mr(0, 3, Mark.Bold), mr(0, 3, Mark.Underline), mr(1, 4, Mark.Strike)),
            marksOf(mr(1, 4, Mark.Strike), mr(0, 3, Mark.Underline), mr(0, 2, Mark.Italic), mr(0, 3, Mark.Bold)),
        )
        assertEquals(
            listOf(mr(0, 3, Mark.Bold), mr(0, 3, Mark.Italic), mr(0, 3, Mark.Underline), mr(0, 3, Mark.Strike), mr(0, 3, Mark.Code)),
            marksOf(mr(0, 3, Mark.Code), mr(0, 3, Mark.Strike), mr(0, 3, Mark.Underline), mr(0, 3, Mark.Italic), mr(0, 3, Mark.Bold)),
        )
    }

    // 7
    @Test fun `empty document is one empty body`() {
        assertEquals(listOf(body("")), norm())
    }

    // 8
    @Test fun `table is what its own serialization parses to`() {
        val g = TableGrid(listOf(" A ", "B"), listOf(listOf("1", " 2 ")), colWidths = listOf(140.4f, 80.6f), rowHeights = listOf(44f, 30f, 99f))
        val t = norm(NoteBlock.Table(g)).single() as NoteBlock.Table
        assertEquals(listOf("A", "B"), t.grid.headers)
        assertEquals(listOf(listOf("1", "2")), t.grid.rows)
        assertEquals(listOf(140f, 81f), t.grid.colWidths)
        assertEquals(null, t.grid.rowHeights)   // wrong count (3 != rows + 1) is dropped
    }

    @Test fun `table sizes survive when counts match`() {
        val g = TableGrid(listOf("A"), listOf(listOf("1")), colWidths = listOf(99.6f), rowHeights = listOf(40.2f, 41f))
        val t = norm(NoteBlock.Table(g)).single() as NoteBlock.Table
        assertEquals(listOf(100f), t.grid.colWidths)
        assertEquals(listOf(40f, 41f), t.grid.rowHeights)
    }

    // 9
    @Test fun `empty body between consecutive tables`() {
        val t = NoteBlock.Table(TableGrid(listOf("A"), listOf(listOf("1"))))
        assertEquals(listOf(t, body(""), t), norm(t, t))
        assertEquals(listOf(t, body("x"), t), norm(t, body("x"), t))
        assertEquals(listOf(t, body(""), t, body(""), t), norm(t, t, body(""), t))
    }

    // 10
    @Test fun `image with an invalid source becomes body text`() {
        assertEquals(listOf(body("![](http://x)")), norm(NoteBlock.Image("http://x")))
        assertEquals(listOf(body("![]()")), norm(NoteBlock.Image("")))
        assertEquals(listOf(body("![](tessera-img://)")), norm(NoteBlock.Image("tessera-img://")))
        assertEquals(listOf(NoteBlock.Image("tessera-img://a-1?w=5&rot=90")), norm(NoteBlock.Image("tessera-img://a-1?w=5&rot=90")))
    }

    @Test fun `normalize is idempotent`() {
        for (d in NoteDocFixtures.sampleDocs + NoteDocFixtures.randomDocs(7, 300)) {
            val n = normalize(d)
            assertEquals(n, normalize(n))
        }
    }

    // 11
    @Test fun `line breaks become spaces in text, source and cells`() {
        assertEquals(listOf(body("a b c")), norm(body("a\nb\rc")))
        assertEquals(listOf(NoteBlock.Text(ParagraphKind.Mono, Inline("a b"))), norm(NoteBlock.Text(ParagraphKind.Mono, Inline("a\nb"))))
        assertEquals(listOf(body("![](tessera-img://a b)")), norm(NoteBlock.Image("tessera-img://a\nb")))
        val t = norm(NoteBlock.Table(TableGrid(listOf("h\n1"), listOf(listOf("x\ry"))))).single() as NoteBlock.Table
        assertEquals(listOf("h 1"), t.grid.headers)
        assertEquals(listOf(listOf("x y")), t.grid.rows)
    }

    // 12
    @Test fun `mark boundaries do not split a surrogate pair`() {
        val smile = "\uD83D\uDE00"
        // "a😀b": indices a0 high1 low2 b3
        assertEquals(listOf(mr(0, 1, Mark.Bold)), (norm(body("a${smile}b", mr(0, 2, Mark.Bold))).single() as NoteBlock.Text).content.marks)
        assertEquals(listOf(mr(1, 4, Mark.Bold)), (norm(body("a${smile}b", mr(2, 4, Mark.Bold))).single() as NoteBlock.Text).content.marks)
    }
}
