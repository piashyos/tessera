package app.tessera.core.preferences

/**
 * Light/dark selection only. Dynamic (Material You) color is an orthogonal preference — see
 * [UserPreferences.dynamicColor] — so it can be toggled in any of these modes, including [AUTO].
 */
enum class ThemeMode {
    AUTO,
    LIGHT,
    DARK;

    /** Resolve whether the dark color scheme should be used, given the current system dark state. */
    fun resolveDark(isSystemDark: Boolean): Boolean = when (this) {
        AUTO -> isSystemDark
        LIGHT -> false
        DARK -> true
    }
}
