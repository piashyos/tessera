package app.tessera.core.preferences

/**
 * Scheme style applied to the Material You dynamic color (wallpaper) seed, mirroring the MDC
 * `DynamicScheme` variants. Only takes effect while [UserPreferences.dynamicColor] is on;
 * [TONAL_SPOT] reproduces the stock system scheme.
 */
enum class ThemeStyle {
    TONAL_SPOT,
    VIBRANT,
    EXPRESSIVE,
    FRUIT_SALAD;

    companion object {
        val Default = TONAL_SPOT

        fun fromName(raw: String?): ThemeStyle =
            entries.firstOrNull { it.name == raw } ?: Default
    }
}
