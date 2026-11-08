package app.tessera.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.tessera.ui.notes.NotesViewMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefsDataStore by preferencesDataStore(name = "user_prefs")

private val KEY_THEME = stringPreferencesKey("theme_mode")
private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
private val KEY_PALETTE = stringPreferencesKey("theme_palette")
private val KEY_STYLE = stringPreferencesKey("theme_style")
private val KEY_AMOLED = booleanPreferencesKey("theme_amoled")
private val KEY_GRAIN_ENABLED = booleanPreferencesKey("grain_enabled")
private val KEY_GRAIN_INTENSITY = floatPreferencesKey("grain_intensity")
private val KEY_LANG = stringPreferencesKey("language_tag")
private val KEY_VIEW_MODE = stringPreferencesKey("notes_view_mode")
private val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches")

private const val RECENT_SEARCHES_LIMIT = 8
private const val RECENT_SEARCHES_DELIMITER = "\n"

/** Grain is on by default at a subtle mid strength, matching the Arc-style baseline. */
private const val DEFAULT_GRAIN_ENABLED = true
private const val DEFAULT_GRAIN_INTENSITY = 0.5f

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : UserPreferences {
    private val store: DataStore<Preferences> = context.userPrefsDataStore

    @Volatile
    private var lastThemeMode: ThemeMode = ThemeMode.AUTO
    override val cachedThemeMode: ThemeMode get() = lastThemeMode

    @Volatile
    private var lastDynamicColor: Boolean = true
    override val cachedDynamicColor: Boolean get() = lastDynamicColor

    @Volatile
    private var lastPalette: ThemePalette = ThemePalette.Default
    override val cachedThemePalette: ThemePalette get() = lastPalette

    override val themeMode: Flow<ThemeMode> = store.data.map { p ->
        parseThemeMode(p[KEY_THEME])
    }.onEach { lastThemeMode = it }

    override val dynamicColor: Flow<Boolean> = store.data.map { p ->
        // No stored flag yet (fresh install or upgrade from the old combined ThemeMode): default
        // on, except when the legacy mode was an explicit *_STATIC choice (dynamic deliberately off).
        p[KEY_DYNAMIC_COLOR] ?: legacyDynamicDefault(p[KEY_THEME])
    }.onEach { lastDynamicColor = it }

    override val themePalette: Flow<ThemePalette> = store.data.map { p ->
        ThemePalette.fromName(p[KEY_PALETTE])
    }.onEach { lastPalette = it }

    @Volatile
    private var lastStyle: ThemeStyle = ThemeStyle.Default
    override val cachedThemeStyle: ThemeStyle get() = lastStyle

    @Volatile
    private var lastAmoled: Boolean = false
    override val cachedAmoled: Boolean get() = lastAmoled

    override val themeStyle: Flow<ThemeStyle> = store.data.map { p ->
        ThemeStyle.fromName(p[KEY_STYLE])
    }.onEach { lastStyle = it }

    override val amoled: Flow<Boolean> = store.data.map { p ->
        p[KEY_AMOLED] ?: false
    }.onEach { lastAmoled = it }

    @Volatile
    private var lastGrainEnabled: Boolean = DEFAULT_GRAIN_ENABLED
    override val cachedGrainEnabled: Boolean get() = lastGrainEnabled

    @Volatile
    private var lastGrainIntensity: Float = DEFAULT_GRAIN_INTENSITY
    override val cachedGrainIntensity: Float get() = lastGrainIntensity

    override val grainEnabled: Flow<Boolean> = store.data.map { p ->
        p[KEY_GRAIN_ENABLED] ?: DEFAULT_GRAIN_ENABLED
    }.onEach { lastGrainEnabled = it }

    override val grainIntensity: Flow<Float> = store.data.map { p ->
        (p[KEY_GRAIN_INTENSITY] ?: DEFAULT_GRAIN_INTENSITY).coerceIn(0f, 1f)
    }.onEach { lastGrainIntensity = it }

    /** Empty string = follow system default. */
    override val languageTag: Flow<String> = store.data.map { it[KEY_LANG] ?: "" }

    override val notesViewMode: Flow<NotesViewMode> = store.data.map { p ->
        p[KEY_VIEW_MODE]?.let { runCatching { NotesViewMode.valueOf(it) }.getOrNull() } ?: NotesViewMode.List
    }

    override val recentSearches: Flow<List<String>> = store.data.map { p ->
        p[KEY_RECENT_SEARCHES]
            ?.split(RECENT_SEARCHES_DELIMITER)
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[KEY_THEME] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setThemePalette(palette: ThemePalette) {
        store.edit { it[KEY_PALETTE] = palette.name }
    }

    override suspend fun setThemeStyle(style: ThemeStyle) {
        store.edit { it[KEY_STYLE] = style.name }
    }

    override suspend fun setAmoled(enabled: Boolean) {
        store.edit { it[KEY_AMOLED] = enabled }
    }

    override suspend fun setGrainEnabled(enabled: Boolean) {
        store.edit { it[KEY_GRAIN_ENABLED] = enabled }
    }

    override suspend fun setGrainIntensity(intensity: Float) {
        store.edit { it[KEY_GRAIN_INTENSITY] = intensity.coerceIn(0f, 1f) }
    }

    override suspend fun setLanguageTag(tag: String) {
        store.edit { it[KEY_LANG] = tag }
    }

    override suspend fun setNotesViewMode(mode: NotesViewMode) {
        store.edit { it[KEY_VIEW_MODE] = mode.name }
    }

    override suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        store.edit { prefs ->
            val current = prefs[KEY_RECENT_SEARCHES]
                ?.split(RECENT_SEARCHES_DELIMITER)
                ?.filter { it.isNotBlank() }
                .orEmpty()
            val updated = (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) })
                .take(RECENT_SEARCHES_LIMIT)
            prefs[KEY_RECENT_SEARCHES] = updated.joinToString(RECENT_SEARCHES_DELIMITER)
        }
    }

    override suspend fun removeRecentSearch(query: String) {
        store.edit { prefs ->
            val updated = prefs[KEY_RECENT_SEARCHES]
                ?.split(RECENT_SEARCHES_DELIMITER)
                ?.filter { it.isNotBlank() && !it.equals(query, ignoreCase = true) }
                .orEmpty()
            prefs[KEY_RECENT_SEARCHES] = updated.joinToString(RECENT_SEARCHES_DELIMITER)
        }
    }

    override suspend fun clearRecentSearches() {
        store.edit { it.remove(KEY_RECENT_SEARCHES) }
    }

    /**
     * Read the persisted theme, mapping the legacy combined values (which encoded dynamic color in
     * the name) onto the light/dark-only [ThemeMode]. Dynamic color now lives in [KEY_DYNAMIC_COLOR].
     */
    private fun parseThemeMode(raw: String?): ThemeMode = when (raw) {
        "AUTO" -> ThemeMode.AUTO
        "LIGHT", "LIGHT_DYNAMIC", "LIGHT_STATIC" -> ThemeMode.LIGHT
        "DARK", "DARK_DYNAMIC", "DARK_STATIC" -> ThemeMode.DARK
        else -> ThemeMode.AUTO
    }

    private fun legacyDynamicDefault(rawTheme: String?): Boolean = when (rawTheme) {
        "LIGHT_STATIC", "DARK_STATIC" -> false
        else -> true
    }
}
