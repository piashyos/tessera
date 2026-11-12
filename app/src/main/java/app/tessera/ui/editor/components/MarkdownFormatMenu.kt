package app.tessera.ui.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.core.ui.components.TesseraPopup
import app.tessera.core.ui.theme.TesseraSpacing
import app.tessera.ui.editor.engine.FormatState
import app.tessera.ui.editor.engine.ParagraphFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FormatQuote
import com.composables.icons.lucide.Heading1
import com.composables.icons.lucide.Heading2
import com.composables.icons.lucide.Heading3
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pilcrow
import dev.chrisbanes.haze.HazeState

data class FormatEntry(
    val icon: ImageVector,
    val labelRes: Int,
    val onClick: () -> Unit,
    /** The paragraph kind this entry sets; the row is active when the caret paragraph is of it. */
    val kind: ParagraphKind,
)

/** The ⋯ catalogue (spec §7): the paragraph kinds the engine has; the divider lives on the bar. */
fun formatMenuEntries(actions: RichBodyActions): List<FormatEntry> = listOf(
    FormatEntry(Lucide.Heading1, R.string.editor_style_title, actions::toggleHeading1, ParagraphKind.Title),
    FormatEntry(Lucide.Heading2, R.string.editor_style_heading, actions::toggleHeading2, ParagraphKind.Heading),
    FormatEntry(Lucide.Heading3, R.string.editor_style_subheading, actions::toggleSubheading, ParagraphKind.Subheading),
    FormatEntry(Lucide.Pilcrow, R.string.editor_style_body, actions::toggleParagraph, ParagraphKind.Body),
    FormatEntry(Icons.Rounded.Code, R.string.editor_style_mono, actions::toggleCode, ParagraphKind.Mono),
    FormatEntry(Icons.Rounded.FormatQuote, R.string.toolbar_quote, actions::toggleQuote, ParagraphKind.Quote),
)

/**
 * The ⋯ block-format sheet: a modal like the search filters (blurred, dimmed backdrop over the whole screen, a card above the format bar).
 * The bar itself is drawn after it, so the ⋯ button stays reachable and closes the sheet. [bottomInset] is the bar with its margins: the card
 * stands over it. The keyboard stays (`clearFocusOnDismiss = false`): choosing a row changes the paragraph of the caret.
 */
@Composable
fun MarkdownFormatMenu(
    expanded: Boolean,
    actions: RichBodyActions,
    onDismiss: () -> Unit,
    hazeState: HazeState,
    bottomInset: Dp,
    formatState: FormatState = FormatState.Empty,
) {
    TesseraPopup(
        visible = expanded,
        onDismiss = onDismiss,
        hazeState = hazeState,
        bottomInset = bottomInset,
        clearFocusOnDismiss = false,
    ) {
        Text(
            text = stringResource(R.string.editor_style_menu_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        formatMenuEntries(actions).forEach { entry ->
            val active = formatState.paragraph == ParagraphFormat.Single(entry.kind)
            Surface(
                onClick = { entry.onClick(); onDismiss() },
                color = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().semantics { selected = active },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = TesseraSpacing.md, vertical = TesseraSpacing.md),
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(entry.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
