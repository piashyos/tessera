# Material You Redesign — Plan 01: Tokens Foundation

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Заменить тонкий тематический слой Tessera на полную Material You палитру с брендовой айдентикой, двумя шрифтами (Inter variable + Instrument Serif), полным набором ролей M3-типографики, shape/spacing/elevation/motion токенами и брендовым ripple. Это foundation — никакого UI кода не меняем, только тему и ресурсы.

**Architecture:** Token-объекты в `core/ui/theme/` (Color, Type, Shape, Spacing, Elevation, Motion, Ripple). `Theme.kt` собирает full M3 `ColorScheme` с гибридной dynamic-color стратегией (бренд держит primary/background/surface, динамика тянет secondary/tertiary/surface-containers на API 31+). XML mirror в `colors.xml`/`themes.xml` для Aztec и system Activity.

**Tech Stack:** Jetpack Compose Material3, Kotlin, Android res/font (variable TTF), Material3 DayNight base theme.

**Spec reference:** `docs/superpowers/specs/2026-06-19-tessera-material-you-redesign-design.md` §4 (Tokens).

---

## Chunk 1: File Structure & Font Assets

### File map

```
app/src/main/
  res/
    font/
      inter_variable.ttf          (NEW, ~340 KB, from Google Fonts)
      instrument_serif_regular.ttf (NEW, ~35 KB, from Google Fonts)
      instrument_serif_italic.ttf  (NEW, ~35 KB, from Google Fonts)
    values/
      colors.xml                   (MODIFY: full M3 palette mapped to brand)
      themes.xml                   (MODIFY: Theme.Material3.DayNight base + attr mapping)
    values-night/
      colors.xml                   (MODIFY: dark M3 palette — currently doesn't exist or has minimal)
  java/app/tessera/core/ui/theme/
    Color.kt        (MODIFY: full TesseraBrandLight/Dark objects, 30 roles each)
    Type.kt         (MODIFY: 5 lines → ~80 lines with Inter/InstrumentSerif FontFamily + 15 roles)
    Shape.kt        (NEW: TesseraShapes + TesseraShape semantic constants)
    Spacing.kt      (NEW: TesseraSpacing object, 9 levels)
    Elevation.kt    (NEW: TesseraElevation enum + tesseraShadow Modifier extension)
    Motion.kt       (NEW: TesseraMotion easings + durations)
    Ripple.kt       (NEW: brandRipple RippleConfiguration + LocalBrandRipple)
    Theme.kt        (MODIFY: full ColorScheme assembly + dynamic-color try/catch + hybrid copy)
app/src/test/java/app/tessera/core/ui/theme/
  TesseraColorTest.kt        (NEW)
  TesseraTypographyTest.kt   (NEW)
  TesseraShapeTest.kt        (NEW)
```

### Task 1: Add Inter variable font

**Files:**
- Create: `app/src/main/res/font/inter_variable.ttf`

- [ ] **Step 1: Download Inter variable TTF**

Inter (variable, full weight axis 100-900) live on Google Fonts. Download from <https://fonts.google.com/specimen/Inter> → "Get font" → unpack ZIP → use `Inter-VariableFont_opsz,wght.ttf` OR `Inter[opsz,wght].ttf` (same file). The variable version supports the `wght` axis (100-900) and `opsz` axis (optical sizing).

Rename to lowercase with underscores (Android resource naming): `inter_variable.ttf`.

```bash
# from project root, after unzipping the Google Fonts archive elsewhere:
cp /path/to/unzipped/Inter-VariableFont_opsz,wght.ttf app/src/main/res/font/inter_variable.ttf
ls -lh app/src/main/res/font/inter_variable.ttf
```

Expected: file ~300-400 KB.

- [ ] **Step 2: Verify font loads via aapt**

Run: `./gradlew :app:processDebugResources`
Expected: Build succeeds. No "Invalid file name" error.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/font/inter_variable.ttf
git commit -m "feat(theme): add Inter variable font asset"
```

---

### Task 2: Add Instrument Serif font (regular + italic)

**Files:**
- Create: `app/src/main/res/font/instrument_serif_regular.ttf`
- Create: `app/src/main/res/font/instrument_serif_italic.ttf`

- [ ] **Step 1: Download Instrument Serif from Google Fonts**

From <https://fonts.google.com/specimen/Instrument+Serif> → "Get font" → unpack. Use `InstrumentSerif-Regular.ttf` and `InstrumentSerif-Italic.ttf`.

- [ ] **Step 2: Copy and rename**

```bash
cp /path/to/InstrumentSerif-Regular.ttf app/src/main/res/font/instrument_serif_regular.ttf
cp /path/to/InstrumentSerif-Italic.ttf  app/src/main/res/font/instrument_serif_italic.ttf
ls -lh app/src/main/res/font/instrument_serif_*.ttf
```

Expected: two files, ~30-50 KB each.

- [ ] **Step 3: Verify aapt processes them**

Run: `./gradlew :app:processDebugResources`
Expected: Build succeeds.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/font/instrument_serif_regular.ttf app/src/main/res/font/instrument_serif_italic.ttf
git commit -m "feat(theme): add Instrument Serif font assets (regular + italic)"
```

---

## Chunk 2: Color tokens

### Task 3: Replace `Color.kt` with full TesseraBrandLight/Dark

**Files:**
- Modify: `app/src/main/java/app/tessera/core/ui/theme/Color.kt`
- Test: `app/src/test/java/app/tessera/core/ui/theme/TesseraColorTest.kt` (new)

- [ ] **Step 1: Write failing test**

Create `app/src/test/java/app/tessera/core/ui/theme/TesseraColorTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.theme.TesseraColorTest"`
Expected: COMPILATION ERROR — `TesseraBrandLight` / `TesseraBrandDark` unresolved.

- [ ] **Step 3: Implement Color.kt**

Replace contents of `app/src/main/java/app/tessera/core/ui/theme/Color.kt`:

```kotlin
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
  override val scrim                     = Color(0x52000000) // alpha=0.32 → 0x52

  override val shadowAmbient             = Color(0x38B8B1A6) // 0.22 of #B8B1A6
  override val shadowSpot                = Color(0x2E3A1A0F) // 0.18 of #3A1A0F
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

  override val shadowAmbient             = Color(0x66000000) // 0.40 black
  override val shadowSpot                = Color(0x99000000) // 0.60 black
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.theme.TesseraColorTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Verify nothing else broke**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. The existing references to `PrimaryLight`/`PrimaryDark`/`SurfaceLight`/etc still resolve — *we kept them as aliases? No, we replaced the file.* The replaced `Color.kt` removed those top-level vals. We must restore them as deprecated aliases to keep `Theme.kt` compiling.

  **Add at bottom of `Color.kt`:**

  ```kotlin
  // ── Legacy aliases (will be removed when Theme.kt is rewritten in next chunk) ──
  @Deprecated("Use TesseraBrandLight.surface")          val SurfaceLight             = TesseraBrandLight.surface
  @Deprecated("Use TesseraBrandDark.surface")           val SurfaceDark              = TesseraBrandDark.surface
  @Deprecated("Use TesseraBrandLight.background")       val BackgroundGlowLight      = TesseraBrandLight.background
  @Deprecated("Use TesseraBrandDark.background")        val BackgroundGlowDark       = TesseraBrandDark.background
  @Deprecated("Use TesseraBrandLight.surfaceContainerHigh") val SurfaceVariantLight  = TesseraBrandLight.surfaceContainerHigh
  @Deprecated("Use TesseraBrandDark.surfaceContainerHigh")  val SurfaceVariantDark   = TesseraBrandDark.surfaceContainerHigh
  @Deprecated("Use TesseraBrandLight.onSurface")        val OnSurfaceLight           = TesseraBrandLight.onSurface
  @Deprecated("Use TesseraBrandDark.onSurface")         val OnSurfaceDark            = TesseraBrandDark.onSurface
  @Deprecated("Use TesseraBrandLight.primary")          val PrimaryLight             = TesseraBrandLight.primary
  @Deprecated("Use TesseraBrandDark.primary")           val PrimaryDark              = TesseraBrandDark.primary

  // Capsule/floating constants — temporary, will move to component-specific files when components are rewritten.
  val CapsuleBorder            = Color.White.copy(alpha = 0.20f)
  val CapsuleTintLight         = Color.White.copy(alpha = 0.65f)
  val CapsuleTintDark          = Color(0xFF201F1D).copy(alpha = 0.72f)
  val CapsuleHighlightLight    = Color.White.copy(alpha = 0.40f)
  val CapsuleHighlightDark     = Color.White.copy(alpha = 0.14f)
  val CapsuleShadowLight       = Color(0xFFB8B1A6).copy(alpha = 0.16f)
  val CapsuleShadowDark        = Color.Black.copy(alpha = 0.32f)
  val FloatingShadowAmbient    = Color.Black.copy(alpha = 0.52f)
  val FloatingShadowSpot       = Color.Black.copy(alpha = 0.70f)
  ```

Run build again: `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/theme/Color.kt app/src/test/java/app/tessera/core/ui/theme/TesseraColorTest.kt
git commit -m "feat(theme): full M3 brand color tokens (TesseraBrandLight/Dark)

Adds 30-role palettes mirroring design/mockups/tokens.css. Existing
PrimaryLight/SurfaceLight/etc kept as deprecated aliases so Theme.kt
and existing capsule code keep compiling until rewritten."
```

---

## Chunk 3: Type tokens (Inter + Instrument Serif + 15 roles)

### Task 4: Replace `Type.kt` with full Typography

**Files:**
- Modify: `app/src/main/java/app/tessera/core/ui/theme/Type.kt`
- Test: `app/src/test/java/app/tessera/core/ui/theme/TesseraTypographyTest.kt` (new)

- [ ] **Step 1: Write failing test**

Create `app/src/test/java/app/tessera/core/ui/theme/TesseraTypographyTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.theme.TesseraTypographyTest"`
Expected: COMPILATION ERROR — `InstrumentSerif`, `Inter` unresolved.

- [ ] **Step 3: Implement Type.kt**

Replace contents of `app/src/main/java/app/tessera/core/ui/theme/Type.kt`:

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.theme.TesseraTypographyTest"`
Expected: PASS (9 tests).

- [ ] **Step 5: Build check**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (Theme.kt уже использовал `TesseraTypography` без полей — теперь поля есть, ничего не ломается.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/theme/Type.kt app/src/test/java/app/tessera/core/ui/theme/TesseraTypographyTest.kt
git commit -m "feat(theme): 15-role M3 typography with Inter + Instrument Serif"
```

---

## Chunk 4: Shape, Spacing, Motion tokens

### Task 5: Create `Shape.kt`

**Files:**
- Create: `app/src/main/java/app/tessera/core/ui/theme/Shape.kt`
- Test: `app/src/test/java/app/tessera/core/ui/theme/TesseraShapeTest.kt` (new)

- [ ] **Step 1: Write failing test**

Create `app/src/test/java/app/tessera/core/ui/theme/TesseraShapeTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test, expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.theme.TesseraShapeTest"`
Expected: COMPILATION ERROR — `TesseraShapes`, `TesseraShape` unresolved.

- [ ] **Step 3: Implement Shape.kt**

Create `app/src/main/java/app/tessera/core/ui/theme/Shape.kt`:

```kotlin
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
  val bottomSheetTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  val formatBarTop   = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
}
```

- [ ] **Step 4: Run test, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.theme.TesseraShapeTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/theme/Shape.kt app/src/test/java/app/tessera/core/ui/theme/TesseraShapeTest.kt
git commit -m "feat(theme): Shape tokens (M3 Shapes + semantic constants)"
```

---

### Task 6: Create `Spacing.kt`

**Files:**
- Create: `app/src/main/java/app/tessera/core/ui/theme/Spacing.kt`

Spacing — простой объект констант, тесты не критичны (компилятор гарантирует значения). Без TDD здесь.

- [ ] **Step 1: Create Spacing.kt**

```kotlin
package app.tessera.core.ui.theme

import androidx.compose.ui.unit.dp

/** 4dp-grid spacing tokens. Use in Modifier.padding to keep the grid consistent. */
object TesseraSpacing {
  val xs      = 4.dp
  val sm      = 8.dp
  val md      = 12.dp
  val lg      = 16.dp
  val xl      = 20.dp
  val xxl     = 24.dp
  val xxxl    = 32.dp
  val xxxxl   = 40.dp
  val xxxxxl  = 48.dp
}
```

- [ ] **Step 2: Build check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/theme/Spacing.kt
git commit -m "feat(theme): Spacing tokens (4dp grid, 9 levels)"
```

---

### Task 7: Create `Motion.kt`

**Files:**
- Create: `app/src/main/java/app/tessera/core/ui/theme/Motion.kt`

- [ ] **Step 1: Create Motion.kt**

```kotlin
package app.tessera.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

/** Motion tokens: M3-aligned easings and durations (ms). */
object TesseraMotion {
  val EaseStandard    = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
  val EaseEmphasized  = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
  val EaseDecelerate  = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

  // Durations in milliseconds.
  const val DurShort1Ms   = 50
  const val DurShort4Ms   = 200
  const val DurMedium2Ms  = 300
  const val DurMedium4Ms  = 400
}
```

- [ ] **Step 2: Build check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/theme/Motion.kt
git commit -m "feat(theme): Motion tokens (easings + durations)"
```

---

## Chunk 5: Elevation modifier + Brand ripple

### Task 8: Create `Elevation.kt` with `tesseraShadow` modifier

**Files:**
- Create: `app/src/main/java/app/tessera/core/ui/theme/Elevation.kt`

- [ ] **Step 1: Create Elevation.kt**

```kotlin
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
 * Note: on API 26-27 ambientColor/spotColor are ignored by the system and
 * the shadow renders default black (acceptable for two old API levels).
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
```

- [ ] **Step 2: Build check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/theme/Elevation.kt
git commit -m "feat(theme): tesseraShadow Modifier with warm-tinted drop shadows"
```

---

### Task 9: Create `Ripple.kt` with brand ripple config

**Files:**
- Create: `app/src/main/java/app/tessera/core/ui/theme/Ripple.kt`

- [ ] **Step 1: Create Ripple.kt**

```kotlin
package app.tessera.core.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Brand-tinted ripple configuration. Scope per-component via
 * CompositionLocalProvider(LocalRippleConfiguration provides brandRipple) — do NOT install
 * globally, or every IconButton in the tree will get a terracotta ripple.
 */
@OptIn(ExperimentalMaterial3Api::class)
val brandRipple: RippleConfiguration
  @Composable @ReadOnlyComposable
  get() = RippleConfiguration(
    color = MaterialTheme.colorScheme.primary,
    rippleAlpha = RippleAlpha(
      draggedAlpha = 0.16f,
      focusedAlpha = 0.12f,
      hoveredAlpha = 0.10f,
      pressedAlpha = 0.20f,
    ),
  )
```

- [ ] **Step 2: Build check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/theme/Ripple.kt
git commit -m "feat(theme): brand-tinted RippleConfiguration (per-component scope)"
```

---

## Chunk 6: Theme.kt — assemble full M3 ColorScheme

### Task 10: Rewrite `Theme.kt` with hybrid dynamic color + full brand mapping

**Files:**
- Modify: `app/src/main/java/app/tessera/core/ui/theme/Theme.kt`

- [ ] **Step 1: Rewrite Theme.kt**

Replace contents of `app/src/main/java/app/tessera/core/ui/theme/Theme.kt`:

```kotlin
package app.tessera.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.tessera.core.preferences.ThemeMode

private fun brandLightScheme() = lightColorScheme(
  primary                   = TesseraBrandLight.primary,
  onPrimary                 = TesseraBrandLight.onPrimary,
  primaryContainer          = TesseraBrandLight.primaryContainer,
  onPrimaryContainer        = TesseraBrandLight.onPrimaryContainer,
  secondary                 = TesseraBrandLight.secondary,
  onSecondary               = TesseraBrandLight.onSecondary,
  secondaryContainer        = TesseraBrandLight.secondaryContainer,
  onSecondaryContainer      = TesseraBrandLight.onSecondaryContainer,
  tertiary                  = TesseraBrandLight.tertiary,
  onTertiary                = TesseraBrandLight.onTertiary,
  tertiaryContainer         = TesseraBrandLight.tertiaryContainer,
  onTertiaryContainer       = TesseraBrandLight.onTertiaryContainer,
  error                     = TesseraBrandLight.error,
  onError                   = TesseraBrandLight.onError,
  errorContainer            = TesseraBrandLight.errorContainer,
  onErrorContainer          = TesseraBrandLight.onErrorContainer,
  background                = TesseraBrandLight.background,
  onBackground              = TesseraBrandLight.onBackground,
  surface                   = TesseraBrandLight.surface,
  onSurface                 = TesseraBrandLight.onSurface,
  surfaceDim                = TesseraBrandLight.surfaceDim,
  surfaceBright             = TesseraBrandLight.surfaceBright,
  surfaceContainerLowest    = TesseraBrandLight.surfaceContainerLowest,
  surfaceContainerLow       = TesseraBrandLight.surfaceContainerLow,
  surfaceContainer          = TesseraBrandLight.surfaceContainer,
  surfaceContainerHigh      = TesseraBrandLight.surfaceContainerHigh,
  surfaceContainerHighest   = TesseraBrandLight.surfaceContainerHighest,
  onSurfaceVariant          = TesseraBrandLight.onSurfaceVariant,
  outline                   = TesseraBrandLight.outline,
  outlineVariant            = TesseraBrandLight.outlineVariant,
  scrim                     = TesseraBrandLight.scrim,
)

private fun brandDarkScheme() = darkColorScheme(
  primary                   = TesseraBrandDark.primary,
  onPrimary                 = TesseraBrandDark.onPrimary,
  primaryContainer          = TesseraBrandDark.primaryContainer,
  onPrimaryContainer        = TesseraBrandDark.onPrimaryContainer,
  secondary                 = TesseraBrandDark.secondary,
  onSecondary               = TesseraBrandDark.onSecondary,
  secondaryContainer        = TesseraBrandDark.secondaryContainer,
  onSecondaryContainer      = TesseraBrandDark.onSecondaryContainer,
  tertiary                  = TesseraBrandDark.tertiary,
  onTertiary                = TesseraBrandDark.onTertiary,
  tertiaryContainer         = TesseraBrandDark.tertiaryContainer,
  onTertiaryContainer       = TesseraBrandDark.onTertiaryContainer,
  error                     = TesseraBrandDark.error,
  onError                   = TesseraBrandDark.onError,
  errorContainer            = TesseraBrandDark.errorContainer,
  onErrorContainer          = TesseraBrandDark.onErrorContainer,
  background                = TesseraBrandDark.background,
  onBackground              = TesseraBrandDark.onBackground,
  surface                   = TesseraBrandDark.surface,
  onSurface                 = TesseraBrandDark.onSurface,
  surfaceDim                = TesseraBrandDark.surfaceDim,
  surfaceBright             = TesseraBrandDark.surfaceBright,
  surfaceContainerLowest    = TesseraBrandDark.surfaceContainerLowest,
  surfaceContainerLow       = TesseraBrandDark.surfaceContainerLow,
  surfaceContainer          = TesseraBrandDark.surfaceContainer,
  surfaceContainerHigh      = TesseraBrandDark.surfaceContainerHigh,
  surfaceContainerHighest   = TesseraBrandDark.surfaceContainerHighest,
  onSurfaceVariant          = TesseraBrandDark.onSurfaceVariant,
  outline                   = TesseraBrandDark.outline,
  outlineVariant            = TesseraBrandDark.outlineVariant,
  scrim                     = TesseraBrandDark.scrim,
)

/**
 * Overlay our brand identity onto a dynamic-color scheme:
 * primary/background/surface always brand; let secondary/tertiary/surfaceContainer-*
 * keep their dynamic warm-tinted values harmonized from the user's wallpaper.
 */
private fun ColorScheme.brandOverlay(dark: Boolean): ColorScheme {
  val brand = if (dark) TesseraBrandDark else TesseraBrandLight
  return this.copy(
    primary                = brand.primary,
    onPrimary              = brand.onPrimary,
    primaryContainer       = brand.primaryContainer,
    onPrimaryContainer     = brand.onPrimaryContainer,
    background             = brand.background,
    onBackground           = brand.onBackground,
    surface                = brand.surface,
    onSurface              = brand.onSurface,
  )
}

@Composable
fun TesseraTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val ctx = LocalContext.current
  val scheme: ColorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      runCatching {
        val dyn = if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dyn.brandOverlay(darkTheme)
      }.getOrElse {
        if (darkTheme) brandDarkScheme() else brandLightScheme()
      }
    }
    darkTheme -> brandDarkScheme()
    else      -> brandLightScheme()
  }

  MaterialTheme(
    colorScheme = scheme,
    typography  = TesseraTypography,
    shapes      = TesseraShapes,
    content     = content,
  )
}

@Composable
fun TesseraTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
  val (darkTheme, dynamicColor) = themeMode.resolve(isSystemInDarkTheme())
  TesseraTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
```

**Removed:** `NoIndication` object, `LocalIndication provides NoIndication`, `LocalRippleConfiguration provides null`. M3 ripples are now restored globally; brand-tinted ripple is opt-in per component (via `brandRipple` from `Ripple.kt`).

- [ ] **Step 2: Build check**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Compilation may flag unused legacy color aliases (`SurfaceLight`, `BackgroundGlowLight`, etc.) — they're still referenced by `app/src/main/AndroidManifest.xml`-related theme code? No. They're only referenced internally. With `@Deprecated` annotations but no usage, warnings appear but the build still passes.

- [ ] **Step 3: Run all existing unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: ALL PASS (existing tests + the 3 new theme test classes). If any UI snapshot tests existed they might break — but `app/src/test/` only has MarkdownConverter/MarkdownBlocks tests so far, none of them touch theme.

- [ ] **Step 4: Run androidTests (smoke)**

Run: `./gradlew :app:connectedDebugAndroidTest` (requires emulator / device)

If no device available, skip this and document: "androidTest verification deferred to manual QA."
Expected (if device): existing tests (`NotesListScreenTest`, `TesseraShowcaseScreenTest`, `SearchClearTest`) pass. These tests use `TesseraTheme` as wrapper, so verify they still launch.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/theme/Theme.kt
git commit -m "feat(theme): full M3 ColorScheme with hybrid dynamic-color overlay

Replaces 8-property thin scheme with all 30 M3 roles mapped to brand
tokens. On API 31+ dynamic color is overlaid: primary/background/
surface stay brand, secondary/tertiary/surfaceContainers harmonize from
wallpaper. Crash-safe via runCatching. NoIndication and null
RippleConfiguration removed — M3 ripples restored globally; brand
ripple is opt-in via app/tessera/core/ui/theme/Ripple.kt."
```

---

## Chunk 7: XML mirror — colors.xml + themes.xml

### Task 11: Rewrite `res/values/colors.xml`

**Files:**
- Modify: `app/src/main/res/values/colors.xml`

- [ ] **Step 1: Replace contents**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Light brand palette (mirror of TesseraBrandLight). Aztec, system Activity, and
         any XML drawable references read from here via ?attr/colorXxx. -->

    <color name="brand_primary">#FFC96442</color>
    <color name="brand_on_primary">#FFFFFFFF</color>
    <color name="brand_primary_container">#FFFBE3D8</color>
    <color name="brand_on_primary_container">#FF3A1A0F</color>

    <color name="brand_secondary">#FF7C5E4F</color>
    <color name="brand_on_secondary">#FFFFFFFF</color>
    <color name="brand_secondary_container">#FFF5E1D6</color>
    <color name="brand_on_secondary_container">#FF2F1E14</color>

    <color name="brand_tertiary">#FF5F6A4F</color>
    <color name="brand_on_tertiary">#FFFFFFFF</color>
    <color name="brand_tertiary_container">#FFE2EAD3</color>
    <color name="brand_on_tertiary_container">#FF1B2412</color>

    <color name="brand_error">#FFB3261E</color>
    <color name="brand_on_error">#FFFFFFFF</color>
    <color name="brand_error_container">#FFF9DEDC</color>
    <color name="brand_on_error_container">#FF410E0B</color>

    <color name="brand_background">#FFFAF9F5</color>
    <color name="brand_on_background">#FF262420</color>
    <color name="brand_surface">#FFFFFFFF</color>
    <color name="brand_on_surface">#FF262420</color>
    <color name="brand_surface_dim">#FFEDEAE3</color>
    <color name="brand_surface_bright">#FFFFFFFF</color>
    <color name="brand_surface_container_lowest">#FFFFFFFF</color>
    <color name="brand_surface_container_low">#FFF6F4ED</color>
    <color name="brand_surface_container">#FFF3F0E8</color>
    <color name="brand_surface_container_high">#FFEDEAE3</color>
    <color name="brand_surface_container_highest">#FFE7E3DA</color>
    <color name="brand_on_surface_variant">#FF5C584F</color>
    <color name="brand_outline">#FF8A8478</color>
    <color name="brand_outline_variant">#FFD9D4C7</color>

    <!-- Legacy aliases (kept for compatibility with code/layouts still using these names) -->
    <color name="aztec_bullet">@color/brand_primary</color>
    <color name="window_background">@color/brand_background</color>
</resources>
```

- [ ] **Step 2: Create `res/values-night/colors.xml`** (if it doesn't exist, create with same `name`s but dark values):

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Dark brand palette (mirror of TesseraBrandDark). -->

    <color name="brand_primary">#FFD97757</color>
    <color name="brand_on_primary">#FF3A1A0F</color>
    <color name="brand_primary_container">#FF5C2A18</color>
    <color name="brand_on_primary_container">#FFFBE3D8</color>

    <color name="brand_secondary">#FFD8B8A2</color>
    <color name="brand_on_secondary">#FF3A2818</color>
    <color name="brand_secondary_container">#FF523929</color>
    <color name="brand_on_secondary_container">#FFF5E1D6</color>

    <color name="brand_tertiary">#FFB7C6A1</color>
    <color name="brand_on_tertiary">#FF1F2A12</color>
    <color name="brand_tertiary_container">#FF3D4928</color>
    <color name="brand_on_tertiary_container">#FFE2EAD3</color>

    <color name="brand_error">#FFF2B8B5</color>
    <color name="brand_on_error">#FF601410</color>
    <color name="brand_error_container">#FF8C1D18</color>
    <color name="brand_on_error_container">#FFF9DEDC</color>

    <color name="brand_background">#FF191815</color>
    <color name="brand_on_background">#FFECE9E3</color>
    <color name="brand_surface">#FF201F1D</color>
    <color name="brand_on_surface">#FFECE9E3</color>
    <color name="brand_surface_dim">#FF191815</color>
    <color name="brand_surface_bright">#FF393734</color>
    <color name="brand_surface_container_lowest">#FF131211</color>
    <color name="brand_surface_container_low">#FF232220</color>
    <color name="brand_surface_container">#FF26241F</color>
    <color name="brand_surface_container_high">#FF2C2B27</color>
    <color name="brand_surface_container_highest">#FF37352F</color>
    <color name="brand_on_surface_variant">#FFC9C3B6</color>
    <color name="brand_outline">#FF948D80</color>
    <color name="brand_outline_variant">#FF4A4740</color>

    <!-- Legacy aliases -->
    <color name="aztec_bullet">@color/brand_primary</color>
    <color name="window_background">@color/brand_background</color>
</resources>
```

- [ ] **Step 3: Verify aapt + build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/colors.xml app/src/main/res/values-night/colors.xml
git commit -m "feat(theme): XML color mirror of TesseraBrand{Light,Dark}"
```

---

### Task 12: Update `themes.xml` to Material3 DayNight base with brand mapping

**Files:**
- Modify: `app/src/main/res/values/themes.xml`

- [ ] **Step 1: Replace contents**

```xml
<resources>
    <!-- Base theme. Material3 DayNight gives us system attr surfaces that match Compose.
         Aztec and any XML-inflated views (rich_note_body.xml) tie into ?attr/colorXxx,
         which we map here to our brand tokens. -->
    <style name="Theme.Tessera" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="colorPrimary">@color/brand_primary</item>
        <item name="colorOnPrimary">@color/brand_on_primary</item>
        <item name="colorPrimaryContainer">@color/brand_primary_container</item>
        <item name="colorOnPrimaryContainer">@color/brand_on_primary_container</item>

        <item name="colorSecondary">@color/brand_secondary</item>
        <item name="colorOnSecondary">@color/brand_on_secondary</item>
        <item name="colorSecondaryContainer">@color/brand_secondary_container</item>
        <item name="colorOnSecondaryContainer">@color/brand_on_secondary_container</item>

        <item name="colorTertiary">@color/brand_tertiary</item>
        <item name="colorOnTertiary">@color/brand_on_tertiary</item>
        <item name="colorTertiaryContainer">@color/brand_tertiary_container</item>
        <item name="colorOnTertiaryContainer">@color/brand_on_tertiary_container</item>

        <item name="colorError">@color/brand_error</item>
        <item name="colorOnError">@color/brand_on_error</item>
        <item name="colorErrorContainer">@color/brand_error_container</item>
        <item name="colorOnErrorContainer">@color/brand_on_error_container</item>

        <item name="android:colorBackground">@color/brand_background</item>
        <item name="colorOnBackground">@color/brand_on_background</item>
        <item name="colorSurface">@color/brand_surface</item>
        <item name="colorOnSurface">@color/brand_on_surface</item>
        <item name="colorSurfaceVariant">@color/brand_surface_container_high</item>
        <item name="colorOnSurfaceVariant">@color/brand_on_surface_variant</item>
        <item name="colorOutline">@color/brand_outline</item>
        <item name="colorOutlineVariant">@color/brand_outline_variant</item>

        <!-- Avoid black flash on Activity recreation (locale change, etc.) -->
        <item name="android:windowBackground">@color/brand_background</item>
    </style>
</resources>
```

- [ ] **Step 2: Verify Material3 dependency exists**

Run: `grep -r "com.google.android.material" app/build.gradle.kts`
Expected: a line like `implementation("com.google.android.material:material:1.x.x")`. If missing — add to `dependencies { ... }`:

```kotlin
implementation("com.google.android.material:material:1.12.0")
```

Then sync gradle.

- [ ] **Step 3: Build check**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Smoke launch**

Run app on emulator/device, navigate to Notes List → Settings → back. Verify no crashes, fonts visible, colors не выглядят как-то странно (status bar, action bar elevation flashes — мелочи, чинятся в дальнейших планах).

If no device available: document in commit message that smoke test deferred.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/themes.xml app/build.gradle.kts
git commit -m "feat(theme): Material3.DayNight base + brand attr mapping

Theme.Tessera now extends Theme.Material3.DayNight.NoActionBar so that
Aztec and any XML-inflated views read brand-mapped ?attr/colorXxx. Adds
com.google.android.material:material:1.12.0 dependency if not present."
```

---

## Final verification

### Task 13: Full build + test pass

- [ ] **Step 1: Clean build**

Run: `./gradlew clean :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Full unit test pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: ALL PASS, including the 3 new theme test classes (TesseraColorTest, TesseraTypographyTest, TesseraShapeTest) and all existing tests.

- [ ] **Step 3: Lint check**

Run: `./gradlew :app:lintDebug`
Expected: SUCCESS. Warnings about deprecated `SurfaceLight`/`BackgroundGlowLight`/`PrimaryLight`/etc are EXPECTED — they'll be removed when component code stops using them in subsequent plans.

- [ ] **Step 4: Smoke launch on device/emulator**

Launch app, exercise:
- Notes list opens, fonts visible (Inter for UI text)
- Tap a note → editor opens, no crash
- Settings → opens, no crash
- Toggle dark mode in system settings → app follows, colors look right
- (On API 31+, with custom wallpaper) Settings → "Use dynamic color" → verify primary stays terracotta but accents shift

Document any visual regressions — they may be intentional (foundation layer is laid, components still using old aliases will be rewritten in subsequent plans).

If no device available: skip and document deferred.

---

## What this plan does NOT do

- Does NOT modify any UI screen, composable, or AndroidView. All visual changes happen in subsequent plans.
- Does NOT add new components (TesseraTopBar, TesseraDrawer, etc.) — those come in Plan 02.
- Does NOT touch folder data layer (Plan 03), NotesListScreen (Plan 04), Editor (Plan 05), or Settings (Plan 06).
- Does NOT remove deprecated `SurfaceLight`/`PrimaryLight`/`CapsuleBorder`/etc — they'll be cleaned up in the plan that rewrites their last consumer.

## Acceptance criteria

- [ ] All new theme files exist and compile: `Color.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt`, `Elevation.kt`, `Motion.kt`, `Ripple.kt`, `Theme.kt`
- [ ] Inter variable + Instrument Serif TTFs in `res/font/`
- [ ] `res/values/colors.xml` + `res/values-night/colors.xml` mirror Compose tokens
- [ ] `res/values/themes.xml` uses `Theme.Material3.DayNight.NoActionBar` with brand attr mapping
- [ ] `TesseraColorTest`, `TesseraTypographyTest`, `TesseraShapeTest` pass
- [ ] `./gradlew :app:assembleDebug` + `:app:testDebugUnitTest` green
- [ ] Existing UI still launches (deferred to manual QA if no device)

## Next plan

`docs/superpowers/plans/2026-06-19-material-you-redesign-02-components.md` — reusable composables (TesseraTopBar, TesseraChipRow, TesseraNoteCard, TesseraBottomActionBar, TesseraDrawer, TesseraBottomSheet, TesseraDialog, TesseraSnackbar). Will be written after this plan completes.
