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

    /** Whether the app-wide film-grain overlay is painted over every background/surface. */
    val grainEnabled: Flow<Boolean>

    /** Synchronously-readable last [grainEnabled] value, used to seed the UI on (re)creation. */
    val cachedGrainEnabled: Boolean

    /** Film-grain strength in 0f..1f (mapped to overlay alpha); only applies while [grainEnabled]. */
    val grainIntensity: Flow<Float>

    /** Synchronously-readable last [grainIntensity] value, used to seed the UI on (re)creation. */
    val cachedGrainIntensity: Float

    /** Film-grain size in 1..4 — how many noise pixels make up one grain; only applies while [grainEnabled]. */
    val grainSize: Flow<Int>

    /** Synchronously-readable last [grainSize] value, used to seed the UI on (re)creation. */
    val cachedGrainSize: Int

    /** Whether the soft touch-reactive gradient is painted behind the app content. */
    val gradientEnabled: Flow<Boolean>

    /** Synchronously-readable last [gradientEnabled] value, used to seed the UI on (re)creation. */
    val cachedGradientEnabled: Boolean

    /** Whether the background gradient reacts to touches; only applies while [gradientEnabled]. */
    val backgroundMotionEnabled: Flow<Boolean>

    /** Synchronously-readable last [backgroundMotionEnabled] value, used to seed the UI on (re)creation. */
    val cachedBackgroundMotionEnabled: Boolean

    val languageTag: Flow<String>
    val notesViewMode: Flow<NotesViewMode>

    /** Most-recent-first list of committed search queries. */
    val recentSearches: Flow<List<String>>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setThemePalette(palette: ThemePalette)
    suspend fun setThemeStyle(style: ThemeStyle)
    suspend fun setAmoled(enabled: Boolean)
    suspend fun setGrainEnabled(enabled: Boolean)

    /** Persists [intensity], clamped to 0f..1f. */
    suspend fun setGrainIntensity(intensity: Float)

    /** Persists [size], clamped to 1..4. */
    suspend fun setGrainSize(size: Int)
    suspend fun setGradientEnabled(enabled: Boolean)
    suspend fun setBackgroundMotionEnabled(enabled: Boolean)
    suspend fun setLanguageTag(tag: String)
    suspend fun setNotesViewMode(mode: NotesViewMode)

    /** Adds [query] to the top of the recent list (de-duplicated, capped). */
    suspend fun addRecentSearch(query: String)
    suspend fun removeRecentSearch(query: String)
    suspend fun clearRecentSearches()
}
