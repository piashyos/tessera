package app.tessera.core.preferences

import app.tessera.ui.notes.NotesViewMode
import kotlinx.coroutines.flow.Flow

interface UserPreferences {
    val themeMode: Flow<ThemeMode>

    /**
     * Last theme emitted by [themeMode] while it was being collected, exposed synchronously.
     * Used to seed UI on (re)creation so the theme doesn't flash to the [ThemeMode.AUTO] default
     * before DataStore re-emits — e.g. when a language change recreates the activity.
     */
    val cachedThemeMode: ThemeMode
    val languageTag: Flow<String>
    val notesViewMode: Flow<NotesViewMode>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setLanguageTag(tag: String)
    suspend fun setNotesViewMode(mode: NotesViewMode)
}
