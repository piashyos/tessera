package app.tessera.ui.editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import app.tessera.core.text.notedoc.ParagraphKind
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
 * Block-format panel expanded from the format bar's ⋯ button, shown as an OVERLAY [Popup] anchored
 * directly above the toolbar pill (right-aligned to it, 8dp apart).
 *
 * Why a Popup and not an inline sibling: rendered inline in the Scaffold's `bottomBar` slot it grew
 * the bar's measured height, so Scaffold shrank the content inset and the editor body above it was
 * re-measured/compressed every time the panel opened. As a Popup it doesn't participate in that
 * layout, so nothing above it moves. [PopupProperties.focusable] = false keeps the IME up (the panel
 * never steals focus) and a custom [PopupPositionProvider] pins it to the bar instead of letting a
 * `DropdownMenu` clamp against the window top and leave a gap.
 */
@Composable
fun MarkdownFormatMenu(
    expanded: Boolean,
    actions: RichBodyActions,
    onDismiss: () -> Unit,
    formatState: FormatState = FormatState.Empty,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val gapPx = with(density) { 8.dp.roundToPx() }
    // Full-width like the bottom toolbar: same 16dp side margins as the bar's Column.
    val sideMarginDp = 16.dp
    val marginPx = with(density) { sideMarginDp.roundToPx() }
    val menuWidth = (LocalConfiguration.current.screenWidthDp.dp - sideMarginDp * 2)
    val positionProvider = remember(gapPx, marginPx) { AboveBarPositionProvider(gapPx, marginPx) }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        // Animate the panel in on mount (the Popup unmounts on collapse, so exit is instant).
        val transition = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = transition,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 3.dp,
                modifier = Modifier.width(menuWidth),
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    formatMenuEntries(actions).forEach { entry ->
                        val active = formatState.paragraph == ParagraphFormat.Single(entry.kind)
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
}

/**
 * Positions the ⋯ panel just above its anchor (the toolbar pill), inset [marginPx] from the left
 * edge so it spans the same width as the bottom bar.
 */
private class AboveBarPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        return IntOffset(marginPx, y)
    }
}
