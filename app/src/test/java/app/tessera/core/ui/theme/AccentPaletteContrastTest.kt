package app.tessera.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.tessera.core.preferences.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec §3.1: contrast of every accent in light/dark against all four palettes of Theme.kt. */
class AccentPaletteContrastTest {
  private fun ratio(a: Color, b: Color): Double {
    val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (hi + 0.05) / (lo + 0.05)
  }

  @Test fun contentOnContainer_atLeast4_5() = forAll { c, dark, _ ->
    ratio(c.content(dark), c.container(dark)) to 4.5
  }

  @Test fun onSurfaceOnCardTint_atLeast4_5() = forAll { c, dark, p ->
    ratio(paletteColors(p, dark).onSurface, c.cardTint(dark)) to 4.5
  }

  @Test fun onSurfaceVariantOnCardTint_atLeast3() = forAll { c, dark, p ->
    ratio(paletteColors(p, dark).onSurfaceVariant, c.cardTint(dark)) to 3.0
  }

  private fun forAll(measure: (AccentColor, Boolean, ThemePalette) -> Pair<Double, Double>) {
    val failures = buildList {
      for (c in AccentColor.entries) for (dark in listOf(false, true)) for (p in ThemePalette.entries) {
        val (actual, min) = measure(c, dark, p)
        if (actual < min) add("${c.key} dark=$dark $p: %.2f < $min".format(actual))
      }
    }
    assertTrue(failures.joinToString("\n"), failures.isEmpty())
  }

  @Test fun tenColors_uniqueKeys_fromKeyRoundTrips() {
    assertEquals(10, AccentColor.entries.size)
    assertEquals(10, AccentColor.entries.map { it.key }.toSet().size)
    AccentColor.entries.forEach { assertEquals(it, AccentColor.fromKey(it.key)) }
    assertNull(AccentColor.fromKey("magenta"))
    assertNull(AccentColor.fromKey(null))
  }

  @Test fun cardTint_softerThanContainer() {
    for (c in AccentColor.entries) {
      assertTrue(c.key, c.cardTint(false).luminance() > c.container(false).luminance())
      assertTrue(c.key, c.cardTint(true).luminance() < c.container(true).luminance())
    }
  }
}
