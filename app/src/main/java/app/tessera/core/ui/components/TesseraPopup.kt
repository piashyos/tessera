package app.tessera.core.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.GlassBlurRadius
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TesseraPopup(
    visible: Boolean,
    onDismiss: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val maxCardHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    val frostColor = MaterialTheme.colorScheme.surface
    val keyboardVisible = WindowInsets.ime.getBottom(density) > WindowInsets.navigationBars.getBottom(density)
    val cardBottomGap = if (keyboardVisible) 12.dp else 16.dp
    val dismiss: () -> Unit = {
        focusManager.clearFocus(force = true)
        onDismiss()
    }
    // Kept current so the remembered NestedScrollConnection never invokes a stale callback.
    val currentDismiss = rememberUpdatedState(dismiss)

    // Swipe-down-to-dismiss state for the card. The Animatable drives rendering,
    // while a synchronous accumulator tracks the current drag distance so the
    // threshold check at drag-end is not subject to coroutine scheduling lag.
    val dragOffset = remember { Animatable(0f) }
    val dragTotal = remember { mutableFloatStateOf(0f) }
    val dragScope = rememberCoroutineScope()
    val dismissThresholdPx = with(density) { 120.dp.toPx() }
    LaunchedEffect(visible) {
        dragTotal.floatValue = 0f
        dragOffset.snapTo(0f)
    }
    // Bridge the inner scrollable's leftover downward scroll into the card offset so
    // dragging the card (or its content while already at the top) drags the whole card
    // down. Pre-scroll handles "drag back up" while the card is offset; post-scroll
    // captures the remaining downward gesture once the content can no longer scroll.
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // While the card is pulled down, consume upward scroll to push it back first.
                return if (available.y < 0f && dragTotal.floatValue > 0f) {
                    val consumed = (-dragTotal.floatValue).coerceAtLeast(available.y)
                    dragTotal.floatValue = (dragTotal.floatValue + consumed).coerceAtLeast(0f)
                    dragScope.launch { dragOffset.snapTo(dragTotal.floatValue) }
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
                return if (available.y > 0f) {
                    dragTotal.floatValue = (dragTotal.floatValue + available.y).coerceAtLeast(0f)
                    dragScope.launch { dragOffset.snapTo(dragTotal.floatValue) }
                    Offset(0f, available.y)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragTotal.floatValue >= dismissThresholdPx) {
                    currentDismiss.value()
                } else if (dragTotal.floatValue > 0f) {
                    dragTotal.floatValue = 0f
                    dragOffset.animateTo(0f, spring())
                }
                return Velocity.Zero
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        BackHandler(enabled = visible, onBack = dismiss)

        Box(modifier = Modifier.fillMaxSize()) {
            // Blurred + dimmed backdrop; tap to dismiss
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeEffect(state = hazeState) {
                        backgroundColor = frostColor
                        blurEnabled = true
                        blurRadius = GlassBlurRadius
                    }
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f))
                    .testTag(UiTestTags.PopupScrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = dismiss,
                    ),
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
                        enter = slideInVertically(spring()) { it },
                        exit = slideOutVertically(spring()) { it },
                    )
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = cardBottomGap)
                    .offset { IntOffset(0, dragOffset.value.roundToInt()) }
                    .testTag(UiTestTags.PopupCard)
                    .nestedScroll(nestedScrollConnection)
                    // Direct drag handling for gestures that begin on the card chrome
                    // (e.g. the rounded surface padding) rather than the scrollable column.
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { dragTotal.floatValue = dragOffset.value },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragTotal.floatValue = (dragTotal.floatValue + dragAmount).coerceAtLeast(0f)
                                dragScope.launch { dragOffset.snapTo(dragTotal.floatValue) }
                            },
                            onDragEnd = {
                                if (dragTotal.floatValue >= dismissThresholdPx) {
                                    dismiss()
                                } else {
                                    dragTotal.floatValue = 0f
                                    dragScope.launch { dragOffset.animateTo(0f, spring()) }
                                }
                            },
                            onDragCancel = {
                                dragTotal.floatValue = 0f
                                dragScope.launch { dragOffset.animateTo(0f, spring()) }
                            },
                        )
                    }
                    // card taps must not reach the scrim
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = maxCardHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
    }
}
