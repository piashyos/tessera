package app.tessera.core.text.markdownblocks

sealed interface Block {
    data class Text(val markdown: String) : Block
    data class Table(val grid: TableGrid) : Block
}

fun splitMarkdownIntoBlocks(markdown: String): List<Block> {
    if (markdown.isEmpty()) return emptyList()
    val lines = markdown.split("\n")
    val out = mutableListOf<Block>()
    val buf = mutableListOf<String>()
    var i = 0
    while (i < lines.size) {
        if (lines[i].trimStart().startsWith("|") && i + 1 < lines.size &&
            Regex("""^\s*\|?\s*:?-{3,}:?""").containsMatchIn(lines[i + 1])
        ) {
            var j = i + 2
            while (j < lines.size && lines[j].trimStart().startsWith("|")) j++
            val tableLines = lines.subList(i, j)
            val grid = parseGfmTable(tableLines)
            if (grid != null) {
                if (buf.isNotEmpty()) {
                    out.add(Block.Text(buf.joinToString("\n").trim('\n')))
                    buf.clear()
                }
                out.add(Block.Table(grid))
                i = j
                continue
            }
        }
        buf.add(lines[i])
        i++
    }
    if (buf.isNotEmpty()) {
        out.add(Block.Text(buf.joinToString("\n").trim('\n')))
    }
    return out
}

fun joinBlocksIntoMarkdown(blocks: List<Block>): String =
    blocks.joinToString("\n\n") { block ->
        when (block) {
            is Block.Text -> block.markdown
            is Block.Table -> serializeGfmTable(block.grid)
        }
    }
