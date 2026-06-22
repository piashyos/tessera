package app.tessera.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 5-level elevation scale for warm-tinted drop shadows on floating elements. */
enum class TesseraElevation(val dp: Dp) {
  Level1(2.dp), Level2(4.dp), Level3(8.dp), Level4(12.dp), Level5(18.dp);
}

/**
 * Drop shadow with warm ambient/spot colors on light theme, black on dark.
 * Note: on API 26-27 ambientColor/spotColor are ignored by the system.
 */
@Composable
fun Modifier.tesseraShadow(
  elevation: TesseraElevation,
  shape: Shape = RectangleShape,
  clip: Boolean = false,
): Modifier {
  val dark = isSystemInDarkTheme()
  val brand: TesseraBrandColors = if (dark) TesseraBrandDark else TesseraBrandLight
  return this.shadow(
    elevation = elevation.dp,
    shape = shape,
    clip = clip,
    ambientColor = brand.shadowAmbient,
    spotColor = brand.shadowSpot,
  )
}
