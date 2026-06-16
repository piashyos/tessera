package app.tessera.ui.editor.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.tessera.R
import app.tessera.core.ui.theme.FloatingShadowAmbient
import app.tessera.core.ui.theme.FloatingShadowSpot
import com.composables.icons.lucide.Bold
import com.composables.icons.lucide.Heading1
import com.composables.icons.lucide.Heading2
import com.composables.icons.lucide.Heading3
import com.composables.icons.lucide.Heading4
import com.composables.icons.lucide.Heading5
import com.composables.icons.lucide.Heading6
import com.composables.icons.lucide.Italic
import com.composables.icons.lucide.List
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pilcrow
import com.composables.icons.lucide.Strikethrough
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import org.wordpress.aztec.AztecTextFormat

data class FormatEntry(
    val format: AztecTextFormat,
    val icon: ImageVector,
    val labelRes: Int,
    val onClick: () -> Unit,
)

@Composable
fun MarkdownFormatMenu(
    visible: Boolean,
    activeFormats: Set<AztecTextFormat>,
    actions: RichBodyActions,
    onDismiss: () -> Unit,
    hazeState: HazeState,
) {
    if (!visible) return

    val surfaceColor = MaterialTheme.colorScheme.surface
    val entries = listOf(
        FormatEntry(AztecTextFormat.FORMAT_PARAGRAPH, Lucide.Pilcrow, R.string.toolbar_paragraph, actions::toggleParagraph),
        FormatEntry(AztecTextFormat.FORMAT_HEADING_1, Lucide.Heading1, R.string.toolbar_h1, actions::toggleHeading1),
        FormatEntry(AztecTextFormat.FORMAT_HEADING_2, Lucide.Heading2, R.string.toolbar_h2, actions::toggleHeading2),
        FormatEntry(AztecTextFormat.FORMAT_HEADING_3, Lucide.Heading3, R.string.toolbar_h3, actions::toggleHeading3),
        FormatEntry(AztecTextFormat.FORMAT_HEADING_4, Lucide.Heading4, R.string.toolbar_h4, actions::toggleHeading4),
        FormatEntry(AztecTextFormat.FORMAT_HEADING_5, Lucide.Heading5, R.string.toolbar_h5, actions::toggleHeading5),
        FormatEntry(AztecTextFormat.FORMAT_HEADING_6, Lucide.Heading6, R.string.toolbar_h6, actions::toggleHeading6),
        FormatEntry(AztecTextFormat.FORMAT_BOLD, Lucide.Bold, R.string.toolbar_bold, actions::toggleBold),
        FormatEntry(AztecTextFormat.FORMAT_ITALIC, Lucide.Italic, R.string.toolbar_italic, actions::toggleItalic),
        FormatEntry(AztecTextFormat.FORMAT_STRIKETHROUGH, Lucide.Strikethrough, R.string.toolbar_strikethrough, actions::toggleStrikethrough),
        FormatEntry(AztecTextFormat.FORMAT_UNORDERED_LIST, Lucide.List, R.string.toolbar_bullet, actions::toggleBulletList),
        FormatEntry(AztecTextFormat.FORMAT_TASK_LIST, Lucide.ListChecks, R.string.toolbar_checklist, actions::toggleTaskList),
    )

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
        popupPositionProvider = aboveAnchorPositionProvider(),
    ) {
        Surface(
            modifier = Modifier
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = FloatingShadowAmbient,
                    spotColor = FloatingShadowSpot,
                )
                .clip(RoundedCornerShape(20.dp))
                .hazeEffect(state = hazeState) {
                    backgroundColor = surfaceColor.copy(alpha = 0.82f)
                    blurRadius = 24.dp
                },
            color = surfaceColor.copy(alpha = 0.0f),
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                entries.forEach { entry ->
                    FormatRow(entry = entry, active = entry.format in activeFormats) {
                        entry.onClick()
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatRow(entry: FormatEntry, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(entry.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

private fun aboveAnchorPositionProvider() = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // Anchor to the trigger button: bottom-left of the popup sits 8dp above the
        // top of the anchor, horizontally aligned to the anchor's left edge.
        val gapPx = 24 // ~8dp on mdpi; tighten if needed
        val x = anchorBounds.left.coerceAtMost(windowSize.width - popupContentSize.width)
        val y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
