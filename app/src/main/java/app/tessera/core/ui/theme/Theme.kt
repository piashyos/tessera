package app.tessera.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.tessera.core.preferences.ThemeMode

private fun brandLightScheme() = lightColorScheme(
  primary                   = TesseraBrandLight.primary,
  onPrimary                 = TesseraBrandLight.onPrimary,
  primaryContainer          = TesseraBrandLight.primaryContainer,
  onPrimaryContainer        = TesseraBrandLight.onPrimaryContainer,
  secondary                 = TesseraBrandLight.secondary,
  onSecondary               = TesseraBrandLight.onSecondary,
  secondaryContainer        = TesseraBrandLight.secondaryContainer,
  onSecondaryContainer      = TesseraBrandLight.onSecondaryContainer,
  tertiary                  = TesseraBrandLight.tertiary,
  onTertiary                = TesseraBrandLight.onTertiary,
  tertiaryContainer         = TesseraBrandLight.tertiaryContainer,
  onTertiaryContainer       = TesseraBrandLight.onTertiaryContainer,
  error                     = TesseraBrandLight.error,
  onError                   = TesseraBrandLight.onError,
  errorContainer            = TesseraBrandLight.errorContainer,
  onErrorContainer          = TesseraBrandLight.onErrorContainer,
  background                = TesseraBrandLight.background,
  onBackground              = TesseraBrandLight.onBackground,
  surface                   = TesseraBrandLight.surface,
  onSurface                 = TesseraBrandLight.onSurface,
  surfaceDim                = TesseraBrandLight.surfaceDim,
  surfaceBright             = TesseraBrandLight.surfaceBright,
  surfaceContainerLowest    = TesseraBrandLight.surfaceContainerLowest,
  surfaceContainerLow       = TesseraBrandLight.surfaceContainerLow,
  surfaceContainer          = TesseraBrandLight.surfaceContainer,
  surfaceContainerHigh      = TesseraBrandLight.surfaceContainerHigh,
  surfaceContainerHighest   = TesseraBrandLight.surfaceContainerHighest,
  onSurfaceVariant          = TesseraBrandLight.onSurfaceVariant,
  outline                   = TesseraBrandLight.outline,
  outlineVariant            = TesseraBrandLight.outlineVariant,
  scrim                     = TesseraBrandLight.scrim,
)

private fun brandDarkScheme() = darkColorScheme(
  primary                   = TesseraBrandDark.primary,
  onPrimary                 = TesseraBrandDark.onPrimary,
  primaryContainer          = TesseraBrandDark.primaryContainer,
  onPrimaryContainer        = TesseraBrandDark.onPrimaryContainer,
  secondary                 = TesseraBrandDark.secondary,
  onSecondary               = TesseraBrandDark.onSecondary,
  secondaryContainer        = TesseraBrandDark.secondaryContainer,
  onSecondaryContainer      = TesseraBrandDark.onSecondaryContainer,
  tertiary                  = TesseraBrandDark.tertiary,
  onTertiary                = TesseraBrandDark.onTertiary,
  tertiaryContainer         = TesseraBrandDark.tertiaryContainer,
  onTertiaryContainer       = TesseraBrandDark.onTertiaryContainer,
  error                     = TesseraBrandDark.error,
  onError                   = TesseraBrandDark.onError,
  errorContainer            = TesseraBrandDark.errorContainer,
  onErrorContainer          = TesseraBrandDark.onErrorContainer,
  background                = TesseraBrandDark.background,
  onBackground              = TesseraBrandDark.onBackground,
  surface                   = TesseraBrandDark.surface,
  onSurface                 = TesseraBrandDark.onSurface,
  surfaceDim                = TesseraBrandDark.surfaceDim,
  surfaceBright             = TesseraBrandDark.surfaceBright,
  surfaceContainerLowest    = TesseraBrandDark.surfaceContainerLowest,
  surfaceContainerLow       = TesseraBrandDark.surfaceContainerLow,
  surfaceContainer          = TesseraBrandDark.surfaceContainer,
  surfaceContainerHigh      = TesseraBrandDark.surfaceContainerHigh,
  surfaceContainerHighest   = TesseraBrandDark.surfaceContainerHighest,
  onSurfaceVariant          = TesseraBrandDark.onSurfaceVariant,
  outline                   = TesseraBrandDark.outline,
  outlineVariant            = TesseraBrandDark.outlineVariant,
  scrim                     = TesseraBrandDark.scrim,
)

/**
 * Overlay our brand identity onto a dynamic-color scheme:
 * primary/background/surface always brand; let secondary/tertiary/surfaceContainer-*
 * keep their dynamic warm-tinted values harmonized from the user's wallpaper.
 */
private fun ColorScheme.brandOverlay(dark: Boolean): ColorScheme {
  val brand = if (dark) TesseraBrandDark else TesseraBrandLight
  return this.copy(
    primary                = brand.primary,
    onPrimary              = brand.onPrimary,
    primaryContainer       = brand.primaryContainer,
    onPrimaryContainer     = brand.onPrimaryContainer,
    background             = brand.background,
    onBackground           = brand.onBackground,
    surface                = brand.surface,
    onSurface              = brand.onSurface,
  )
}

@Composable
fun TesseraTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val ctx = LocalContext.current
  val scheme: ColorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      runCatching {
        val dyn = if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dyn.brandOverlay(darkTheme)
      }.getOrElse {
        if (darkTheme) brandDarkScheme() else brandLightScheme()
      }
    }
    darkTheme -> brandDarkScheme()
    else      -> brandLightScheme()
  }

  MaterialTheme(
    colorScheme = scheme,
    typography  = TesseraTypography,
    shapes      = TesseraShapes,
    content     = content,
  )
}

@Composable
fun TesseraTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
  val (darkTheme, dynamicColor) = themeMode.resolve(isSystemInDarkTheme())
  TesseraTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
