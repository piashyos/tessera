package app.tessera.core.ui.theme

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

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
