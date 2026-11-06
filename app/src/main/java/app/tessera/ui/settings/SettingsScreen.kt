package app.tessera.ui.settings

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.TesseraSpacing
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.sync.SyncIndicator
import app.tessera.ui.settings.components.AccountSwitchDialog
import app.tessera.ui.settings.components.EmptyTrashDialog
import app.tessera.ui.settings.components.LanguagePickerSheet
import app.tessera.ui.settings.components.SettingsChevron
import app.tessera.ui.settings.components.SettingsDivider
import app.tessera.ui.settings.components.SettingsGroup
import app.tessera.ui.settings.components.SettingsRow
import app.tessera.ui.settings.components.SettingsValue
import app.tessera.ui.settings.components.SyncStatusRow
import app.tessera.ui.settings.components.ThemePickerSheet
import coil.compose.AsyncImage
import java.io.OutputStream
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person

private const val FallbackAppVersion = "0.1.0"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onConfirmMove: () -> Unit = {},
    onDeleteLocal: () -> Unit = {},
    onDismissDialog: () -> Unit = {},
    onSelectThemeMode: (ThemeMode) -> Unit = {},
    onSetDynamicColor: (Boolean) -> Unit = {},
    onSelectPalette: (ThemePalette) -> Unit = {},
    onSelectStyle: (app.tessera.core.preferences.ThemeStyle) -> Unit = {},
    onSetAmoled: (Boolean) -> Unit = {},
    onSetGrainEnabled: (Boolean) -> Unit = {},
    onSetGrainIntensity: (Float) -> Unit = {},
    onSelectLanguage: (String) -> Unit = {},
    onRetrySync: () -> Unit = {},
    onExport: (OutputStream) -> Unit = {},
    onEmptyTrash: () -> Unit = {},
    onMessageShown: () -> Unit = {},
    /** Debug-only rows (the new-editor switch); the caller decides, see `NotesNavGraph`. */
    showDebugOptions: Boolean = false,
    onSetUseNativeEditor: (Boolean) -> Unit = {},
) {
    var showThemeSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val context = LocalContext.current
    val exportFileName = stringResource(R.string.settings_export_filename)
    val exportFailedText = stringResource(R.string.settings_export_failed)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Open the destination here (UI owns Context); hand the stream to the VM to fill.
        val out = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
        if (out != null) {
            onExport(out)
        } else {
            Toast.makeText(context, exportFailedText, Toast.LENGTH_SHORT).show()
        }
    }

    // Surface one-shot results (export done / trash emptied) as a toast, then clear.
    val currentOnMessageShown by rememberUpdatedState(onMessageShown)
    LaunchedEffect(state.message) {
        val text = when (val message = state.message) {
            SettingsMessage.TrashEmptied -> context.getString(R.string.settings_trash_emptied)
            is SettingsMessage.ExportSuccess -> context.resources.getQuantityString(
                R.plurals.settings_export_success,
                message.count,
                message.count,
            )
            SettingsMessage.ExportFailed -> exportFailedText
            null -> null
        }
        if (text != null) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            currentOnMessageShown()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.SettingsRoot)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        // Transparent so the app-wide background + film grain shows through; the settings cards
        // paint their own opaque surface on top and stay grain-free.
        containerColor = Color.Transparent,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(UiTestTags.SettingsBack),
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
                AccountSection(
                    state = state,
                    onSignIn = onSignIn,
                    onSignOut = onSignOut,
                )
            }

            if (state.isSignedIn) {
                item {
                    SettingsGroup(label = stringResource(R.string.settings_section_sync)) {
                        SyncStatusRow(indicator = state.syncStatus, onRetry = onRetrySync)
                    }
                }
            }

            item {
                AppearanceSection(
                    themeMode = state.themeMode,
                    languageTag = state.languageTag,
                    grainEnabled = state.grainEnabled,
                    grainIntensity = state.grainIntensity,
                    onOpenThemeSheet = { showThemeSheet = true },
                    onOpenLanguageSheet = { showLanguageSheet = true },
                    onSetGrainEnabled = onSetGrainEnabled,
                    onSetGrainIntensity = onSetGrainIntensity,
                )
            }

            item {
                DataSection(
                    isExporting = state.isExporting,
                    trashCount = state.trashCount,
                    isEmptyingTrash = state.isEmptyingTrash,
                    onExport = { exportLauncher.launch(exportFileName) },
                    onEmptyTrash = { showEmptyTrashDialog = true },
                )
            }

            item {
                AboutSection(
                    showDebugOptions = showDebugOptions,
                    useNativeEditor = state.useNativeEditor,
                    onSetUseNativeEditor = onSetUseNativeEditor,
                )
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

    if (showLanguageSheet) {
        LanguagePickerSheet(
            selectedTag = state.languageTag,
            onSelect = onSelectLanguage,
            onDismiss = { showLanguageSheet = false },
        )
    }

    if (showEmptyTrashDialog) {
        EmptyTrashDialog(
            count = state.trashCount,
            onConfirm = {
                showEmptyTrashDialog = false
                onEmptyTrash()
            },
            onDismiss = { showEmptyTrashDialog = false },
        )
    }

    state.pendingSwitch?.let {
        AccountSwitchDialog(
            onMove = onConfirmMove,
            onDelete = onDeleteLocal,
            onDismiss = onDismissDialog,
        )
    }
}

@Composable
private fun AccountSection(
    state: SettingsState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    SettingsGroup(label = stringResource(R.string.settings_section_account)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(TesseraSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(TesseraSpacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountAvatar(photoUrl = state.photoUrl)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = state.displayName?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.settings_account),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = state.email
                            ?: state.userId
                            ?: stringResource(R.string.settings_signed_out),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!state.isSignedIn) {
                Text(
                    text = stringResource(R.string.settings_signed_out_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.isSignedIn) {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.SettingsSignOut),
                ) {
                    Text(stringResource(R.string.settings_sign_out))
                }
            } else {
                Button(
                    onClick = onSignIn,
                    enabled = !state.isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTestTags.SettingsSignIn),
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(TesseraSpacing.sm))
                    }
                    Text(stringResource(R.string.settings_sign_in))
                }
            }

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AppearanceSection(
    themeMode: ThemeMode,
    languageTag: String,
    grainEnabled: Boolean,
    grainIntensity: Float,
    onOpenThemeSheet: () -> Unit,
    onOpenLanguageSheet: () -> Unit,
    onSetGrainEnabled: (Boolean) -> Unit,
    onSetGrainIntensity: (Float) -> Unit,
) {
    SettingsGroup(label = stringResource(R.string.settings_section_appearance)) {
        SettingsRow(
            leadingIcon = Icons.Rounded.Palette,
            title = stringResource(R.string.settings_theme_sheet_title),
            onClick = onOpenThemeSheet,
            trailing = { ValueChevron(themeChoiceLabel(themeMode)) },
        )
        SettingsDivider()
        SettingsRow(
            leadingIcon = Icons.Rounded.Language,
            title = stringResource(R.string.settings_section_language),
            onClick = onOpenLanguageSheet,
            trailing = { ValueChevron(languageLabel(languageTag)) },
        )
        SettingsDivider()
        SettingsRow(
            leadingIcon = Icons.Rounded.Grain,
            title = stringResource(R.string.settings_grain_title),
            subtitle = stringResource(R.string.settings_grain_subtitle),
            onClick = { onSetGrainEnabled(!grainEnabled) },
            trailing = {
                Switch(checked = grainEnabled, onCheckedChange = onSetGrainEnabled)
            },
        )
        if (grainEnabled) {
            GrainIntensityRow(
                intensity = grainIntensity,
                onIntensityChange = onSetGrainIntensity,
            )
        }
    }
}

/**
 * Intensity slider for the film-grain overlay. Tracks the drag locally for a smooth thumb and only
 * persists on release (via [Slider.onValueChangeFinished]) so it doesn't spam DataStore mid-drag.
 * Re-seeds from [intensity] when the persisted value changes elsewhere.
 */
@Composable
private fun GrainIntensityRow(
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
) {
    var sliderValue by remember(intensity) { mutableStateOf(intensity) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TesseraSpacing.lg, vertical = TesseraSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.settings_grain_intensity),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onIntensityChange(sliderValue) },
            valueRange = 0f..1f,
        )
    }
}

@Composable
private fun DataSection(
    isExporting: Boolean,
    trashCount: Int,
    isEmptyingTrash: Boolean,
    onExport: () -> Unit,
    onEmptyTrash: () -> Unit,
) {
    val trashEmpty = trashCount == 0
    SettingsGroup(label = stringResource(R.string.settings_section_data)) {
        SettingsRow(
            leadingIcon = Icons.Rounded.Download,
            title = stringResource(R.string.settings_data_export),
            subtitle = stringResource(R.string.settings_data_export_subtitle),
            // Block re-entry while a write is in flight; show progress instead of the chevron.
            onClick = if (isExporting) null else onExport,
            trailing = { if (isExporting) RowSpinner() else SettingsChevron() },
        )
        SettingsDivider()
        SettingsRow(
            leadingIcon = Icons.Rounded.Delete,
            title = stringResource(R.string.settings_data_empty_trash),
            subtitle = if (trashEmpty) {
                stringResource(R.string.settings_data_empty_trash_empty)
            } else {
                pluralStringResource(
                    R.plurals.settings_data_empty_trash_count,
                    trashCount,
                    trashCount,
                )
            },
            destructive = !trashEmpty,
            // Disabled (non-clickable, no chevron) when there's nothing to empty.
            onClick = if (trashEmpty || isEmptyingTrash) null else onEmptyTrash,
            trailing = {
                when {
                    isEmptyingTrash -> RowSpinner()
                    !trashEmpty -> SettingsChevron()
                }
            },
        )
    }
}

@Composable
private fun RowSpinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(20.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AboutSection(
    showDebugOptions: Boolean,
    useNativeEditor: Boolean,
    onSetUseNativeEditor: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val appVersion = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: FallbackAppVersion
    }
    SettingsGroup(label = stringResource(R.string.settings_section_about)) {
        SettingsRow(
            leadingIcon = Icons.Rounded.Info,
            title = stringResource(R.string.settings_about),
            subtitle = stringResource(R.string.settings_version, appVersion),
        )
        if (showDebugOptions) {
            SettingsDivider()
            SettingsRow(
                leadingIcon = Icons.Rounded.EditNote,
                title = stringResource(R.string.settings_native_editor),
                onClick = { onSetUseNativeEditor(!useNativeEditor) },
                trailing = {
                    Switch(checked = useNativeEditor, onCheckedChange = onSetUseNativeEditor)
                },
            )
        }
        SettingsDivider()
        SettingsRow(
            leadingIcon = Icons.Rounded.Balance,
            title = stringResource(R.string.settings_licenses),
            onClick = {},
            trailing = { SettingsChevron() },
        )
    }
}

@Composable
private fun ValueChevron(value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.xs),
    ) {
        SettingsValue(value)
        SettingsChevron()
    }
}

@Composable
private fun themeChoiceLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.AUTO -> stringResource(R.string.settings_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
}

@Composable
private fun languageLabel(tag: String): String = when (tag) {
    "en" -> stringResource(R.string.settings_language_en)
    "ru" -> stringResource(R.string.settings_language_ru)
    else -> stringResource(R.string.settings_language_system)
}

@Composable
private fun AccountAvatar(photoUrl: String?) {
    val avatarModifier = Modifier
        .size(48.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer)

    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = stringResource(R.string.settings_account),
            modifier = avatarModifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier = avatarModifier, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFBF4)
@Composable
private fun SettingsSignedOutPreview() {
    TesseraTheme(darkTheme = false, dynamicColor = false) {
        SettingsScreen(state = SettingsState(isSignedIn = false))
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF101116,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsSignedInPreview() {
    TesseraTheme(darkTheme = true, dynamicColor = false) {
        SettingsScreen(
            state = SettingsState(
                isSignedIn = true,
                userId = "uid-123",
                displayName = "Jane Doe",
                email = "jane@example.com",
                syncStatus = SyncIndicator.Ok(System.currentTimeMillis()),
            ),
        )
    }
}
