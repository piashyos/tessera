package app.tessera.core.ui.tessera

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import app.tessera.core.ui.theme.AccentColor
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TagAppearanceUiTest {
  @get:Rule val rule = createComposeRule()

  private val chips = listOf(
    TesseraChip("all", "All", selected = true),
    TesseraChip("work", "#work", selected = false),
  )
  private val tagsOnly = listOf(
    TesseraChip("work", "#work", selected = false),
    TesseraChip("home", "#home", selected = true),
  )
  private val star = LucideIconSet.first { it.key == "star" }

  @Test fun longClick_callsLambda_notClick_andTapStillClicks() {
    var clicked: String? = null
    var longClicked: String? = null
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraChipRow(chips, onChipClick = { clicked = it }, onChipLongClick = { longClicked = it })
      }
    }
    rule.onNodeWithText("#work").performTouchInput { longClick() }
    assertEquals("work", longClicked)
    assertNull("long press must not toggle the filter", clicked)
    rule.onNodeWithText("#work").performClick()
    assertEquals("work", clicked)
  }

  @Test fun nonTagChip_staysFilterChip_tapClicks() {
    var clicked: String? = null
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraChipRow(chips, onChipClick = { clicked = it }, onChipLongClick = {})
      }
    }
    rule.onNodeWithText("All").performClick()
    assertEquals("all", clicked)
  }

  @Test fun tagChip_exposesSelectedState() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraChipRow(tagsOnly, onChipClick = {}, onChipLongClick = {})
      }
    }
    rule.onNodeWithText("#home").assertIsSelected()
    rule.onNodeWithText("#work").assertIsNotSelected()
  }

  @Test fun rowHeight_sameWithAndWithoutLongClick() {
    var styles by mutableStateOf(emptyMap<String, TagStyle>())
    var withLongClick by mutableStateOf(false)
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        CompositionLocalProvider(LocalTagAppearances provides styles) {
          TesseraChipRow(
            tagsOnly,
            onChipClick = {},
            onChipLongClick = if (withLongClick) ({}) else null,
            modifier = Modifier.testTag("row"),
          )
        }
      }
    }
    fun rowHeight() = rule.onNodeWithTag("row").getUnclippedBoundsInRoot().height
    val filterChipHeight = rowHeight()
    withLongClick = true
    rule.waitForIdle()
    assertEquals("plain tags", filterChipHeight, rowHeight())
    styles = mapOf("work" to TagStyle(null, EntityIcon.Emoji("🏠")), "home" to TagStyle(null, star))
    rule.waitForIdle()
    assertEquals("tags with icons", filterChipHeight, rowHeight())
  }

  @Test fun icon_replacesHash_inChipRow() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        CompositionLocalProvider(LocalTagAppearances provides mapOf("work" to TagStyle(null, star))) {
          TesseraChipRow(chips, onChipClick = {}, onChipLongClick = {})
        }
      }
    }
    rule.onNodeWithText("work").assertIsDisplayed()
    rule.onNodeWithText("#work").assertDoesNotExist()
  }

  @Test fun icon_replacesHash_inFilterChipFallback() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        CompositionLocalProvider(LocalTagAppearances provides mapOf("work" to TagStyle(null, star))) {
          TesseraChipRow(chips, onChipClick = {})
        }
      }
    }
    rule.onNodeWithText("work").assertIsDisplayed()
    rule.onNodeWithText("#work").assertDoesNotExist()
  }

  // A vector icon has no semantics node: its presence shows as extra chip width (icon 16 + gaps
  // outweigh the dropped "#").
  @Test fun vectorIcon_isDrawnInChip() {
    var styles by mutableStateOf(emptyMap<String, TagStyle>())
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        CompositionLocalProvider(LocalTagAppearances provides styles) {
          TesseraChipRow(chips, onChipClick = {}, onChipLongClick = {})
        }
      }
    }
    val plain = rule.onNodeWithText("#work").getUnclippedBoundsInRoot().width
    styles = mapOf("work" to TagStyle(null, star))
    rule.waitForIdle()
    val withIcon = rule.onNodeWithText("work").getUnclippedBoundsInRoot().width
    assertTrue("$withIcon should exceed $plain", withIcon > plain)
  }

  @Test fun emoji_isRenderedAsText() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        CompositionLocalProvider(LocalTagAppearances provides mapOf("work" to TagStyle(null, EntityIcon.Emoji("🏠")))) {
          TesseraChipRow(chips, onChipClick = {}, onChipLongClick = {})
        }
      }
    }
    rule.onNodeWithText("🏠", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test fun icon_replacesHash_onNoteCard() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        CompositionLocalProvider(LocalTagAppearances provides mapOf("work" to TagStyle(null, star))) {
          TesseraNoteCard("t", "p", "d", listOf("work", "plain"), false, {}, {})
        }
      }
    }
    rule.onNodeWithText("work").assertIsDisplayed()
    rule.onNodeWithText("#plain").assertIsDisplayed()
  }

  @Test fun emojiChip_onCard_isAsTallAsPlainChip() {
    var styles by mutableStateOf(emptyMap<String, TagStyle>())
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        CompositionLocalProvider(LocalTagAppearances provides styles) {
          TesseraNoteCard("t", "p", "d", listOf("other"), false, {}, {}, modifier = Modifier.testTag("card"))
        }
      }
    }
    val plain = rule.onNodeWithTag("card").getUnclippedBoundsInRoot().height
    styles = mapOf("other" to TagStyle(null, EntityIcon.Emoji("🏠")))
    rule.waitForIdle()
    assertEquals(plain, rule.onNodeWithTag("card").getUnclippedBoundsInRoot().height)
    styles = mapOf("other" to TagStyle(null, star))
    rule.waitForIdle()
    assertEquals(plain, rule.onNodeWithTag("card").getUnclippedBoundsInRoot().height)
  }

  @Test fun rememberTagChipColors_takesColorFromLocal_elseHash() {
    var withColor: TagChipColors? = null
    var hashed: TagChipColors? = null
    var expected: TagChipColors? = null
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        hashed = rememberTagChipColors("work")
        CompositionLocalProvider(LocalTagAppearances provides mapOf("work" to TagStyle(AccentColor.Teal, null))) {
          withColor = rememberTagChipColors("work")
          expected = AccentColor.Teal.chipColors()
          assertNull(rememberTagIcon("work"))
        }
      }
    }
    assertEquals(expected, withColor)
    assertEquals(AccentColor.Teal.container(false), withColor!!.container)
    assertNotEquals(withColor, hashed)
  }

  @Test fun chipColorsAndCardTint_followTheme() {
    var dark: Boolean? = null
    rule.setContent {
      TesseraTheme(darkTheme = true, dynamicColor = false) {
        dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        assertEquals(AccentColor.Blue.cardTint(true), AccentColor.Blue.cardTint())
        assertEquals(AccentColor.Blue.container(true), AccentColor.Blue.chipColors().container)
      }
    }
    assertEquals(true, dark)
  }
}
