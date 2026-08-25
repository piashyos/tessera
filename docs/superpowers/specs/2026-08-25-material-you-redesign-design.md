# Material You redesign — design spec

Approved: 2026-08-25 (option A + PixelPlayer-style icons).
Reference: PixelPlayer (github.com/PixelPlayerHQ/PixelPlayer), analyzed at commit cloned 2026-08-25.

## Goal

Rebuild Tessera's visual layer as a full Material You (Material 3 Expressive) app in the
spirit of PixelPlayer: wallpaper-driven color everywhere (including the editor), expressive
motion for navigation and state changes, and richer appearance settings — while keeping the
note-taking ergonomics intact.

## Decisions (user-approved)

- **Option A**: colors come fully from the wallpaper when dynamic color is on (with 4
  selectable scheme styles); the existing 4 hand-tuned brand palettes (WARM/NEUTRAL/SLATE/SAGE)
  remain as the choice when dynamic color is off or unsupported (< Android 12).
- **Playfair Display serif stays** for note headings/display — it's the app's identity.
- **Icons: Material Icons Rounded** (`material-icons-extended`, `Icons.Rounded.*` /
  `Icons.AutoMirrored.Rounded.*`), replacing Lucide — same set PixelPlayer uses (521
  Rounded usages there).
- Ripple is restored app-wide (Material You requires touch feedback).

## Architecture

### Stage 1 — Foundation
1. Dependency bump: compose BOM to a material3-1.4+/expressive-capable version (pin
   material3 explicitly if needed), Kotlin/KSP/Room/Hilt raised only as far as the BOM
   requires. AGP/Gradle kept unless forced.
2. `TesseraTheme` rework:
   - Dynamic path: `dynamicLight/DarkColorScheme` used **as-is** (delete `brandOverlay`).
   - Scheme styles: seed = system accent (`android.R.color.system_accent1_500`), styles map
     to MDC `SchemeTonalSpot | SchemeVibrant | SchemeExpressive | SchemeFruitSalad`
     (contrast 0.0), converted to Compose `ColorScheme` — PixelPlayer's `ColorRoles.kt`
     approach. `TONAL_SPOT` default ≡ stock dynamic scheme.
   - Non-dynamic path: existing brand palettes unchanged.
   - AMOLED true-black toggle (dark scheme with background/surface forced to black).
   - `MaterialExpressiveTheme` + `MotionScheme.expressive()`.
   - Remove `LocalRippleConfiguration provides null` / `NoIndication`.
3. Preferences: `ThemeStyle` (4 values) + `amoled: Boolean` in DataStore; exposed via
   SettingsViewModel; ThemePickerSheet gains style selector with live preview (palette
   swatches double as style preview when dynamic).
4. Icon migration: Lucide → `Icons.Rounded.*` everywhere; drop `icons-lucide` dependency.

### Stage 2 — Motion
- `ui/navigation/Transitions.kt` ported from PixelPlayer: emphasized easings
  (`CubicBezierEasing(0.2,0,0,1)`, decelerate `(0.2,0.85,0.7,1)`, accelerate
  `(0.3,0,0.8,0.15)`), 450ms enter slide(50%)+scale(0.92)+fade, 225ms exits, pop mirrored.
- `ScreenWrapper`-style behind-screen effect: corner radius 0→32dp, dim 0→0.4, blur 0→24dp
  driven by the nav transition state.
- Predictive back on editor/settings.
- Component motion on `MotionScheme.expressive()` springs (FAB, cards, chips, sheets).

### Stage 3 — Screens
- Notes list: collapsing large top bar, M3 FAB/toolbar, card shape morph on
  select/press, wavy/expressive loading indicators.
- Settings: expressive grouped rows (10dp inner / 24dp outer radii, surfaceContainer
  tiers), live-preview appearance section.
- Search, drawer, sheets restyled on surfaceContainer hierarchy.

### Stage 4 — Editor
- M3 chrome: format bar → `HorizontalFloatingToolbar`-style surface, themed top bar.
- Aztec theming reads MaterialTheme tokens (typography size/leading from
  `MaterialTheme.typography.bodyLarge`, not hardcoded 15sp) — keeps live palette switch.

### Stage 5 — Verification
- Regenerate all Roborazzi golden screenshots; run full test suite (classes in isolation
  where the NATIVE-graphics flake bites); manual device pass.

## Error handling
- Dynamic color API failures already guarded by `runCatching` → brand fallback (kept).
- MDC scheme generation is pure/deterministic; unit-tested for all 4 styles × light/dark.

## Testing
- Unit: scheme-style mapping, AMOLED override, preference round-trips.
- Screenshot: per-screen goldens re-recorded per stage (kept per-stage to bisect regressions).
- Existing editor/converter suites must stay green at every stage.

## Out of scope
- Trailing-blank-line preservation in Aztec (separate editor-buffer work).
- macOS port docs, sync features.
