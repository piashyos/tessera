package app.tessera.core.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

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
