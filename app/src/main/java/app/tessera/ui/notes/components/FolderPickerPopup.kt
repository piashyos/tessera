package app.tessera.ui.notes.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.components.TesseraPopup
import app.tessera.core.ui.tessera.TesseraDialog
import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import com.composables.icons.lucide.Folder as FolderIcon
import com.composables.icons.lucide.FolderX
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import dev.chrisbanes.haze.HazeState

/**
 * Glass-styled modal for moving a note into a folder. Each folder row can be
 * deleted in place (with a confirmation dialog); "No folder" detaches the note.
 */
@Composable
fun FolderPickerPopup(
    visible: Boolean,
    folders: List<Folder>,
    onPick: (FolderId?) -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState,
    onDeleteFolder: (FolderId) -> Unit = {},
) {
    var pendingDelete by remember(visible) { mutableStateOf<Folder?>(null) }

    TesseraPopup(
        visible = visible,
        onDismiss = onDismiss,
        hazeState = hazeState,
    ) {
        Text(
            text = stringResource(R.string.note_action_move),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        FolderPickerRow(
            icon = Lucide.FolderX,
            label = stringResource(R.string.folder_picker_none),
            onClick = { onPick(null); onDismiss() },
        )
        folders.forEach { folder ->
            FolderPickerRow(
                icon = Lucide.FolderIcon,
                label = folder.name,
                onClick = { onPick(folder.id); onDismiss() },
                onDelete = { pendingDelete = folder },
            )
        }
    }

    pendingDelete?.let { folder ->
        TesseraDialog(
            onDismissRequest = { pendingDelete = null },
            title = stringResource(R.string.folders_delete_confirm_title),
            text = stringResource(R.string.folders_delete_confirm_message, folder.name),
            confirmLabel = stringResource(R.string.folders_delete),
            onConfirm = {
                onDeleteFolder(folder.id)
                pendingDelete = null
            },
            dismissLabel = stringResource(R.string.folders_cancel),
            destructive = true,
        )
    }
}

@Composable
private fun FolderPickerRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Lucide.Trash2,
                        contentDescription = stringResource(R.string.folders_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
