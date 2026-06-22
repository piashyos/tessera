# Plan D — Settings: Theme Variants + Language Picker

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add user-controlled theme selection (5 options) and language switching (English / Russian, extensible) to the settings screen. Preferences persist via Jetpack DataStore and are applied at the theme/locale layer.

**Theme model:**
| Option | `darkTheme` | `dynamicColor` |
|---|---|---|
| Follow system (default) | `isSystemInDarkTheme()` | `true` (Material You where supported) |
| Light (system colors) | `false` | `true` |
| Dark (system colors) | `true` | `true` |
| Light (static palette) | `false` | `false` |
| Dark (static palette) | `true` | `false` |

This matches the user's ask: "static light / static dark / system light / system dark + auto" — 5 distinct, named options. UX-wise, settings shows them in a single segmented chooser with two rows (Static / System) × two columns (Light / Dark), plus an "Auto" option on top — keeps the model visible without an opaque dropdown.

**Language model:** A simple list of supported locales (English, Russian) with the current selection highlighted. Selecting a locale calls `AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(Locale("ru")))` — Android automatically restarts activities with the new locale; no manual config override needed (per-app language preference API, available API 33+ with backport via AppCompat ≥ 1.6).

**Architecture:**
- `UserPreferencesRepository` backed by `DataStore<Preferences>` exposes `themeMode: Flow<ThemeMode>` and `language: Flow<String>`.
- `MainActivity` collects `themeMode` and passes it to `TesseraTheme` at the root.
- `MainActivity` collects `language` and calls `AppCompatDelegate.setApplicationLocales(...)` when it changes.
- `SettingsViewModel` exposes update methods that write to the repository.
- New composables: `ThemePickerSection`, `LanguagePickerSection` added to `SettingsScreen`.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.datastore:datastore-preferences`, `androidx.appcompat:appcompat` (already in tree? confirm; if not, add).

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `app/src/main/java/app/tessera/core/preferences/UserPreferencesRepository.kt` | Create | DataStore-backed repo: read/write `ThemeMode` + `Locale`. |
| `app/src/main/java/app/tessera/core/preferences/ThemeMode.kt` | Create | Enum: `AUTO`, `LIGHT_DYNAMIC`, `DARK_DYNAMIC`, `LIGHT_STATIC`, `DARK_STATIC` + helper `resolve(isSystemDark): Pair<darkTheme, dynamic>`. |
| `app/src/main/java/app/tessera/core/preferences/PreferencesModule.kt` | Create | Hilt module providing `DataStore<Preferences>` + `UserPreferencesRepository` singletons. |
| `app/src/main/java/app/tessera/MainActivity.kt` | Modify | Collect theme mode + locale prefs, pass to TesseraTheme, apply locale via AppCompatDelegate. |
| `app/src/main/java/app/tessera/core/ui/theme/Theme.kt` | Modify | Accept `themeMode: ThemeMode` parameter; resolve `darkTheme` and `dynamicColor` from it. |
| `app/src/main/java/app/tessera/ui/settings/SettingsScreen.kt` | Modify | Add `ThemePickerSection` and `LanguagePickerSection`. |
| `app/src/main/java/app/tessera/ui/settings/components/ThemePicker.kt` | Create | Compose composable: row of theme tiles with current selection highlighted. |
| `app/src/main/java/app/tessera/ui/settings/components/LanguagePicker.kt` | Create | Compose composable: list of locales with current selection. |
| `app/src/main/java/app/tessera/ui/settings/SettingsViewModel.kt` | Modify | Expose `themeMode: StateFlow`, `language: StateFlow`, plus `setThemeMode()` / `setLanguage()`. |
| `app/src/main/java/app/tessera/ui/settings/SettingsState.kt` | Modify | Add `themeMode` and `language` fields. |
| `app/src/main/res/values/strings.xml` | Modify | Add strings: `settings_section_appearance`, `settings_theme_auto`, `settings_theme_light_dynamic`, …, `settings_section_language`, `settings_language_en`, `settings_language_ru`. |
| `app/src/main/res/values-ru/strings.xml` | Modify | Add Russian translations for all new strings. |
| `gradle/libs.versions.toml` | Modify | Add `datastore-preferences = "1.1.1"` (or current), `appcompat = "1.7.0"` if missing. |
| `app/src/test/java/app/tessera/core/preferences/UserPreferencesRepositoryTest.kt` | Create | Verify read/write round-trip + default fallback. |
| `app/src/test/java/app/tessera/ui/settings/SettingsViewModelTest.kt` | Modify | Cover theme + locale flows. |

---

## Chunk 1: Preferences foundation

### Task 1: Dependency setup

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add DataStore + AppCompat (if missing)**

`libs.versions.toml`:
```toml
[versions]
datastore = "1.1.1"
appcompat = "1.7.0"

[libraries]
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
```

`app/build.gradle.kts` `dependencies { ... }`:
```kotlin
implementation(libs.androidx.datastore.preferences)
implementation(libs.androidx.appcompat)
```

- [ ] **Step 2: Sync + build**

`./gradlew :app:assembleDebug` — expect BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add DataStore + AppCompat dependencies for prefs and locale switching"
```

### Task 2: ThemeMode enum

**Files:**
- Create: `app/src/main/java/app/tessera/core/preferences/ThemeMode.kt`

- [ ] **Step 1: Write the enum + resolver**

```kotlin
package app.tessera.core.preferences

enum class ThemeMode {
    AUTO,
    LIGHT_DYNAMIC,
    DARK_DYNAMIC,
    LIGHT_STATIC,
    DARK_STATIC;

    /** Resolve to (darkTheme, dynamicColor) given the current system dark state. */
    fun resolve(isSystemDark: Boolean): Pair<Boolean, Boolean> = when (this) {
        AUTO -> isSystemDark to true
        LIGHT_DYNAMIC -> false to true
        DARK_DYNAMIC -> true to true
        LIGHT_STATIC -> false to false
        DARK_STATIC -> true to false
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/app/tessera/core/preferences/ThemeMode.kt
git commit -m "feat(prefs): ThemeMode enum with resolver"
```

### Task 3: UserPreferencesRepository

**Files:**
- Create: `app/src/main/java/app/tessera/core/preferences/UserPreferencesRepository.kt`
- Create: `app/src/main/java/app/tessera/core/preferences/PreferencesModule.kt`
- Create: `app/src/test/java/app/tessera/core/preferences/UserPreferencesRepositoryTest.kt`

- [ ] **Step 1: Write the repository**

```kotlin
package app.tessera.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefsDataStore by preferencesDataStore(name = "user_prefs")

private val KEY_THEME = stringPreferencesKey("theme_mode")
private val KEY_LANG = stringPreferencesKey("language_tag")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store: DataStore<Preferences> = context.userPrefsDataStore

    val themeMode: Flow<ThemeMode> = store.data.map { p ->
        p[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.AUTO
    }

    /** Empty string = follow system default. */
    val languageTag: Flow<String> = store.data.map { it[KEY_LANG] ?: "" }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setLanguageTag(tag: String) {
        store.edit { it[KEY_LANG] = tag }
    }
}
```

- [ ] **Step 2: Hilt module**

```kotlin
package app.tessera.core.preferences

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule
```

(Repository is already `@Inject constructor` + `@Singleton`, Hilt picks it up automatically. The empty module is for any future bindings.)

- [ ] **Step 3: Repository unit test**

```kotlin
@RunWith(RobolectricTestRunner::class)
class UserPreferencesRepositoryTest {
    @Test fun `default theme is AUTO`() = runBlocking {
        val repo = UserPreferencesRepository(ApplicationProvider.getApplicationContext())
        assertEquals(ThemeMode.AUTO, repo.themeMode.first())
    }

    @Test fun `set then read theme persists`() = runBlocking {
        val repo = UserPreferencesRepository(ApplicationProvider.getApplicationContext())
        repo.setThemeMode(ThemeMode.DARK_STATIC)
        assertEquals(ThemeMode.DARK_STATIC, repo.themeMode.first())
    }
}
```

- [ ] **Step 4: Run tests**

`./gradlew :app:testDebugUnitTest --tests "app.tessera.core.preferences.UserPreferencesRepositoryTest"`. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/core/preferences/ \
        app/src/test/java/app/tessera/core/preferences/
git commit -m "feat(prefs): DataStore-backed UserPreferencesRepository"
```

---

## Chunk 2: Theme application

### Task 4: Plumb ThemeMode through TesseraTheme

**Files:**
- Modify: `app/src/main/java/app/tessera/core/ui/theme/Theme.kt`
- Modify: `app/src/main/java/app/tessera/MainActivity.kt`

- [ ] **Step 1: Update TesseraTheme signature**

`Theme.kt`:
```kotlin
@Composable
fun TesseraTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    content: @Composable () -> Unit,
) {
    val (darkTheme, dynamicColor) = themeMode.resolve(isSystemInDarkTheme())
    // existing colorScheme resolution using these booleans
    ...
}
```

Keep the old `darkTheme` / `dynamicColor` overload behind `@Deprecated` if compatibility matters; or update all call sites.

- [ ] **Step 2: Update MainActivity to inject preferences**

`MainActivity.kt`:
```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var prefs: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by prefs.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.AUTO)
            val languageTag by prefs.languageTag.collectAsStateWithLifecycle(initialValue = "")

            LaunchedEffect(languageTag) {
                val locales = if (languageTag.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(languageTag)
                }
                AppCompatDelegate.setApplicationLocales(locales)
            }

            TesseraTheme(themeMode = themeMode) {
                TesseraApp()
            }
        }
    }
}
```

- [ ] **Step 3: Build + smoke test**

`./gradlew :app:assembleDebug` + run on emulator. Default behavior unchanged (theme=AUTO, locale=system).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/theme/Theme.kt \
        app/src/main/java/app/tessera/MainActivity.kt
git commit -m "feat(theme): plumb ThemeMode preference through TesseraTheme"
```

---

## Chunk 3: Settings UI — theme picker

### Task 5: ThemePicker composable

**Files:**
- Create: `app/src/main/java/app/tessera/ui/settings/components/ThemePicker.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`

- [ ] **Step 1: Strings**

`values/strings.xml`:
```xml
<string name="settings_section_appearance">Appearance</string>
<string name="settings_theme_auto">Auto</string>
<string name="settings_theme_light_dynamic">Light</string>
<string name="settings_theme_dark_dynamic">Dark</string>
<string name="settings_theme_light_static">Light · Classic</string>
<string name="settings_theme_dark_static">Dark · Classic</string>
<string name="settings_theme_static_hint">Classic uses fixed colors (no Material You)</string>
```

`values-ru/strings.xml`:
```xml
<string name="settings_section_appearance">Внешний вид</string>
<string name="settings_theme_auto">Авто</string>
<string name="settings_theme_light_dynamic">Светлая</string>
<string name="settings_theme_dark_dynamic">Тёмная</string>
<string name="settings_theme_light_static">Светлая · Классика</string>
<string name="settings_theme_dark_static">Тёмная · Классика</string>
<string name="settings_theme_static_hint">Классика — фиксированные цвета (без Material You)</string>
```

- [ ] **Step 2: Composable**

```kotlin
@Composable
fun ThemePicker(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Row 1: Auto (full width)
        ThemeTile(
            label = stringResource(R.string.settings_theme_auto),
            selected = selected == ThemeMode.AUTO,
            onClick = { onSelect(ThemeMode.AUTO) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Row 2: Light / Dark dynamic
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeTile(
                label = stringResource(R.string.settings_theme_light_dynamic),
                selected = selected == ThemeMode.LIGHT_DYNAMIC,
                onClick = { onSelect(ThemeMode.LIGHT_DYNAMIC) },
                modifier = Modifier.weight(1f),
            )
            ThemeTile(
                label = stringResource(R.string.settings_theme_dark_dynamic),
                selected = selected == ThemeMode.DARK_DYNAMIC,
                onClick = { onSelect(ThemeMode.DARK_DYNAMIC) },
                modifier = Modifier.weight(1f),
            )
        }
        // Row 3: Light / Dark static
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeTile(
                label = stringResource(R.string.settings_theme_light_static),
                selected = selected == ThemeMode.LIGHT_STATIC,
                onClick = { onSelect(ThemeMode.LIGHT_STATIC) },
                modifier = Modifier.weight(1f),
            )
            ThemeTile(
                label = stringResource(R.string.settings_theme_dark_static),
                selected = selected == ThemeMode.DARK_STATIC,
                onClick = { onSelect(ThemeMode.DARK_STATIC) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.settings_theme_static_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemeTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.weight(1f))
            if (selected) {
                Icon(
                    imageVector = Lucide.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/tessera/ui/settings/components/ThemePicker.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-ru/strings.xml
git commit -m "feat(settings): ThemePicker composable with 5 modes"
```

### Task 6: Wire ThemePicker into SettingsScreen + ViewModel

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/app/tessera/ui/settings/SettingsState.kt`
- Modify: `app/src/main/java/app/tessera/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Extend state + ViewModel**

`SettingsState.kt`:
```kotlin
data class SettingsState(
    /* existing fields */,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val languageTag: String = "",
)
```

`SettingsViewModel.kt` constructor: add `private val prefs: UserPreferencesRepository`.

Combine the new flows with existing state via `combine(..., prefs.themeMode, prefs.languageTag) { auth, theme, lang -> auth.copy(themeMode = theme, languageTag = lang) }`.

Add:
```kotlin
fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }
fun setLanguage(tag: String) = viewModelScope.launch { prefs.setLanguageTag(tag) }
```

- [ ] **Step 2: Render in SettingsScreen**

Insert above the existing account section:

```kotlin
SectionHeader(text = stringResource(R.string.settings_section_appearance))
ThemePicker(
    selected = state.themeMode,
    onSelect = onSelectThemeMode,
)
```

Add `onSelectThemeMode: (ThemeMode) -> Unit` to `SettingsScreen`'s signature and wire it from the container (`SettingsRoute` host).

- [ ] **Step 3: Smoke test**

Open Settings → tap **Dark · Classic**. The theme applies immediately across the app (because MainActivity collects `themeMode` and recomposes TesseraTheme). Restart the app — preference persists.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/ui/settings/SettingsViewModel.kt \
        app/src/main/java/app/tessera/ui/settings/SettingsState.kt \
        app/src/main/java/app/tessera/ui/settings/SettingsScreen.kt
git commit -m "feat(settings): theme picker section wired through ViewModel"
```

---

## Chunk 4: Language picker

### Task 7: LanguagePicker composable + wiring

**Files:**
- Create: `app/src/main/java/app/tessera/ui/settings/components/LanguagePicker.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Modify: `app/src/main/java/app/tessera/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Strings**

`values/strings.xml`:
```xml
<string name="settings_section_language">Language</string>
<string name="settings_language_system">System default</string>
<string name="settings_language_en">English</string>
<string name="settings_language_ru">Русский</string>
```

(In `values-ru/strings.xml` — translate accordingly: `Язык`, `Системный`, `English` (keep English label since native), `Русский`.)

- [ ] **Step 2: Composable**

```kotlin
@Composable
fun LanguagePicker(
    selectedTag: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        "" to R.string.settings_language_system,
        "en" to R.string.settings_language_en,
        "ru" to R.string.settings_language_ru,
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (tag, labelRes) ->
            ThemeTile(  // reuse the same selectable-row visual
                label = stringResource(labelRes),
                selected = selectedTag == tag,
                onClick = { onSelect(tag) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
```

> Reuse `ThemeTile` since it's a generic selectable row. Consider extracting it as `SelectableSettingsTile` and keep both pickers using it. Simple rename + move.

- [ ] **Step 3: Render in SettingsScreen + wire ViewModel**

```kotlin
SectionHeader(text = stringResource(R.string.settings_section_language))
LanguagePicker(
    selectedTag = state.languageTag,
    onSelect = onSelectLanguage,
)
```

Add `onSelectLanguage: (String) -> Unit` to the screen signature, plumbed to `viewModel::setLanguage`.

- [ ] **Step 4: Smoke test**

Settings → Language → Русский. Verify the activity restarts with Russian strings. Restart the app — preference holds.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/ui/settings/components/LanguagePicker.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-ru/strings.xml \
        app/src/main/java/app/tessera/ui/settings/SettingsScreen.kt
git commit -m "feat(settings): language picker with system / en / ru options"
```

---

## Chunk 5: Verification

### Task 8: End-to-end manual pass

- [ ] **Step 1**: Run emulator. Open Settings.
- [ ] **Step 2**: Test all 5 theme options. Confirm each visibly changes. Toggle device system dark/light while on AUTO — app follows.
- [ ] **Step 3**: Switch language to Russian — all strings change. Switch back to English. Switch to System — follows device locale.
- [ ] **Step 4**: Kill + relaunch the app — last selections persist.
- [ ] **Step 5**: `./gradlew :app:testDebugUnitTest` — all PASS.
- [ ] **Step 6**: `./gradlew :app:lintDebug` — no new errors.
- [ ] **Step 7**: @superpowers:finishing-a-development-branch

---

## Risks / Open Questions

- **Locale switching on API < 33** (Task 4): `AppCompatDelegate.setApplicationLocales` handles the backport. Confirm `androidx.appcompat:1.6+` (or 1.7) is on classpath; the MainActivity must extend `AppCompatActivity` (or `ComponentActivity` with the AppCompat delegate). The plan assumes `ComponentActivity` is fine; verify in `MainActivity.kt`. If `ComponentActivity` doesn't work, switch to `AppCompatActivity`.
- **Restart-free locale switch**: per-app locales API restarts the activity automatically. The unsaved-edit state in NoteEditorScreen could be lost if the user is editing during a switch. Save-on-language-change is out of scope for this plan — flag in docs.
- **Dynamic color support**: only available on Android 12+. On older devices, the LIGHT_DYNAMIC / DARK_DYNAMIC options effectively behave the same as LIGHT_STATIC / DARK_STATIC. The hint string clarifies "Classic = no Material You" — consider also clarifying that the dynamic variants fall back to a default Material 3 palette on older OS versions.
- **Settings ViewModel state combining**: ensure the existing `combine(authRepository.currentProfile, uiState)` is extended without breaking the auth flow. Add the new flows as additional sources in the same `combine`.
