# In-Editor Image Resize & Rotate — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tap an image in the note editor to show a selection outline with corner handles; drag to resize (aspect locked) and tap a button to rotate 90° — size/rotation persist and sync via the markup.

**Architecture:** Display params live in the image ref as a query (`tessera-img://<id>?w=<10..100>&rot=<0|90|180|270>`) so they ride the existing note-content sync — no Room migration, no manifest change. The `TesseraImageLoader` renders `w`/`rot`; a Compose overlay in `RichNoteBody` (editable notes only) draws handles and, on release, mutates the tapped `AztecImageSpan`'s `src`+drawable and reuses the existing `flushHtmlNow()` write path (works in both the fast-path and block-stack editors).

**Tech Stack:** Kotlin, Jetpack Compose (`AndroidView` over WordPress Aztec v2.1.7), Robolectric/JUnit, existing `MarkdownHtmlConverter` + `TesseraImageLoader`.

**Spec:** `docs/superpowers/specs/2026-08-28-image-resize-rotate-design.md` — read it first.

**Verification commands** (from repo root):
- Unit tests: `./gradlew :app:testDebugUnitTest --tests '<pattern>'`
- Compile: `./gradlew :app:compileDebugKotlin`
- Full suite has a known Robolectric NATIVE-graphics flake in screenshot/Compose classes — re-run a failing class in isolation before blaming a change. If `:app:kspDebugKotlin` hangs on macOS: `sudo killall syspolicyd`.

CODE-NAV: plain `grep`/`rg`/`find` over `*.kt`/`*.java` is BLOCKED (prefix `CBM_BYPASS=1`); use the Read tool + codebase-memory-mcp. grep over non-code (xml/gradle) is fine.

---

## Chunk 1: Step-0 spike (GATE — validate Aztec before UI)

This de-risks the two unknowns the whole persistence/render approach rests on. Do NOT build Chunk 3 until both pass. Chunk 2 is safe either way.

### Task 1: Aztec spike — smaller bounds honored + query survives round-trip

**Files:**
- Test: `app/src/test/java/app/tessera/ui/editor/components/AztecImageParamSpikeTest.kt` (throwaway-style Robolectric test that documents the findings; keep it — it doubles as a regression guard)

- [ ] **Step 1: Write the spike test** — a Robolectric Compose test (mirror `RichNoteBodyFormatFlushTest`: `@RunWith(RobolectricTestRunner)`, `@GraphicsMode(NATIVE)`, `@Config(sdk=[34], qualifiers="w411dp-h891dp-port-xxhdpi")`, `createAndroidComposeRule<ComponentActivity>`). In it:
  - (a) **Query survival:** build an `AztecText` via the same inflation `RichNoteBody` uses (or `Aztec.Factory.with` + a bare `AztecText`), `editor.fromHtml("<img src=\"tessera-img://abc-1?w=50&rot=90\">")`, then assert `editor.toHtml()` contains `tessera-img://abc-1?w=50&rot=90` verbatim.
  - (b) **Smaller bounds honored:** create an `AztecImageSpan` (or via `editor.insertImage(drawable, attrs)`) whose drawable bounds are set to a width MUCH smaller than the editor width (e.g. 200px in an 800px-wide editor), then read the span's `getSize(...)` (or measure the editor) and assert the rendered width tracks ~200px, NOT the full editor width.
- [ ] **Step 2: Run it** — `./gradlew :app:testDebugUnitTest --tests 'app.tessera.ui.editor.components.AztecImageParamSpikeTest'`
- [ ] **Step 3: Record the verdict** in the test's KDoc header:
  - **(a) passes** → params can live in the `src`; proceed. **(a) fails** → STOP: fall back to DB/manifest storage (rejected spec option B) — escalate to the human before continuing.
  - **(b) passes** → `w%` renders by setting drawable bounds; no custom span needed. **(b) fails** → Chunk 3 must add a `TesseraImageSpan` subclass overriding `getSize`/`adjustBounds` to honor a stored `w%`; note it here and add that task to Chunk 3 before the overlay work.
- [ ] **Step 4: Commit** — `git add app/src/test/java/app/tessera/ui/editor/components/AztecImageParamSpikeTest.kt && git commit -m "test(images): Aztec spike — query survival + smaller-bounds sizing"`

---

## Chunk 2: Markup + render (`w`/`rot`) — data/render layer

### Task 2: `parseImageRef` — split id / w / rot

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/components/TesseraImageLoader.kt` (add `data class ImageRef` + `parseImageRef`; keep `parseImageId` delegating to it)
- Test: `app/src/test/java/app/tessera/ui/editor/components/ImageRefTest.kt`

- [ ] **Step 1: Failing tests.** Cover: bare `tessera-img://abc-1` → id=`abc-1`, w=100, rot=0; `tessera-img://abc-1?w=50&rot=90` → w=50,rot=90; params in any order; unknown params ignored; out-of-range clamped (`w=5`→10, `w=250`→100, `rot=45`→snap to nearest of {0,90,180,270} OR reject→0 — pick REJECT→0 and test it); non-`tessera-img` source → null; malformed query tolerated (no crash).
- [ ] **Step 2: Run → FAIL.** `./gradlew :app:testDebugUnitTest --tests 'app.tessera.ui.editor.components.ImageRefTest'`
- [ ] **Step 3: Implement.**

```kotlin
/** Parsed tessera-img reference: bare id plus optional display params. */
data class ImageRef(val id: String, val wPercent: Int, val rotDegrees: Int)

private const val SCHEME = "tessera-img://"
private val VALID_ROT = setOf(0, 90, 180, 270)

/** Parse `tessera-img://<id>?w=..&rot=..`; null when [source] isn't a tessera-img ref. Defaults w=100, rot=0. */
fun parseImageRef(source: String): ImageRef? {
    if (!source.startsWith(SCHEME)) return null
    val rest = source.removePrefix(SCHEME)
    val id = rest.substringBefore('?')
    if (id.isBlank()) return null
    val query = rest.substringAfter('?', "")
    val params = query.split('&').mapNotNull {
        val k = it.substringBefore('=', ""); val v = it.substringAfter('=', "")
        if (k.isEmpty()) null else k to v
    }.toMap()
    val w = params["w"]?.toIntOrNull()?.coerceIn(10, 100) ?: 100
    val rot = params["rot"]?.toIntOrNull()?.takeIf { it in VALID_ROT } ?: 0
    return ImageRef(id, w, rot)
}
```
Update the existing `parseImageId(source): String?` to `= parseImageRef(source)?.id` (keep its call sites working).
- [ ] **Step 4: Run → PASS.** Same command.
- [ ] **Step 5: Commit** — `git add TesseraImageLoader.kt ImageRefTest.kt && git commit -m "feat(images): parse w/rot from tessera-img ref"`

### Task 3: Render `w` and `rot` in the loader

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/components/TesseraImageLoader.kt` (`loadImage` sync path, `decode`, `setDisplayBounds`)
- Test: `app/src/test/java/app/tessera/ui/editor/components/TesseraImageLoaderTest.kt` (append)

- [ ] **Step 1: Failing tests** (Robolectric, mirror the existing class): write a 120×200 webp to `filesDir/images/<id>.webp`; then
  - `loadImage("tessera-img://<id>?rot=90", cb, 800)` → delivered drawable bounds are LANDSCAPE (width>height) — rotation swapped the orientation.
  - `loadImage("tessera-img://<id>?w=50", cb, 800)` delivers a drawable whose width is half of the `w=100` (bare) case.
  - `loadImage("tessera-img://<id>?w=100", cb, 800)` matches the bare-ref bounds (no upscale for a sub-800 image → stays native 120 wide, not 800).
- [ ] **Step 2: Run → FAIL.**
- [ ] **Step 3: Implement.**
  - In `loadImage`, parse once: `val ref = parseImageRef(source) ?: run { callbacks.onImageFailed(); return }`. Use `ref.id` for `conventionLocalFile`/DAO; pass `ref` (or its `wPercent`/`rotDegrees`) into `decode`.
  - In `decode`: after decoding `bitmap`, if `ref.rotDegrees != 0` rotate via `Matrix().apply { postRotate(rot.toFloat()) }` + `Bitmap.createBitmap(...)`; compute `rotatedW/rotatedH` from the rotated bitmap.
  - Replace `setDisplayBounds(drawable, srcW, srcH, maxWidth)` with a `w`-aware version: `defaultWidth = min(rotatedW, maxWidth)`; `targetWidth = round(wPercent/100f * defaultWidth).coerceAtLeast(1)`; `targetHeight = round(targetWidth * rotatedH.toFloat() / rotatedW)`; `drawable.setBounds(0,0,targetWidth,targetHeight)`.
  - Thread the `ref` through the async download branch too (same `w`/`rot` applied to a downloaded file).
- [ ] **Step 4: Run → PASS**, and re-run `TesseraImageLoaderTest` fully (the existing sync-delivery test must stay green).
- [ ] **Step 5: Commit** — `git add TesseraImageLoader.kt TesseraImageLoaderTest.kt && git commit -m "feat(images): render w% size and 90° rotation from the ref"`

### Task 4: Converter round-trip for parameterized refs

**Files:**
- Modify: `app/src/main/java/app/tessera/core/text/MarkdownHtmlConverter.kt` (`IMAGE_LINE` regex + the `markdownToHtml` image branch; `consumeImage` needs no change — it copies `src` verbatim)
- Test: `app/src/test/java/app/tessera/core/text/MarkdownHtmlConverterTest.kt` (append)

- [ ] **Step 1: Failing tests.** `![](tessera-img://abc-1?w=50&rot=90)` round-trips (md→html→md unchanged; html contains `src="tessera-img://abc-1?w=50&rot=90"`); bare `![](tessera-img://abc-1)` still round-trips (defaults NOT added); a parameterized ref adjacent to a bullet list and to a blank line round-trips.
- [ ] **Step 2: Run → FAIL.**
- [ ] **Step 3: Implement.** Extend `IMAGE_LINE` to capture the whole `tessera-img://…` payload incl. an optional query, e.g.:
  `Regex("""^!\[]\((tessera-img://[A-Za-z0-9-]+(?:\?[^)]*)?)\)$""")` — group 1 is the FULL source. In `markdownToHtml`, emit `addBlock("<img src=\"${m.groupValues[1]}\" />")` using that full source (do NOT reconstruct from just the id). `htmlToMarkdown`/`consumeImage` already parse `src` only and emit `![](<src>)` — no change.
- [ ] **Step 4: Run the WHOLE converter class** — `./gradlew :app:testDebugUnitTest --tests 'app.tessera.core.text.MarkdownHtmlConverterTest'` — ALL pass (round-trip regressions are this file's known failure mode).
- [ ] **Step 5: Commit** — `git add MarkdownHtmlConverter.kt MarkdownHtmlConverterTest.kt && git commit -m "feat(text): round-trip tessera-img refs with w/rot query"`

### Task 5: `FindOrphanImages` still counts parameterized refs

**Files:**
- Test: `app/src/test/java/app/tessera/domain/usecase/FindOrphanImagesTest.kt` (append) — likely NO production change (it matches `content.contains("tessera-img://"+bareId)`), this locks it in.

- [ ] **Step 1: Failing/guard test.** A note whose content is `![](tessera-img://id-1?w=50&rot=90)` keeps image `id-1` referenced (NOT stamped orphaned). Run — it should already PASS (the `contains` substring matches); if it does, that's the intended outcome (regression guard). If it FAILS, fix `findOrphans` to compare against the bare id substring only.
- [ ] **Step 2: Commit** — `git add FindOrphanImagesTest.kt && git commit -m "test(sync): orphan scan tolerates w/rot query on refs"`

---

## Chunk 3: Tap-to-select + overlay + resize/rotate (UI)

> If Chunk 1 Task 1(b) FAILED, insert a task here first: create `TesseraImageSpan : AztecImageSpan` overriding `getSize`/`adjustBounds` to honor a `wPercent` field, and have the loader build it. Otherwise setting drawable bounds (Task 3) already sizes correctly and this chunk uses the stock span.

### Task 6: Pure geometry helpers

**Files:**
- Create: `app/src/main/java/app/tessera/ui/editor/components/ImageSelectionGeometry.kt`
- Test: `app/src/test/java/app/tessera/ui/editor/components/ImageSelectionGeometryTest.kt`

- [ ] **Step 1: Failing tests.**
  - `dragToWidthPercent(startPercent, dragDx, defaultWidthPx)`: dragging a corner OUTWARD by `+dx` grows `w`; INWARD shrinks; result clamped 10..100. E.g. start=100, defaultWidth=200, dragDx=-100 (shrink by half) → 50; dragDx that would exceed 100 → 100; below 10 → 10. (Aspect is locked, so map horizontal delta to a width fraction: `newW = ((startPercent/100 * defaultWidth + dragDx) / defaultWidth * 100)`.)
  - `withWidthPercent(source, w)` / `withRotation(source, rot)`: pure string ops producing a CANONICAL `tessera-img://id?w=..&rot=..` src, DROPPING params equal to defaults (`w=100`, `rot=0`) so a reset image serializes bare. E.g. `withWidthPercent("tessera-img://x?rot=90", 100)` → `tessera-img://x?rot=90`; `withRotation("tessera-img://x?w=50", 0)` → `tessera-img://x?w=50`; both default → `tessera-img://x`.
- [ ] **Step 2: Run → FAIL.**
- [ ] **Step 3: Implement** the two pure functions (parse via `parseImageRef`, recompute, re-emit canonical).
- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `git add ImageSelectionGeometry.kt ImageSelectionGeometryTest.kt && git commit -m "feat(images): pure resize/rotate geometry + canonical src writers"`

### Task 7: Tap detection → selected span (RichNoteBody), editable only

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt`

- [ ] **Step 1: Read** `RichNoteBody.kt` fully (the `AndroidView` factory, `editorRef`, `flushHtmlNow`, the `insertImage` action, the `editable` param). Confirm Aztec's `AztecText.setOnImageTappedListener` / `getOffsetForPosition` signatures against the resolved artifact (or use a touch listener that calls `editor.getOffsetForPosition(x,y)`).
- [ ] **Step 2: Add selection state.** Introduce `data class SelectedImage(val spanStart: Int, val source: String, val rect: androidx.compose.ui.geometry.Rect, val wPercent: Int, val rotDegrees: Int)` and a `remember { mutableStateOf<SelectedImage?>(null) }` in `RichNoteBody`. GATE everything below on `editable` — when `!editable`, never install the listener and keep selection null.
- [ ] **Step 3: Detect tap on an image.** On the `AztecText`, install a tap detector (e.g. a `GestureDetector` in an `setOnTouchListener`, careful to `return false` so normal editing still works) that on single-tap-up computes `offset = editor.getOffsetForPosition(x,y)`, reads `editor.editableText.getSpans(offset, offset, AztecImageSpan::class.java).firstOrNull()`; if present → build `SelectedImage` (spanStart via `editable.getSpanStart(span)`, `source` from the span's `attributes.getValue("src")`, `rect` via the Task 8 helper, `wPercent`/`rotDegrees` via `parseImageRef`). A tap NOT on an image clears selection.
- [ ] **Step 4: Compile check** — `./gradlew :app:compileDebugKotlin` (no behavior test yet; verified on-device in Task 10).
- [ ] **Step 5: Commit** — `git add RichNoteBody.kt && git commit -m "feat(editor): detect image taps to drive a selection (editable only)"`

### Task 8: Span → screen-rect geometry + scroll/re-render recompute

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt`
- Test: `app/src/test/java/app/tessera/ui/editor/components/ImageSpanRectTest.kt` (Robolectric — builds a real AztecText, inserts an image, asserts the computed rect is non-empty and matches the drawable size)

- [ ] **Step 1: Failing test.** In a Robolectric Compose test, render a `RichNoteBody` with an image, then call the extracted helper `imageSpanRect(editor, spanStart): Rect?` and assert it returns a rect whose width ≈ the drawable bounds width and top ≥ 0.
- [ ] **Step 2: Run → FAIL.**
- [ ] **Step 3: Implement** `imageSpanRect(editor: AztecText, spanStart: Int): Rect?` — `layout.getLineForOffset(spanStart)` → `line top/bottom + paddingTop`, `x = layout.getPrimaryHorizontal(spanStart) + paddingLeft`, width/height from the span drawable bounds; subtract `editor.scrollY` so it's in the visible AndroidView space. Return null if `layout == null`.
- [ ] **Step 4: Recompute triggers.** Recompute `SelectedImage.rect` when: the fast-path editor scrolls (`editor.setOnScrollChangeListener { … }`), the outer Compose scroll changes (block path — observe the scroll state passed down or a `LaunchedEffect` on the IME inset already present), and after `flushHtmlNow`/re-render. Keep it simple: recompute on scroll-change + on selection change; accept a 1-frame lag on fast scroll.
- [ ] **Step 5: Run → PASS**, `./gradlew :app:testDebugUnitTest --tests 'app.tessera.ui.editor.components.ImageSpanRectTest'` (rerun in isolation if the NATIVE-graphics flake hits).
- [ ] **Step 6: Commit** — `git add RichNoteBody.kt ImageSpanRectTest.kt && git commit -m "feat(editor): compute image-span screen rect for the overlay"`

### Task 9: Overlay UI — outline, handles, rotate button, resize/rotate commit

**Files:**
- Create: `app/src/main/java/app/tessera/ui/editor/components/ImageSelectionOverlay.kt` (Compose overlay: outline + 4 corner handles + rotate button)
- Modify: `app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt` (wrap the `AndroidView` in a `Box`; draw the overlay when `SelectedImage != null`)
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ru/strings.xml` (content descriptions: rotate handle, resize handle)

- [ ] **Step 1: Overlay composable.** `ImageSelectionOverlay(rect, onResizeDelta: (dragDx) -> Unit, onResizeCommit: () -> Unit, onRotate: () -> Unit)` — a `Box` positioned/sized to `rect` (offset via `Modifier.offset { }`), a 2dp `primary` outline, 4 corner handles (small circles) each with `pointerInput`/`detectDragGestures` reporting horizontal drag, and a rotate icon button above the top edge (Material `Icons.Rounded.Rotate90DegreesCw` or similar). Match Tessera styling (primary color, rounded).
- [ ] **Step 2: Wire into RichNoteBody.** Wrap the existing `AndroidView` in a `Box`; when `SelectedImage != null` render `ImageSelectionOverlay(sel.rect, …)`:
  - **onResizeDelta(dx):** live preview — `newW = dragToWidthPercent(sel.wPercent, dx, defaultWidthPx)`; find the span (`editable.getSpans` at `sel.spanStart`), set its drawable bounds to the new size, `editor.invalidate()`, and update `sel.rect` so handles track. Do NOT flush per frame.
  - **onResizeCommit():** write the canonical src (`withWidthPercent(sel.source, newW)`) into the span's `AztecAttributes` (`attrs.setValue("src", newSrc)`) and re-decode/re-bound its drawable via the loader (`loader.resolve` is `suspend` — reuse the caret scope), then `flushHtmlNow()`. Update `sel` (source/wPercent).
  - **onRotate():** `newRot = (sel.rotDegrees + 90) % 360`; `newSrc = withRotation(sel.source, newRot)`; set span src attr + re-decode drawable (rotation applied by the loader) + `flushHtmlNow()`; update `sel`.
  (Reuse the exact span-mutation + `flushHtmlNow` pattern from the existing `insertImage` action so both editor layouts persist correctly.)
- [ ] **Step 3: Strings** — add EN + RU content descriptions.
- [ ] **Step 4: Compile + existing editor tests** — `./gradlew :app:compileDebugKotlin` and `./gradlew :app:testDebugUnitTest --tests 'app.tessera.ui.editor.*' --tests 'app.tessera.core.text.*'` (rerun NATIVE-graphics flakes in isolation).
- [ ] **Step 5: Commit** — `git add -A app/src/main/java/app/tessera/ui/editor/components app/src/main/res && git commit -m "feat(editor): image selection overlay — drag-resize + rotate 90°"`

### Task 10: On-device verification + polish

- [ ] **Step 1: Build + install** — `./gradlew :app:assembleDebug` then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 2: Manual checklist** (drive via the app; screenshot each): tap an image → outline + handles + rotate button appear; drag a corner → resizes with locked aspect, handles track; release → size persists across leaving+reopening the note; rotate button → image turns 90° each tap and persists; tap outside → overlay clears; a Viewer/read-only shared note shows NO handles; a note with the SAME image inserted twice → tapping each selects the correct one. Confirm the stored content shows `?w=..&rot=..` only when non-default (`adb shell run-as app.tessera sqlite3 … "SELECT content FROM notes WHERE …"`).
- [ ] **Step 3: Fix any issues found**, re-verify.
- [ ] **Step 4: Full unit suite** — `./gradlew :app:testDebugUnitTest` — green modulo the isolated-and-confirmed NATIVE-graphics flake.
- [ ] **Step 5: Final commit + use superpowers:finishing-a-development-branch.**
