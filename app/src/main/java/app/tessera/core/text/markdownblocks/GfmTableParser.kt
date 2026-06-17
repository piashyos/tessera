package app.tessera.core.text.markdownblocks

/**
 * Parse a list of consecutive markdown lines as a GFM table. Returns null if the input
 * is not a well-formed table (header row + separator row + ≥0 body rows).
 *
 * Separator row pattern: `|---|---|` (cells may have leading/trailing `:` for alignment;
 * alignment is ignored — preserved on round-trip via column-width pcts only.)
 *
 * Handles `\|` as escaped pipe within a cell.
 */
fun parseGfmTable(lines: List<String>): TableGrid? {
    // Filter blank lines for robustness against trailing newlines in heredocs / serializer output.
    val nonBlank = lines.filter { it.isNotBlank() }
    if (nonBlank.size < 2) return null
    val header = parseCells(nonBlank[0]) ?: return null
    val sep = nonBlank[1].trim()
    if (!Regex("""^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?$""").matches(sep)) return null
    val sepCells = parseCells(nonBlank[1]) ?: return null
    if (sepCells.size != header.size) return null

    val rows = nonBlank.drop(2)
        .map { parseCells(it) ?: return null }
        .map { if (it.size < header.size) it + List(header.size - it.size) { "" } else it.take(header.size) }

    return TableGrid(header, rows)
}

private fun parseCells(line: String): List<String>? {
    val trimmed = line.trim()
    if (!trimmed.startsWith("|")) return null
    val cells = mutableListOf<String>()
    val buf = StringBuilder()
    var i = 1 // skip leading |
    while (i < trimmed.length) {
        val c = trimmed[i]
        when {
            c == '\\' && i + 1 < trimmed.length && trimmed[i + 1] == '|' -> {
                buf.append('|'); i += 2
            }
            c == '|' -> { cells.add(buf.toString().trim()); buf.clear(); i++ }
            else -> { buf.append(c); i++ }
        }
    }
    if (buf.isNotEmpty()) cells.add(buf.toString().trim())
    return cells
}

fun serializeGfmTable(grid: TableGrid): String {
    val sb = StringBuilder()
    sb.append("| ").append(grid.headers.joinToString(" | ") { it.replace("|", "\\|") }).append(" |\n")
    sb.append("|").append(grid.headers.joinToString("|") { "---" }).append("|\n")
    grid.rows.forEach { row ->
        sb.append("| ").append(row.joinToString(" | ") { it.replace("|", "\\|") }).append(" |\n")
    }
    return sb.toString().trimEnd()
}
