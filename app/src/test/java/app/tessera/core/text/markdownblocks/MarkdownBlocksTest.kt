package app.tessera.core.text.markdownblocks

import app.tessera.core.text.notedoc.NoteBlock
import app.tessera.core.text.notedoc.NoteDoc
import app.tessera.core.text.notedoc.NoteMarkdown
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownBlocksTest {
    private val grid = TableGrid(listOf("A", "B"), listOf(listOf("1", "2")))

    @Test fun `no sizing gives no comment`() {
        assertNull(metaComment(grid))
    }

    @Test fun `sizes become a tessera comment`() {
        val sized = grid.copy(colWidths = listOf(140f, 80f), rowHeights = listOf(44f, 30f))
        assertEquals("<!-- tessera:cols=140,80;rows=44,30 -->", metaComment(sized))
    }

    @Test fun `the comment line is recognised`() {
        val m = TESSERA_META.matchEntire("<!-- tessera:cols=140,80;rows=44,30 -->")!!
        assertEquals("cols=140,80;rows=44,30", m.groupValues[1])
    }

    @Test fun `the separator row is recognised`() {
        assertTrue(SEPARATOR.containsMatchIn("|---|:--:|"))
        assertTrue(!SEPARATOR.containsMatchIn("| a | b |"))
    }

    @Test fun `valid sizes are attached to the grid`() {
        val sized = applyMeta(grid, "cols=140,80;rows=44,30")
        assertEquals(listOf(140f, 80f), sized.colWidths)
        assertEquals(listOf(44f, 30f), sized.rowHeights)
    }

    @Test fun `sizes of the wrong count are dropped`() {
        assertEquals(grid, applyMeta(grid, "cols=140;rows=44"))
        assertEquals(grid, applyMeta(grid, "junk"))
    }

    @Test fun `column widths and row heights round-trip through the note markdown`() {
        val sized = grid.copy(colWidths = listOf(140f, 80f), rowHeights = listOf(44f, 30f))
        val md = NoteMarkdown.serialize(NoteDoc(listOf(NoteBlock.Table(sized))))
        assertTrue(md.contains("<!-- tessera:cols=140,80;rows=44,30 -->"))
        val parsed = NoteMarkdown.parse(md).blocks.filterIsInstance<NoteBlock.Table>().single().grid
        assertEquals(listOf(140f, 80f), parsed.colWidths)
        assertEquals(listOf(44f, 30f), parsed.rowHeights)
        assertEquals(listOf("A", "B"), parsed.headers)
    }
}
