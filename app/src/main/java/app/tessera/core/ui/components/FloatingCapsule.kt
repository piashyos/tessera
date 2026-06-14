package app.tessera.core.ui.components

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.BackgroundGlowDark
import app.tessera.core.ui.theme.BackgroundGlowLight
import app.tessera.core.ui.theme.CapsuleBorder
import app.tessera.core.ui.theme.CapsuleHighlightDark
import app.tessera.core.ui.theme.CapsuleHighlightLight
import app.tessera.core.ui.theme.CapsuleShadowDark
import app.tessera.core.ui.theme.CapsuleShadowLight
import app.tessera.core.ui.theme.CapsuleTintDark
import app.tessera.core.ui.theme.CapsuleTintLight
import app.tessera.core.ui.theme.TesseraTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

internal fun capsuleScaleFactor(
    scrollProgress: Float,
    collapsedScale: Float = 0.92f,
): Float {
    val progress = scrollProgress.coerceIn(0f, 1f)
    return 1f - ((1f - collapsedScale) * progress)
}

fun Modifier.capsuleScale(
    scrollProgress: Float,
    collapsedScale: Float = 0.92f,
    collapsedAlpha: Float = 0.90f,
): Modifier = graphicsLayer {
    val progress = scrollProgress.coerceIn(0f, 1f)
    val scale = capsuleScaleFactor(progress, collapsedScale)
    scaleX = scale
    scaleY = scale
    alpha = 1f - ((1f - collapsedAlpha) * progress)
}

@Composable
fun FloatingCapsule(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    backgroundColor: Color = capsuleTintColor(),
    borderColor: Color = CapsuleBorder,
    blurRadius: Dp = 14.dp,
    shadowElevation: Dp = 4.dp,
    hazeState: HazeState? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val shadowColor = capsuleShadowColor()
    val frostBase = MaterialTheme.colorScheme.surface
    val tintColor = backgroundColor
    Surface(
        modifier =
            modifier.shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = shadowColor,
                spotColor = shadowColor,
            ),
        shape = shape,
        color = if (hazeState != null) Color.Transparent else backgroundColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(modifier = Modifier.clip(shape)) {
            if (hazeState != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(shape)
                        .hazeEffect(state = hazeState) {
                            this.backgroundColor = frostBase
                            this.blurRadius = blurRadius
                            this.tints = listOf(HazeTint(tintColor.copy(alpha = tintColor.alpha * 0.7f)))
                        },
                )
            }
            Box(
                modifier = glassLayerModifier(shape = shape, blurRadius = blurRadius)
                    .background(capsuleHighlightBrush())
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(width = 1.dp, color = borderColor, shape = shape)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Box(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

private fun BoxScope.glassLayerModifier(shape: Shape, blurRadius: Dp): Modifier {
    val baseModifier = Modifier.matchParentSize().clip(shape)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        baseModifier.blur(radius = blurRadius)
    } else {
        baseModifier
    }
}

@Composable
private fun capsuleTintColor(): Color {
    return if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        CapsuleTintDark
    } else {
        CapsuleTintLight
    }
}

@Composable
private fun capsuleHighlightBrush(): Brush {
    val highlight = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        CapsuleHighlightDark
    } else {
        CapsuleHighlightLight
    }
    return Brush.linearGradient(
        colors = listOf(
            highlight,
            highlight.copy(alpha = highlight.alpha * 0.35f),
            Color.Transparent,
        ),
    )
}

@Composable
private fun capsuleShadowColor(): Color {
    return if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        CapsuleShadowDark
    } else {
        CapsuleShadowLight
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFBF4)
@Composable
private fun FloatingCapsuleLightPreview() {
    TesseraTheme(darkTheme = false, dynamicColor = false) {
        Box(
            modifier = Modifier
                .background(BackgroundGlowLight)
                .padding(24.dp),
        ) {
            FloatingCapsule(modifier = Modifier.size(width = 240.dp, height = 72.dp)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Text("Floating capsule")
                }
            }
        }
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF101116,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FloatingCapsuleDarkPreview() {
    TesseraTheme(darkTheme = true, dynamicColor = false) {
        Box(
            modifier = Modifier
                .background(BackgroundGlowDark)
                .padding(24.dp),
        ) {
            FloatingCapsule(modifier = Modifier.size(width = 240.dp, height = 72.dp)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Text("Floating capsule")
                }
            }
        }
    }
}
