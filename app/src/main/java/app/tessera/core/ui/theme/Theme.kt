package app.tessera.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.preferences.ThemeStyle

/** Resolves the brand color tokens for a [palette] in the given light/dark variant. */
fun paletteColors(palette: ThemePalette, dark: Boolean): TesseraBrandColors = when (palette) {
  ThemePalette.WARM    -> if (dark) TesseraBrandDark else TesseraBrandLight
  ThemePalette.NEUTRAL -> if (dark) TesseraNeutralDark else TesseraNeutralLight
  ThemePalette.SLATE   -> if (dark) TesseraSlateDark else TesseraSlateLight
  ThemePalette.SAGE    -> if (dark) TesseraSageDark else TesseraSageLight
}

private fun lightSchemeFor(c: TesseraBrandColors) = lightColorScheme(
  // surfaceVariant has no dedicated brand token; mirror the XML theme's mapping
  // (colorSurfaceVariant -> surface_container_high) so consumers don't fall back
  // to the off-palette M3 baseline tone.
  surfaceVariant            = c.surfaceContainerHigh,
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
  surfaceVariant            = c.surfaceContainerHigh,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TesseraTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  palette: ThemePalette = ThemePalette.Default,
  style: ThemeStyle = ThemeStyle.Default,
  amoled: Boolean = false,
  content: @Composable () -> Unit,
) {
  val ctx = LocalContext.current
  val brand = paletteColors(palette, darkTheme)
  // Full Material You: while dynamic color is on, the wallpaper seed drives the WHOLE scheme.
  // TONAL_SPOT uses the platform scheme verbatim; the other styles re-mix the same seed via MDC's
  // scheme variants. The hand-tuned brand palettes are the non-dynamic (or < Android 12) path.
  var scheme: ColorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      runCatching {
        if (style == ThemeStyle.TONAL_SPOT) {
          if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        } else {
          styledDynamicColorScheme(ctx, style, darkTheme)
        }
      }.getOrElse {
        if (darkTheme) darkSchemeFor(brand) else lightSchemeFor(brand)
      }
    }
    darkTheme -> darkSchemeFor(brand)
    else      -> lightSchemeFor(brand)
  }
  if (amoled && darkTheme) scheme = scheme.withTrueBlack()

  MaterialExpressiveTheme(
    colorScheme  = scheme,
    motionScheme = MotionScheme.expressive(),
    typography   = TesseraTypography,
    shapes       = TesseraShapes,
    content      = content,
  )
}

@Composable
fun TesseraTheme(
  themeMode: ThemeMode,
  dynamicColor: Boolean,
  palette: ThemePalette = ThemePalette.Default,
  style: ThemeStyle = ThemeStyle.Default,
  amoled: Boolean = false,
  content: @Composable () -> Unit,
) {
  val darkTheme = themeMode.resolveDark(isSystemInDarkTheme())
  TesseraTheme(
    darkTheme = darkTheme,
    dynamicColor = dynamicColor,
    palette = palette,
    style = style,
    amoled = amoled,
    content = content,
  )
}
