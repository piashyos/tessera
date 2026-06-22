package app.tessera.ui.notes.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import app.tessera.core.ui.tessera.TesseraNoteCard
import app.tessera.core.ui.theme.TesseraShape
import app.tessera.core.ui.theme.TesseraSpacing

/**
 * [TesseraNoteCard] wrapped in [SwipeToDismissBox]:
 * - swipe start-to-end (right) → archive (secondaryContainer)
 * - swipe end-to-start (left)  → delete (errorContainer)
 *
 * Confirmation/undo are the caller's responsibility — host wires Snackbar restore.
 */
@Composable
fun TesseraSwipeableNoteCard(
  title: String,
  preview: String,
  dateLabel: String,
  tags: List<String>,
  isPinned: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onArchive: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val state = rememberSwipeToDismissBoxState(
    confirmValueChange = { value ->
      when (value) {
        SwipeToDismissBoxValue.StartToEnd -> { onArchive(); true }
        SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
        SwipeToDismissBoxValue.Settled -> false
      }
    },
  )
  LaunchedEffect(state.currentValue) {
    if (state.currentValue != SwipeToDismissBoxValue.Settled) state.reset()
  }
  SwipeToDismissBox(
    state = state,
    modifier = modifier,
    backgroundContent = {
      val isArchive = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
      val bg = if (isArchive) MaterialTheme.colorScheme.secondaryContainer
               else MaterialTheme.colorScheme.errorContainer
      val tint = if (isArchive) MaterialTheme.colorScheme.onSecondaryContainer
                 else MaterialTheme.colorScheme.onErrorContainer
      val align = if (isArchive) Alignment.CenterStart else Alignment.CenterEnd
      Box(
        modifier = Modifier
          .fillMaxSize()
          .clip(TesseraShape.noteCard)
          .background(bg)
          .padding(horizontal = TesseraSpacing.xl),
        contentAlignment = align,
      ) {
        Icon(
          imageVector = if (isArchive) Lucide.Archive else Lucide.Trash2,
          contentDescription = null,
          tint = tint,
        )
      }
    },
  ) {
    TesseraNoteCard(
      title = title,
      preview = preview,
      dateLabel = dateLabel,
      tags = tags,
      isPinned = isPinned,
      onClick = onClick,
      onLongClick = onLongClick,
    )
  }
}
