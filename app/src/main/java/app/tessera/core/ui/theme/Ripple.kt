package app.tessera.core.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode

/**
 * Brand-tinted ripple configuration. Scope per-component via
 * CompositionLocalProvider(LocalRippleConfiguration provides brandRipple) — do NOT install
 * globally, or every IconButton in the tree will get a terracotta ripple.
 */
@OptIn(ExperimentalMaterial3Api::class)
val brandRipple: RippleConfiguration
  @Composable @ReadOnlyComposable
  get() = RippleConfiguration(
    color = MaterialTheme.colorScheme.primary,
    rippleAlpha = RippleAlpha(
      draggedAlpha = 0.16f,
      focusedAlpha = 0.12f,
      hoveredAlpha = 0.10f,
      pressedAlpha = 0.20f,
    ),
  )

/**
 * No-op press indication. Installed globally as [androidx.compose.foundation.LocalIndication]
 * so bare `Modifier.clickable`/`combinedClickable` surfaces draw no dark state layer on press.
 * Material components that use `ripple()` are silenced separately via a null
 * `LocalRippleConfiguration`.
 */
object NoIndication : IndicationNodeFactory {
  override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = 0
}
