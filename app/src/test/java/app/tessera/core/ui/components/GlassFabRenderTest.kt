package app.tessera.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class GlassFabRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fabHasNoInnerCircleArtifact() {
        composeRule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.padding(24.dp)) {
                    GlassFab(onClick = {}) { Text("+") }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("ui/glass-fab.png")
    }
}
