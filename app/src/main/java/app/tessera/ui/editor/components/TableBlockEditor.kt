package app.tessera.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.text.markdownblocks.TableGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Remove

/**
 * Inline table editor. The grid sits DIRECTLY on the editor canvas (no card container — a nested
 * block read as "an editor inside the editor"), rows grow with their content (tallest cell wins
 * via [IntrinsicSize.Min]; no fixed row heights), and all structure operations live behind a
 * single ⋮ handle at the table's top-right corner — a compact, always-visible affordance that
 * doesn't compete with the cells the way the old 6-button strip under the grid did.
 */
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

    Box(modifier = modifier) {
        // The grid scrolls horizontally so adding columns grows the table sideways
        // (fixed/min column widths) instead of cramming columns into the screen width.
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 4.dp, end = 20.dp),
        ) {
            TableRow(
                cells = grid.headers,
                widths = widths,
                isHeader = true,
                onCellChange = { c, v -> onGridChange(grid.setHeader(c, v)) },
                onColResize = { c, dxPx ->
                    onGridChange(grid.withColWidth(c, widths[c] + with(density) { dxPx.toDp().value }))
                },
            )
            grid.rows.forEachIndexed { rowIdx, row ->
                TableRow(
                    cells = row,
                    widths = widths,
                    isHeader = false,
                    onCellChange = { c, v -> onGridChange(grid.setCell(rowIdx, c, v)) },
                    onColResize = { c, dxPx ->
                        onGridChange(grid.withColWidth(c, widths[c] + with(density) { dxPx.toDp().value }))
                    },
                )
            }
        }

        // Structure operations behind a compact ⋮ handle pinned to the table's corner.
        var menuExpanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            Surface(
                onClick = { menuExpanded = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.table_menu),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                TableMenuItem(Icons.Rounded.Add, R.string.table_add_row) {
                    onGridChange(grid.addRow()); menuExpanded = false
                }
                TableMenuItem(
                    Icons.Rounded.Remove, R.string.table_remove_row,
                    enabled = grid.rowCount > 0,
                ) {
                    onGridChange(grid.removeRow(grid.rowCount - 1)); menuExpanded = false
                }
                TableMenuItem(Icons.Rounded.Add, R.string.table_add_column) {
                    onGridChange(grid.addColumn()); menuExpanded = false
                }
                TableMenuItem(
                    Icons.Rounded.Remove, R.string.table_remove_column,
                    enabled = grid.columnCount > 1,
                ) {
                    onGridChange(grid.removeColumn(grid.columnCount - 1)); menuExpanded = false
                }
                TableMenuItem(
                    Icons.Rounded.ArrowUpward, R.string.table_move_up,
                    enabled = onMoveUp != null,
                ) {
                    onMoveUp?.invoke(); menuExpanded = false
                }
                TableMenuItem(
                    Icons.Rounded.ArrowDownward, R.string.table_move_down,
                    enabled = onMoveDown != null,
                ) {
                    onMoveDown?.invoke(); menuExpanded = false
                }
            }
        }
    }
}

@Composable
private fun TableMenuItem(
    icon: ImageVector,
    labelRes: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun TableRow(
    cells: List<String>,
    widths: List<Float>,
    isHeader: Boolean,
    onCellChange: (Int, String) -> Unit,
    onColResize: (Int, Float) -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val headerBg = MaterialTheme.colorScheme.surfaceContainerLow
    // IntrinsicSize.Min lets the tallest cell set the row height — every cell then fills
    // that height, so borders stay continuous while text wraps freely.
    Row(
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .heightIn(min = 40.dp),
    ) {
        cells.forEachIndexed { idx, value ->
            Box(
                modifier = Modifier
                    .width(widths.getOrElse(idx) { TableGrid.DEFAULT_COL_WIDTH }.dp)
                    .fillMaxHeight()
                    .heightIn(min = 40.dp)
                    .let { if (isHeader) it.background(headerBg) else it }
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
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
                // Right-edge handle: drag horizontally to resize this column.
                ResizeHandle(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(14.dp)
                        .fillMaxHeight(),
                    onDelta = { onColResize(idx, it) },
                )
            }
        }
    }
}

/**
 * A thin, invisible drag strip reporting the x-delta (column resize). Keyed by [Unit] so an
 * in-flight gesture survives the recompositions triggered by committing each delta through
 * the grid.
 */
@Composable
private fun ResizeHandle(
    modifier: Modifier,
    onDelta: (Float) -> Unit,
) {
    val current by rememberUpdatedState(onDelta)
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                current(dragAmount.x)
            }
        },
    )
}
