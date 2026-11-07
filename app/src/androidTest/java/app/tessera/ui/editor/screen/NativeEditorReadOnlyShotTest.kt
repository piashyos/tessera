package app.tessera.ui.editor.screen

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.components.NoteBody
import app.tessera.ui.editor.engine.savePng
import org.junit.Rule
import org.junit.Test

/**
 * The read-only body (a viewer of a shared note, spec §6.10) for the screenshots without a mockup. On the screen it needs a
 * signed-in viewer of a shared note, which the device has not got; the body is the same `NoteBody(editable = false)` the screen
 * draws, in the app's theme, at the screen's side padding.
 */
class NativeEditorReadOnlyShotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun readOnlyBodyLightAndDark() {
        var dark by mutableStateOf(false) // setContent may run once per test: the theme is switched through state
        compose.setContent {
            TesseraTheme(darkTheme = dark, dynamicColor = false, palette = ThemePalette.WARM) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    NoteBody(
                        markdown = READ_ONLY,
                        onMarkdownChange = {},
                        onActionsReady = {},
                        onFormatStateChanged = {},
                        editable = false,
                        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp),
                    )
                }
            }
        }
        val i = InstrumentationRegistry.getInstrumentation()
        for (d in listOf(false, true)) {
            compose.runOnUiThread { dark = d }
            compose.waitForIdle()
            Thread.sleep(800)
            val b = i.uiAutomation.takeScreenshot() ?: error("no screenshot")
            savePng(i.targetContext, "6b-06-read-only-${if (d) "dark" else "light"}-fs${i.targetContext.resources.configuration.fontScale}", b)
        }
    }

    private companion object {
        const val READ_ONLY = "# Общая заметка (только чтение)\n" +
            "Зритель видит текст, выделяет и копирует его, но не правит.\n" +
            "## Список\n" +
            "- [x] Отмеченный пункт\n" +
            "- [ ] Неотмеченный пункт\n" +
            "> Цитата\n" +
            "```\n" +
            "моно\n" +
            "```"
    }
}
