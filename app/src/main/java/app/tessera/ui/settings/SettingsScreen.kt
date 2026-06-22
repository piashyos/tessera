package app.tessera.ui.settings

import android.content.res.Configuration
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.TesseraSpacing
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.sync.SyncIndicator
import app.tessera.ui.settings.components.AccountSwitchDialog
import app.tessera.ui.settings.components.LanguagePickerSheet
import app.tessera.ui.settings.components.SettingsChevron
import app.tessera.ui.settings.components.SettingsDivider
import app.tessera.ui.settings.components.SettingsGroup
import app.tessera.ui.settings.components.SettingsRow
import app.tessera.ui.settings.components.SettingsValue
import app.tessera.ui.settings.components.SyncStatusRow
import app.tessera.ui.settings.components.ThemePickerSheet
import app.tessera.ui.settings.components.ThemeChoice
import app.tessera.ui.settings.components.toThemeChoice
import coil.compose.AsyncImage
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Languages
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.Scale
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Upload
import com.composables.icons.lucide.User

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
    onSelectLanguage: (String) -> Unit = {},
    onRetrySync: () -> Unit = {},
) {
    var showThemeSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(UiTestTags.SettingsRoot)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
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
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
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
                    onOpenThemeSheet = { showThemeSheet = true },
                    onOpenLanguageSheet = { showLanguageSheet = true },
                )
            }

            item { DataSection() }

            item { AboutSection() }
        }
    }

    if (showThemeSheet) {
        ThemePickerSheet(
            selected = state.themeMode,
            onSelect = onSelectThemeMode,
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
    onOpenThemeSheet: () -> Unit,
    onOpenLanguageSheet: () -> Unit,
) {
    SettingsGroup(label = stringResource(R.string.settings_section_appearance)) {
        SettingsRow(
            leadingIcon = Lucide.Palette,
            title = stringResource(R.string.settings_theme_sheet_title),
            onClick = onOpenThemeSheet,
            trailing = { ValueChevron(themeChoiceLabel(themeMode)) },
        )
        SettingsDivider()
        SettingsRow(
            leadingIcon = Lucide.Languages,
            title = stringResource(R.string.settings_section_language),
            onClick = onOpenLanguageSheet,
            trailing = { ValueChevron(languageLabel(languageTag)) },
        )
    }
}

@Composable
private fun DataSection() {
    SettingsGroup(label = stringResource(R.string.settings_section_data)) {
        SettingsRow(
            leadingIcon = Lucide.Download,
            title = stringResource(R.string.settings_data_export),
            onClick = {},
            trailing = { SettingsChevron() },
        )
        SettingsDivider()
        SettingsRow(
            leadingIcon = Lucide.Upload,
            title = stringResource(R.string.settings_data_import),
            onClick = {},
            trailing = { SettingsChevron() },
        )
        SettingsDivider()
        SettingsRow(
            leadingIcon = Lucide.Trash2,
            title = stringResource(R.string.settings_data_empty_trash),
            destructive = true,
            onClick = {},
            trailing = { SettingsChevron() },
        )
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val appVersion = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: FallbackAppVersion
    }
    SettingsGroup(label = stringResource(R.string.settings_section_about)) {
        SettingsRow(
            leadingIcon = Lucide.Info,
            title = stringResource(R.string.settings_about),
            subtitle = stringResource(R.string.settings_version, appVersion),
        )
        SettingsDivider()
        SettingsRow(
            leadingIcon = Lucide.Scale,
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
private fun themeChoiceLabel(mode: ThemeMode): String = when (mode.toThemeChoice()) {
    ThemeChoice.System -> stringResource(R.string.settings_theme_system)
    ThemeChoice.Light -> stringResource(R.string.settings_theme_light)
    ThemeChoice.Dark -> stringResource(R.string.settings_theme_dark)
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
                imageVector = Lucide.User,
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
