package app.tessera.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val HandleSize = 22.dp
private val RotateButtonSize = 36.dp

/**
 * Selection chrome drawn over a tapped image: a 2dp outline, four draggable corner handles
 * (aspect-locked horizontal resize), and a rotate button above the top edge. [rect] is the image's
 * on-screen rectangle in the parent's px coordinate space.
 *
 * @param onResizeDelta live per-drag callback with the CUMULATIVE outward-positive delta (px) since
 *   the drag began — positive grows the image, negative shrinks. Left/top handles negate the raw dx.
 * @param onResizeCommit fires once when a resize drag ends (persist the new width).
 * @param onRotate fires on a rotate-button tap (rotate 90° clockwise + persist).
 */
@Composable
fun ImageSelectionOverlay(
    rect: Rect,
    onResizeDelta: (Float) -> Unit,
    onResizeCommit: () -> Unit,
    onRotate: () -> Unit,
    resizeContentDescription: String,
    rotateContentDescription: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val handlePx = with(density) { HandleSize.toPx() }
    val rotatePx = with(density) { RotateButtonSize.toPx() }
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Box(modifier.fillMaxSize()) {
        // Outline
        Box(
            Modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                .border(2.dp, primary, RoundedCornerShape(2.dp)),
        )

        // Corner handles: (x, y, outward-sign for the raw dx). Left corners grow when dragged left.
        val corners = listOf(
            Triple(rect.left, rect.top, -1f),
            Triple(rect.right, rect.top, 1f),
            Triple(rect.left, rect.bottom, -1f),
            Triple(rect.right, rect.bottom, 1f),
        )
        corners.forEach { (cx, cy, sign) ->
            ResizeHandle(
                centerX = cx,
                centerY = cy,
                sizePx = handlePx,
                sign = sign,
                color = primary,
                handleDescription = resizeContentDescription,
                onResizeDelta = onResizeDelta,
                onResizeCommit = onResizeCommit,
            )
        }

        // Rotate button centered above the top edge.
        val rotX = (rect.left + rect.right) / 2f - rotatePx / 2f
        val rotY = rect.top - rotatePx - handlePx / 2f
        Box(
            modifier = Modifier
                .offset { IntOffset(rotX.roundToInt(), rotY.roundToInt()) }
                .size(RotateButtonSize)
                .clip(CircleShape)
                .background(primary)
                .semantics {
                    contentDescription = rotateContentDescription
                    onClick { onRotate(); true }
                }
                .pointerInput(Unit) { detectTapGestures { onRotate() } },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Rotate90DegreesCw,
                contentDescription = null,
                tint = onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ResizeHandle(
    centerX: Float,
    centerY: Float,
    sizePx: Float,
    sign: Float,
    color: Color,
    handleDescription: String,
    onResizeDelta: (Float) -> Unit,
    onResizeCommit: () -> Unit,
) {
    val density = LocalDensity.current
    var accum by remember { mutableStateOf(0f) }
    Box(
        Modifier
            .offset {
                IntOffset(
                    (centerX - sizePx / 2f).roundToInt(),
                    (centerY - sizePx / 2f).roundToInt(),
                )
            }
            .size(with(density) { sizePx.toDp() })
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = handleDescription }
            .pointerInput(sign) {
                detectDragGestures(
                    onDragStart = { accum = 0f },
                    onDragEnd = { onResizeCommit() },
                    onDragCancel = { onResizeCommit() },
                ) { change, dragAmount ->
                    change.consume()
                    accum += dragAmount.x * sign
                    onResizeDelta(accum)
                }
            },
    )
}
