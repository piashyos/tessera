package app.tessera.ui.settings.components

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.ui.tessera.TesseraBottomSheet
import app.tessera.core.ui.theme.TesseraShapes
import app.tessera.core.ui.theme.TesseraSpacing

/** The three user-facing theme options, mapped onto the underlying [ThemeMode] enum. */
enum class ThemeChoice { System, Light, Dark }

internal fun ThemeMode.toThemeChoice(): ThemeChoice = when (this) {
    ThemeMode.AUTO -> ThemeChoice.System
    ThemeMode.LIGHT_DYNAMIC, ThemeMode.LIGHT_STATIC -> ThemeChoice.Light
    ThemeMode.DARK_DYNAMIC, ThemeMode.DARK_STATIC -> ThemeChoice.Dark
}

internal fun ThemeMode.isDynamicColor(): Boolean = when (this) {
    ThemeMode.AUTO, ThemeMode.LIGHT_DYNAMIC, ThemeMode.DARK_DYNAMIC -> true
    ThemeMode.LIGHT_STATIC, ThemeMode.DARK_STATIC -> false
}

/**
 * Combine a [ThemeChoice] and a dynamic-color preference back into a [ThemeMode]. [ThemeChoice.System]
 * always resolves to [ThemeMode.AUTO] (which is inherently dynamic), so [dynamic] is ignored there.
 */
internal fun themeModeOf(choice: ThemeChoice, dynamic: Boolean): ThemeMode = when (choice) {
    ThemeChoice.System -> ThemeMode.AUTO
    ThemeChoice.Light -> if (dynamic) ThemeMode.LIGHT_DYNAMIC else ThemeMode.LIGHT_STATIC
    ThemeChoice.Dark -> if (dynamic) ThemeMode.DARK_DYNAMIC else ThemeMode.DARK_STATIC
}

private val dynamicColorSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun ThemePickerSheet(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Apply each change immediately so the whole app re-themes live while the sheet is open.
    val choice = selected.toThemeChoice()
    val dynamic = selected.isDynamicColor()

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

        ThemeRadioRow(
            label = stringResource(R.string.settings_theme_system),
            selected = choice == ThemeChoice.System,
            onClick = { onSelect(themeModeOf(ThemeChoice.System, dynamic)) },
        )
        ThemeRadioRow(
            label = stringResource(R.string.settings_theme_light),
            selected = choice == ThemeChoice.Light,
            onClick = { onSelect(themeModeOf(ThemeChoice.Light, dynamic)) },
        )
        ThemeRadioRow(
            label = stringResource(R.string.settings_theme_dark),
            selected = choice == ThemeChoice.Dark,
            onClick = { onSelect(themeModeOf(ThemeChoice.Dark, dynamic)) },
        )

        if (dynamicColorSupported) {
            val dynamicEnabled = choice != ThemeChoice.System
            SettingsRow(
                leadingIcon = null,
                title = stringResource(R.string.settings_dynamic_color),
                subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                onClick = if (dynamicEnabled) {
                    { onSelect(themeModeOf(choice, !dynamic)) }
                } else {
                    null
                },
                trailing = {
                    Switch(
                        checked = dynamic,
                        onCheckedChange = if (dynamicEnabled) {
                            { onSelect(themeModeOf(choice, it)) }
                        } else {
                            null
                        },
                        enabled = dynamicEnabled,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                    )
                },
            )
        }
    }
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
