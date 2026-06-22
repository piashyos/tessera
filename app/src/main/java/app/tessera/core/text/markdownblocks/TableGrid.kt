package app.tessera.core.text.markdownblocks

/**
 * Immutable representation of a GFM table.
 *
 * @param headers cell strings for the header row
 * @param rows ordered list of body rows; each row's size == headers.size
 * @param colWidths optional per-column width in dp. `null` = default/even width. When
 *        present, size == [columnCount]. Persisted out-of-band (see [serializeGfmTable]
 *        callers) because GFM has no column-width syntax.
 * @param rowHeights optional per-visual-row height in dp, where index 0 is the header
 *        row and indices 1..rowCount are the body rows. `null` = auto/default height.
 *        When present, size == rowCount + 1.
 */
data class TableGrid(
    val headers: List<String>,
    val rows: List<List<String>>,
    val colWidths: List<Float>? = null,
    val rowHeights: List<Float>? = null,
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
        val newRows = rows.subList(0, at) + listOf(empty) + rows.subList(at, rowCount)
        // rowHeights is header-prefixed, so a body row at index `at` is visual index `at + 1`.
        val newHeights = rowHeights?.let { h ->
            val vi = at + 1
            h.subList(0, vi) + DEFAULT_ROW_HEIGHT + h.subList(vi, h.size)
        }
        return copy(rows = newRows, rowHeights = newHeights)
    }

    fun removeRow(at: Int): TableGrid {
        val newRows = rows.filterIndexed { i, _ -> i != at }
        val newHeights = rowHeights?.filterIndexed { i, _ -> i != at + 1 }
        return copy(rows = newRows, rowHeights = newHeights)
    }

    fun addColumn(at: Int = columnCount): TableGrid {
        val newHeaders = headers.subList(0, at) + "" + headers.subList(at, columnCount)
        val newRows = rows.map { row -> row.subList(0, at) + "" + row.subList(at, columnCount) }
        val newWidths = colWidths?.let { ws ->
            ws.subList(0, at) + DEFAULT_COL_WIDTH + ws.subList(at, columnCount)
        }
        return TableGrid(newHeaders, newRows, newWidths, rowHeights)
    }

    fun removeColumn(at: Int): TableGrid {
        if (columnCount <= 1) return this
        val newHeaders = headers.filterIndexed { i, _ -> i != at }
        val newRows = rows.map { row -> row.filterIndexed { i, _ -> i != at } }
        val newWidths = colWidths?.filterIndexed { i, _ -> i != at }
        return TableGrid(newHeaders, newRows, newWidths, rowHeights)
    }

    /** Set the width (dp) of column [index], materializing defaults for the other columns. */
    fun withColWidth(index: Int, width: Float): TableGrid {
        val base = (colWidths ?: List(columnCount) { DEFAULT_COL_WIDTH }).toMutableList()
        if (index !in base.indices) return this
        base[index] = width.coerceAtLeast(MIN_COL_WIDTH)
        return copy(colWidths = base)
    }

    /**
     * Set the height (dp) of visual row [visualIndex] (0 == header, 1..rowCount == body
     * rows), materializing defaults for the other rows.
     */
    fun withRowHeight(visualIndex: Int, height: Float): TableGrid {
        val base = (rowHeights ?: List(rowCount + 1) { DEFAULT_ROW_HEIGHT }).toMutableList()
        if (visualIndex !in base.indices) return this
        base[visualIndex] = height.coerceAtLeast(MIN_ROW_HEIGHT)
        return copy(rowHeights = base)
    }

    companion object {
        const val DEFAULT_COL_WIDTH = 140f
        const val MIN_COL_WIDTH = 56f
        const val DEFAULT_ROW_HEIGHT = 44f
        const val MIN_ROW_HEIGHT = 36f

        fun empty(columns: Int = 2, rows: Int = 2): TableGrid =
            TableGrid(
                headers = List(columns) { "" },
                rows = List(rows) { List(columns) { "" } },
            )
    }
}
