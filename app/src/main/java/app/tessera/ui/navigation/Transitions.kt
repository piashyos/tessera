package app.tessera.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin

/**
 * Screen-to-screen motion, ported from PixelPlayer's Transitions.kt (M3 emphasized easing set):
 * a pushed screen slides in from the right with a subtle scale+fade; the screen behind
 * parallax-slides a quarter width and is further dimmed/rounded by [ScreenMotionWrapper].
 * Exits run at half duration so content never lingers over the incoming screen.
 */
object TesseraTransitions {
    val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerateEasing = CubicBezierEasing(0.2f, 0.85f, 0.7f, 1f)
    val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val DURATION = 450
    private const val DURATION_EXIT = DURATION / 2

    fun enter(): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(DURATION, easing = EmphasizedDecelerateEasing),
            initialOffsetX = { (it * 0.5f).toInt() },
        ) + scaleIn(
            animationSpec = tween(DURATION, easing = EmphasizedDecelerateEasing),
            initialScale = 0.92f,
            transformOrigin = TransformOrigin(0.5f, 0.5f),
        ) + fadeIn(tween(DURATION, easing = EmphasizedAccelerateEasing))

    fun exit(): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(DURATION, easing = EmphasizedAccelerateEasing),
            targetOffsetX = { -(it * 0.25f).toInt() },
        ) + fadeOut(tween(DURATION_EXIT, easing = EmphasizedAccelerateEasing))

    fun popEnter(): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(DURATION, easing = EmphasizedDecelerateEasing),
            initialOffsetX = { -(it * 0.25f).toInt() },
        ) + scaleIn(
            animationSpec = tween(DURATION, easing = EmphasizedDecelerateEasing),
            initialScale = 0.95f,
        ) + fadeIn(tween(DURATION_EXIT, easing = EmphasizedDecelerateEasing))

    fun popExit(): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(DURATION, easing = EmphasizedAccelerateEasing),
            targetOffsetX = { (it * 0.5f).toInt() },
        ) + scaleOut(
            animationSpec = tween(DURATION, easing = EmphasizedAccelerateEasing),
            targetScale = 0.92f,
            transformOrigin = TransformOrigin(0.5f, 0.5f),
        ) + fadeOut(tween(DURATION_EXIT, easing = EmphasizedAccelerateEasing))
}
