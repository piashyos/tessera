package app.tessera.core.preferences

import app.tessera.ui.notes.NotesViewMode
import kotlinx.coroutines.flow.Flow

interface UserPreferences {
    val themeMode: Flow<ThemeMode>
    val languageTag: Flow<String>
    val notesViewMode: Flow<NotesViewMode>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setLanguageTag(tag: String)
    suspend fun setNotesViewMode(mode: NotesViewMode)
}
