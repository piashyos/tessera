package app.tessera.core.text.markdownblocks

/**
 * Immutable representation of a GFM table.
 * @param headers cell strings for the header row
 * @param rows ordered list of body rows; each row's size == headers.size
 * @param colWidthsPct optional per-column width as a fraction of total table width;
 *        if null, columns are equal-width. Must sum to ~1.0 when present.
 */
data class TableGrid(
    val headers: List<String>,
    val rows: List<List<String>>,
    val colWidthsPct: List<Float>? = null,
) {
    val columnCount: Int get() = headers.size
    val rowCount: Int get() = rows.size

    fun setCell(rowIndex: Int, colIndex: Int, value: String): TableGrid =
        copy(rows = rows.mapIndexed { r, row ->
            if (r == rowIndex) row.mapIndexed { c, v -> if (c == colIndex) value else v }
            else row
        })

    fun setHeader(colIndex: Int, value: String): TableGrid =
        copy(headers = headers.mapIndexed { c, h -> if (c == colIndex) value else h })

    fun addRow(at: Int = rowCount): TableGrid {
        val empty = List(columnCount) { "" }
        return copy(rows = rows.subList(0, at) + listOf(empty) + rows.subList(at, rowCount))
    }

    fun removeRow(at: Int): TableGrid =
        copy(rows = rows.filterIndexed { i, _ -> i != at })

    fun addColumn(at: Int = columnCount): TableGrid {
        val newHeaders = headers.subList(0, at) + "" + headers.subList(at, columnCount)
        val newRows = rows.map { row -> row.subList(0, at) + "" + row.subList(at, columnCount) }
        val newWidths = colWidthsPct?.let { ws ->
            val even = 1f / (columnCount + 1)
            ws.subList(0, at) + even + ws.subList(at, columnCount)
        }
        return TableGrid(newHeaders, newRows, newWidths)
    }

    fun removeColumn(at: Int): TableGrid {
        if (columnCount <= 1) return this
        val newHeaders = headers.filterIndexed { i, _ -> i != at }
        val newRows = rows.map { row -> row.filterIndexed { i, _ -> i != at } }
        val newWidths = colWidthsPct?.filterIndexed { i, _ -> i != at }
        return TableGrid(newHeaders, newRows, newWidths)
    }

    companion object {
        fun empty(columns: Int = 2, rows: Int = 2): TableGrid =
            TableGrid(
                headers = List(columns) { "" },
                rows = List(rows) { List(columns) { "" } },
            )
    }
}
