package app.tessera.core.preferences

/**
 * Selectable color palette, orthogonal to [ThemeMode] (light/dark) and [UserPreferences.dynamicColor].
 * Each palette ships a light and a dark variant; see `TesseraBrand*` objects in the theme package.
 *
 * [WARM] is the original terracotta brand. The others are intentionally low-saturation so notes
 * stay the focus: [NEUTRAL] is achromatic near-white/charcoal, [SLATE] a cool blue-gray, and
 * [SAGE] a muted green.
 */
enum class ThemePalette {
    WARM,
    NEUTRAL,
    SLATE,
    SAGE;

    companion object {
        val Default = WARM

        fun fromName(raw: String?): ThemePalette =
            entries.firstOrNull { it.name == raw } ?: Default
    }
}
