package app.tessera.test.fakes

import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.preferences.ThemeStyle
import app.tessera.core.preferences.UserPreferences
import app.tessera.ui.notes.NotesViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUserPreferences : UserPreferences {
    private val theme = MutableStateFlow(ThemeMode.AUTO)
    private val dynamic = MutableStateFlow(true)
    private val palette = MutableStateFlow(ThemePalette.Default)
    private val style = MutableStateFlow(ThemeStyle.Default)
    private val amoledFlow = MutableStateFlow(false)
    private val grainEnabledFlow = MutableStateFlow(true)
    private val grainIntensityFlow = MutableStateFlow(0.5f)
    private val lang = MutableStateFlow("")
    private val viewMode = MutableStateFlow(NotesViewMode.List)
    private val recent = MutableStateFlow<List<String>>(emptyList())

    override val themeMode: Flow<ThemeMode> get() = theme
    override val cachedThemeMode: ThemeMode get() = theme.value
    override val dynamicColor: Flow<Boolean> get() = dynamic
    override val cachedDynamicColor: Boolean get() = dynamic.value
    override val themePalette: Flow<ThemePalette> get() = palette
    override val cachedThemePalette: ThemePalette get() = palette.value
    override val themeStyle: Flow<ThemeStyle> get() = style
    override val cachedThemeStyle: ThemeStyle get() = style.value
    override val amoled: Flow<Boolean> get() = amoledFlow
    override val cachedAmoled: Boolean get() = amoledFlow.value
    override val grainEnabled: Flow<Boolean> get() = grainEnabledFlow
    override val cachedGrainEnabled: Boolean get() = grainEnabledFlow.value
    override val grainIntensity: Flow<Float> get() = grainIntensityFlow
    override val cachedGrainIntensity: Float get() = grainIntensityFlow.value
    override val languageTag: Flow<String> get() = lang
    override val notesViewMode: Flow<NotesViewMode> get() = viewMode
    override val recentSearches: Flow<List<String>> get() = recent

    override suspend fun setThemeMode(mode: ThemeMode) {
        theme.value = mode
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dynamic.value = enabled
    }

    override suspend fun setThemePalette(palette: ThemePalette) {
        this.palette.value = palette
    }

    override suspend fun setThemeStyle(style: ThemeStyle) {
        this.style.value = style
    }

    override suspend fun setAmoled(enabled: Boolean) {
        amoledFlow.value = enabled
    }

    override suspend fun setGrainEnabled(enabled: Boolean) {
        grainEnabledFlow.value = enabled
    }

    override suspend fun setGrainIntensity(intensity: Float) {
        grainIntensityFlow.value = intensity.coerceIn(0f, 1f)
    }

    override suspend fun setLanguageTag(tag: String) {
        lang.value = tag
    }

    override suspend fun setNotesViewMode(mode: NotesViewMode) {
        viewMode.value = mode
    }

    override suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            recent.value = (listOf(trimmed) + recent.value.filterNot { it.equals(trimmed, ignoreCase = true) }).take(8)
        }
    }

    override suspend fun removeRecentSearch(query: String) {
        recent.value = recent.value.filterNot { it.equals(query, ignoreCase = true) }
    }

    override suspend fun clearRecentSearches() {
        recent.value = emptyList()
    }
}
