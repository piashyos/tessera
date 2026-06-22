package app.tessera.core.ui.theme

import androidx.compose.ui.graphics.Color

/** Brand color tokens. Mirror of `design/mockups/tokens.css`. */
interface TesseraBrandColors {
  val primary: Color;                val onPrimary: Color
  val primaryContainer: Color;       val onPrimaryContainer: Color
  val secondary: Color;              val onSecondary: Color
  val secondaryContainer: Color;     val onSecondaryContainer: Color
  val tertiary: Color;               val onTertiary: Color
  val tertiaryContainer: Color;      val onTertiaryContainer: Color
  val error: Color;                  val onError: Color
  val errorContainer: Color;         val onErrorContainer: Color
  val background: Color;             val onBackground: Color
  val surface: Color;                val onSurface: Color
  val surfaceDim: Color;             val surfaceBright: Color
  val surfaceContainerLowest: Color; val surfaceContainerLow: Color
  val surfaceContainer: Color;       val surfaceContainerHigh: Color
  val surfaceContainerHighest: Color
  val onSurfaceVariant: Color
  val outline: Color;                val outlineVariant: Color
  val scrim: Color
  /** Warm ambient color for Modifier.shadow on light surfaces. */
  val shadowAmbient: Color
  /** Warm spot color for Modifier.shadow on light surfaces. */
  val shadowSpot: Color
}

object TesseraBrandLight : TesseraBrandColors {
  override val primary                   = Color(0xFFC96442)
  override val onPrimary                 = Color(0xFFFFFFFF)
  override val primaryContainer          = Color(0xFFFBE3D8)
  override val onPrimaryContainer        = Color(0xFF3A1A0F)

  override val secondary                 = Color(0xFF7C5E4F)
  override val onSecondary               = Color(0xFFFFFFFF)
  override val secondaryContainer        = Color(0xFFF5E1D6)
  override val onSecondaryContainer      = Color(0xFF2F1E14)

  override val tertiary                  = Color(0xFF5F6A4F)
  override val onTertiary                = Color(0xFFFFFFFF)
  override val tertiaryContainer         = Color(0xFFE2EAD3)
  override val onTertiaryContainer       = Color(0xFF1B2412)

  override val error                     = Color(0xFFB3261E)
  override val onError                   = Color(0xFFFFFFFF)
  override val errorContainer            = Color(0xFFF9DEDC)
  override val onErrorContainer          = Color(0xFF410E0B)

  override val background                = Color(0xFFFAF9F5)
  override val onBackground              = Color(0xFF262420)
  override val surface                   = Color(0xFFFFFFFF)
  override val onSurface                 = Color(0xFF262420)
  override val surfaceDim                = Color(0xFFEDEAE3)
  override val surfaceBright             = Color(0xFFFFFFFF)
  override val surfaceContainerLowest    = Color(0xFFFFFFFF)
  override val surfaceContainerLow       = Color(0xFFF6F4ED)
  override val surfaceContainer          = Color(0xFFF3F0E8)
  override val surfaceContainerHigh      = Color(0xFFEDEAE3)
  override val surfaceContainerHighest   = Color(0xFFE7E3DA)
  override val onSurfaceVariant          = Color(0xFF5C584F)
  override val outline                   = Color(0xFF8A8478)
  override val outlineVariant            = Color(0xFFD9D4C7)
  override val scrim                     = Color(0x52000000)

  override val shadowAmbient             = Color(0x38B8B1A6)
  override val shadowSpot                = Color(0x2E3A1A0F)
}

object TesseraBrandDark : TesseraBrandColors {
  override val primary                   = Color(0xFFD97757)
  override val onPrimary                 = Color(0xFF3A1A0F)
  override val primaryContainer          = Color(0xFF5C2A18)
  override val onPrimaryContainer        = Color(0xFFFBE3D8)

  override val secondary                 = Color(0xFFD8B8A2)
  override val onSecondary               = Color(0xFF3A2818)
  override val secondaryContainer        = Color(0xFF523929)
  override val onSecondaryContainer      = Color(0xFFF5E1D6)

  override val tertiary                  = Color(0xFFB7C6A1)
  override val onTertiary                = Color(0xFF1F2A12)
  override val tertiaryContainer         = Color(0xFF3D4928)
  override val onTertiaryContainer       = Color(0xFFE2EAD3)

  override val error                     = Color(0xFFF2B8B5)
  override val onError                   = Color(0xFF601410)
  override val errorContainer            = Color(0xFF8C1D18)
  override val onErrorContainer          = Color(0xFFF9DEDC)

  override val background                = Color(0xFF191815)
  override val onBackground              = Color(0xFFECE9E3)
  override val surface                   = Color(0xFF201F1D)
  override val onSurface                 = Color(0xFFECE9E3)
  override val surfaceDim                = Color(0xFF191815)
  override val surfaceBright             = Color(0xFF393734)
  override val surfaceContainerLowest    = Color(0xFF131211)
  override val surfaceContainerLow       = Color(0xFF232220)
  override val surfaceContainer          = Color(0xFF26241F)
  override val surfaceContainerHigh      = Color(0xFF2C2B27)
  override val surfaceContainerHighest   = Color(0xFF37352F)
  override val onSurfaceVariant          = Color(0xFFC9C3B6)
  override val outline                   = Color(0xFF948D80)
  override val outlineVariant            = Color(0xFF4A4740)
  override val scrim                     = Color(0x52000000)

  override val shadowAmbient             = Color(0x66000000)
  override val shadowSpot                = Color(0x99000000)
}

// ── Legacy aliases (will be removed when consumers migrate to TesseraBrand{Light,Dark}) ──
@Deprecated("Use TesseraBrandLight.surface")              val SurfaceLight             = TesseraBrandLight.surface
@Deprecated("Use TesseraBrandDark.surface")               val SurfaceDark              = TesseraBrandDark.surface
@Deprecated("Use TesseraBrandLight.background")           val BackgroundGlowLight      = TesseraBrandLight.background
@Deprecated("Use TesseraBrandDark.background")            val BackgroundGlowDark       = TesseraBrandDark.background
@Deprecated("Use TesseraBrandLight.surfaceContainerHigh") val SurfaceVariantLight      = TesseraBrandLight.surfaceContainerHigh
@Deprecated("Use TesseraBrandDark.surfaceContainerHigh")  val SurfaceVariantDark       = TesseraBrandDark.surfaceContainerHigh
@Deprecated("Use TesseraBrandLight.onSurface")            val OnSurfaceLight           = TesseraBrandLight.onSurface
@Deprecated("Use TesseraBrandDark.onSurface")             val OnSurfaceDark            = TesseraBrandDark.onSurface
@Deprecated("Use TesseraBrandLight.primary")              val PrimaryLight             = TesseraBrandLight.primary
@Deprecated("Use TesseraBrandDark.primary")               val PrimaryDark              = TesseraBrandDark.primary

val CapsuleBorder            = Color.White.copy(alpha = 0.20f)
val CapsuleTintLight         = Color.White.copy(alpha = 0.65f)
val CapsuleTintDark          = Color(0xFF201F1D).copy(alpha = 0.72f)
val CapsuleHighlightLight    = Color.White.copy(alpha = 0.40f)
val CapsuleHighlightDark     = Color.White.copy(alpha = 0.14f)
val CapsuleShadowLight       = Color(0xFFB8B1A6).copy(alpha = 0.16f)
val CapsuleShadowDark        = Color.Black.copy(alpha = 0.32f)
val FloatingShadowAmbient    = Color.Black.copy(alpha = 0.52f)
val FloatingShadowSpot       = Color.Black.copy(alpha = 0.70f)
