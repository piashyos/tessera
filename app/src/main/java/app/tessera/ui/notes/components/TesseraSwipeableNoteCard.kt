package app.tessera.ui.notes.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.tessera.TesseraNoteCard
import app.tessera.core.ui.theme.TesseraMotion
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val SwipeButtonSize = 48.dp
private val SwipeButtonGap = 8.dp

/**
 * [TesseraNoteCard] that reveals action buttons when swiped, instead of dismissing.
 *
 * - Swipe start-to-end (right) reveals [leftActions] pinned to the leading edge.
 * - Swipe end-to-start (left) reveals [rightActions] pinned to the trailing edge.
 *
 * Nothing mutates on swipe alone: an action only fires when its revealed button is tapped.
 * A partial drag that doesn't cross an anchor settles back to center and reveals nothing.
 * A side with an empty action list has no anchor, so that swipe direction is inert.
 *
 * Open/closed is hoisted via [isOpen]/[onOpenChange] so the caller can keep at most one card
 * open at a time and collapse on external events.
 */
@Composable
fun TesseraSwipeableNoteCard(
  noteId: String,
  title: String,
  preview: String,
  dateLabel: String,
  tags: List<String>,
  isPinned: Boolean,
  isOpen: Boolean,
  onOpenChange: (Boolean) -> Unit,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  leftActions: List<SwipeAction>,
  rightActions: List<SwipeAction>,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  avatars: (@Composable () -> Unit)? = null,
  tint: Color? = null,
) {
  val density = LocalDensity.current
  // Revealed width per side = the buttons plus a gap between each and one at either edge,
  // matching SwipeActionsRow's spacedBy + horizontal padding so the card stops flush with it.
  fun sideWidthPx(count: Int) = with(density) {
    if (count == 0) 0f else (SwipeButtonSize * count + SwipeButtonGap * (count + 1)).toPx()
  }
  val leftWidthPx = sideWidthPx(leftActions.size)
  val rightWidthPx = sideWidthPx(rightActions.size)
  // Where the card can rest: centre, plus one stop per side that has actions. A side without actions
  // has its limit at 0, so pulling that way stretches the card like a rubber band instead of moving it.
  val anchors = listOf(0f, leftWidthPx, -rightWidthPx).distinct()

  // The card's offset is plain state: the finger writes it while dragging, a spring writes it after release.
  var x by remember { mutableFloatStateOf(0f) }
  var cardWidthPx by remember { mutableFloatStateOf(0f) }
  var target by remember { mutableFloatStateOf(0f) } // the anchor the card rests at or is heading to
  val scope = rememberCoroutineScope()
  val currentIsOpen by rememberUpdatedState(isOpen)
  val currentOnOpenChange by rememberUpdatedState(onOpenChange)
  val currentAnchors by rememberUpdatedState(anchors)
  val currentLimits by rememberUpdatedState(-rightWidthPx to leftWidthPx)
  val currentWidth by rememberUpdatedState(cardWidthPx)
  val settling = remember { arrayOfNulls<Job>(1) }

  fun settle(to: Float, velocity: Float = 0f) {
    settling[0]?.cancel()
    target = to
    settling[0] = scope.launch {
      // Interruptible: a new touch cancels this, and the finger takes over from wherever x is.
      animate(x, to, velocity, TesseraMotion.snappySpring()) { value, _ -> x = value }
      val open = to != 0f
      if (open != currentIsOpen) currentOnOpenChange(open)
    }
  }

  // Caller asked us to close and we are (or are heading) open -> spring back to centre.
  LaunchedEffect(isOpen) {
    if (!isOpen && target != 0f) settle(0f)
  }
  // An anchor that no longer exists (the actions changed) must not strand the card open.
  LaunchedEffect(leftWidthPx, rightWidthPx) {
    if (target != 0f && target !in currentAnchors) settle(0f)
  }

  val dragState = rememberDraggableState { delta ->
    val (min, max) = currentLimits
    x = resistedDrag(x, delta, min, max, currentWidth.coerceAtLeast(1f))
  }

  Box(modifier = modifier) {
    if (leftActions.isNotEmpty()) {
      SwipeActionsRow(
        noteId = noteId,
        actions = leftActions,
        onDismiss = { onOpenChange(false) },
        modifier = Modifier.matchParentSize(),
        alignment = Alignment.CenterStart,
      )
    }
    if (rightActions.isNotEmpty()) {
      SwipeActionsRow(
        noteId = noteId,
        actions = rightActions,
        onDismiss = { onOpenChange(false) },
        modifier = Modifier.matchParentSize(),
        alignment = Alignment.CenterEnd,
      )
    }

    val open = target != 0f
    TesseraNoteCard(
      title = title,
      preview = preview,
      dateLabel = dateLabel,
      tags = tags,
      isPinned = isPinned,
      // Tapping the body while open collapses instead of navigating.
      onClick = { if (open) onOpenChange(false) else onClick() },
      onLongClick = onLongClick,
      selected = selected,
      avatars = avatars,
      tint = tint,
      modifier = Modifier
        .testTag(UiTestTags.noteCard(noteId))
        .fillMaxWidth()
        .onSizeChanged { cardWidthPx = it.width.toFloat() }
        .offset { IntOffset(x.roundToInt(), 0) }
        .draggable(
          state = dragState,
          orientation = Orientation.Horizontal,
          onDragStarted = { settling[0]?.cancel() },
          onDragStopped = { velocity -> settle(settleTarget(x, velocity, currentAnchors), velocity) },
        ),
    )
  }
}

/** Fraction of the drag that still follows the finger as the card is pulled past its limit (UIScrollView's 0.55). */
private const val RubberBandStiffness = 0.55f

/** Seconds of the release velocity that the card is assumed to keep travelling: picks the stop a flick is aimed at. */
private const val ProjectionSeconds = 0.2f

/**
 * One drag step with rubber-band resistance, the way UIScrollView stretches at its edge: free inside
 * [min]..[max], and past a limit the card follows a shrinking share of the finger, so it can only ever
 * be pulled a fraction of [dimension] beyond it. The share depends on how far past the limit it already is,
 * which makes dragging back out of the stretch exactly retrace the way in.
 */
internal fun resistedDrag(current: Float, delta: Float, min: Float, max: Float, dimension: Float): Float {
  val next = current + delta
  val over = when {
    current > max -> current - max
    current < min -> min - current
    else -> 0f
  }
  if (over == 0f) {
    if (next in min..max) return next
    val limit = if (next > max) max else min
    return limit + (next - limit) * RubberBandStiffness
  }
  val share = (1f - over / dimension).coerceAtLeast(0f)
  val stretched = current + delta * RubberBandStiffness * share * share
  // dragging back toward the range never overshoots the limit it is returning to
  return when {
    current > max && delta < 0f -> maxOf(stretched, max)
    current < min && delta > 0f -> minOf(stretched, min)
    else -> stretched
  }
}

/** The stop a release at [offset] with [velocity] settles on: the one nearest to where the card is heading. */
internal fun settleTarget(offset: Float, velocity: Float, anchors: List<Float>): Float {
  val projected = offset + velocity * ProjectionSeconds
  return anchors.minByOrNull { abs(it - projected) } ?: 0f
}

@Composable
private fun SwipeActionsRow(
  noteId: String,
  actions: List<SwipeAction>,
  onDismiss: () -> Unit,
  alignment: Alignment,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier, contentAlignment = alignment) {
    Row(
      modifier = Modifier.padding(horizontal = SwipeButtonGap),
      horizontalArrangement = Arrangement.spacedBy(SwipeButtonGap),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      actions.forEach { action ->
        Box(
          modifier = Modifier
            .testTag(UiTestTags.swipeAction(noteId, action.key))
            .size(SwipeButtonSize)
            .clip(MaterialTheme.shapes.medium)
            .background(action.container)
            .clickable {
              action.onClick()
              onDismiss()
            },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = action.icon,
            contentDescription = action.label,
            tint = action.content,
          )
        }
      }
    }
  }
}
