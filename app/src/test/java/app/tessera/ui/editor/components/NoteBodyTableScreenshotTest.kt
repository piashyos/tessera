package app.tessera.ui.editor.components

import android.os.Looper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A table in `NoteBody` (phase 2 spec §4), light and dark: its overlay sits on the span between two paragraphs, a wide table runs past the right
 * edge (it scrolls inside the overlay). The golden files are written by the orchestrator.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class NoteBodyTableScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val md = "Текст над таблицей.\n" +
        "| Товар | Кол-во | Цена | Примечание |\n| --- | --- | --- | --- |\n| Яблоки | 3 | 1,20 | красные |\n| Груши | 5 | 0,90 | зелёные |\n" +
        "Текст под таблицей."

    private fun capture(name: String, dark: Boolean, editable: Boolean) {
        rule.setContent {
            TesseraTheme(darkTheme = dark, dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NoteBody(
                        markdown = md,
                        onMarkdownChange = {},
                        onActionsReady = {},
                        onFormatStateChanged = {},
                        editable = editable,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    )
                }
            }
        }
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        rule.waitForIdle()
        // the overlay is a second composition: the first root is the screen one
        rule.onAllNodes(isRoot()).onFirst().captureRoboImage("ui/editor/note_body_table_$name.png")
    }

    @Test fun light() = capture("light", dark = false, editable = true)
    @Test fun dark() = capture("dark", dark = true, editable = true)
    @Test fun readOnlyLight() = capture("read_only_light", dark = false, editable = false)
}
