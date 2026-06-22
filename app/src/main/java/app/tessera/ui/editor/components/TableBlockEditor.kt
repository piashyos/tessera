package app.tessera.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tessera.core.text.markdownblocks.TableGrid
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus

@Composable
fun TableBlockEditor(
    grid: TableGrid,
    onGridChange: (TableGrid) -> Unit,
    modifier: Modifier = Modifier,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val widths = grid.colWidths ?: List(grid.columnCount) { TableGrid.DEFAULT_COL_WIDTH }
    fun rowHeight(visualIndex: Int): Float =
        grid.rowHeights?.getOrNull(visualIndex) ?: TableGrid.DEFAULT_ROW_HEIGHT

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(8.dp),
    ) {
        // The grid scrolls horizontally so adding columns grows the table sideways
        // (fixed/min column widths) instead of cramming columns into the screen width.
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            TableRow(
                cells = grid.headers,
                widths = widths,
                heightDp = rowHeight(0),
                isHeader = true,
                onCellChange = { c, v -> onGridChange(grid.setHeader(c, v)) },
                onColResize = { c, dxPx ->
                    onGridChange(grid.withColWidth(c, widths[c] + with(density) { dxPx.toDp().value }))
                },
                onRowResize = { dyPx ->
                    onGridChange(grid.withRowHeight(0, rowHeight(0) + with(density) { dyPx.toDp().value }))
                },
            )
            grid.rows.forEachIndexed { rowIdx, row ->
                TableRow(
                    cells = row,
                    widths = widths,
                    heightDp = rowHeight(rowIdx + 1),
                    isHeader = false,
                    onCellChange = { c, v -> onGridChange(grid.setCell(rowIdx, c, v)) },
                    onColResize = { c, dxPx ->
                        onGridChange(grid.withColWidth(c, widths[c] + with(density) { dxPx.toDp().value }))
                    },
                    onRowResize = { dyPx ->
                        onGridChange(
                            grid.withRowHeight(rowIdx + 1, rowHeight(rowIdx + 1) + with(density) { dyPx.toDp().value }),
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onGridChange(grid.addRow()) }) {
                Icon(Lucide.Plus, contentDescription = "Add row", modifier = Modifier.size(16.dp))
            }
            Text("Row", style = MaterialTheme.typography.labelSmall)
            IconButton(onClick = {
                if (grid.rowCount > 0) onGridChange(grid.removeRow(grid.rowCount - 1))
            }) {
                Icon(Lucide.Minus, contentDescription = "Remove row", modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = { onGridChange(grid.addColumn()) }) {
                Icon(Lucide.Plus, contentDescription = "Add column", modifier = Modifier.size(16.dp))
            }
            Text("Col", style = MaterialTheme.typography.labelSmall)
            IconButton(onClick = {
                if (grid.columnCount > 1) onGridChange(grid.removeColumn(grid.columnCount - 1))
            }) {
                Icon(Lucide.Minus, contentDescription = "Remove column", modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null) {
                Icon(Lucide.ArrowUp, contentDescription = "Move table up", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = { onMoveDown?.invoke() }, enabled = onMoveDown != null) {
                Icon(Lucide.ArrowDown, contentDescription = "Move table down", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    widths: List<Float>,
    heightDp: Float,
    isHeader: Boolean,
    onCellChange: (Int, String) -> Unit,
    onColResize: (Int, Float) -> Unit,
    onRowResize: (Float) -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Box {
        Row(modifier = Modifier.height(heightDp.dp)) {
            cells.forEachIndexed { idx, value ->
                Box(
                    modifier = Modifier
                        .width(widths.getOrElse(idx) { TableGrid.DEFAULT_COL_WIDTH }.dp)
                        .fillMaxHeight()
                        .border(0.5.dp, outline),
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = { onCellChange(idx, it) },
                        textStyle = LocalTextStyle.current.merge(
                            MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(widths.getOrElse(idx) { TableGrid.DEFAULT_COL_WIDTH }.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    // Right-edge handle: drag horizontally to resize this column.
                    ResizeHandle(
                        vertical = false,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(14.dp)
                            .fillMaxHeight(),
                        onDelta = { onColResize(idx, it) },
                    )
                }
            }
        }
        // Bottom-edge handle spanning the full row: drag vertically to resize this row.
        ResizeHandle(
            vertical = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(12.dp),
            onDelta = { onRowResize(it) },
        )
    }
}

/**
 * A thin, invisible drag strip. [vertical] = true reports the y-delta (row resize),
 * false reports the x-delta (column resize). Keyed by [Unit] so an in-flight gesture
 * survives the recompositions triggered by committing each delta through the grid.
 */
@Composable
private fun ResizeHandle(
    vertical: Boolean,
    modifier: Modifier,
    onDelta: (Float) -> Unit,
) {
    val current by rememberUpdatedState(onDelta)
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                current(if (vertical) dragAmount.y else dragAmount.x)
            }
        },
    )
}
