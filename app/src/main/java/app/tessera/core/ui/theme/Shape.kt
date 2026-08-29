package app.tessera.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val TesseraShapes: Shapes = Shapes(
  extraSmall = RoundedCornerShape(4.dp),
  small      = RoundedCornerShape(8.dp),
  medium     = RoundedCornerShape(12.dp),
  large      = RoundedCornerShape(16.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

/** Semantic shape constants for cases not covered by the M3 Shapes scale. */
object TesseraShape {
  val noteCard       = RoundedCornerShape(14.dp)
  val fab            = RoundedCornerShape(16.dp)
  val fabSmall       = RoundedCornerShape(12.dp)
  val pill           = RoundedCornerShape(999.dp)
  /** Filled text inputs (single-line and multi-line boxes). Pill/search inputs use [pill]. */
  val input          = RoundedCornerShape(16.dp)
  val bottomSheetTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  val formatBarTop   = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
}
