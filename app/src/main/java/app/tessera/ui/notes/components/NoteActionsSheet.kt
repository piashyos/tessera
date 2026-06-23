package app.tessera.ui.notes.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.tessera.R
import app.tessera.core.ui.tessera.TesseraBottomSheet
import app.tessera.core.ui.tessera.TesseraSheetItem
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.ArchiveRestore
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Users

@Composable
fun NoteActionsSheet(
  noteTitle: String,
  isPinned: Boolean,
  isArchived: Boolean,
  onPinToggle: () -> Unit,
  onSelect: () -> Unit,
  onMoveToFolder: () -> Unit,
  onAddTags: () -> Unit,
  onDuplicate: () -> Unit,
  onArchiveToggle: () -> Unit,
  onDelete: () -> Unit,
  onDismiss: () -> Unit,
  onMakeShared: (() -> Unit)? = null,
  /**
   * When false, the Delete action is hidden — used for shared notes the current user may not
   * delete for everyone (only owner/manager can; deleting a shared note is a delete-for-all).
   */
  showDelete: Boolean = true,
) {
  TesseraBottomSheet(
    onDismissRequest = onDismiss,
    header = { Text(noteTitle, style = MaterialTheme.typography.titleMedium) },
  ) {
    TesseraSheetItem(
      label = stringResource(R.string.note_action_select),
      leadingIcon = { Icon(Lucide.ListChecks, contentDescription = null) },
      onClick = { onSelect(); onDismiss() },
    )
    TesseraSheetItem(
      label = stringResource(if (isPinned) R.string.note_action_unpin else R.string.note_action_pin),
      leadingIcon = { Icon(Lucide.Pin, contentDescription = null) },
      onClick = { onPinToggle(); onDismiss() },
    )
    TesseraSheetItem(
      label = stringResource(R.string.note_action_move),
      leadingIcon = { Icon(Lucide.Folder, contentDescription = null) },
      onClick = { onMoveToFolder(); onDismiss() },
    )
    TesseraSheetItem(
      label = stringResource(R.string.note_action_add_tags),
      leadingIcon = { Icon(Lucide.Tag, contentDescription = null) },
      onClick = { onAddTags(); onDismiss() },
    )
    TesseraSheetItem(
      label = stringResource(R.string.note_action_duplicate),
      leadingIcon = { Icon(Lucide.Copy, contentDescription = null) },
      onClick = { onDuplicate(); onDismiss() },
    )
    if (onMakeShared != null) {
      TesseraSheetItem(
        label = stringResource(R.string.note_action_make_shared),
        leadingIcon = { Icon(Lucide.Users, contentDescription = null) },
        onClick = { onMakeShared(); onDismiss() },
      )
    }
    TesseraSheetItem(
      label = stringResource(if (isArchived) R.string.note_action_unarchive else R.string.note_action_archive),
      leadingIcon = {
        Icon(if (isArchived) Lucide.ArchiveRestore else Lucide.Archive, contentDescription = null)
      },
      onClick = { onArchiveToggle(); onDismiss() },
    )
    if (showDelete) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      TesseraSheetItem(
        label = stringResource(R.string.note_action_delete),
        leadingIcon = { Icon(Lucide.Trash2, contentDescription = null) },
        onClick = { onDelete(); onDismiss() },
        destructive = true,
      )
    }
  }
}
