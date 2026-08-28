package app.tessera.core.ui.tessera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraSpacing

data class TesseraChip(
  val key: String,
  val label: String,
  val selected: Boolean,
  val leadingIcon: (@Composable () -> Unit)? = null,
)

@Composable
fun TesseraChipRow(
  chips: List<TesseraChip>,
  onChipClick: (String) -> Unit,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(horizontal = TesseraSpacing.lg, vertical = TesseraSpacing.xs),
) {
  // No drop-shadow strip above/below the row — it reads as stray divider lines under the top bar.
  LazyRow(
    modifier = modifier.fillMaxWidth(),
    contentPadding = contentPadding,
    horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.sm),
    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
  ) {
    items(items = chips, key = { it.key }) { chip ->
      // Each tag carries its own hue (see rememberTagChipColors); folder/other chips (no "#")
      // keep the neutral surface style.
      val isTag = chip.label.startsWith("#")
      val tagColors = if (isTag) rememberTagChipColors(chip.key) else null
      FilterChip(
        selected = chip.selected,
        onClick = { onChipClick(chip.key) },
        label = { Text(chip.label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = chip.leadingIcon,
        shape = if (isTag) CircleShape else FilterChipDefaults.shape,
        border = null,
        colors = if (tagColors != null) {
          FilterChipDefaults.filterChipColors(
            containerColor = tagColors.container,
            selectedContainerColor = tagColors.container,
            labelColor = tagColors.content,
            selectedLabelColor = tagColors.content,
          )
        } else {
          FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        },
      )
    }
  }
}
