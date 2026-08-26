package app.tessera.ui.editor.components

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material.icons.rounded.Remove

/**
 * Inline table editor. The grid sits DIRECTLY on the editor canvas (no card container — a nested
 * block read as "an editor inside the editor"), every row shares the same background, and rows
 * grow with their content ([IntrinsicSize.Min]; the tallest cell wins). Structure operations are
 * NOT drawn on the table at all: focusing any cell surfaces [TableActionsBar] in the editor's
 * bottom toolbar — the Google Docs / OneNote mobile pattern (contextual table controls above the
 * keyboard) — via [onCellFocusChanged].
 */
@Composable
fun TableBlockEditor(
    grid: TableGrid,
    onGridChange: (TableGrid) -> Unit,
    modifier: Modifier = Modifier,
    onCellFocusChanged: (Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    val widths = grid.colWidths ?: List(grid.columnCount) { TableGrid.DEFAULT_COL_WIDTH }

    // The grid scrolls horizontally so adding columns grows the table sideways
    // (fixed/min column widths) instead of cramming columns into the screen width.
    Column(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        TableRow(
            cells = grid.headers,
            widths = widths,
            isHeader = true,
            onCellChange = { c, v -> onGridChange(grid.setHeader(c, v)) },
            onColResize = { c, dxPx ->
                onGridChange(grid.withColWidth(c, widths[c] + with(density) { dxPx.toDp().value }))
            },
            onCellFocusChanged = onCellFocusChanged,
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
                onCellFocusChanged = onCellFocusChanged,
            )
        }
    }
}

/**
 * Contextual table toolbar rendered in the editor's bottom pill while a table cell is focused:
 * `Строка +/−  |  Столбец +/−  |  ↑ ↓`. Labels disambiguate the +/- pairs; move/remove are
 * disabled at the structure's boundaries.
 */
@Composable
fun TableActionsBar(
    canRemoveRow: Boolean,
    canRemoveColumn: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onAddRow: () -> Unit,
    onRemoveRow: () -> Unit,
    onAddColumn: () -> Unit,
    onRemoveColumn: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.table_row),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TableActionButton(Icons.Rounded.Add, R.string.table_add_row, onClick = onAddRow)
        TableActionButton(Icons.Rounded.Remove, R.string.table_remove_row, enabled = canRemoveRow, onClick = onRemoveRow)

        TableGroupSeparator()
        Text(
            text = stringResource(R.string.table_column),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TableActionButton(Icons.Rounded.Add, R.string.table_add_column, onClick = onAddColumn)
        TableActionButton(Icons.Rounded.Remove, R.string.table_remove_column, enabled = canRemoveColumn, onClick = onRemoveColumn)

        TableGroupSeparator()
        TableActionButton(Icons.Rounded.ArrowUpward, R.string.table_move_up, enabled = canMoveUp, onClick = onMoveUp)
        TableActionButton(Icons.Rounded.ArrowDownward, R.string.table_move_down, enabled = canMoveDown, onClick = onMoveDown)
    }
}

@Composable
private fun TableGroupSeparator() {
    Box(
        Modifier
            .padding(horizontal = 6.dp)
            .width(1.dp)
            .height(24.dp),
    ) {
        Surface(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.fillMaxHeight().width(1.dp)) {}
    }
}

@Composable
private fun TableActionButton(
    icon: ImageVector,
    contentDescriptionRes: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        },
        modifier = Modifier.size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(contentDescriptionRes),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    widths: List<Float>,
    isHeader: Boolean,
    onCellChange: (Int, String) -> Unit,
    onColResize: (Int, Float) -> Unit,
    onCellFocusChanged: (Boolean) -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
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
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .onFocusChanged { onCellFocusChanged(it.isFocused) },
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
