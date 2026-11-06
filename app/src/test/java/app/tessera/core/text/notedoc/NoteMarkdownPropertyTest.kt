package app.tessera.core.text.notedoc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** §5.3 fixed point on random documents (fixed seed, no dependencies). */
class NoteMarkdownPropertyTest {

    private val docs = NoteDocFixtures.randomDocs(seed = 20261106, count = 6000)

    @Test fun `parse of serialize is the normalized document, and the record is stable`() {
        assertTrue(docs.size >= 2000)
        for (doc in docs) {
            val md = NoteMarkdown.serialize(doc)
            val expected = normalize(doc)
            val back = NoteMarkdown.parse(md)
            assertEquals(expected, back, "parse(serialize(doc)) != normalize(doc)\ndoc = $doc\nmd = <$md>")
            assertEquals(md, NoteMarkdown.serialize(back), "serialize is not stable\ndoc = $doc\nmd = <$md>")
        }
    }

    @Test fun `generator reaches the interesting shapes`() {
        fun any(p: (NoteBlock) -> Boolean) = docs.any { d -> d.blocks.any(p) }
        assertTrue(any { it is NoteBlock.Table })
        assertTrue(any { it is NoteBlock.Image })
        assertTrue(any { it is NoteBlock.Divider })
        assertTrue(any { it is NoteBlock.Text && it.kind == ParagraphKind.Mono })
        assertTrue(any { it is NoteBlock.Text && it.content.marks.any { m -> m.mark == Mark.Code } && it.content.text.contains('`') })
        assertTrue(docs.any { d -> d.blocks.zipWithNext().any { (a, b) -> a is NoteBlock.Table && b is NoteBlock.Text && b.kind == ParagraphKind.Body } })
        assertTrue(docs.any { d -> d.blocks.zipWithNext().any { (a, b) -> a is NoteBlock.Table && b is NoteBlock.Table } })
        for (level in 0..4) assertTrue(any { it is NoteBlock.Text && (it.kind as? ParagraphKind.ListItem)?.level == level })
        for (t in ListType.entries) assertTrue(any { it is NoteBlock.Text && (it.kind as? ParagraphKind.ListItem)?.type == t })
    }

    @Test fun `any markdown parses to a canonical document`() {
        val rnd = kotlin.random.Random(5)
        repeat(3000) {
            val md = List(rnd.nextInt(1, 6)) { NoteDocFixtures.randomText(rnd) }.joinToString("\n")
            val doc = NoteMarkdown.parse(md)
            assertEquals(doc, normalize(doc), md)
            assertEquals(doc, NoteMarkdown.parse(NoteMarkdown.serialize(doc)), "<$md>")
        }
    }
}
