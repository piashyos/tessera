package app.tessera.core.text.notedoc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Stored Markdown of the Aztec era must parse, and writing it again must not change what a line shows. */
class NoteMarkdownCorpusTest {

    /** What each line shows: its kind and its plain text (marks are markup, not text). */
    private fun visible(doc: NoteDoc): List<Pair<String, String>> = doc.blocks.map {
        when (it) {
            is NoteBlock.Text -> it.kind.toString() to it.content.text
            is NoteBlock.Image -> "Image" to it.src
            is NoteBlock.Table -> "Table" to it.grid.headers.joinToString("|")
            NoteBlock.Divider -> "Divider" to ""
        }
    }

    @Test fun `corpus is not empty`() = assertTrue(NoteDocFixtures.legacyCorpus.size > 50)

    @Test fun `every stored string parses and rewriting keeps the visible lines`() {
        for (md in NoteDocFixtures.legacyCorpus) {
            val doc = NoteMarkdown.parse(md)
            val rewritten = NoteMarkdown.serialize(doc)
            val again = NoteMarkdown.parse(rewritten)
            assertEquals(visible(doc), visible(again), "visible lines changed for <$md> -> <$rewritten>")
            assertEquals(doc, again, "document changed for <$md> -> <$rewritten>")
            assertEquals(rewritten, NoteMarkdown.serialize(again), "unstable for <$md>")
        }
    }

    @Test fun `one source line is one paragraph`() {
        for (md in NoteDocFixtures.legacyCorpus.filterNot { it.contains("```") }) {
            assertEquals(md.split("\n").size, NoteMarkdown.parse(md).blocks.size, "<$md>")
        }
    }

    @Test fun `canonical legacy strings are written back unchanged`() {
        val canonical = listOf(
            "", "**bold**", "*italic*", "~~strike~~", "# Heading one", "## Heading two", "- first\n- second", "- [ ] todo",
            "- [x] done", "Just some text.", "# A **bold** title", "- **bold** item\n- *italic* item", "1. first\n2. second",
            "> quoted", "> line one\n> line two", "`code`", "---", "<u>under</u>", "<u>**bold**</u>", "`a < b && c`",
            "```\ncode\n```", "```\nline one\n\nline two\n```", "```\nif (a < b && c > d) { *x* }\n```",
            "5 < 3", "3 > 5", "Tom & Jerry", "if a < b && c > d", "**a < b**", "# Q & A < B", "- [ ] fix a < b & c",
            "a <fake> tag", "5 < 3 & 7 > 2", "- a\n\n1. b\n\n- [ ] c", "- a\n- ", "first\n\nsecond\nthird",
            "before\n![](tessera-img://abc-1?w=50&rot=90)\nafter", "![](tessera-img://p-1?w=30&rot=180)\n- item\n\n![](tessera-img://p-2?rot=270)",
        )
        for (md in canonical) assertEquals(md, NoteMarkdown.serialize(NoteMarkdown.parse(md)), "<$md>")
    }

    @Test fun `pipe lines without a separator keep their text`() {
        val md = "![](tessera-img://before-2)\n| A | B |\n| 1 | 2 |\n![](tessera-img://after-2)"
        val doc = NoteMarkdown.parse(md)
        assertEquals(
            listOf("Image" to "tessera-img://before-2", "Body" to "| A | B |", "Body" to "| 1 | 2 |", "Image" to "tessera-img://after-2"),
            visible(doc).map { (k, t) -> (if (k.startsWith("Body")) "Body" else k) to t },
        )
    }

    @Test fun `sample docs survive a write and a read`() {
        for (d in NoteDocFixtures.sampleDocs) assertEquals(normalize(d), NoteMarkdown.parse(NoteMarkdown.serialize(d)), "$d")
    }
}
