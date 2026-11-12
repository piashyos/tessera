package app.tessera.core.ui.tessera

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.AccentColor
import app.tessera.core.ui.theme.TesseraSpacing
import app.tessera.core.ui.theme.TesseraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The 10 palette dots (container fill, content outline) in light and dark. Components: [TagAppearanceScreenshotTest]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h400dp-port-xxhdpi")
class AppearanceShowcaseScreenshotTest {
  @get:Rule val composeRule = createComposeRule()

  @Test fun showcase_light() = capture(dark = false)
  @Test fun showcase_dark() = capture(dark = true)

  private fun capture(dark: Boolean) {
    composeRule.setContent {
      TesseraTheme(darkTheme = dark, dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) { Showcase(dark) }
      }
    }
    composeRule.onRoot().captureRoboImage("ui/appearance/showcase_${if (dark) "dark" else "light"}.png")
  }

  @Composable
  private fun Showcase(dark: Boolean) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(TesseraSpacing.lg),
      verticalArrangement = Arrangement.spacedBy(TesseraSpacing.lg),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.sm)) {
        AccentColor.entries.forEach { c ->
          Surface(
            shape = CircleShape,
            color = c.container(dark),
            border = BorderStroke(1.5.dp, c.content(dark)),
            modifier = Modifier.size(28.dp),
          ) {}
        }
      }
    }
  }
}
