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
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefsDataStore by preferencesDataStore(name = "user_prefs")

private val KEY_THEME = stringPreferencesKey("theme_mode")
private val KEY_LANG = stringPreferencesKey("language_tag")
private val KEY_VIEW_MODE = stringPreferencesKey("notes_view_mode")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : UserPreferences {
    private val store: DataStore<Preferences> = context.userPrefsDataStore

    override val themeMode: Flow<ThemeMode> = store.data.map { p ->
        p[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.AUTO
    }

    /** Empty string = follow system default. */
    override val languageTag: Flow<String> = store.data.map { it[KEY_LANG] ?: "" }

    override val notesViewMode: Flow<NotesViewMode> = store.data.map { p ->
        p[KEY_VIEW_MODE]?.let { runCatching { NotesViewMode.valueOf(it) }.getOrNull() } ?: NotesViewMode.List
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
}
