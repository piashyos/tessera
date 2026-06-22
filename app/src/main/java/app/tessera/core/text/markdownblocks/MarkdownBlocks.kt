package app.tessera.core.text.markdownblocks

import kotlin.math.roundToInt

sealed interface Block {
    data class Text(val markdown: String) : Block
    data class Table(val grid: TableGrid) : Block
}

// GFM has no syntax for column widths / row heights, so we persist them out-of-band in an
// HTML comment immediately preceding the table. External markdown renderers (GitHub, etc.)
// treat the comment as invisible, so the table still renders everywhere — the sizing is
// simply a Tessera-only enhancement that round-trips through our own parser.
//
// Example:
//   <!-- tessera:cols=140,80;rows=44,30,30 -->
//   | A | B |
//   |---|---|
//   | 1 | 2 |
private val TESSERA_META = Regex("""^\s*<!--\s*tessera:(.*?)\s*-->\s*$""")
private val SEPARATOR = Regex("""^\s*\|?\s*:?-{3,}:?""")

fun splitMarkdownIntoBlocks(markdown: String): List<Block> {
    if (markdown.isEmpty()) return emptyList()
    val lines = markdown.split("\n")
    val out = mutableListOf<Block>()
    val buf = mutableListOf<String>()
    var i = 0

    fun flushText() {
        if (buf.isNotEmpty()) {
            out.add(Block.Text(buf.joinToString("\n").trim('\n')))
            buf.clear()
        }
    }

    while (i < lines.size) {
        // A table may be preceded by a Tessera sizing comment; if so, the table starts on
        // the following line and the comment is consumed (not rendered as prose).
        val metaMatch = TESSERA_META.matchEntire(lines[i])
        val tableStart = if (metaMatch != null) i + 1 else i

        if (tableStart < lines.size &&
            lines[tableStart].trimStart().startsWith("|") &&
            tableStart + 1 < lines.size &&
            SEPARATOR.containsMatchIn(lines[tableStart + 1])
        ) {
            var j = tableStart + 2
            while (j < lines.size && lines[j].trimStart().startsWith("|")) j++
            val grid = parseGfmTable(lines.subList(tableStart, j))
            if (grid != null) {
                flushText()
                val sized = metaMatch?.groupValues?.get(1)?.let { applyMeta(grid, it) } ?: grid
                out.add(Block.Table(sized))
                i = j
                continue
            }
        }
        buf.add(lines[i])
        i++
    }
    flushText()
    return out
}

fun joinBlocksIntoMarkdown(blocks: List<Block>): String =
    blocks.joinToString("\n\n") { block ->
        when (block) {
            is Block.Text -> block.markdown
            is Block.Table -> {
                val meta = metaComment(block.grid)
                val table = serializeGfmTable(block.grid)
                if (meta != null) "$meta\n$table" else table
            }
        }
    }

/** Parse the `cols=...;rows=...` payload and attach it to [grid] if the sizes are valid. */
private fun applyMeta(grid: TableGrid, payload: String): TableGrid {
    var cols: List<Float>? = null
    var heights: List<Float>? = null
    payload.split(';').forEach { part ->
        val (key, value) = part.split('=', limit = 2).let {
            if (it.size == 2) it[0].trim() to it[1].trim() else return@forEach
        }
        val nums = value.split(',').mapNotNull { it.trim().toFloatOrNull() }
        when (key) {
            "cols" -> if (nums.size == grid.columnCount) cols = nums
            "rows" -> if (nums.size == grid.rowCount + 1) heights = nums
        }
    }
    return if (cols == null && heights == null) grid
    else grid.copy(colWidths = cols, rowHeights = heights)
}

/** Build the `<!-- tessera:... -->` comment for [grid], or null if it has no custom sizing. */
private fun metaComment(grid: TableGrid): String? {
    val parts = mutableListOf<String>()
    grid.colWidths?.let { parts.add("cols=" + it.joinToString(",") { w -> w.roundToInt().toString() }) }
    grid.rowHeights?.let { parts.add("rows=" + it.joinToString(",") { h -> h.roundToInt().toString() }) }
    if (parts.isEmpty()) return null
    return "<!-- tessera:" + parts.joinToString(";") + " -->"
}
