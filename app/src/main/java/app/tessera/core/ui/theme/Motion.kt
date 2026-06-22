package app.tessera.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/** Motion tokens: M3-aligned easings and durations (ms). */
object TesseraMotion {
  val EaseStandard    = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
  val EaseEmphasized  = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
  val EaseDecelerate  = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

  const val DurShort1Ms   = 50
  const val DurShort4Ms   = 200
  const val DurMedium2Ms  = 300
  const val DurMedium4Ms  = 400
}

object Motion {
    /** Container resize / position changes that should feel responsive but landed. */
    val ContainerSpring = spring<Any>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = null,
    )

    /** Subtle scale / fade for incidental motion (chips, tooltips). */
    val IncidentalSpring = spring<Float>(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMedium,
    )

    /** Shared-element morph duration cap for very large bounds deltas. */
    const val SharedElementDurationMs = 360
}
