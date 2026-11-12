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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraSpacing

object TesseraNoteCardTags {
  const val PinnedBar = "tessera_note_card_pinned_bar"
  const val Root = "tessera_note_card_root"
}

private const val MaxInlineTags = 3

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
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
  selected: Boolean = false,
  avatars: (@Composable () -> Unit)? = null,
  // Folder-colour fill (AccentColor.cardTint()); selection still wins over it.
  tint: Color? = null,
) {
  val haptics = LocalHapticFeedback.current
  // Selection is expressed the PixelPlayer way — a corner-radius morph plus a tonal shift —
  // rather than a border. Both run on the expressive spatial/effects springs.
  val cornerRadius by animateDpAsState(
    targetValue = if (selected) 28.dp else 14.dp,
    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    label = "noteCardCorner",
  )
  val containerColor by animateColorAsState(
    targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
                  else tint ?: MaterialTheme.colorScheme.surfaceContainerLow,
    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    label = "noteCardColor",
  )
  val shape = RoundedCornerShape(cornerRadius)
  Surface(
    color = containerColor,
    shape = shape,
    border = null,
    modifier = modifier
      .testTag(TesseraNoteCardTags.Root)
      .fillMaxWidth()
      .heightIn(min = 78.dp)
      // Clip the touch/indication node to the card shape so the tap ripple stays inside the
      // rounded corners instead of bleeding into the rectangular bounds.
      .clip(shape)
      .combinedClickable(
        onClick = onClick,
        onLongClick = {
          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          onLongClick()
        },
      ),
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
              val tagColors = rememberTagChipColors(tag)
              val tagIcon = rememberTagIcon(tag)
              Row(
                modifier = Modifier
                  .clip(CircleShape)
                  .background(tagColors.container)
                  .padding(horizontal = TesseraSpacing.sm, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.xs),
              ) {
                if (tagIcon != null) EntityIconView(tagIcon, tint = tagColors.content, size = 12.dp)
                Text(
                  text = if (tagIcon != null) tag else "#$tag",
                  style = MaterialTheme.typography.labelSmall,
                  color = tagColors.content,
                  maxLines = 1,
                )
              }
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
        if (avatars != null) {
          Spacer(modifier = Modifier.padding(top = 2.dp))
          avatars()
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
