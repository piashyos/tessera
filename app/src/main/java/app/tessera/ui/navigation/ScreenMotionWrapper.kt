package app.tessera.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController

/**
 * PixelPlayer-style depth cue for the screen a push leaves BEHIND: while another destination
 * covers this one, the covered screen rounds its corners to 32dp, dims to 40% black and blurs —
 * and animates all three back as the covering screen pops.
 *
 * "Is this screen behind?" is `previousBackStackEntry.id == entry.id` — deliberately not
 * `visibleEntries` (predictive back's prepareForTransition doesn't re-emit visibleEntries;
 * PixelPlayer's ScreenWrapper documents the every-other-gesture bug that causes).
 *
 * The graphicsLayer keeps [CompositingStrategy.Offscreen] for the whole transition lifecycle:
 * toggling it mid-transition causes a one-frame flash.
 */
@Composable
fun ScreenMotionWrapper(
    navController: NavHostController,
    entry: NavBackStackEntry,
    animatedVisibilityScope: AnimatedVisibilityScope,
    content: @Composable () -> Unit,
) {
    val transition = animatedVisibilityScope.transition
    val isBehind = navController.previousBackStackEntry?.id == entry.id

    val cornerRadius by transition.animateDp(
        transitionSpec = { tween(350, easing = FastOutSlowInEasing) },
        label = "behindCornerRadius",
    ) { state -> if (state == EnterExitState.PostExit && isBehind) 32.dp else 0.dp }

    val dimAlpha by transition.animateFloat(
        transitionSpec = { tween(350, easing = DimEasing) },
        label = "behindDim",
    ) { state -> if (state == EnterExitState.PostExit && isBehind) 0.4f else 0f }

    val blurRadius by transition.animateDp(
        transitionSpec = { tween(350, easing = DimEasing) },
        label = "behindBlur",
    ) { state -> if (state == EnterExitState.PostExit && isBehind) 24.dp else 0.dp }

    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                shape = RoundedCornerShape(with(density) { cornerRadius.toPx() })
                clip = true
            }
            .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
    ) {
        content()
        if (dimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha)),
            )
        }
    }
}

private val DimEasing = androidx.compose.animation.core.CubicBezierEasing(0.5f, 0f, 0.8f, 0.2f)
