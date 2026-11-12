package app.tessera.core.ui.tessera

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
  // Non-null switches tag chips ("#…") from FilterChip to TagChip, which can see a long press.
  onChipLongClick: ((String) -> Unit)? = null,
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
      val tagIcon = if (isTag) rememberTagIcon(chip.key) else null
      if (isTag && onChipLongClick != null) {
        TagChip(
          label = if (tagIcon != null) chip.label.removePrefix("#") else chip.label,
          icon = tagIcon,
          colors = tagColors!!,
          selected = chip.selected,
          onClick = { onChipClick(chip.key) },
          onLongClick = { onChipLongClick(chip.key) },
        )
        return@items
      }
      FilterChip(
        selected = chip.selected,
        onClick = { onChipClick(chip.key) },
        label = { Text(if (tagIcon != null) chip.label.removePrefix("#") else chip.label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = if (tagIcon != null) {
          { EntityIconView(tagIcon, tint = tagColors!!.content, size = 16.dp) }
        } else {
          chip.leadingIcon
        },
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

/**
 * Own tag chip for [TesseraChipRow] when long press is wanted: FilterChip's inner `clickable`
 * swallows DOWN, so a wrapper never sees the long press. Size and paddings mirror FilterChip.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagChip(
  label: String,
  icon: EntityIcon?,
  colors: TagChipColors,
  selected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val haptics = LocalHapticFeedback.current
  Surface(
    color = colors.container,
    contentColor = colors.content,
    shape = CircleShape,
    modifier = Modifier
      // Same layout height as FilterChip (48 dp touch slot around a 32 dp chip).
      .minimumInteractiveComponentSize()
      .heightIn(min = 32.dp)
      .clip(CircleShape)
      .semantics { this.selected = selected }
      .combinedClickable(
        role = Role.Checkbox,
        onClick = onClick,
        onLongClick = {
          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          onLongClick()
        },
      ),
  ) {
    Row(
      modifier = Modifier.padding(start = if (icon != null) TesseraSpacing.sm else TesseraSpacing.lg, end = TesseraSpacing.lg),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.sm),
    ) {
      if (icon != null) EntityIconView(icon, tint = colors.content, size = 16.dp)
      Text(label, style = MaterialTheme.typography.labelLarge)
    }
  }
}
