@file:SuppressLint("RestrictedApi")
// MDC's color-utilities classes are @RestrictTo(LIBRARY_GROUP) but are the only public
// implementation of the Material You scheme variants; using them directly is the established
// pattern (see PixelPlayer's ColorRoles.kt) and they are pure color math with a stable API.

package app.tessera.core.ui.theme

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import app.tessera.core.preferences.ThemeStyle
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeFruitSalad
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant

/**
 * Builds a Material You [ColorScheme] for [style] from the system dynamic-color seed
 * (`system_accent1_500`, the wallpaper-derived accent). [ThemeStyle.TONAL_SPOT] is the algorithm
 * the platform itself uses, so it visually matches `dynamicLight/DarkColorScheme`; the other
 * styles re-mix the same seed with MDC's alternative scheme variants.
 */
@RequiresApi(Build.VERSION_CODES.S)
fun styledDynamicColorScheme(context: Context, style: ThemeStyle, dark: Boolean): ColorScheme {
    val seed = ContextCompat.getColor(context, android.R.color.system_accent1_500)
    return schemeForSeed(seed, style, dark)
}

/** Pure variant used by previews/tests: builds the [style] scheme from an explicit seed ARGB. */
fun schemeForSeed(seedArgb: Int, style: ThemeStyle, dark: Boolean): ColorScheme {
    val hct = Hct.fromInt(seedArgb)
    val scheme: DynamicScheme = when (style) {
        ThemeStyle.TONAL_SPOT -> SchemeTonalSpot(hct, dark, 0.0)
        ThemeStyle.VIBRANT -> SchemeVibrant(hct, dark, 0.0)
        ThemeStyle.EXPRESSIVE -> SchemeExpressive(hct, dark, 0.0)
        ThemeStyle.FRUIT_SALAD -> SchemeFruitSalad(hct, dark, 0.0)
    }
    return scheme.toComposeColorScheme()
}

/**
 * AMOLED variant: true-black background and dimmest surfaces while keeping the tonal
 * container ladder readable. Only meaningful on a dark scheme.
 */
fun ColorScheme.withTrueBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
)

private fun DynamicScheme.toComposeColorScheme(): ColorScheme = ColorScheme(
    primary = Color(primary),
    onPrimary = Color(onPrimary),
    primaryContainer = Color(primaryContainer),
    onPrimaryContainer = Color(onPrimaryContainer),
    inversePrimary = Color(inversePrimary),
    secondary = Color(secondary),
    onSecondary = Color(onSecondary),
    secondaryContainer = Color(secondaryContainer),
    onSecondaryContainer = Color(onSecondaryContainer),
    tertiary = Color(tertiary),
    onTertiary = Color(onTertiary),
    tertiaryContainer = Color(tertiaryContainer),
    onTertiaryContainer = Color(onTertiaryContainer),
    background = Color(background),
    onBackground = Color(onBackground),
    surface = Color(surface),
    onSurface = Color(onSurface),
    surfaceVariant = Color(surfaceVariant),
    onSurfaceVariant = Color(onSurfaceVariant),
    surfaceTint = Color(surfaceTint),
    inverseSurface = Color(inverseSurface),
    inverseOnSurface = Color(inverseOnSurface),
    error = Color(error),
    onError = Color(onError),
    errorContainer = Color(errorContainer),
    onErrorContainer = Color(onErrorContainer),
    outline = Color(outline),
    outlineVariant = Color(outlineVariant),
    scrim = Color(scrim),
    surfaceBright = Color(surfaceBright),
    surfaceDim = Color(surfaceDim),
    surfaceContainer = Color(surfaceContainer),
    surfaceContainerHigh = Color(surfaceContainerHigh),
    surfaceContainerHighest = Color(surfaceContainerHighest),
    surfaceContainerLow = Color(surfaceContainerLow),
    surfaceContainerLowest = Color(surfaceContainerLowest),
    primaryFixed = Color(primaryFixed),
    primaryFixedDim = Color(primaryFixedDim),
    onPrimaryFixed = Color(onPrimaryFixed),
    onPrimaryFixedVariant = Color(onPrimaryFixedVariant),
    secondaryFixed = Color(secondaryFixed),
    secondaryFixedDim = Color(secondaryFixedDim),
    onSecondaryFixed = Color(onSecondaryFixed),
    onSecondaryFixedVariant = Color(onSecondaryFixedVariant),
    tertiaryFixed = Color(tertiaryFixed),
    tertiaryFixedDim = Color(tertiaryFixedDim),
    onTertiaryFixed = Color(onTertiaryFixed),
    onTertiaryFixedVariant = Color(onTertiaryFixedVariant),
)
