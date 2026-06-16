package app.tessera.ui.notes.components

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.UiTestTags
import app.tessera.domain.model.Note
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.PinOff
import com.composables.icons.lucide.Trash2
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class SwipeAnchor { Resting, Revealed }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableNoteCard(
    note: Note,
    modifier: Modifier = Modifier,
    isTile: Boolean = false,
    onClick: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    val density = LocalDensity.current
    val actionWidth = 72.dp
    val actionsPx = with(density) { (actionWidth * 2).toPx() }
    val velocityPx = with(density) { 120.dp.toPx() }

    val scope = rememberCoroutineScope()
    val state =
        remember(actionsPx) {
            AnchoredDraggableState(
                initialValue = SwipeAnchor.Resting,
                anchors = DraggableAnchors {
                    SwipeAnchor.Resting at 0f
                    SwipeAnchor.Revealed at -actionsPx
                },
                positionalThreshold = { distance -> distance * 0.5f },
                velocityThreshold = { velocityPx },
                snapAnimationSpec = spring(),
                decayAnimationSpec = exponentialDecay(),
            )
        }

    val containerShape = if (isTile) RoundedCornerShape(24.dp) else RoundedCornerShape(0.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(containerShape),
    ) {
        // Reveal strip — width grows with the drag so actions slide out from under the card,
        // not by fading in. Anchored to the right edge and clipped to its own bounds so the
        // fixed-width SwipeAction children get cropped while emerging.
        val totalActionsWidthDp = actionWidth * 2
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(
                    with(density) {
                        kotlin.math.abs(state.requireOffset()).toDp().coerceAtMost(totalActionsWidthDp)
                    },
                )
                .clipToBounds(),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .requiredWidth(totalActionsWidthDp)
                    .fillMaxHeight()
                    .padding(end = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
            SwipeAction(
                icon = if (note.isPinned) Lucide.PinOff else Lucide.Pin,
                contentDescription = if (note.isPinned) {
                    stringResource(R.string.note_action_unpin)
                } else {
                    stringResource(R.string.note_action_pin)
                },
                container = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                content = MaterialTheme.colorScheme.primary,
                width = actionWidth,
                testTag = UiTestTags.revealPinAction(note.id.value),
                onClick = {
                    onTogglePin()
                    scope.launch { state.animateTo(SwipeAnchor.Resting) }
                },
            )
            SwipeAction(
                icon = Lucide.Trash2,
                contentDescription = stringResource(R.string.note_action_delete),
                container = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                content = MaterialTheme.colorScheme.error,
                width = actionWidth,
                testTag = UiTestTags.revealDeleteAction(note.id.value),
                onClick = onDelete,
            )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(state.requireOffset().roundToInt(), 0) }
                .anchoredDraggable(state, Orientation.Horizontal)
                .then(
                    if (isTile) {
                        Modifier.background(MaterialTheme.colorScheme.background)
                    } else {
                        Modifier
                    },
                ),
        ) {
            NoteCard(
                note = note,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (isTile) 12.dp else 0.dp)
                    .testTag(UiTestTags.noteCard(note.id.value)),
                isTile = isTile,
                onClick = onClick,
            )
            if (!isTile) {
                HorizontalDivider(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun SwipeAction(
    icon: ImageVector,
    contentDescription: String,
    container: Color,
    content: Color,
    width: Dp,
    testTag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = content)
    }
}
