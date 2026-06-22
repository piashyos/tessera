package app.tessera.ui.notes.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.tessera.TesseraDrawerItem
import app.tessera.core.ui.tessera.TesseraDrawerShell
import app.tessera.core.ui.theme.TesseraSpacing
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderTreeNode
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Trash2

@Composable
fun TesseraNotesDrawer(
  drawerState: DrawerState,
  folderTree: List<FolderTreeNode>,
  selectedFolderId: FolderId?,
  selectedSystemKey: String,
  onSystemSelect: (String) -> Unit,
  onFolderSelect: (FolderId?) -> Unit,
  onCreateRootFolder: () -> Unit,
  onCreateSubfolder: (FolderId) -> Unit,
  onSettingsClick: () -> Unit,
  content: @Composable () -> Unit,
) {
  TesseraDrawerShell(
    drawerState = drawerState,
    header = { Text("Tessera") },
    content = {
      TesseraDrawerItem(
        label = "All notes",
        selected = selectedSystemKey == "all" && selectedFolderId == null,
        onClick = { onSystemSelect("all"); onFolderSelect(null) },
        leadingIcon = { Icon(Lucide.Inbox, contentDescription = null) },
      )
      TesseraDrawerItem(
        label = "Pinned",
        selected = selectedSystemKey == "pinned",
        onClick = { onSystemSelect("pinned") },
        leadingIcon = { Icon(Lucide.Pin, contentDescription = null) },
      )
      TesseraDrawerItem(
        label = "Archive",
        selected = selectedSystemKey == "archive",
        onClick = { onSystemSelect("archive") },
        leadingIcon = { Icon(Lucide.Archive, contentDescription = null) },
      )
      TesseraDrawerItem(
        label = "Trash",
        selected = selectedSystemKey == "trash",
        onClick = { onSystemSelect("trash") },
        leadingIcon = { Icon(Lucide.Trash2, contentDescription = null) },
      )
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(
            top = TesseraSpacing.xl,
            start = TesseraSpacing.lg,
            end = TesseraSpacing.sm,
            bottom = TesseraSpacing.xs,
          ),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "FOLDERS",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCreateRootFolder, modifier = Modifier.size(36.dp)) {
          Icon(Lucide.Plus, contentDescription = "Create folder")
        }
      }
      folderTree.forEach { node ->
        TesseraDrawerItem(
          label = node.folder.name,
          selected = selectedFolderId == node.folder.id,
          onClick = { onFolderSelect(node.folder.id) },
          leadingIcon = { Icon(Lucide.Folder, contentDescription = null) },
          trailing = {
            IconButton(onClick = { onCreateSubfolder(node.folder.id) }) {
              Icon(Lucide.Plus, contentDescription = "Create subfolder")
            }
          },
          indentLevel = 0,
        )
        node.children.forEach { child ->
          TesseraDrawerItem(
            label = child.name,
            selected = selectedFolderId == child.id,
            onClick = { onFolderSelect(child.id) },
            leadingIcon = { Icon(Lucide.Folder, contentDescription = null) },
            indentLevel = 1,
          )
        }
      }
      Spacer(modifier = Modifier.padding(TesseraSpacing.xs))
    },
    footer = {
      TesseraDrawerItem(
        label = "Settings",
        selected = false,
        onClick = onSettingsClick,
        leadingIcon = { Icon(Lucide.Settings, contentDescription = null) },
      )
    },
    screenContent = content,
  )
}
