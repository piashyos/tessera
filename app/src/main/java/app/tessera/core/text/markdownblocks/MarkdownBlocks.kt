package app.tessera.core.text.markdownblocks

import kotlin.math.roundToInt

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
internal val TESSERA_META = Regex("""^\s*<!--\s*tessera:(.*?)\s*-->\s*$""")
internal val SEPARATOR = Regex("""^\s*\|?\s*:?-{3,}:?""")

/** Parse the `cols=...;rows=...` payload and attach it to [grid] if the sizes are valid. */
internal fun applyMeta(grid: TableGrid, payload: String): TableGrid {
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
internal fun metaComment(grid: TableGrid): String? {
    val parts = mutableListOf<String>()
    grid.colWidths?.let { parts.add("cols=" + it.joinToString(",") { w -> w.roundToInt().toString() }) }
    grid.rowHeights?.let { parts.add("rows=" + it.joinToString(",") { h -> h.roundToInt().toString() }) }
    if (parts.isEmpty()) return null
    return "<!-- tessera:" + parts.joinToString(";") + " -->"
}
