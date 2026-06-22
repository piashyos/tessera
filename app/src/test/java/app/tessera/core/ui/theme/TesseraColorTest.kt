package app.tessera.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class TesseraColorTest {

  @Test fun `light primary is brand terracotta`() {
    assertEquals(Color(0xFFC96442), TesseraBrandLight.primary)
  }

  @Test fun `dark primary is brand terracotta lighter shade`() {
    assertEquals(Color(0xFFD97757), TesseraBrandDark.primary)
  }

  @Test fun `light background is cream`() {
    assertEquals(Color(0xFFFAF9F5), TesseraBrandLight.background)
  }

  @Test fun `dark background is warm charcoal`() {
    assertEquals(Color(0xFF191815), TesseraBrandDark.background)
  }

  @Test fun `light has 5 surface container levels`() {
    val brand = TesseraBrandLight
    assertEquals(Color(0xFFFFFFFF), brand.surfaceContainerLowest)
    assertEquals(Color(0xFFF6F4ED), brand.surfaceContainerLow)
    assertEquals(Color(0xFFF3F0E8), brand.surfaceContainer)
    assertEquals(Color(0xFFEDEAE3), brand.surfaceContainerHigh)
    assertEquals(Color(0xFFE7E3DA), brand.surfaceContainerHighest)
  }

  @Test fun `dark has 5 surface container levels`() {
    val brand = TesseraBrandDark
    assertEquals(Color(0xFF131211), brand.surfaceContainerLowest)
    assertEquals(Color(0xFF232220), brand.surfaceContainerLow)
    assertEquals(Color(0xFF26241F), brand.surfaceContainer)
    assertEquals(Color(0xFF2C2B27), brand.surfaceContainerHigh)
    assertEquals(Color(0xFF37352F), brand.surfaceContainerHighest)
  }
}
