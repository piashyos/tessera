package app.tessera.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
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
  // Follow the APP's resolved theme, not the system: with a forced Light theme under a dark
  // system (or vice versa) isSystemInDarkTheme() picked the wrong shadow variant. The active
  // scheme's background luminance is the ground truth for "is this theme dark".
  val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val brand: TesseraBrandColors = if (dark) TesseraBrandDark else TesseraBrandLight
  return this.shadow(
    elevation = elevation.dp,
    shape = shape,
    clip = clip,
    ambientColor = brand.shadowAmbient,
    spotColor = brand.shadowSpot,
  )
}
