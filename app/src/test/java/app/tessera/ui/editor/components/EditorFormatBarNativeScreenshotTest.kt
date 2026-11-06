package app.tessera.ui.editor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.tessera.core.text.notedoc.Mark
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.engine.FormatState
import app.tessera.ui.editor.engine.ParagraphFormat
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The native bar: a state with marks, a heading and history on (the active pills), and the empty one (undo / redo / indents off). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w840dp-h200dp-land-xxhdpi")
class EditorFormatBarNativeScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun capture(name: String, fs: FormatState, dark: Boolean) {
        rule.setContent {
            TesseraTheme(darkTheme = dark, dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box(Modifier.padding(8.dp)) {
                        EditorFormatBar(actions = null, activeFormats = emptySet(), nativeEngine = true, formatState = fs)
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage("ui/editor/format_bar_native_$name.png")
    }

    private val active = FormatState(
        paragraph = ParagraphFormat.Single(ParagraphKind.Title),
        marks = setOf(Mark.Bold, Mark.Underline),
        canIndent = true,
        canUndo = true,
    )

    @Test fun activeLight() = capture("active_light", active, dark = false)
    @Test fun activeDark() = capture("active_dark", active, dark = true)
    @Test fun emptyLight() = capture("empty_light", FormatState.Empty, dark = false)
}
