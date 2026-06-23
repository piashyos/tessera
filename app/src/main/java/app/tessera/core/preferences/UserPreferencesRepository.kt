package app.tessera.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

    override val themeMode: Flow<ThemeMode> = store.data.map { p ->
        p[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.AUTO
    }.onEach { lastThemeMode = it }

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
}
