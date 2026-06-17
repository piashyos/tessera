package app.tessera.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tessera.core.text.markdownblocks.TableGrid
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus

@Composable
fun TableBlockEditor(
    grid: TableGrid,
    onGridChange: (TableGrid) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(8.dp),
    ) {
        TableRow(
            cells = grid.headers,
            onCellChange = { i, v -> onGridChange(grid.setHeader(i, v)) },
            isHeader = true,
        )
        grid.rows.forEachIndexed { rowIdx, row ->
            TableRow(
                cells = row,
                onCellChange = { colIdx, v -> onGridChange(grid.setCell(rowIdx, colIdx, v)) },
                isHeader = false,
            )
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
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    onCellChange: (Int, String) -> Unit,
    isHeader: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEachIndexed { idx, value ->
            BasicTextField(
                value = value,
                onValueChange = { onCellChange(idx, it) },
                textStyle = if (isHeader) {
                    MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                modifier = Modifier
                    .weight(1f)
                    .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}
