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
