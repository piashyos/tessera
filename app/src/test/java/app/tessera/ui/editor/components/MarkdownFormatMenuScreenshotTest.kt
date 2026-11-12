package app.tessera.ui.editor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.unit.dp
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.core.ui.tessera.tesseraGlass
import app.tessera.core.ui.theme.TesseraElevation
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.core.ui.theme.tesseraGlassShadow
import app.tessera.ui.editor.engine.FormatState
import app.tessera.ui.editor.engine.ParagraphFormat
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The ⋯ sheet as the editor stacks it (spec §4.2): blurred screen under the sheet's backdrop, the card above a sharp glass format bar.
 * The whole screen is not used: under Robolectric the keyboard is never visible, so the screen has no bar; the layers are built here the
 * way the screen builds them (source box, sheet, bar after it).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class MarkdownFormatMenuScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private val noActions = object : RichBodyActions {
        override fun toggleBold() {}
        override fun toggleItalic() {}
        override fun toggleStrikethrough() {}
        override fun toggleParagraph() {}
        override fun toggleHeading1() {}
        override fun toggleHeading2() {}
        override fun toggleHeading3() {}
        override fun toggleHeading4() {}
        override fun toggleHeading5() {}
        override fun toggleHeading6() {}
        override fun toggleBulletList() {}
        override fun toggleTaskList() {}
        override fun focusBody() {}
    }

    private fun capture(name: String, darkTheme: Boolean) {
        composeRule.setContent {
            TesseraTheme(darkTheme = darkTheme, dynamicColor = false) {
                val haze = HazeState()
                val state = FormatState(paragraph = ParagraphFormat.Single(ParagraphKind.Heading))
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().hazeSource(haze)) {
                            Text("Material You redesign\n\nFinal QA pass for the Tessera editor surface.", Modifier.padding(20.dp))
                        }
                        MarkdownFormatMenu(
                            expanded = true,
                            actions = noActions,
                            onDismiss = {},
                            hazeState = haze,
                            bottomInset = 64.dp,
                            formatState = state,
                        )
                        val pill = MaterialTheme.colorScheme.surfaceContainerHigh
                        Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Surface(
                                color = Color.Transparent,
                                shape = RoundedCornerShape(28.dp),
                                modifier = Modifier.tesseraGlassShadow(TesseraElevation.Level2, RoundedCornerShape(28.dp)),
                            ) {
                                Box(Modifier.tesseraGlass(haze, pill)) {
                                    EditorFormatBar(actions = noActions, menuExpanded = true, onToggleMenu = {}, formatState = state)
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodes(isRoot()).onFirst().captureRoboImage("ui/editor/$name.png")
    }

    @Test fun sheetLight() = capture("editor_format_sheet_light", darkTheme = false)

    @Test fun sheetDark() = capture("editor_format_sheet_dark", darkTheme = true)
}
