package app.tessera.core.preferences

enum class ThemeMode {
    AUTO,
    LIGHT_DYNAMIC,
    DARK_DYNAMIC,
    LIGHT_STATIC,
    DARK_STATIC;

    /** Resolve to (darkTheme, dynamicColor) given the current system dark state. */
    fun resolve(isSystemDark: Boolean): Pair<Boolean, Boolean> = when (this) {
        AUTO -> isSystemDark to true
        LIGHT_DYNAMIC -> false to true
        DARK_DYNAMIC -> true to true
        LIGHT_STATIC -> false to false
        DARK_STATIC -> true to false
    }
}
