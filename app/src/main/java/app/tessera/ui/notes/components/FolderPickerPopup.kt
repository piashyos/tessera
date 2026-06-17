package app.tessera.ui.notes.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.theme.FloatingShadowAmbient
import app.tessera.core.ui.theme.FloatingShadowSpot
import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import com.composables.icons.lucide.Folder as FolderIcon
import com.composables.icons.lucide.FolderX
import com.composables.icons.lucide.Lucide
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * Compact folder picker. Rendered as an in-window centered overlay so
 * [hazeEffect] can blur the underlying note list (a separate Popup window
 * would not be reachable by the haze source).
 */
@Composable
fun FolderPickerPopup(
    visible: Boolean,
    folders: List<Folder>,
    onPick: (FolderId?) -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState,
) {
    if (!visible) return
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = FloatingShadowAmbient,
                    spotColor = FloatingShadowSpot,
                )
                .clip(RoundedCornerShape(24.dp))
                .hazeEffect(state = hazeState) {
                    backgroundColor = surfaceColor.copy(alpha = 0.86f)
                    blurRadius = 28.dp
                },
            color = surfaceColor.copy(alpha = 0.0f),
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                PickerRow(
                    icon = Lucide.FolderX,
                    label = stringResource(R.string.folder_picker_none),
                    onClick = { onPick(null); onDismiss() },
                )
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(folders, key = { it.id.value }) { folder ->
                        PickerRow(
                            icon = Lucide.FolderIcon,
                            label = folder.name,
                            onClick = { onPick(folder.id); onDismiss() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .width(240.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
