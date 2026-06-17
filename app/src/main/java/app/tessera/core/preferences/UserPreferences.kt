package app.tessera.core.preferences

import kotlinx.coroutines.flow.Flow

interface UserPreferences {
    val themeMode: Flow<ThemeMode>
    val languageTag: Flow<String>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setLanguageTag(tag: String)
}
