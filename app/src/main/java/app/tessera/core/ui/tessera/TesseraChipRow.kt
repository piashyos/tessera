package app.tessera.core.ui.tessera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraElevation
import app.tessera.core.ui.theme.TesseraSpacing
import app.tessera.core.ui.theme.tesseraShadow

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
  contentPadding: PaddingValues = PaddingValues(horizontal = TesseraSpacing.lg),
) {
  LazyRow(
    modifier = modifier
      .fillMaxWidth()
      .height(48.dp)
      .tesseraShadow(elevation = TesseraElevation.Level1, shape = RectangleShape),
    contentPadding = contentPadding,
    horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.sm),
  ) {
    items(items = chips, key = { it.key }) { chip ->
      FilterChip(
        selected = chip.selected,
        onClick = { onChipClick(chip.key) },
        label = { Text(chip.label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = chip.leadingIcon,
        colors = FilterChipDefaults.filterChipColors(
          containerColor = MaterialTheme.colorScheme.surface,
          selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
          labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
          selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
      )
    }
  }
}
