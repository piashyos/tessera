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

    /**
     * Whether Material You dynamic color (derived from the wallpaper) is applied. Orthogonal to
     * [themeMode]: when enabled it tints both the light and the dark scheme, in any theme mode
     * (including [ThemeMode.AUTO]). Only takes effect on Android 12+.
     */
    val dynamicColor: Flow<Boolean>

    /** Synchronously-readable last [dynamicColor] value, used to seed the UI on (re)creation. */
    val cachedDynamicColor: Boolean

    /** Selected color palette (orthogonal to [themeMode]/[dynamicColor]). */
    val themePalette: Flow<ThemePalette>

    /** Synchronously-readable last [themePalette] value, used to seed the UI on (re)creation. */
    val cachedThemePalette: ThemePalette

    /** Dynamic-color scheme style (only applies while [dynamicColor] is on). */
    val themeStyle: Flow<ThemeStyle>

    /** Synchronously-readable last [themeStyle] value, used to seed the UI on (re)creation. */
    val cachedThemeStyle: ThemeStyle

    /** True-black (AMOLED) dark surfaces; only affects the dark scheme. */
    val amoled: Flow<Boolean>

    /** Synchronously-readable last [amoled] value, used to seed the UI on (re)creation. */
    val cachedAmoled: Boolean

    val languageTag: Flow<String>
    val notesViewMode: Flow<NotesViewMode>

    /** Most-recent-first list of committed search queries. */
    val recentSearches: Flow<List<String>>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setThemePalette(palette: ThemePalette)
    suspend fun setThemeStyle(style: ThemeStyle)
    suspend fun setAmoled(enabled: Boolean)
    suspend fun setLanguageTag(tag: String)
    suspend fun setNotesViewMode(mode: NotesViewMode)

    /** Adds [query] to the top of the recent list (de-duplicated, capped). */
    suspend fun addRecentSearch(query: String)
    suspend fun removeRecentSearch(query: String)
    suspend fun clearRecentSearches()
}
