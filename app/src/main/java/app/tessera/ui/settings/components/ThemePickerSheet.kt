package app.tessera.ui.settings.components

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.preferences.ThemeStyle
import app.tessera.core.ui.tessera.TesseraBottomSheet
import app.tessera.core.ui.theme.schemeForSeed
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.tessera.core.ui.theme.TesseraShapes
import app.tessera.core.ui.theme.TesseraSpacing
import app.tessera.core.ui.theme.paletteColors

private val dynamicColorSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Theme picker. The radio rows pick light/dark only ([ThemeMode]); the dynamic-color switch is an
 * independent toggle that's always available (on Android 12+) and, when on, tints both the light
 * and dark scheme with the system wallpaper colors — including in [ThemeMode.AUTO].
 */
@Composable
fun ThemePickerSheet(
    selected: ThemeMode,
    dynamicColor: Boolean,
    selectedPalette: ThemePalette,
    selectedStyle: ThemeStyle,
    amoled: Boolean,
    onSelectTheme: (ThemeMode) -> Unit,
    onToggleDynamic: (Boolean) -> Unit,
    onSelectPalette: (ThemePalette) -> Unit,
    onSelectStyle: (ThemeStyle) -> Unit,
    onToggleAmoled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TesseraBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        header = { Text(stringResource(R.string.settings_theme_sheet_title)) },
    ) {
        ThemePreviewCard(
            modifier = Modifier.padding(
                start = TesseraSpacing.xxl,
                end = TesseraSpacing.xxl,
                bottom = TesseraSpacing.lg,
            ),
        )

        if (dynamicColor && dynamicColorSupported) {
            // Dynamic color on: the wallpaper seed drives everything; offer the scheme style.
            StyleSelector(selected = selectedStyle, onSelect = onSelectStyle)
        } else {
            PaletteSelector(
                selected = selectedPalette,
                enabled = true,
                onSelect = onSelectPalette,
            )
        }

        ThemeRadioRow(
            label = stringResource(R.string.settings_theme_system),
            selected = selected == ThemeMode.AUTO,
            onClick = { onSelectTheme(ThemeMode.AUTO) },
        )
        ThemeRadioRow(
            label = stringResource(R.string.settings_theme_light),
            selected = selected == ThemeMode.LIGHT,
            onClick = { onSelectTheme(ThemeMode.LIGHT) },
        )
        ThemeRadioRow(
            label = stringResource(R.string.settings_theme_dark),
            selected = selected == ThemeMode.DARK,
            onClick = { onSelectTheme(ThemeMode.DARK) },
        )

        if (dynamicColorSupported) {
            SettingsRow(
                leadingIcon = null,
                title = stringResource(R.string.settings_dynamic_color),
                subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                onClick = { onToggleDynamic(!dynamicColor) },
                trailing = {
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = { onToggleDynamic(it) },
                    )
                },
            )
        }
        SettingsRow(
            leadingIcon = null,
            title = stringResource(R.string.settings_amoled),
            subtitle = stringResource(R.string.settings_amoled_subtitle),
            onClick = { onToggleAmoled(!amoled) },
            trailing = {
                Switch(
                    checked = amoled,
                    onCheckedChange = { onToggleAmoled(it) },
                )
            },
        )
    }
}

/**
 * Dynamic-color scheme style picker: one swatch per [ThemeStyle], each previewing the style's
 * actual primary/secondary/tertiary mix generated live from the current system accent seed.
 */
@Composable
private fun StyleSelector(
    selected: ThemeStyle,
    onSelect: (ThemeStyle) -> Unit,
) {
    val context = LocalContext.current
    // Preview the variant of the APP's resolved theme, not the system's.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TesseraSpacing.xxl, vertical = TesseraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(TesseraSpacing.md),
    ) {
        Text(
            text = stringResource(R.string.settings_style_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.md),
        ) {
            val seed = if (dynamicColorSupported) {
                ContextCompat.getColor(context, android.R.color.system_accent1_500)
            } else {
                MaterialTheme.colorScheme.primary.hashCode()
            }
            ThemeStyle.entries.forEach { style ->
                val preview = schemeForSeed(seed, style, dark)
                val name = styleName(style)
                val ring = MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .selectable(selected = style == selected, onClick = { onSelect(style) })
                        .then(if (style == selected) Modifier.border(2.dp, ring, CircleShape) else Modifier)
                        .semantics { contentDescription = name },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = preview.primary,
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier.size(if (style == selected) 30.dp else 36.dp),
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun styleName(style: ThemeStyle): String = when (style) {
    ThemeStyle.TONAL_SPOT -> stringResource(R.string.settings_style_tonal_spot)
    ThemeStyle.VIBRANT -> stringResource(R.string.settings_style_vibrant)
    ThemeStyle.EXPRESSIVE -> stringResource(R.string.settings_style_expressive)
    ThemeStyle.FRUIT_SALAD -> stringResource(R.string.settings_style_fruit_salad)
}

@Composable
private fun ThemeRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = TesseraSpacing.xxl, vertical = TesseraSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.lg),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PaletteSelector(
    selected: ThemePalette,
    enabled: Boolean,
    onSelect: (ThemePalette) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TesseraSpacing.xxl, vertical = TesseraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(TesseraSpacing.md),
    ) {
        Text(
            text = stringResource(R.string.settings_palette_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.md),
        ) {
            ThemePalette.entries.forEach { palette ->
                PaletteSwatch(
                    palette = palette,
                    selected = palette == selected,
                    enabled = enabled,
                    onClick = { onSelect(palette) },
                )
            }
        }
    }
}

@Composable
private fun PaletteSwatch(
    palette: ThemePalette,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val swatchColor = paletteColors(palette, MaterialTheme.colorScheme.background.luminance() < 0.5f).primary
    val name = paletteName(palette)
    val ring = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .selectable(selected = selected, enabled = enabled, onClick = onClick)
            .then(if (selected) Modifier.border(2.dp, ring, CircleShape) else Modifier)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = swatchColor.copy(alpha = if (enabled) 1f else 0.35f),
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.size(if (selected) 30.dp else 36.dp),
        ) {}
    }
}

@Composable
private fun paletteName(palette: ThemePalette): String = when (palette) {
    ThemePalette.WARM -> stringResource(R.string.settings_palette_warm)
    ThemePalette.NEUTRAL -> stringResource(R.string.settings_palette_neutral)
    ThemePalette.SLATE -> stringResource(R.string.settings_palette_slate)
    ThemePalette.SAGE -> stringResource(R.string.settings_palette_sage)
}

@Composable
private fun ThemePreviewCard(modifier: Modifier = Modifier) {
    Surface(
        shape = TesseraShapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(TesseraSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.settings_theme_preview_sample),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            ColorSwatch(MaterialTheme.colorScheme.primary)
            ColorSwatch(MaterialTheme.colorScheme.secondary)
            ColorSwatch(MaterialTheme.colorScheme.tertiary)
            ColorSwatch(MaterialTheme.colorScheme.surfaceContainerHighest)
        }
    }
}

@Composable
private fun ColorSwatch(color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color,
        shape = CircleShape,
        modifier = Modifier.size(24.dp),
    ) {}
}
