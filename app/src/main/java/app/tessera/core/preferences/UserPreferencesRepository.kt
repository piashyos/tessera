package app.tessera.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
private val KEY_LANG = stringPreferencesKey("language_tag")
private val KEY_VIEW_MODE = stringPreferencesKey("notes_view_mode")
private val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches")

private const val RECENT_SEARCHES_LIMIT = 8
private const val RECENT_SEARCHES_DELIMITER = "\n"

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
