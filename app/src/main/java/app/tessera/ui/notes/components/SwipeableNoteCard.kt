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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.UiTestTags
import app.tessera.domain.model.Note
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class SwipeAnchor { Resting, Revealed }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeableNoteCard(
    note: Note,
    modifier: Modifier = Modifier,
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
    ) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(end = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwipeAction(
                label = if (note.isPinned) {
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
                label = stringResource(R.string.note_action_delete),
                container = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                content = MaterialTheme.colorScheme.error,
                width = actionWidth,
                testTag = UiTestTags.revealDeleteAction(note.id.value),
                onClick = onDelete,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(state.requireOffset().roundToInt(), 0) }
                .anchoredDraggable(state, Orientation.Horizontal)
                .background(MaterialTheme.colorScheme.background),
        ) {
            NoteCard(
                note = note,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .testTag(UiTestTags.noteCard(note.id.value)),
                onClick = onClick,
            )
        }
    }
}

@Composable
private fun SwipeAction(
    label: String,
    container: Color,
    content: Color,
    width: androidx.compose.ui.unit.Dp,
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
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
