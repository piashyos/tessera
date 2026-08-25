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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Unarchive

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
   * When non-null, a Restore action is shown at the top — used in the Trash view to move a note
   * back out of the trash. The rest of the actions are hidden in this restore-only mode.
   */
  onRestore: (() -> Unit)? = null,
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
    if (onRestore != null) {
      TesseraSheetItem(
        label = stringResource(R.string.note_action_restore),
        leadingIcon = { Icon(Icons.Rounded.Unarchive, contentDescription = null) },
        onClick = { onRestore(); onDismiss() },
      )
      if (showDelete) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TesseraSheetItem(
          label = stringResource(R.string.note_action_delete),
          leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
          onClick = { onDelete(); onDismiss() },
          destructive = true,
        )
      }
      return@TesseraBottomSheet
    }
    TesseraSheetItem(
      label = stringResource(R.string.note_action_select),
      leadingIcon = { Icon(Icons.Rounded.Checklist, contentDescription = null) },
      onClick = { onSelect(); onDismiss() },
    )
    TesseraSheetItem(
      label = stringResource(if (isPinned) R.string.note_action_unpin else R.string.note_action_pin),
      leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
      onClick = { onPinToggle(); onDismiss() },
    )
    TesseraSheetItem(
      label = stringResource(R.string.note_action_move),
      leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
      onClick = { onMoveToFolder(); onDismiss() },
    )
    TesseraSheetItem(
      label = stringResource(R.string.note_action_add_tags),
      leadingIcon = { Icon(Icons.Rounded.Sell, contentDescription = null) },
      onClick = { onAddTags(); onDismiss() },
    )
    TesseraSheetItem(
      label = stringResource(R.string.note_action_duplicate),
      leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
      onClick = { onDuplicate(); onDismiss() },
    )
    if (onMakeShared != null) {
      TesseraSheetItem(
        label = stringResource(R.string.note_action_make_shared),
        leadingIcon = { Icon(Icons.Rounded.Group, contentDescription = null) },
        onClick = { onMakeShared(); onDismiss() },
      )
    }
    TesseraSheetItem(
      label = stringResource(if (isArchived) R.string.note_action_unarchive else R.string.note_action_archive),
      leadingIcon = {
        Icon(if (isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, contentDescription = null)
      },
      onClick = { onArchiveToggle(); onDismiss() },
    )
    if (showDelete) {
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      TesseraSheetItem(
        label = stringResource(R.string.note_action_delete),
        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
        onClick = { onDelete(); onDismiss() },
        destructive = true,
      )
    }
  }
}
