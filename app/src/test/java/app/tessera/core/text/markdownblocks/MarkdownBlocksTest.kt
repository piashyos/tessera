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
}
