package app.tessera.ui.notes.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.tessera.R
import com.composables.icons.lucide.FolderInput
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.PinOff
import com.composables.icons.lucide.Trash2
import dev.chrisbanes.haze.HazeState

/**
 * Long-press context menu for note cards. Rendered as an in-window overlay so the
 * shared [GlassMenuCard] can blur the underlying notes list.
 */
@Composable
fun NoteContextMenu(
    visible: Boolean,
    anchor: IntOffset?,
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    hazeState: HazeState,
) {
    if (!visible || anchor == null) return
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val marginPx = with(density) { 8.dp.toPx() }
    val placedX = anchor.x.coerceAtMost(
        (screenWidthPx - menuSize.width - marginPx).toInt().coerceAtLeast(marginPx.toInt()),
    )
    val placedY = anchor.y.coerceAtMost(
        (screenHeightPx - menuSize.height - marginPx).toInt().coerceAtLeast(marginPx.toInt()),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    )

    GlassMenuCard(
        actions = listOf(
            GlassMenuAction(
                icon = if (isPinned) Lucide.PinOff else Lucide.Pin,
                label = stringResource(
                    if (isPinned) R.string.note_action_unpin else R.string.note_action_pin,
                ),
                onClick = { onTogglePin(); onDismiss() },
            ),
            GlassMenuAction(
                icon = Lucide.FolderInput,
                label = stringResource(R.string.note_action_move),
                onClick = onMove,
            ),
            GlassMenuAction(
                icon = Lucide.Trash2,
                label = stringResource(R.string.note_action_delete),
                destructive = true,
                onClick = { onDelete(); onDismiss() },
            ),
        ),
        hazeState = hazeState,
        modifier = Modifier
            .offset { IntOffset(placedX, placedY) }
            .onSizeChanged { menuSize = it },
    )
}
