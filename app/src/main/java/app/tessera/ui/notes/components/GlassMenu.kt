package app.tessera.ui.notes.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.FloatingShadowAmbient
import app.tessera.core.ui.theme.FloatingShadowSpot
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/** Visual style shared by both the note long-press menu and the folder ⋮ menu. */
data class GlassMenuAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Rounded blur-tinted floating surface used for in-window context menus.
 * Caller supplies a [HazeState] from the surrounding screen — the menu's
 * background blurs whatever content is currently behind it.
 */
@Composable
fun GlassMenuCard(
    actions: List<GlassMenuAction>,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    Surface(
        modifier = modifier
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = FloatingShadowAmbient,
                spotColor = FloatingShadowSpot,
            )
            .clip(RoundedCornerShape(20.dp))
            .hazeEffect(state = hazeState) {
                backgroundColor = surfaceColor.copy(alpha = 0.86f)
                blurRadius = 28.dp
            },
        color = surfaceColor.copy(alpha = 0.0f),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            actions.forEach { action ->
                GlassMenuRow(
                    icon = action.icon,
                    label = action.label,
                    destructive = action.destructive,
                    onClick = action.onClick,
                )
            }
        }
    }
}

@Composable
private fun GlassMenuRow(
    icon: ImageVector,
    label: String,
    destructive: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .width(220.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = tint)
        }
    }
}
