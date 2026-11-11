package app.tessera.ui.editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
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
import app.tessera.core.ui.theme.TesseraMotion
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
    // The Popup stays mounted until the exit has played out, so closing is animated too.
    val visibility = remember { MutableTransitionState(false) }
    // The room above the bar (set once the popup has been positioned) is known before the panel starts to appear,
    // so its entry is played in view and not under a window that is still being placed.
    var roomAbovePx by remember { mutableIntStateOf(Int.MAX_VALUE) }
    visibility.targetState = expanded && roomAbovePx != Int.MAX_VALUE
    if (!expanded && visibility.isIdle && !visibility.currentState) {
        roomAbovePx = Int.MAX_VALUE
        return
    }
    val density = LocalDensity.current
    val gapPx = with(density) { 8.dp.roundToPx() }
    // Full-width like the bottom toolbar: same 16dp side margins as the bar's Column.
    val sideMarginDp = 16.dp
    val marginPx = with(density) { sideMarginDp.roundToPx() }
    val menuWidth = (LocalConfiguration.current.screenWidthDp.dp - sideMarginDp * 2)
    // The popup window clips what it draws outside itself, shadow included: the shadow gets its own room inside
    // the window. The bottom room stays under the 8dp gap — the window takes every touch inside its bounds,
    // and a deeper one would cover the ⋯ button.
    val shadowRoomPx = with(density) { ShadowRoom.roundToPx() }
    val shadowRoomBottomPx = with(density) { ShadowRoomBottom.roundToPx() }
    // The room between the status bar and the bar the panel sits on (with the keyboard up it is less than the
    // panel's natural height): the panel's list scrolls inside it instead of being pushed down over the bar.
    val topSafePx = WindowInsets.statusBars.getTop(density) + gapPx
    val positionProvider = remember(gapPx, marginPx, shadowRoomPx, shadowRoomBottomPx, topSafePx) {
        AboveBarPositionProvider(gapPx, marginPx, shadowRoomPx, shadowRoomBottomPx) { barTop ->
            roomAbovePx = barTop - gapPx - topSafePx - shadowRoomPx - shadowRoomBottomPx
        }
    }
    // never less than three rows: where there is hardly any room (landscape with the keyboard up) an empty panel is worse than an overlap
    val listMax = minOf(320.dp, maxOf(MinListHeight, with(density) { roomAbovePx.coerceAtLeast(0).toDp() }))
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        // Only transforms and alpha move: the popup's size never changes, so the window is not re-laid-out
        // per frame. It grows out of the bottom-end corner, where the ⋯ button that opened it sits.
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(TesseraMotion.snappySpring()) +
                scaleIn(TesseraMotion.snappySpring(), initialScale = 0.85f, transformOrigin = FromButton),
            exit = fadeOut(TesseraMotion.snappySpring()) +
                scaleOut(TesseraMotion.snappySpring(), targetScale = 0.85f, transformOrigin = FromButton),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .padding(start = ShadowRoom, end = ShadowRoom, top = ShadowRoom, bottom = ShadowRoomBottom)
                    .width(menuWidth),
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = listMax)
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

private val ShadowRoom = 12.dp
private val MinListHeight = 144.dp
private val ShadowRoomBottom = 6.dp
private val FromButton = TransformOrigin(1f, 1f)

/**
 * Positions the ⋯ panel just above its anchor (the toolbar pill), inset [marginPx] from the left
 * edge so it spans the same width as the bottom bar.
 */
private class AboveBarPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int,
    private val shadowRoomPx: Int,
    private val shadowRoomBottomPx: Int,
    private val onBarTop: (Int) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        onBarTop(anchorBounds.top)
        // The panel itself (not its shadow room) sits marginPx from the left and gapPx above the bar.
        val y = (anchorBounds.top - popupContentSize.height - gapPx + shadowRoomBottomPx).coerceAtLeast(0)
        return IntOffset(marginPx - shadowRoomPx, y)
    }
}
