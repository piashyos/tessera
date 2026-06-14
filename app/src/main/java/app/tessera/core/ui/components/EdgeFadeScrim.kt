package app.tessera.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

enum class ScrimEdge { Top, Bottom }

/**
 * Frosted fade that blurs whatever scrolls behind it and dissolves the content into [scrimColor]
 * towards the given screen [edge]. Pair the same [hazeState] with a `Modifier.hazeSource` on the
 * scrolling content.
 */
@Composable
fun EdgeFadeScrim(
    hazeState: HazeState,
    scrimColor: Color,
    edge: ScrimEdge,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 28.dp,
) {
    Box(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(brush = maskBrush(edge), blendMode = BlendMode.DstIn)
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeEffect(state = hazeState) {
                    backgroundColor = scrimColor
                    this.blurRadius = blurRadius
                    progressive = HazeProgressive.verticalGradient(
                        startIntensity = if (edge == ScrimEdge.Top) 1f else 0f,
                        endIntensity = if (edge == ScrimEdge.Top) 0f else 1f,
                    )
                },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimBrush(scrimColor, edge)),
        )
    }
}

/** Alpha mask that forces the faded edge to dissolve to fully transparent, removing any hard seam. */
private fun maskBrush(edge: ScrimEdge): Brush =
    when (edge) {
        ScrimEdge.Top ->
            Brush.verticalGradient(
                0.0f to Color.Black,
                0.55f to Color.Black,
                0.8f to Color.Black.copy(alpha = 0.45f),
                1.0f to Color.Transparent,
            )

        ScrimEdge.Bottom ->
            Brush.verticalGradient(
                0.0f to Color.Transparent,
                0.2f to Color.Black.copy(alpha = 0.45f),
                0.45f to Color.Black,
                1.0f to Color.Black,
            )
    }

private fun scrimBrush(color: Color, edge: ScrimEdge): Brush =
    when (edge) {
        ScrimEdge.Top ->
            Brush.verticalGradient(
                0.0f to color,
                0.5f to color,
                0.72f to color.copy(alpha = 0.5f),
                0.88f to color.copy(alpha = 0.15f),
                1.0f to color.copy(alpha = 0f),
            )

        ScrimEdge.Bottom ->
            Brush.verticalGradient(
                0.0f to color.copy(alpha = 0f),
                0.35f to color.copy(alpha = 0.35f),
                0.7f to color.copy(alpha = 0.85f),
                0.9f to color,
                1.0f to color,
            )
    }
