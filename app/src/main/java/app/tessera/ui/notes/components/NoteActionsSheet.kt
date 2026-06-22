package app.tessera.ui.notes.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.tessera.core.ui.tessera.TesseraBottomSheet
import app.tessera.core.ui.tessera.TesseraSheetItem
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.Trash2

@Composable
fun NoteActionsSheet(
  noteTitle: String,
  isPinned: Boolean,
  onPinToggle: () -> Unit,
  onMoveToFolder: () -> Unit,
  onAddTags: () -> Unit,
  onDuplicate: () -> Unit,
  onShare: () -> Unit,
  onArchive: () -> Unit,
  onDelete: () -> Unit,
  onDismiss: () -> Unit,
) {
  TesseraBottomSheet(
    onDismissRequest = onDismiss,
    header = { Text(noteTitle, style = MaterialTheme.typography.titleMedium) },
  ) {
    TesseraSheetItem(
      label = if (isPinned) "Unpin" else "Pin",
      leadingIcon = { Icon(Lucide.Pin, contentDescription = null) },
      onClick = { onPinToggle(); onDismiss() },
    )
    TesseraSheetItem(
      label = "Move to folder",
      leadingIcon = { Icon(Lucide.Folder, contentDescription = null) },
      onClick = { onMoveToFolder(); onDismiss() },
    )
    TesseraSheetItem(
      label = "Add tags",
      leadingIcon = { Icon(Lucide.Tag, contentDescription = null) },
      onClick = { onAddTags(); onDismiss() },
    )
    TesseraSheetItem(
      label = "Duplicate",
      leadingIcon = { Icon(Lucide.Copy, contentDescription = null) },
      onClick = { onDuplicate(); onDismiss() },
    )
    TesseraSheetItem(
      label = "Share",
      leadingIcon = { Icon(Lucide.Share2, contentDescription = null) },
      onClick = { onShare(); onDismiss() },
    )
    TesseraSheetItem(
      label = "Archive",
      leadingIcon = { Icon(Lucide.Archive, contentDescription = null) },
      onClick = { onArchive(); onDismiss() },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    TesseraSheetItem(
      label = "Delete",
      leadingIcon = { Icon(Lucide.Trash2, contentDescription = null) },
      onClick = { onDelete(); onDismiss() },
      destructive = true,
    )
  }
}
