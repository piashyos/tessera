# Reusable Input + Popup — Design

**Goal:** Make text input look and behave uniformly across the app, and replace the system `ModalBottomSheet` (tag picker) with a custom, reusable popup that floats above the keyboard over a blurred, dimmed background.

**Context:** Tessera (Jetpack Compose, package `app.tessera`). The app already has a glass aesthetic built on the `dev.chrisbanes.haze` library: each screen creates a `HazeState`, applies `hazeSource(hazeState)` to its scrollable content, and `FloatingCapsule`/`EdgeFadeScrim` blur it via `hazeEffect`. The bottom search bar (`ui/notes/components/BottomSearchBar.kt`) currently inlines a `BasicTextField`; the tag picker (`ui/editor/components/TagChips.kt`) uses a `ModalBottomSheet` containing an `OutlinedTextField`.

---

## Component 1: `TesseraTextField`

A single reusable text input matching today's search-field look (borderless, placeholder, primary cursor). Replaces the inlined search `BasicTextField` and the tag picker's `OutlinedTextField` so all inputs are visually identical.

**Location:** `app/src/main/java/app/tessera/core/ui/components/TesseraTextField.kt`

**API:**
```kotlin
@Composable
fun TesseraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: () -> Unit = {},
    leadingIcon: (@Composable () -> Unit)? = null,
)
```

**Behavior / visuals:**
- `BasicTextField` with transparent background, `bodyMedium`/`onSurface` text, `SolidColor(primary)` cursor.
- `decorationBox` shows `placeholder` (`onSurfaceVariant`) when `value` is empty; optional `leadingIcon` rendered before the text.
- `KeyboardOptions(imeAction = imeAction)` + `KeyboardActions` invoking `onImeAction`.
- No border/box of its own — the container (search capsule / popup) provides the surface.

**Consumers:**
- `BottomSearchBar` — replace its inline `BasicTextField` with `TesseraTextField`, keeping the existing `SearchField` test tag and focus handling.
- Tag picker — replace `OutlinedTextField` with `TesseraTextField` (placeholder = add-tag hint, `imeAction = Done`, `onImeAction` adds the trimmed tag).

---

## Component 2: `TesseraPopup`

A reusable, in-composition overlay replacing `ModalBottomSheet`. Rendered at the screen root so it can blur everything behind it.

**Location:** `app/src/main/java/app/tessera/core/ui/components/TesseraPopup.kt`

**API:**
```kotlin
@Composable
fun TesseraPopup(
    visible: Boolean,
    onDismiss: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
)
```

**Layout / behavior:**
- When `visible`, fills the whole screen (over status/nav bars).
- **Backdrop:** full-screen box that (a) blurs the content behind it via `hazeEffect(hazeState)` and (b) draws a slight dim scrim on top (e.g. `Color.Black` ~0.25 alpha). Tapping the backdrop calls `onDismiss`. `BackHandler` also calls `onDismiss`.
- **Card:** bottom-anchored, full width minus **12dp** side margins, rounded corners, surface-colored, holding `content` (a `ColumnScope`). Uses `imePadding()` plus a **12dp** bottom gap so the gap to the keyboard equals the side margins; when the keyboard is closed it sits 12dp above the bottom safe-area inset. (Confirmed positioning: option A.)
- Enter/exit: fade + slide-up (spring), consistent with the app's motion.
- Card taps do not propagate to the backdrop (no dismiss).

**Blur wiring:** the popup blurs via the screen's `HazeState`. To cover the full background (content **and** floating capsules), the screen's root content is wrapped as a single `hazeSource` that the popup backdrop samples. The editor passes its `hazeState` into `TesseraPopup`.

**Consumer:** the tag picker in `TagChips.kt` swaps `ModalBottomSheet` for `TesseraPopup`, with content = a title, a `TesseraTextField` for a new tag, and a wrapping row of existing-tag chips (`availableTags`).

---

## Out of scope

- The editor title/body fields stay as-is (large content editors, different purpose).
- No new tag persistence/logic; only presentation changes.

## Testing

- `TesseraTextField`: instrumented Compose test — typing invokes `onValueChange`; placeholder shown when empty; `onImeAction` fires on Done.
- `TesseraPopup`: instrumented Compose test — hidden when `visible = false`; backdrop tap and back press invoke `onDismiss`; content is shown when visible.
- Existing `NotesListScreenTest` (search field tag) and editor tests must still pass.

## Verification

Build + unit/instrumented tests pass; manual emulator check: search input unchanged in feel; tag popup opens above keyboard with 12dp margins over a blurred, dimmed background, dismisses on back/scrim tap.
