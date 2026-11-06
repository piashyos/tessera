package app.tessera.core.preferences

import app.tessera.ui.notes.NotesViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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

    /** Whether the app-wide film-grain overlay is painted over every background/surface. */
    val grainEnabled: Flow<Boolean>

    /** Synchronously-readable last [grainEnabled] value, used to seed the UI on (re)creation. */
    val cachedGrainEnabled: Boolean

    /** Film-grain strength in 0f..1f (mapped to overlay alpha); only applies while [grainEnabled]. */
    val grainIntensity: Flow<Float>

    /** Synchronously-readable last [grainIntensity] value, used to seed the UI on (re)creation. */
    val cachedGrainIntensity: Float

    val languageTag: Flow<String>
    val notesViewMode: Flow<NotesViewMode>

    /** Most-recent-first list of committed search queries. */
    val recentSearches: Flow<List<String>>

    /**
     * Debug flag: edit notes with the new native editor (phase 1) instead of Aztec. Off by default.
     * Has a default implementation so test fakes of this interface don't have to declare it.
     */
    val useNativeEditor: Flow<Boolean> get() = flowOf(false)

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setThemePalette(palette: ThemePalette)
    suspend fun setThemeStyle(style: ThemeStyle)
    suspend fun setAmoled(enabled: Boolean)
    suspend fun setGrainEnabled(enabled: Boolean)

    /** Persists [intensity], clamped to 0f..1f. */
    suspend fun setGrainIntensity(intensity: Float)
    suspend fun setLanguageTag(tag: String)
    suspend fun setNotesViewMode(mode: NotesViewMode)

    /** Persists the [useNativeEditor] debug flag. */
    suspend fun setUseNativeEditor(enabled: Boolean) {}

    /** Adds [query] to the top of the recent list (de-duplicated, capped). */
    suspend fun addRecentSearch(query: String)
    suspend fun removeRecentSearch(query: String)
    suspend fun clearRecentSearches()
}
