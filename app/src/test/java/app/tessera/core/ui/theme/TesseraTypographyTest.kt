package app.tessera.core.ui.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class TesseraTypographyTest {

  @Test fun `display roles use Instrument Serif`() {
    assertEquals(InstrumentSerif, TesseraTypography.displayLarge.fontFamily)
    assertEquals(InstrumentSerif, TesseraTypography.displayMedium.fontFamily)
    assertEquals(InstrumentSerif, TesseraTypography.displaySmall.fontFamily)
  }

  @Test fun `headlineLarge and headlineMedium use Instrument Serif`() {
    assertEquals(InstrumentSerif, TesseraTypography.headlineLarge.fontFamily)
    assertEquals(InstrumentSerif, TesseraTypography.headlineMedium.fontFamily)
  }

  @Test fun `headlineSmall and below use Inter`() {
    assertEquals(Inter, TesseraTypography.headlineSmall.fontFamily)
    assertEquals(Inter, TesseraTypography.titleLarge.fontFamily)
    assertEquals(Inter, TesseraTypography.bodyLarge.fontFamily)
    assertEquals(Inter, TesseraTypography.labelLarge.fontFamily)
  }

  @Test fun `body roles have 15, 13, 11 sp sizes`() {
    assertEquals(15.sp, TesseraTypography.bodyLarge.fontSize)
    assertEquals(13.sp, TesseraTypography.bodyMedium.fontSize)
    assertEquals(11.sp, TesseraTypography.bodySmall.fontSize)
  }

  @Test fun `displayLarge is 52sp`() {
    assertEquals(52.sp, TesseraTypography.displayLarge.fontSize)
  }

  @Test fun `headlineLarge is 28sp Instrument Serif`() {
    val s = TesseraTypography.headlineLarge
    assertEquals(28.sp, s.fontSize)
    assertEquals(InstrumentSerif, s.fontFamily)
  }

  @Test fun `titleSmall uses W600 with letterSpacing for caps`() {
    assertEquals(FontWeight.W600, TesseraTypography.titleSmall.fontWeight)
    assertEquals(0.8.sp, TesseraTypography.titleSmall.letterSpacing)
  }

  @Test fun `labelSmall uses W600 uppercase letterSpacing`() {
    assertEquals(FontWeight.W600, TesseraTypography.labelSmall.fontWeight)
    assertEquals(0.8.sp, TesseraTypography.labelSmall.letterSpacing)
  }

  @Test fun `body and title letterSpacing override is zero`() {
    assertEquals(0.sp, TesseraTypography.bodyLarge.letterSpacing)
    assertEquals(0.sp, TesseraTypography.titleLarge.letterSpacing)
    assertEquals(0.sp, TesseraTypography.titleMedium.letterSpacing)
  }
}
