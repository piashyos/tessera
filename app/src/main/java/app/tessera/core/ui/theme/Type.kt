package app.tessera.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.tessera.R

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun interFont(weight: Int) = Font(
  resId = R.font.inter_variable,
  weight = FontWeight(weight),
  style = FontStyle.Normal,
  variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Inter: FontFamily = FontFamily(
  interFont(400),
  interFont(500),
  interFont(600),
  interFont(700),
)

val InstrumentSerif: FontFamily = FontFamily(
  Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
  Font(R.font.instrument_serif_italic,  FontWeight.Normal, FontStyle.Italic),
)

val TesseraTypography: Typography = Typography(
  displayLarge   = TextStyle(fontFamily = InstrumentSerif, fontSize = 52.sp, lineHeight = 58.sp),
  displayMedium  = TextStyle(fontFamily = InstrumentSerif, fontSize = 40.sp, lineHeight = 48.sp),
  displaySmall   = TextStyle(fontFamily = InstrumentSerif, fontSize = 32.sp, lineHeight = 40.sp),

  headlineLarge  = TextStyle(fontFamily = InstrumentSerif, fontSize = 28.sp, lineHeight = 36.sp),
  headlineMedium = TextStyle(fontFamily = InstrumentSerif, fontSize = 24.sp, lineHeight = 32.sp),
  headlineSmall  = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W600,
                             fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.sp),

  titleLarge     = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W600,
                             fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
  titleMedium    = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W500,
                             fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
  titleSmall     = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W600,
                             fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.8.sp),

  bodyLarge      = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W400,
                             fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
  bodyMedium     = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W400,
                             fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
  bodySmall      = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W400,
                             fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.sp),

  labelLarge     = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W500,
                             fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
  labelMedium    = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W500,
                             fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.sp),
  labelSmall     = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W600,
                             fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp),
)
