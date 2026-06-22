package app.tessera.core.ui.theme

import android.os.Build
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalContext
import app.tessera.core.preferences.ThemeMode

private val TesseraLightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    background = BackgroundGlowLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onPrimary = CapsuleHighlightLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceLight,
)

private val TesseraDarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    background = BackgroundGlowDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onPrimary = BackgroundGlowDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceDark,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TesseraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val ctx = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        darkTheme -> TesseraDarkColorScheme
        else -> TesseraLightColorScheme
    }
    MaterialTheme(colorScheme = scheme, typography = TesseraTypography) {
        CompositionLocalProvider(
            LocalIndication provides NoIndication,
            LocalRippleConfiguration provides null,
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TesseraTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val (darkTheme, dynamicColor) = themeMode.resolve(isSystemInDarkTheme())
    TesseraTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}

/** No-op indication so clickable elements never darken or ripple on press. */
private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node(), DrawModifierNode {
            override fun ContentDrawScope.draw() = drawContent()
        }

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = -1
}
