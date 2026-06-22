package app.tessera.ui.notes.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Folder as FolderIcon
import com.composables.icons.lucide.FolderPlus
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Sliding folder drawer with an opaque surface — the caller animates this composable
 * in/out from the leading edge.
 */
@Composable
fun FoldersDrawer(
    folders: List<Folder>,
    selectedFolderId: FolderId?,
    hazeState: HazeState,
    onSelectAll: () -> Unit,
    onSelectFolder: (FolderId) -> Unit,
    onCreateFolder: () -> Unit,
    onRenameFolder: (Folder) -> Unit,
    onDeleteFolder: (Folder) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.folders_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        )

        DrawerRow(
            label = stringResource(R.string.folders_all_notes),
            leading = { Icon(Lucide.Inbox, contentDescription = null) },
            selected = selectedFolderId == null,
            onClick = onSelectAll,
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(folders, key = { it.id.value }) { folder ->
                FolderRowWithMenu(
                    folder = folder,
                    selected = selectedFolderId == folder.id,
                    hazeState = hazeState,
                    onSelect = { onSelectFolder(folder.id) },
                    onRename = { onRenameFolder(folder) },
                    onDelete = { onDeleteFolder(folder) },
                )
            }
        }

        Spacer(modifier = Modifier.padding(4.dp))

        DrawerRow(
            label = stringResource(R.string.folders_new),
            leading = { Icon(Lucide.FolderPlus, contentDescription = null) },
            selected = false,
            onClick = onCreateFolder,
        )
    }
}

@Composable
private fun FolderRowWithMenu(
    folder: Folder,
    selected: Boolean,
    hazeState: HazeState,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    DrawerRow(
        label = folder.name,
        leading = { Icon(Lucide.FolderIcon, contentDescription = null) },
        selected = selected,
        onClick = onSelect,
        trailing = {
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Lucide.EllipsisVertical,
                        contentDescription = stringResource(R.string.folders_rename),
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (menuExpanded) {
                    // Same glass chrome as the note long-press menu so both context
                    // menus feel like a single component family.
                    Popup(
                        onDismissRequest = { menuExpanded = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Box(modifier = Modifier.padding(8.dp)) {
                            GlassMenuCard(
                                actions = listOf(
                                    GlassMenuAction(
                                        icon = Lucide.Pencil,
                                        label = stringResource(R.string.folders_rename),
                                        onClick = {
                                            menuExpanded = false
                                            onRename()
                                        },
                                    ),
                                    GlassMenuAction(
                                        icon = Lucide.Trash2,
                                        label = stringResource(R.string.folders_delete),
                                        destructive = true,
                                        onClick = {
                                            menuExpanded = false
                                            onDelete()
                                        },
                                    ),
                                ),
                                hazeState = hazeState,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun DrawerRow(
    label: String,
    leading: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val rowColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        Color.Transparent
    }
    Surface(
        color = rowColor,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading()
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
    }
}
