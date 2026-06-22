package app.tessera.ui.settings.components

import app.tessera.core.preferences.ThemeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThemeChoiceMapperTest {
    @Test
    fun `theme mode maps to the right choice`() {
        assertEquals(ThemeChoice.System, ThemeMode.AUTO.toThemeChoice())
        assertEquals(ThemeChoice.Light, ThemeMode.LIGHT_DYNAMIC.toThemeChoice())
        assertEquals(ThemeChoice.Light, ThemeMode.LIGHT_STATIC.toThemeChoice())
        assertEquals(ThemeChoice.Dark, ThemeMode.DARK_DYNAMIC.toThemeChoice())
        assertEquals(ThemeChoice.Dark, ThemeMode.DARK_STATIC.toThemeChoice())
    }

    @Test
    fun `dynamic color flag reflects the mode`() {
        assertEquals(true, ThemeMode.AUTO.isDynamicColor())
        assertEquals(true, ThemeMode.LIGHT_DYNAMIC.isDynamicColor())
        assertEquals(true, ThemeMode.DARK_DYNAMIC.isDynamicColor())
        assertEquals(false, ThemeMode.LIGHT_STATIC.isDynamicColor())
        assertEquals(false, ThemeMode.DARK_STATIC.isDynamicColor())
    }

    @Test
    fun `choice plus dynamic flag round-trips to a theme mode`() {
        assertEquals(ThemeMode.AUTO, themeModeOf(ThemeChoice.System, dynamic = true))
        assertEquals(ThemeMode.AUTO, themeModeOf(ThemeChoice.System, dynamic = false))
        assertEquals(ThemeMode.LIGHT_DYNAMIC, themeModeOf(ThemeChoice.Light, dynamic = true))
        assertEquals(ThemeMode.LIGHT_STATIC, themeModeOf(ThemeChoice.Light, dynamic = false))
        assertEquals(ThemeMode.DARK_DYNAMIC, themeModeOf(ThemeChoice.Dark, dynamic = true))
        assertEquals(ThemeMode.DARK_STATIC, themeModeOf(ThemeChoice.Dark, dynamic = false))
    }
}
