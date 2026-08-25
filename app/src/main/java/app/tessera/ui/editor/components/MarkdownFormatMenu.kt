package app.tessera.ui.editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tessera.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TableChart
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

/** Full block-format catalogue surfaced by the ⋯ panel: paragraph, headings, quote/code/insert. */
fun formatMenuEntries(actions: RichBodyActions): List<FormatEntry> = listOf(
    FormatEntry(AztecTextFormat.FORMAT_PARAGRAPH, Lucide.Pilcrow, R.string.toolbar_paragraph, actions::toggleParagraph),
    FormatEntry(AztecTextFormat.FORMAT_HEADING_1, Lucide.Heading1, R.string.toolbar_h1, actions::toggleHeading1),
    FormatEntry(AztecTextFormat.FORMAT_HEADING_2, Lucide.Heading2, R.string.toolbar_h2, actions::toggleHeading2),
    FormatEntry(AztecTextFormat.FORMAT_HEADING_3, Lucide.Heading3, R.string.toolbar_h3, actions::toggleHeading3),
    FormatEntry(AztecTextFormat.FORMAT_HEADING_4, Lucide.Heading4, R.string.toolbar_h4, actions::toggleHeading4),
    FormatEntry(AztecTextFormat.FORMAT_HEADING_5, Lucide.Heading5, R.string.toolbar_h5, actions::toggleHeading5),
    FormatEntry(AztecTextFormat.FORMAT_HEADING_6, Lucide.Heading6, R.string.toolbar_h6, actions::toggleHeading6),
    FormatEntry(AztecTextFormat.FORMAT_QUOTE, Icons.Rounded.FormatQuote, R.string.toolbar_quote, actions::toggleQuote),
    FormatEntry(AztecTextFormat.FORMAT_PREFORMAT, Icons.Rounded.Code, R.string.toolbar_code, actions::toggleCode),
    FormatEntry(null, Icons.Rounded.Remove, R.string.toolbar_divider, actions::insertHorizontalRule),
    FormatEntry(null, Icons.Rounded.TableChart, R.string.toolbar_table, actions::insertTable),
)

/**
 * Block-format panel expanded from the format bar's ⋯ button. Rendered INLINE directly above
 * the bar (same bottom-anchored column), not as a popup — a `DropdownMenu` above an IME-anchored
 * anchor clamps against the window top when space is tight, leaving an ugly gap between the menu
 * and the bar. Being inline also means it never steals focus, so the keyboard stays up.
 */
@Composable
fun MarkdownFormatMenu(
    expanded: Boolean,
    activeFormats: Set<AztecTextFormat>,
    actions: RichBodyActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(expandFrom = androidx.compose.ui.Alignment.Bottom) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = androidx.compose.ui.Alignment.Bottom) + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                formatMenuEntries(actions).forEach { entry ->
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
