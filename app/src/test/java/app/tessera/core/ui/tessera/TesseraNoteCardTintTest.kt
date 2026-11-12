package app.tessera.core.ui.tessera

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.drawToBitmap
import androidx.compose.ui.test.onNodeWithTag
import app.tessera.core.ui.theme.AccentColor
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Fill colour of [TesseraNoteCard]: tint when idle, `secondaryContainer` when selected (selection wins). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h400dp-port-xxhdpi")
class TesseraNoteCardTintTest {
  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  private fun fill(selected: Boolean, tint: Color?, onTheme: (Color, Color) -> Unit): Color {
    var expectedSelected = Color.Unspecified
    var expectedDefault = Color.Unspecified
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        expectedSelected = MaterialTheme.colorScheme.secondaryContainer
        expectedDefault = MaterialTheme.colorScheme.surfaceContainerLow
        TesseraNoteCard("t", "p", "d", emptyList(), false, {}, {}, selected = selected, tint = tint)
      }
    }
    onTheme(expectedSelected, expectedDefault)
    rule.waitForIdle()
    // captureToImage() times out under Robolectric; draw the window's decor view instead.
    val bounds = rule.onNodeWithTag(TesseraNoteCardTags.Root).fetchSemanticsNode().boundsInRoot
    val bmp = rule.activity.window.decorView.drawToBitmap()
    // Middle row, a few px from the card's right edge: clear of text and rounded corners.
    return Color(bmp.getPixel((bounds.right - 20).toInt(), bounds.center.y.toInt()))
  }

  private fun assertClose(expected: Color, actual: Color) {
    assertEquals("r", expected.red, actual.red, 0.01f)
    assertEquals("g", expected.green, actual.green, 0.01f)
    assertEquals("b", expected.blue, actual.blue, 0.01f)
  }

  @Test fun idleCard_usesTint() {
    val tint = AccentColor.Purple.cardTint(false)
    assertClose(tint, fill(selected = false, tint = tint) { _, _ -> })
  }

  @Test fun selectedCard_winsOverTint() {
    var selectedColor = Color.Unspecified
    val actual = fill(selected = true, tint = AccentColor.Purple.cardTint(false)) { sel, _ -> selectedColor = sel }
    assertClose(selectedColor, actual)
  }

  @Test fun noTint_keepsSurfaceContainerLow() {
    var default = Color.Unspecified
    val actual = fill(selected = false, tint = null) { _, d -> default = d }
    assertClose(default, actual)
  }
}
