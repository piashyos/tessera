package app.tessera.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class TesseraShapeTest {

  @Test fun `Shapes scale matches M3 standard radii`() {
    assertEquals(RoundedCornerShape(4.dp),  TesseraShapes.extraSmall)
    assertEquals(RoundedCornerShape(8.dp),  TesseraShapes.small)
    assertEquals(RoundedCornerShape(12.dp), TesseraShapes.medium)
    assertEquals(RoundedCornerShape(16.dp), TesseraShapes.large)
    assertEquals(RoundedCornerShape(28.dp), TesseraShapes.extraLarge)
  }

  @Test fun `semantic shapes have expected radii`() {
    assertEquals(RoundedCornerShape(14.dp), TesseraShape.noteCard)
    assertEquals(RoundedCornerShape(16.dp), TesseraShape.fab)
    assertEquals(RoundedCornerShape(12.dp), TesseraShape.fabSmall)
    assertEquals(RoundedCornerShape(999.dp), TesseraShape.pill)
  }

  @Test fun `bottomSheetTop and formatBarTop are top-only rounded`() {
    assertEquals(
      RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
      TesseraShape.bottomSheetTop
    )
    assertEquals(
      RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
      TesseraShape.formatBarTop
    )
  }
}
