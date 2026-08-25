package app.tessera.ui.notes.components

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.tessera.TesseraNoteCard
import kotlin.math.roundToInt

private val SwipeButtonSize = 48.dp
private val SwipeButtonGap = 8.dp

private enum class RevealAnchor { LeftRevealed, Settled, RightRevealed }

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
@OptIn(ExperimentalFoundationApi::class)
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
) {
  val density = LocalDensity.current
  // Revealed width per side = the buttons plus a gap between each and one at either edge,
  // matching SwipeActionsRow's spacedBy + horizontal padding so the card stops flush with it.
  fun sideWidthPx(count: Int) = with(density) {
    if (count == 0) 0f else (SwipeButtonSize * count + SwipeButtonGap * (count + 1)).toPx()
  }
  val leftWidthPx = sideWidthPx(leftActions.size)
  val rightWidthPx = sideWidthPx(rightActions.size)

  fun buildAnchors() = DraggableAnchors {
    RevealAnchor.Settled at 0f
    if (leftActions.isNotEmpty()) RevealAnchor.LeftRevealed at leftWidthPx
    if (rightActions.isNotEmpty()) RevealAnchor.RightRevealed at -rightWidthPx
  }

  // Anchor widths are known at composition time (density + action counts), so seed the anchors
  // synchronously here. Deferring this to a LaunchedEffect leaves the offset uninitialized during
  // the first layout pass, where the .offset modifier's requireOffset() would throw.
  val state = remember {
    AnchoredDraggableState(
      initialValue = RevealAnchor.Settled,
      positionalThreshold = { distance -> distance * 0.5f },
      velocityThreshold = { with(density) { 125.dp.toPx() } },
      snapAnimationSpec = tween(),
      decayAnimationSpec = exponentialDecay(),
    ).apply { updateAnchors(buildAnchors()) }
  }

  // Rebuild anchors whenever the revealed widths change. A side with no actions gets no anchor,
  // so that drag direction cannot open.
  LaunchedEffect(leftWidthPx, rightWidthPx) {
    state.updateAnchors(buildAnchors())
  }

  // Inbound: caller asked us to close and we are (or are settling) open -> animate back to center.
  LaunchedEffect(isOpen) {
    if (!isOpen && state.settledValue != RevealAnchor.Settled) {
      state.animateTo(RevealAnchor.Settled)
    }
  }

  // Outbound: report our settled open/closed state to the caller, guarding against a feedback loop.
  // rememberUpdatedState keeps the long-lived (key = Unit) collector reading the latest values
  // instead of the stale ones captured at first composition.
  val currentIsOpen by rememberUpdatedState(isOpen)
  val currentOnOpenChange by rememberUpdatedState(onOpenChange)
  LaunchedEffect(Unit) {
    snapshotFlow { state.settledValue }.collect { settled ->
      val open = settled != RevealAnchor.Settled
      if (open != currentIsOpen) currentOnOpenChange(open)
    }
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

    val open = state.targetValue != RevealAnchor.Settled
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
      modifier = Modifier
        .testTag(UiTestTags.noteCard(noteId))
        .fillMaxWidth()
        .offset { IntOffset(state.requireOffset().roundToInt(), 0) }
        .anchoredDraggable(state, Orientation.Horizontal),
    )
  }
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
