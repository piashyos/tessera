package app.tessera.ui.notes.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.tessera.EntityIconView
import app.tessera.core.ui.tessera.TesseraDrawerItem
import app.tessera.core.ui.tessera.TesseraDrawerShell
import app.tessera.core.ui.tessera.chipColors
import app.tessera.core.ui.tessera.parseEntityIcon
import app.tessera.core.ui.theme.AccentColor
import app.tessera.core.ui.theme.TesseraSpacing
import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderTreeNode
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings

@Composable
fun TesseraNotesDrawer(
  drawerState: DrawerState,
  folderTree: List<FolderTreeNode>,
  selectedFolderId: FolderId?,
  selectedSystemKey: String,
  showSharedSection: Boolean,
  onSystemSelect: (String) -> Unit,
  onFolderSelect: (FolderId?) -> Unit,
  onCreateRootFolder: () -> Unit,
  onCreateSubfolder: (FolderId) -> Unit,
  onDeleteFolder: (FolderId) -> Unit,
  onSettingsClick: () -> Unit,
  onEditFolder: (FolderId) -> Unit = {},
  content: @Composable () -> Unit,
) {
  TesseraDrawerShell(
    drawerState = drawerState,
    header = { Text(stringResource(R.string.app_name)) },
    content = {
      TesseraDrawerItem(
        label = stringResource(R.string.folders_all_notes),
        selected = selectedSystemKey == "all" && selectedFolderId == null,
        onClick = { onSystemSelect("all"); onFolderSelect(null) },
        leadingIcon = { Icon(Icons.Rounded.Inbox, contentDescription = null) },
      )
      TesseraDrawerItem(
        label = stringResource(R.string.drawer_pinned),
        selected = selectedSystemKey == "pinned",
        onClick = { onSystemSelect("pinned") },
        leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
      )
      TesseraDrawerItem(
        label = stringResource(R.string.notes_archive),
        selected = selectedSystemKey == "archive",
        onClick = { onSystemSelect("archive") },
        leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
      )
      TesseraDrawerItem(
        label = stringResource(R.string.drawer_trash),
        selected = selectedSystemKey == "trash",
        onClick = { onSystemSelect("trash") },
        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
      )
      if (showSharedSection) {
        TesseraDrawerItem(
          label = stringResource(R.string.drawer_shared),
          selected = selectedSystemKey == "shared",
          onClick = { onSystemSelect("shared") },
          leadingIcon = { Icon(Icons.Rounded.Group, contentDescription = null) },
        )
      }
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
          text = stringResource(R.string.drawer_folders_section),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCreateRootFolder) {
          Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.folders_create))
        }
      }
      folderTree.forEach { node ->
        DrawerFolderRow(
          folder = node.folder,
          effectiveColor = node.folder.color,
          selected = selectedFolderId == node.folder.id,
          indentLevel = 0,
          onClick = { onFolderSelect(node.folder.id) },
          onEdit = { onEditFolder(node.folder.id) },
          onAddSubfolder = { onCreateSubfolder(node.folder.id) },
          onDelete = { onDeleteFolder(node.folder.id) },
        )
        node.children.forEach { child ->
          DrawerFolderRow(
            folder = child,
            // A subfolder without a colour of its own takes its parent's.
            effectiveColor = child.color ?: node.folder.color,
            selected = selectedFolderId == child.id,
            indentLevel = 1,
            onClick = { onFolderSelect(child.id) },
            onEdit = { onEditFolder(child.id) },
            onAddSubfolder = null,
            onDelete = { onDeleteFolder(child.id) },
          )
        }
      }
      Spacer(modifier = Modifier.padding(TesseraSpacing.xs))
    },
    footer = {
      TesseraDrawerItem(
        label = stringResource(R.string.settings_title),
        selected = false,
        onClick = onSettingsClick,
        leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
      )
    },
    screenContent = content,
  )
}

@Composable
private fun DrawerFolderRow(
  folder: Folder,
  effectiveColor: String?,
  selected: Boolean,
  indentLevel: Int,
  onClick: () -> Unit,
  onEdit: () -> Unit,
  onAddSubfolder: (() -> Unit)?,
  onDelete: () -> Unit,
) {
  val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
  val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                     else MaterialTheme.colorScheme.onSurface
  // The folder's colour tints its icon only; the name keeps the row's text colour.
  val iconColor = AccentColor.fromKey(effectiveColor)?.chipColors()?.content ?: contentColor
  val icon = parseEntityIcon(folder.icon)
  var menuOpen by remember { mutableStateOf(false) }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = (indentLevel * 20).dp)
      .clip(RoundedCornerShape(24.dp))
      .background(background)
      .clickable(onClick = onClick)
      .padding(start = TesseraSpacing.md, end = TesseraSpacing.xs),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.md),
  ) {
    if (icon != null) {
      EntityIconView(icon, tint = iconColor, size = 18.dp)
    } else {
      Icon(
        Icons.Rounded.Folder,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = iconColor,
      )
    }
    Text(
      text = folder.name,
      style = MaterialTheme.typography.bodyMedium,
      color = contentColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .weight(1f)
        .padding(vertical = 10.dp),
    )
    // One "⋮" instead of the separate add / delete buttons: edit, new subfolder (roots only), delete.
    Box {
      DrawerFolderAction(
        icon = Icons.Rounded.MoreVert,
        contentDescription = stringResource(R.string.folders_more),
        tint = contentColor,
        onClick = { menuOpen = true },
      )
      DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
          text = { Text(stringResource(R.string.folders_edit)) },
          onClick = { menuOpen = false; onEdit() },
        )
        if (onAddSubfolder != null) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.folders_create_subfolder)) },
            onClick = { menuOpen = false; onAddSubfolder() },
          )
        }
        DropdownMenuItem(
          text = { Text(stringResource(R.string.folders_delete), color = MaterialTheme.colorScheme.error) },
          onClick = { menuOpen = false; onDelete() },
        )
      }
    }
  }
}

@Composable
private fun DrawerFolderAction(
  icon: ImageVector,
  contentDescription: String,
  tint: Color = LocalContentColor.current,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .size(44.dp)
      .clip(CircleShape)
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      modifier = Modifier.size(18.dp),
      tint = tint,
    )
  }
}
