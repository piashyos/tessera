package app.tessera.core.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.GlassBlurRadius
import app.tessera.core.ui.theme.TesseraMotion
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Modal card over a blurred backdrop, dismissed by the backdrop tap, Back or a swipe down.
 * [onDismiss] MUST turn [visible] to `false`: after a swipe the card is already off the screen and stays there until [visible] flips.
 */
@Composable
fun TesseraPopup(
    visible: Boolean,
    onDismiss: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    /** Room kept free under the card, above the keyboard / navigation bar (the editor's format bar): the card sits over it. */
    bottomInset: Dp = 0.dp,
    /** `false`: closing keeps the focus (and the keyboard) where it is. */
    clearFocusOnDismiss: Boolean = true,
    /** Buttons pinned under the scrolling content: they stay visible however tall the content is. */
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val sixtyPercent = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    val frostColor = MaterialTheme.colorScheme.surface
    val scrimColor = MaterialTheme.colorScheme.scrim
    val insets = WindowInsets.ime.union(WindowInsets.navigationBars)
    var closing by remember { mutableStateOf(false) }
    // While the card leaves its bottom padding and keyboardVisible stay as they were: the keyboard goes away on its own,
    // the card must not change size or padding half way. Kept only while the popup is open and not closing.
    val frozen = remember { FrozenInsets() }
    if (visible && !closing) {
        frozen.bottomPx = insets.getBottom(density)
        frozen.imeBottomPx = WindowInsets.ime.getBottom(density)
        frozen.keyboardVisible = frozen.imeBottomPx > WindowInsets.navigationBars.getBottom(density)
    }
    val keyboardVisible = frozen.keyboardVisible
    val cardBottomGap = if (keyboardVisible) 12.dp else 16.dp
    // With a bottom inset the card also stops under the status bar, but never gets shorter than three rows (landscape with the keyboard up).
    // A popup with a footer also stops above the keyboard: its buttons must not hide behind it.
    val maxCardHeight = if (bottomInset == 0.dp && (footer == null || !keyboardVisible)) {
        sixtyPercent
    } else {
        val belowCardPx = frozen.bottomPx + with(density) { (bottomInset + cardBottomGap).roundToPx() }
        sheetMaxHeight(LocalWindowInfo.current.containerSize.height, WindowInsets.statusBars.getTop(density), belowCardPx, sixtyPercent, density)
    }
    val dismiss: () -> Unit = {
        if (!closing) {
            if (clearFocusOnDismiss) focusManager.clearFocus(force = true)
            onDismiss()
        }
    }

    // Swipe-down state. offset is written synchronously from the gesture (no launch per event: the finger never lags a frame);
    // settleJob is the only thing that moves it outside a gesture, a new settle cancels the previous one.
    var offset by remember { mutableFloatStateOf(0f) }
    var cardHeight by remember { mutableIntStateOf(0) }
    var restTopPx by remember { mutableFloatStateOf(0f) } // the card top in the window at offset 0
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val windowInfo = LocalWindowInfo.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentVisible by rememberUpdatedState(visible)
    // The card has left the screen and the backdrop is clear: no exit animation, so nothing invisible keeps catching touches.
    var gone by remember { mutableStateOf(false) }
    val dismissDistancePx = with(density) { DismissDistance.toPx() }
    val flingPxPerSec = with(density) { FlingVelocity.toPx() }
    val progress = { if (cardHeight == 0) 1f else 1f - (offset / cardHeight).coerceIn(0f, 1f) }
    LaunchedEffect(visible) {
        if (visible) {
            settleJob?.cancel()
            offset = 0f
            closing = false
            gone = false
        } else if (!closing) {
            settleJob?.cancel() // the exit starts from where the card is, without a spring pulling it up meanwhile
        }
    }
    // Release of a swipe (finger or nested fling): leave with the release velocity or spring back.
    val settle: (Float) -> Unit = { velocity ->
        settleJob?.cancel()
        val thresholdPx = if (cardHeight > 0) min(dismissDistancePx, cardHeight / 2f) else dismissDistancePx
        if (releaseDismisses(offset, velocity, thresholdPx, flingPxPerSec)) {
            closing = true
            val windowHeight = windowInfo.containerSize.height.toFloat()
            // Past this offset the card top is below the window: it is gone, no need to wait for the tail of the spring.
            // The keyboard is a window of its own above the app: the card is hidden already under its top edge.
            val goneAtPx = windowHeight - (if (frozen.keyboardVisible) frozen.imeBottomPx else 0) - restTopPx
            // The target is beyond the threshold by a card height: the threshold is crossed at speed, not in the tail.
            val target = max(windowHeight, goneAtPx + cardHeight)
            settleJob = scope.launch {
                AnimationState(offset, max(velocity, 0f)).animateTo(target, TesseraMotion.smoothSpring()) {
                    offset = value
                    if (value >= goneAtPx) cancelAnimation()
                }
                gone = true
                // The focus goes only now: clearing it starts the keyboard's exit, and the stall that follows (the IME
                // rebuilds) would stop the main thread in the middle of the spring. Reopening cancels this job: the focus stays.
                if (clearFocusOnDismiss) focusManager.clearFocus(force = true)
                if (currentVisible) currentOnDismiss() // once per opening: the caller may have closed it meanwhile
            }
        } else {
            settleJob = scope.launch {
                AnimationState(offset, velocity).animateTo(0f, TesseraMotion.snappySpring()) {
                    if (currentVisible) offset = value.coerceAtLeast(0f) else cancelAnimation()
                }
            }
        }
    }
    val currentSettle by rememberUpdatedState(settle)
    // Bridge the inner scrollable's leftover downward scroll into the card offset so
    // dragging the card (or its content while already at the top) drags the whole card
    // down. Only the finger moves it: the inertia of the content never does.
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || closing || !currentVisible) return Offset.Zero
                // While the card is pulled down, consume upward scroll to push it back first.
                return if (available.y < 0f && offset > 0f) {
                    settleJob?.cancel()
                    val consumed = (-offset).coerceAtLeast(available.y)
                    offset = (offset + consumed).coerceAtLeast(0f)
                    Offset(0f, consumed)
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || closing || !currentVisible || available.y <= 0f) return Offset.Zero
                settleJob?.cancel()
                offset += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // Only the gesture that holds the card: a running settle means the card is not this fling's.
                if (closing || !currentVisible || offset <= 0f || settleJob?.isActive == true) return Velocity.Zero
                currentSettle(available.y)
                return Velocity(0f, available.y) // the vertical velocity is the card's: the content below does not scroll on
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(TesseraMotion.smoothSpring()),
        exit = if (gone) ExitTransition.None else fadeOut(TesseraMotion.smoothSpring()),
        modifier = modifier,
    ) {
        BackHandler(enabled = visible, onBack = dismiss) // while closing, `dismiss` does nothing: Back is swallowed

        Box(modifier = Modifier.fillMaxSize()) {
            // Blurred + dimmed backdrop, fading out as the card goes down; tap to dismiss
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeEffect(state = hazeState) {
                        backgroundColor = frostColor
                        blurEnabled = true
                        blurRadius = GlassBlurRadius
                        alpha = progress()
                    }
                    .testTag(UiTestTags.PopupScrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = dismiss,
                    ),
            )

            // The dimming is its own layer: its alpha follows the card without a recomposition.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress() }
                    .background(scrimColor.copy(alpha = 0.25f)),
            )

            // Bottom-anchored card; slides up while the scrim fades.
            // Sits above the keyboard (12dp gap) or, when the keyboard is hidden,
            // above the nav bar with a doubled 24dp gap.
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .animateEnterExit(
                        enter = slideInVertically(TesseraMotion.smoothSpring()) { it },
                        exit = if (gone) ExitTransition.None else slideOutVertically(TesseraMotion.smoothSpring()) { it },
                    )
                    .padding(bottom = with(density) { frozen.bottomPx.toDp() })
                    .consumeWindowInsets(insets)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = cardBottomGap + bottomInset)
                    .offset { IntOffset(0, offset.roundToInt()) }
                    .onGloballyPositioned {
                        cardHeight = it.size.height
                        if (!closing) restTopPx = it.positionInWindow().y - offset
                    }
                    .testTag(UiTestTags.PopupCard)
                    .nestedScroll(nestedScrollConnection)
                    // Direct drag handling for gestures that begin on the card chrome (the footer row, the padding)
                    // rather than the scrollable column.
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta -> offset = (offset + delta).coerceAtLeast(0f) },
                        enabled = visible && !closing,
                        onDragStarted = { settleJob?.cancel() },
                        onDragStopped = { velocity -> settle(velocity) },
                    )
                    // card taps must not reach the scrim
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                // The caret marker and selection handles are windows above the keyboard, and the focus (so the keyboard) stays
                // until the card is out: while it leaves, the marker would be drawn over the keyboard. Hide it, keep the highlight.
                val selectionColors = LocalTextSelectionColors.current
                CompositionLocalProvider(
                    LocalTextSelectionColors provides
                        if (closing) TextSelectionColors(handleColor = Color.Transparent, backgroundColor = selectionColors.backgroundColor) else selectionColors,
                ) {
                    if (footer == null) {
                        Column(
                            modifier = Modifier
                                .heightIn(max = maxCardHeight)
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            content = content,
                        )
                    } else {
                        Column(modifier = Modifier.heightIn(max = maxCardHeight)) {
                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState())
                                    .padding(start = 20.dp, top = 20.dp, end = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                content = content,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                content = footer,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Whether to close a card let go at [offsetPx] with [velocityPxPerSec] (down is positive): a fling decides, otherwise the distance. */
internal fun releaseDismisses(offsetPx: Float, velocityPxPerSec: Float, thresholdPx: Float, flingPxPerSec: Float): Boolean =
    when {
        velocityPxPerSec >= flingPxPerSec -> true
        velocityPxPerSec <= -flingPxPerSec -> false
        else -> offsetPx >= thresholdPx
    }

private val DismissDistance = 120.dp
private val FlingVelocity = 500.dp // per second

/** The bottom padding and keyboard flag of the last frame before the card started to leave. */
private class FrozenInsets(var bottomPx: Int = 0, var imeBottomPx: Int = 0, var keyboardVisible: Boolean = false)

/** The room between the status bar and the space kept under the card: at most [sixtyPercent], at least [MinCardHeight]. */
internal fun sheetMaxHeight(containerPx: Int, statusTopPx: Int, belowCardPx: Int, sixtyPercent: Dp, density: Density): Dp {
    val room = with(density) { (containerPx - statusTopPx - belowCardPx).coerceAtLeast(0).toDp() }
    return minOf(sixtyPercent, maxOf(MinCardHeight, room))
}

/** Where there is hardly any room (landscape with the keyboard up) an empty card is worse than one under the status bar. */
private val MinCardHeight = 144.dp
