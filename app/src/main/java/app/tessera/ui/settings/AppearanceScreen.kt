package app.tessera.ui.settings

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Gradient
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import app.tessera.R
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.preferences.ThemeStyle
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.TesseraSpacing
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.settings.components.SettingsDivider
import app.tessera.ui.settings.components.SettingsGroup
import app.tessera.ui.settings.components.SettingsRow
import app.tessera.ui.settings.components.ThemePickerSheet
import kotlin.math.roundToInt

/** Everything about how the app looks: theme, background gradient and its animation, film grain. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    state: SettingsState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSelectThemeMode: (ThemeMode) -> Unit = {},
    onSetDynamicColor: (Boolean) -> Unit = {},
    onSelectPalette: (ThemePalette) -> Unit = {},
    onSelectStyle: (ThemeStyle) -> Unit = {},
    onSetAmoled: (Boolean) -> Unit = {},
    onSetGradientEnabled: (Boolean) -> Unit = {},
    onSetBackgroundMotionEnabled: (Boolean) -> Unit = {},
    onSetGrainEnabled: (Boolean) -> Unit = {},
    onSetGrainIntensity: (Float) -> Unit = {},
    onSetGrainSize: (Int) -> Unit = {},
) {
    var showThemeSheet by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.AppearanceRoot)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_appearance),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(UiTestTags.AppearanceBack),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = TesseraSpacing.xl,
                end = TesseraSpacing.xl,
                top = TesseraSpacing.sm,
                bottom = TesseraSpacing.xxxl,
            ),
            verticalArrangement = Arrangement.spacedBy(TesseraSpacing.lg),
        ) {
            item {
                SettingsGroup {
                    SettingsRow(
                        modifier = Modifier.testTag(UiTestTags.AppearanceThemeRow),
                        leadingIcon = Icons.Rounded.Contrast,
                        title = stringResource(R.string.settings_theme_sheet_title),
                        onClick = { showThemeSheet = true },
                        trailing = { ValueChevron(themeChoiceLabel(state.themeMode)) },
                    )
                    SettingsDivider()
                    SettingsRow(
                        modifier = Modifier.testTag(UiTestTags.AppearanceGradientRow),
                        leadingIcon = Icons.Rounded.Gradient,
                        title = stringResource(R.string.settings_gradient_title),
                        subtitle = stringResource(R.string.settings_gradient_subtitle),
                        onClick = { onSetGradientEnabled(!state.gradientEnabled) },
                        trailing = {
                            Switch(
                                checked = state.gradientEnabled,
                                onCheckedChange = onSetGradientEnabled,
                                modifier = Modifier.labeled(R.string.settings_gradient_title),
                            )
                        },
                    )
                    if (state.gradientEnabled) {
                        SettingsDivider()
                        SettingsRow(
                            modifier = Modifier.testTag(UiTestTags.AppearanceMotionRow),
                            leadingIcon = Icons.Rounded.Animation,
                            title = stringResource(R.string.settings_background_motion_title),
                            subtitle = stringResource(R.string.settings_background_motion_subtitle),
                            onClick = { onSetBackgroundMotionEnabled(!state.backgroundMotionEnabled) },
                            trailing = {
                                Switch(
                                    checked = state.backgroundMotionEnabled,
                                    onCheckedChange = onSetBackgroundMotionEnabled,
                                    modifier = Modifier.labeled(R.string.settings_background_motion_title),
                                )
                            },
                        )
                    }
                    SettingsDivider()
                    SettingsRow(
                        modifier = Modifier.testTag(UiTestTags.AppearanceGrainRow),
                        leadingIcon = Icons.Rounded.Grain,
                        title = stringResource(R.string.settings_grain_title),
                        subtitle = stringResource(R.string.settings_grain_subtitle),
                        onClick = { onSetGrainEnabled(!state.grainEnabled) },
                        trailing = {
                            Switch(
                                checked = state.grainEnabled,
                                onCheckedChange = onSetGrainEnabled,
                                modifier = Modifier.labeled(R.string.settings_grain_title),
                            )
                        },
                    )
                    if (state.grainEnabled) {
                        GrainIntensityRow(
                            label = stringResource(R.string.settings_grain_intensity),
                            value = state.grainIntensity,
                            onValueChange = onSetGrainIntensity,
                        )
                        GrainIntensityRow(
                            label = stringResource(R.string.settings_grain_size),
                            value = state.grainSize.toFloat(),
                            onValueChange = { onSetGrainSize(it.roundToInt()) },
                            valueRange = 1f..4f,
                            steps = 2,
                        )
                    }
                }
            }
        }
    }

    if (showThemeSheet) {
        ThemePickerSheet(
            selected = state.themeMode,
            dynamicColor = state.dynamicColor,
            selectedPalette = state.themePalette,
            selectedStyle = state.themeStyle,
            amoled = state.amoled,
            onSelectTheme = onSelectThemeMode,
            onToggleDynamic = onSetDynamicColor,
            onSelectPalette = onSelectPalette,
            onSelectStyle = onSelectStyle,
            onToggleAmoled = onSetAmoled,
            onDismiss = { showThemeSheet = false },
        )
    }
}

/** Names a row's Switch for TalkBack (the Switch is its own node, apart from the row's text). */
@Composable
private fun Modifier.labeled(@StringRes title: Int): Modifier {
    val label = stringResource(title)
    return semantics { contentDescription = label }
}

/**
 * Labelled slider for the film-grain overlay (intensity, size). Tracks the drag locally for a smooth thumb and only
 * persists on release (via [Slider.onValueChangeFinished]) so it doesn't spam DataStore mid-drag.
 * Re-seeds from [value] when the persisted value changes elsewhere.
 */
@Composable
private fun GrainIntensityRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    var sliderValue by remember(value) { mutableStateOf(value) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TesseraSpacing.lg, vertical = TesseraSpacing.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChange(sliderValue) },
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFBF4)
@Composable
private fun AppearanceLightPreview() {
    TesseraTheme(darkTheme = false, dynamicColor = false) {
        AppearanceScreen(state = SettingsState())
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101116, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppearanceDarkPreview() {
    TesseraTheme(darkTheme = true, dynamicColor = false) {
        AppearanceScreen(state = SettingsState())
    }
}
