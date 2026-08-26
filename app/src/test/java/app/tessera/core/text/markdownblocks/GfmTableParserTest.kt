package app.tessera.core.text.markdownblocks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GfmTableParserTest {
    @Test fun `parse minimal 2x1 table`() {
        val md = """
            | A | B |
            |---|---|
            | 1 | 2 |
        """.trimIndent()
        val grid = parseGfmTable(md.lines())
        assertEquals(listOf("A", "B"), grid?.headers)
        assertEquals(listOf(listOf("1", "2")), grid?.rows)
    }

    @Test fun `serialize round-trip`() {
        val grid = TableGrid(listOf("A", "B"), listOf(listOf("1", "2"), listOf("3", "4")))
        val md = serializeGfmTable(grid)
        val back = parseGfmTable(md.lines())
        assertEquals(grid, back)
    }

    @Test fun `parse handles escaped pipes`() {
        val grid = parseGfmTable("""
            | A | B |
            |---|---|
            | a \| b | c |
        """.trimIndent().lines())
        assertEquals("a | b", grid?.rows?.first()?.first())
    }

    @Test fun `parse single-column table`() {
        val md = """
            | A |
            |---|
            | 1 |
        """.trimIndent()
        val grid = parseGfmTable(md.lines())
        assertEquals(listOf("A"), grid?.headers)
        assertEquals(listOf(listOf("1")), grid?.rows)
    }

    @Test fun `single-column table survives serialize round-trip`() {
        val grid = TableGrid(listOf("A", "B"), listOf(listOf("1", "2"))).removeColumn(1)
        val back = parseGfmTable(serializeGfmTable(grid).lines())
        assertEquals(grid, back)
    }

    @Test fun `non-table input returns null`() {
        assertNull(parseGfmTable(listOf("just text")))
    }
}
