package app.tessera.core.ui.tessera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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

/** Real components with appearance applied (plan Chunk 3, step B). No golden yet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h500dp-port-xxhdpi")
class TagAppearanceScreenshotTest {
  @get:Rule val composeRule = createComposeRule()

  @Test fun chipRowAndCards_light() = capture(false)
  @Test fun chipRowAndCards_dark() = capture(true)

  private fun icon(key: String) = LucideIconSet.first { it.key == key }

  private fun capture(dark: Boolean) {
    val styles = mapOf(
      "work" to TagStyle(AccentColor.Blue, icon("briefcase")),
      "home" to TagStyle(AccentColor.Green, EntityIcon.Emoji("🏠")),
      "ideas" to TagStyle(AccentColor.Yellow, null),
      "food" to TagStyle(AccentColor.Purple, icon("utensils")),
    )
    composeRule.setContent {
      TesseraTheme(darkTheme = dark, dynamicColor = false) {
        CompositionLocalProvider(LocalTagAppearances provides styles) {
          Surface(color = MaterialTheme.colorScheme.background) {
            Column(
              Modifier.fillMaxWidth().padding(vertical = TesseraSpacing.lg),
              verticalArrangement = Arrangement.spacedBy(TesseraSpacing.md),
            ) {
              TesseraChipRow(
                chips = listOf(
                  TesseraChip("all", "All", selected = true),
                  TesseraChip("work", "#work", selected = false),
                  TesseraChip("home", "#home", selected = false),
                  TesseraChip("ideas", "#ideas", selected = false),
                  TesseraChip("other", "#other", selected = false),
                ),
                onChipClick = {},
                onChipLongClick = {},
              )
              val tint = AccentColor.Purple.cardTint()
              Column(
                Modifier.padding(horizontal = TesseraSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(TesseraSpacing.sm),
              ) {
                TesseraNoteCard("Lunch ideas", "Tacos, sushi, ramen…", "2d ago", listOf("food", "ideas"), false, {}, {}, tint = tint)
                TesseraNoteCard("Selected tinted", "Selection wins over tint", "now", listOf("work"), true, {}, {}, selected = true, tint = tint)
                TesseraNoteCard("Plain card", "No tint, default tag", "1w ago", listOf("other", "home"), false, {}, {})
              }
            }
          }
        }
      }
    }
    composeRule.onRoot().captureRoboImage("ui/appearance/components_${if (dark) "dark" else "light"}.png")
  }
}
