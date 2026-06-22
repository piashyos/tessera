package app.tessera.core.text.markdownblocks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MarkdownBlocksTest {
    @Test fun `prose-only input becomes a single Text block`() {
        val md = "Hello\n\nWorld"
        val blocks = splitMarkdownIntoBlocks(md)
        assertEquals(listOf(Block.Text("Hello\n\nWorld")), blocks)
    }

    @Test fun `table is isolated as a Table block`() {
        val md = """
            Intro

            | A | B |
            |---|---|
            | 1 | 2 |

            Outro
        """.trimIndent()
        val blocks = splitMarkdownIntoBlocks(md)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is Block.Text)
        assertTrue(blocks[1] is Block.Table)
        assertTrue(blocks[2] is Block.Text)
    }

    @Test fun `round-trip preserves content`() {
        val md = """
            # Title

            | A | B |
            |---|---|
            | 1 | 2 |

            - item
        """.trimIndent()
        assertEquals(md, joinBlocksIntoMarkdown(splitMarkdownIntoBlocks(md)))
    }

    @Test fun `column widths and row heights round-trip via tessera comment`() {
        val grid = TableGrid(
            headers = listOf("A", "B"),
            rows = listOf(listOf("1", "2")),
            colWidths = listOf(140f, 80f),
            rowHeights = listOf(44f, 30f),
        )
        val md = joinBlocksIntoMarkdown(listOf(Block.Table(grid)))
        assertTrue(md.startsWith("<!-- tessera:cols=140,80;rows=44,30 -->"))

        val parsed = (splitMarkdownIntoBlocks(md).single() as Block.Table).grid
        assertEquals(listOf(140f, 80f), parsed.colWidths)
        assertEquals(listOf(44f, 30f), parsed.rowHeights)
        assertEquals(listOf("A", "B"), parsed.headers)
    }

    @Test fun `table without custom sizing emits no comment`() {
        val md = joinBlocksIntoMarkdown(
            listOf(Block.Table(TableGrid(listOf("A", "B"), listOf(listOf("1", "2"))))),
        )
        assertTrue(!md.contains("tessera:"))
    }
}
