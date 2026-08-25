package app.tessera.ui.editor.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import app.tessera.R
import com.composables.icons.lucide.Heading1
import com.composables.icons.lucide.Heading2
import com.composables.icons.lucide.Heading3
import com.composables.icons.lucide.Heading4
import com.composables.icons.lucide.Heading5
import com.composables.icons.lucide.Heading6
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pilcrow
import org.wordpress.aztec.AztecTextFormat

data class FormatEntry(
    /**
     * Aztec format this entry corresponds to. Used to highlight the row when
     * the cursor's active formats include it. May be `null` for entries that
     * are not toggles (e.g. "Insert table"), which never appear active.
     */
    val format: AztecTextFormat?,
    val icon: ImageVector,
    val labelRes: Int,
    val onClick: () -> Unit,
)

/**
 * Block-style picker (paragraph + headings) surfaced from the format bar's overflow button.
 * Built on the Material 3 [DropdownMenu] so it inherits Material You positioning (anchored to
 * the button, auto-flipping above the keyboard), the tonal `surfaceContainer` container, the
 * standard menu shape/elevation and item ripple — the previous hand-rolled translucent popup
 * mis-anchored to the screen corner and let the note text bleed through.
 */
@Composable
fun MarkdownFormatMenu(
    expanded: Boolean,
    activeFormats: Set<AztecTextFormat>,
    actions: RichBodyActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = listOf(
        FormatEntry(AztecTextFormat.FORMAT_PARAGRAPH, Lucide.Pilcrow, R.string.toolbar_paragraph, actions::toggleParagraph),
        FormatEntry(AztecTextFormat.FORMAT_HEADING_1, Lucide.Heading1, R.string.toolbar_h1, actions::toggleHeading1),
        FormatEntry(AztecTextFormat.FORMAT_HEADING_2, Lucide.Heading2, R.string.toolbar_h2, actions::toggleHeading2),
        FormatEntry(AztecTextFormat.FORMAT_HEADING_3, Lucide.Heading3, R.string.toolbar_h3, actions::toggleHeading3),
        FormatEntry(AztecTextFormat.FORMAT_HEADING_4, Lucide.Heading4, R.string.toolbar_h4, actions::toggleHeading4),
        FormatEntry(AztecTextFormat.FORMAT_HEADING_5, Lucide.Heading5, R.string.toolbar_h5, actions::toggleHeading5),
        FormatEntry(AztecTextFormat.FORMAT_HEADING_6, Lucide.Heading6, R.string.toolbar_h6, actions::toggleHeading6),
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        // Cap the height so the 7-row menu fits in the space above the IME-anchored format bar
        // and anchors directly to the overflow button. Left uncapped it is taller than the room
        // above the keyboard, so the menu clamps to the top of the screen, leaving a large gap
        // between it and the button. Overflow scrolls inside the menu.
        modifier = modifier.heightIn(max = 304.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        // Non-focusable popup: a focusable menu would grab window focus from the AztecText,
        // which hides the soft keyboard. The IME-anchored format bar then collapses with the
        // keyboard, dismissing the menu — and the keyboard reopens once focus returns. Keeping
        // the popup non-focusable leaves the editor focused and the keyboard up; outside taps
        // still dismiss via dismissOnClickOutside.
        properties = PopupProperties(focusable = false),
    ) {
        entries.forEach { entry ->
            val active = entry.format != null && entry.format in activeFormats
            val accent = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            val labelColor = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(entry.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                onClick = {
                    entry.onClick()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                colors = MenuDefaults.itemColors(
                    textColor = labelColor,
                    leadingIconColor = accent,
                ),
            )
        }
    }
}
