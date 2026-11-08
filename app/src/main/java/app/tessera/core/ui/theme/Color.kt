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

// ─────────────────────────────────────────────────────────────────────────────
// Alternate palettes. All intentionally low-saturation; error/scrim reuse the
// brand's semantic red so destructive affordances stay recognisable everywhere.
// ─────────────────────────────────────────────────────────────────────────────

/** Achromatic near-white / charcoal — no color cast (Tailwind zinc-style). */
object TesseraNeutralLight : TesseraBrandColors {
  override val primary                   = Color(0xFF3A3A3A)
  override val onPrimary                 = Color(0xFFFFFFFF)
  override val primaryContainer          = Color(0xFFE3E3E3)
  override val onPrimaryContainer        = Color(0xFF1B1B1B)

  override val secondary                 = Color(0xFF5F5F5F)
  override val onSecondary               = Color(0xFFFFFFFF)
  override val secondaryContainer        = Color(0xFFE8E8E8)
  override val onSecondaryContainer      = Color(0xFF1F1F1F)

  override val tertiary                  = Color(0xFF4A4A4A)
  override val onTertiary                = Color(0xFFFFFFFF)
  override val tertiaryContainer         = Color(0xFFE0E0E0)
  override val onTertiaryContainer       = Color(0xFF1C1C1C)

  override val error                     = TesseraBrandLight.error
  override val onError                   = TesseraBrandLight.onError
  override val errorContainer            = TesseraBrandLight.errorContainer
  override val onErrorContainer          = TesseraBrandLight.onErrorContainer

  override val background                = Color(0xFFFAFAFA)
  override val onBackground              = Color(0xFF1A1A1A)
  override val surface                   = Color(0xFFFFFFFF)
  override val onSurface                 = Color(0xFF1A1A1A)
  override val surfaceDim                = Color(0xFFE6E6E6)
  override val surfaceBright             = Color(0xFFFFFFFF)
  override val surfaceContainerLowest    = Color(0xFFFFFFFF)
  override val surfaceContainerLow       = Color(0xFFF5F5F5)
  override val surfaceContainer          = Color(0xFFF0F0F0)
  override val surfaceContainerHigh      = Color(0xFFEAEAEA)
  override val surfaceContainerHighest   = Color(0xFFE4E4E4)
  override val onSurfaceVariant          = Color(0xFF5A5A5A)
  override val outline                   = Color(0xFF8C8C8C)
  override val outlineVariant            = Color(0xFFD6D6D6)
  override val scrim                     = Color(0x52000000)

  override val shadowAmbient             = Color(0x33B0B0B0)
  override val shadowSpot                = Color(0x29000000)
}

object TesseraNeutralDark : TesseraBrandColors {
  override val primary                   = Color(0xFFD6D6D6)
  override val onPrimary                 = Color(0xFF2A2A2A)
  override val primaryContainer          = Color(0xFF3A3A3A)
  override val onPrimaryContainer        = Color(0xFFE9E9E9)

  override val secondary                 = Color(0xFFBFBFBF)
  override val onSecondary               = Color(0xFF2A2A2A)
  override val secondaryContainer        = Color(0xFF383838)
  override val onSecondaryContainer      = Color(0xFFE8E8E8)

  override val tertiary                  = Color(0xFFCACACA)
  override val onTertiary                = Color(0xFF2A2A2A)
  override val tertiaryContainer         = Color(0xFF3A3A3A)
  override val onTertiaryContainer       = Color(0xFFE8E8E8)

  override val error                     = TesseraBrandDark.error
  override val onError                   = TesseraBrandDark.onError
  override val errorContainer            = TesseraBrandDark.errorContainer
  override val onErrorContainer          = TesseraBrandDark.onErrorContainer

  override val background                = Color(0xFF121212)
  override val onBackground              = Color(0xFFE6E6E6)
  override val surface                   = Color(0xFF1A1A1A)
  override val onSurface                 = Color(0xFFE6E6E6)
  override val surfaceDim                = Color(0xFF121212)
  override val surfaceBright             = Color(0xFF383838)
  override val surfaceContainerLowest    = Color(0xFF0D0D0D)
  override val surfaceContainerLow       = Color(0xFF1C1C1C)
  override val surfaceContainer          = Color(0xFF1F1F1F)
  override val surfaceContainerHigh      = Color(0xFF292929)
  override val surfaceContainerHighest   = Color(0xFF333333)
  override val onSurfaceVariant          = Color(0xFFC2C2C2)
  override val outline                   = Color(0xFF8E8E8E)
  override val outlineVariant            = Color(0xFF484848)
  override val scrim                     = Color(0x52000000)

  override val shadowAmbient             = Color(0x66000000)
  override val shadowSpot                = Color(0x99000000)
}

/** Cool blue-gray (Tailwind slate scale). */
object TesseraSlateLight : TesseraBrandColors {
  override val primary                   = Color(0xFF475569)
  override val onPrimary                 = Color(0xFFFFFFFF)
  override val primaryContainer          = Color(0xFFE2E8F0)
  override val onPrimaryContainer        = Color(0xFF0F172A)

  override val secondary                 = Color(0xFF64748B)
  override val onSecondary               = Color(0xFFFFFFFF)
  override val secondaryContainer        = Color(0xFFE8EDF3)
  override val onSecondaryContainer      = Color(0xFF1E293B)

  override val tertiary                  = Color(0xFF566377)
  override val onTertiary                = Color(0xFFFFFFFF)
  override val tertiaryContainer         = Color(0xFFE2E8F0)
  override val onTertiaryContainer       = Color(0xFF0F172A)

  override val error                     = TesseraBrandLight.error
  override val onError                   = TesseraBrandLight.onError
  override val errorContainer            = TesseraBrandLight.errorContainer
  override val onErrorContainer          = TesseraBrandLight.onErrorContainer

  override val background                = Color(0xFFF8FAFC)
  override val onBackground              = Color(0xFF1E293B)
  override val surface                   = Color(0xFFFFFFFF)
  override val onSurface                 = Color(0xFF1E293B)
  override val surfaceDim                = Color(0xFFE7ECF2)
  override val surfaceBright             = Color(0xFFFFFFFF)
  override val surfaceContainerLowest    = Color(0xFFFFFFFF)
  override val surfaceContainerLow       = Color(0xFFF4F7FA)
  override val surfaceContainer          = Color(0xFFEEF2F7)
  override val surfaceContainerHigh      = Color(0xFFE7ECF2)
  override val surfaceContainerHighest   = Color(0xFFDFE6EE)
  override val onSurfaceVariant          = Color(0xFF556070)
  override val outline                   = Color(0xFF8593A4)
  override val outlineVariant            = Color(0xFFD2DAE3)
  override val scrim                     = Color(0x52000000)

  override val shadowAmbient             = Color(0x33A0AEC0)
  override val shadowSpot                = Color(0x290F172A)
}

object TesseraSlateDark : TesseraBrandColors {
  override val primary                   = Color(0xFFCBD5E1)
  override val onPrimary                 = Color(0xFF1E293B)
  override val primaryContainer          = Color(0xFF334155)
  override val onPrimaryContainer        = Color(0xFFE2E8F0)

  override val secondary                 = Color(0xFFB2BFCE)
  override val onSecondary               = Color(0xFF1E293B)
  override val secondaryContainer        = Color(0xFF2E3B4D)
  override val onSecondaryContainer      = Color(0xFFE2E8F0)

  override val tertiary                  = Color(0xFFC0CCDA)
  override val onTertiary                = Color(0xFF1E293B)
  override val tertiaryContainer         = Color(0xFF334155)
  override val onTertiaryContainer       = Color(0xFFE2E8F0)

  override val error                     = TesseraBrandDark.error
  override val onError                   = TesseraBrandDark.onError
  override val errorContainer            = TesseraBrandDark.errorContainer
  override val onErrorContainer          = TesseraBrandDark.onErrorContainer

  override val background                = Color(0xFF0F172A)
  override val onBackground              = Color(0xFFE2E8F0)
  override val surface                   = Color(0xFF131C30)
  override val onSurface                 = Color(0xFFE2E8F0)
  override val surfaceDim                = Color(0xFF0F172A)
  override val surfaceBright             = Color(0xFF2E3A50)
  override val surfaceContainerLowest    = Color(0xFF0A1020)
  override val surfaceContainerLow       = Color(0xFF141D33)
  override val surfaceContainer          = Color(0xFF18223A)
  override val surfaceContainerHigh      = Color(0xFF202C46)
  override val surfaceContainerHighest   = Color(0xFF2A374F)
  override val onSurfaceVariant          = Color(0xFFAEB9C9)
  override val outline                   = Color(0xFF7E8A9C)
  override val outlineVariant            = Color(0xFF3A4658)
  override val scrim                     = Color(0x52000000)

  override val shadowAmbient             = Color(0x66000000)
  override val shadowSpot                = Color(0x99000000)
}

/** Muted desaturated green (sage). */
object TesseraSageLight : TesseraBrandColors {
  override val primary                   = Color(0xFF5E6F52)
  override val onPrimary                 = Color(0xFFFFFFFF)
  override val primaryContainer          = Color(0xFFDEE6D4)
  override val onPrimaryContainer        = Color(0xFF1A2113)

  override val secondary                 = Color(0xFF6B7563)
  override val onSecondary               = Color(0xFFFFFFFF)
  override val secondaryContainer        = Color(0xFFE2E7DB)
  override val onSecondaryContainer      = Color(0xFF1D241A)

  override val tertiary                  = Color(0xFF5A6B5E)
  override val onTertiary                = Color(0xFFFFFFFF)
  override val tertiaryContainer         = Color(0xFFDCE7DD)
  override val onTertiaryContainer       = Color(0xFF18241B)

  override val error                     = TesseraBrandLight.error
  override val onError                   = TesseraBrandLight.onError
  override val errorContainer            = TesseraBrandLight.errorContainer
  override val onErrorContainer          = TesseraBrandLight.onErrorContainer

  override val background                = Color(0xFFF6F8F2)
  override val onBackground              = Color(0xFF1E2419)
  override val surface                   = Color(0xFFFCFDF8)
  override val onSurface                 = Color(0xFF1E2419)
  override val surfaceDim                = Color(0xFFE7EADF)
  override val surfaceBright             = Color(0xFFFCFDF8)
  override val surfaceContainerLowest    = Color(0xFFFFFFFF)
  override val surfaceContainerLow       = Color(0xFFF3F6EC)
  override val surfaceContainer          = Color(0xFFEEF1E6)
  override val surfaceContainerHigh      = Color(0xFFE7EBDF)
  override val surfaceContainerHighest   = Color(0xFFE1E6D8)
  override val onSurfaceVariant          = Color(0xFF56604F)
  override val outline                   = Color(0xFF84897B)
  override val outlineVariant            = Color(0xFFD2D7C8)
  override val scrim                     = Color(0x52000000)

  override val shadowAmbient             = Color(0x33B2B8A6)
  override val shadowSpot                = Color(0x291A2113)
}

object TesseraSageDark : TesseraBrandColors {
  override val primary                   = Color(0xFFB2C29E)
  override val onPrimary                 = Color(0xFF25311A)
  override val primaryContainer          = Color(0xFF3C4A2F)
  override val onPrimaryContainer        = Color(0xFFDEE6D4)

  override val secondary                 = Color(0xFFBDC6AF)
  override val onSecondary               = Color(0xFF28311F)
  override val secondaryContainer        = Color(0xFF3A4433)
  override val onSecondaryContainer      = Color(0xFFE2E7DB)

  override val tertiary                  = Color(0xFFB7C7B4)
  override val onTertiary                = Color(0xFF233121)
  override val tertiaryContainer         = Color(0xFF3A4A3C)
  override val onTertiaryContainer       = Color(0xFFDCE7DD)

  override val error                     = TesseraBrandDark.error
  override val onError                   = TesseraBrandDark.onError
  override val errorContainer            = TesseraBrandDark.errorContainer
  override val onErrorContainer          = TesseraBrandDark.onErrorContainer

  override val background                = Color(0xFF14160F)
  override val onBackground              = Color(0xFFE4E7DC)
  override val surface                   = Color(0xFF1B1E15)
  override val onSurface                 = Color(0xFFE4E7DC)
  override val surfaceDim                = Color(0xFF14160F)
  override val surfaceBright             = Color(0xFF353A2C)
  override val surfaceContainerLowest    = Color(0xFF0F110A)
  override val surfaceContainerLow       = Color(0xFF1D2016)
  override val surfaceContainer          = Color(0xFF20241A)
  override val surfaceContainerHigh      = Color(0xFF2A2E20)
  override val surfaceContainerHighest   = Color(0xFF343A2A)
  override val onSurfaceVariant          = Color(0xFFC3C8B6)
  override val outline                   = Color(0xFF8C9180)
  override val outlineVariant            = Color(0xFF45493B)
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

/** Alpha of a theme-colour tint over a Haze backdrop: low enough for the blurred content to read, high enough that the hue stays recognisable. */
const val GlassTintAlpha = 0.62f

val CapsuleBorder            = Color.White.copy(alpha = 0.20f)
val CapsuleTintLight         = Color.White.copy(alpha = 0.65f)
val CapsuleTintDark          = Color(0xFF201F1D).copy(alpha = 0.72f)
val CapsuleHighlightLight    = Color.White.copy(alpha = 0.40f)
val CapsuleHighlightDark     = Color.White.copy(alpha = 0.14f)
val CapsuleShadowLight       = Color(0xFFB8B1A6).copy(alpha = 0.16f)
val CapsuleShadowDark        = Color.Black.copy(alpha = 0.32f)
