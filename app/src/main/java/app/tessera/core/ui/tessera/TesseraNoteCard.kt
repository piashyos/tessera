package app.tessera.core.ui.tessera

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraShape
import app.tessera.core.ui.theme.TesseraSpacing

object TesseraNoteCardTags {
  const val PinnedBar = "tessera_note_card_pinned_bar"
  const val Root = "tessera_note_card_root"
}

private const val MaxInlineTags = 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TesseraNoteCard(
  title: String,
  preview: String,
  dateLabel: String,
  tags: List<String>,
  isPinned: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = TesseraShape.noteCard,
    modifier = modifier
      .testTag(TesseraNoteCardTags.Root)
      .fillMaxWidth()
      .heightIn(min = 78.dp)
      .combinedClickable(onClick = onClick, onLongClick = onLongClick),
  ) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
      if (isPinned) {
        Box(
          modifier = Modifier
            .testTag(TesseraNoteCardTags.PinnedBar)
            .width(3.dp)
            .fillMaxHeight()
            .padding(vertical = TesseraSpacing.md)
            .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.width(TesseraSpacing.md))
      } else {
        Spacer(modifier = Modifier.width(TesseraSpacing.lg))
      }
      Column(
        modifier = Modifier.weight(1f).padding(vertical = TesseraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(TesseraSpacing.xs),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = preview,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (tags.isNotEmpty()) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            tags.take(MaxInlineTags).forEach { tag ->
              Text(
                text = "#$tag",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                  .clip(RoundedCornerShape(999.dp))
                  .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                  .padding(horizontal = TesseraSpacing.sm, vertical = 2.dp),
                maxLines = 1,
              )
            }
            val overflow = tags.size - MaxInlineTags
            if (overflow > 0) {
              Text(
                text = "+$overflow",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
      Spacer(modifier = Modifier.width(TesseraSpacing.md))
      Text(
        text = dateLabel,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = TesseraSpacing.lg, top = TesseraSpacing.md),
      )
    }
  }
}
