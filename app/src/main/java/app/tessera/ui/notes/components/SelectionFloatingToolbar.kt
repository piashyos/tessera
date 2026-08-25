package app.tessera.ui.notes.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tessera.R

/**
 * Floating contextual toolbar shown while notes are selected — batch actions live here, in the
 * thumb zone, instead of the top-right corner (M3 Expressive floating-toolbar pattern). In the
 * Trash view the delete is permanent and Move is hidden.
 */
@Composable
fun SelectionFloatingToolbar(
    isTrashView: Boolean,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 3.dp,
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!isTrashView) {
                IconButton(onClick = onMove) {
                    Icon(
                        Icons.Rounded.DriveFileMove,
                        contentDescription = stringResource(R.string.note_action_move),
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.note_action_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
