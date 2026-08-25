package app.tessera.core.ui.theme

import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.ThemePalette

/** Resolves the brand color tokens for a [palette] in the given light/dark variant. */
fun paletteColors(palette: ThemePalette, dark: Boolean): TesseraBrandColors = when (palette) {
  ThemePalette.WARM    -> if (dark) TesseraBrandDark else TesseraBrandLight
  ThemePalette.NEUTRAL -> if (dark) TesseraNeutralDark else TesseraNeutralLight
  ThemePalette.SLATE   -> if (dark) TesseraSlateDark else TesseraSlateLight
  ThemePalette.SAGE    -> if (dark) TesseraSageDark else TesseraSageLight
}

private fun lightSchemeFor(c: TesseraBrandColors) = lightColorScheme(
  primary                   = c.primary,
  onPrimary                 = c.onPrimary,
  primaryContainer          = c.primaryContainer,
  onPrimaryContainer        = c.onPrimaryContainer,
  secondary                 = c.secondary,
  onSecondary               = c.onSecondary,
  secondaryContainer        = c.secondaryContainer,
  onSecondaryContainer      = c.onSecondaryContainer,
  tertiary                  = c.tertiary,
  onTertiary                = c.onTertiary,
  tertiaryContainer         = c.tertiaryContainer,
  onTertiaryContainer       = c.onTertiaryContainer,
  error                     = c.error,
  onError                   = c.onError,
  errorContainer            = c.errorContainer,
  onErrorContainer          = c.onErrorContainer,
  background                = c.background,
  onBackground              = c.onBackground,
  surface                   = c.surface,
  onSurface                 = c.onSurface,
  surfaceDim                = c.surfaceDim,
  surfaceBright             = c.surfaceBright,
  surfaceContainerLowest    = c.surfaceContainerLowest,
  surfaceContainerLow       = c.surfaceContainerLow,
  surfaceContainer          = c.surfaceContainer,
  surfaceContainerHigh      = c.surfaceContainerHigh,
  surfaceContainerHighest   = c.surfaceContainerHighest,
  onSurfaceVariant          = c.onSurfaceVariant,
  outline                   = c.outline,
  outlineVariant            = c.outlineVariant,
  scrim                     = c.scrim,
)

private fun darkSchemeFor(c: TesseraBrandColors) = darkColorScheme(
  primary                   = c.primary,
  onPrimary                 = c.onPrimary,
  primaryContainer          = c.primaryContainer,
  onPrimaryContainer        = c.onPrimaryContainer,
  secondary                 = c.secondary,
  onSecondary               = c.onSecondary,
  secondaryContainer        = c.secondaryContainer,
  onSecondaryContainer      = c.onSecondaryContainer,
  tertiary                  = c.tertiary,
  onTertiary                = c.onTertiary,
  tertiaryContainer         = c.tertiaryContainer,
  onTertiaryContainer       = c.onTertiaryContainer,
  error                     = c.error,
  onError                   = c.onError,
  errorContainer            = c.errorContainer,
  onErrorContainer          = c.onErrorContainer,
  background                = c.background,
  onBackground              = c.onBackground,
  surface                   = c.surface,
  onSurface                 = c.onSurface,
  surfaceDim                = c.surfaceDim,
  surfaceBright             = c.surfaceBright,
  surfaceContainerLowest    = c.surfaceContainerLowest,
  surfaceContainerLow       = c.surfaceContainerLow,
  surfaceContainer          = c.surfaceContainer,
  surfaceContainerHigh      = c.surfaceContainerHigh,
  surfaceContainerHighest   = c.surfaceContainerHighest,
  onSurfaceVariant          = c.onSurfaceVariant,
  outline                   = c.outline,
  outlineVariant            = c.outlineVariant,
  scrim                     = c.scrim,
)

/**
 * Overlay our brand identity onto a dynamic-color scheme:
 * primary/background/surface always come from the chosen palette; let
 * secondary/tertiary/surfaceContainer-* keep their dynamic values harmonized from the wallpaper.
 */
private fun ColorScheme.brandOverlay(c: TesseraBrandColors): ColorScheme = copy(
  primary                = c.primary,
  onPrimary              = c.onPrimary,
  primaryContainer       = c.primaryContainer,
  onPrimaryContainer     = c.onPrimaryContainer,
  background             = c.background,
  onBackground           = c.onBackground,
  surface                = c.surface,
  onSurface              = c.onSurface,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TesseraTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  palette: ThemePalette = ThemePalette.Default,
  content: @Composable () -> Unit,
) {
  val ctx = LocalContext.current
  val brand = paletteColors(palette, darkTheme)
  val scheme: ColorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      runCatching {
        val dyn = if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dyn.brandOverlay(brand)
      }.getOrElse {
        if (darkTheme) darkSchemeFor(brand) else lightSchemeFor(brand)
      }
    }
    darkTheme -> darkSchemeFor(brand)
    else      -> lightSchemeFor(brand)
  }

  MaterialTheme(
    colorScheme = scheme,
    typography  = TesseraTypography,
    shapes      = TesseraShapes,
  ) {
    // Suppress the dark Material press state-layer everywhere: a null ripple config silences
    // `ripple()`-based components, while NoIndication covers bare Modifier.clickable surfaces.
    CompositionLocalProvider(
      LocalRippleConfiguration provides null,
      LocalIndication provides NoIndication,
      content = content,
    )
  }
}

@Composable
fun TesseraTheme(
  themeMode: ThemeMode,
  dynamicColor: Boolean,
  palette: ThemePalette = ThemePalette.Default,
  content: @Composable () -> Unit,
) {
  val darkTheme = themeMode.resolveDark(isSystemInDarkTheme())
  TesseraTheme(
    darkTheme = darkTheme,
    dynamicColor = dynamicColor,
    palette = palette,
    content = content,
  )
}
