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

> Reviewer note (verified against the Aztec v2.1.7 artifact): `AztecDynamicImageSpan.adjustBounds`, once the editor is measured, RECOMPUTES the drawable bounds from the drawable's **intrinsic** size (clamped to paragraph width) — it IGNORES `setBounds`. So (1) `w%` must be applied by **scaling the decoded bitmap** so its intrinsic width equals the target (Task 3), not via `setBounds`; and (2) the spike must read sizing from a **measured** editor using a real `BitmapDrawable`, and must check the query survives **Aztec's** `fromHtml`→`toHtml` byte-for-byte (a raw `&` in an HTML attribute may re-serialize as `&amp;`).

- [ ] **Step 1: Write the spike test** — a Robolectric Compose test (mirror `RichNoteBodyFormatFlushTest`: `@RunWith(RobolectricTestRunner)`, `@GraphicsMode(NATIVE)`, `@Config(sdk=[34], qualifiers="w411dp-h891dp-port-xxhdpi")`, `createAndroidComposeRule<ComponentActivity>`). Render a real `RichNoteBody` (so the `AztecText` is inflated AND measured), grab `editor = activity.findViewById<AztecText>(R.id.aztec)`, then:
  - **(a) Bitmap-scaling honored:** `editor.fromHtml` with a `tessera-img://…` image, then via the loader deliver a `BitmapDrawable` whose **bitmap is scaled to 200px wide** (intrinsic width = 200) into an ~800px-wide editor; `rule.waitForIdle()`; assert the span's on-screen width via `span.getSize(paint, editor.text, spanStart, spanEnd, null)` on the laid-out editor (avoid a forward dependency on Task 8) is ~200px, NOT ~800px. This confirms scaling the bitmap (not `setBounds`) drives the size.
  - **(b) Query survival, byte-for-byte through Aztec:** `editor.fromHtml("<img src=\"tessera-img://abc-1?w=50&rot=90\" />")`, `rule.runOnUiThread { editor.toHtml() }`, assert the result contains the id AND capture EXACTLY how the query comes back — `?w=50&rot=90` vs `?w=50&amp;rot=90` vs params dropped. Record the literal string.
- [ ] **Step 2: Run it** — `./gradlew :app:testDebugUnitTest --tests 'app.tessera.ui.editor.components.AztecImageParamSpikeTest'`
- [ ] **Step 3: Record the verdict** in the test's KDoc header and use it to finalize Chunks 2–3:
  - **(a) ~200px** → apply `w%` by scaling the bitmap (Task 3 as written); no custom span. **(a) ~800px** → Chunk 3 must add a `TesseraImageSpan : AztecImageSpan` overriding `getSize`/`adjustBounds` to honor a `wPercent` field; add that task before the overlay and have the loader build it.
  - **(b) `&` preserved** → converter emits/parses `&` directly. **(b) `&amp;`** → Task 4 must emit `&amp;` in `markdownToHtml` and decode `&amp;`→`&` in `consumeImage`; `parseImageRef` must also decode `&amp;`→`&` before splitting (Aztec hands the loader the `&amp;` form). **(b) params dropped** → STOP: fall back to DB/manifest storage (rejected spec option B) — escalate to the human.
- [ ] **Step 4: Commit** — `git add app/src/test/java/app/tessera/ui/editor/components/AztecImageParamSpikeTest.kt && git commit -m "test(images): Aztec spike — query survival + smaller-bounds sizing"`

---

## Chunk 2: Markup + render (`w`/`rot`) — data/render layer

> Task 3's sizing (scale the bitmap) and Task 4's ampersand handling are finalized by the Task 1 spike verdict — apply its (a)/(b) outcomes here.

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
    // Aztec's toHtml may hand back the query with `&amp;` (HTML-escaped); decode so splitting works
    // whether the source came from stored markdown (raw `&`) or from editor.toHtml (`&amp;`).
    val query = rest.substringAfter('?', "").replace("&amp;", "&")
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

- [ ] **Step 1: Failing tests** (Robolectric, mirror the existing class): write a 120×200 webp to `filesDir/images/<id>.webp`; then assert on the delivered `BitmapDrawable`'s **intrinsic** size (`drawable.intrinsicWidth/intrinsicHeight`) — NOT `bounds` — because the on-screen size is driven by the intrinsic (scaled bitmap) size, per the spike:
  - `loadImage("tessera-img://<id>?rot=90", cb, 800)` → delivered drawable `intrinsicWidth > intrinsicHeight` (LANDSCAPE — rotation swapped orientation).
  - `loadImage("tessera-img://<id>?w=50", cb, 800)` → `intrinsicWidth` is half of the `w=100` (bare) case.
  - `loadImage("tessera-img://<id>?w=100", cb, 800)` → `intrinsicWidth == 120` (native; a sub-800 image is NOT upscaled).
- [ ] **Step 2: Run → FAIL.**
- [ ] **Step 3: Implement.**
  - In `loadImage`, parse once: `val ref = parseImageRef(source) ?: run { callbacks.onImageFailed(); return }`. Use `ref.id` for `conventionLocalFile`/DAO; pass `ref` (or its `wPercent`/`rotDegrees`) into `decode`.
  - In `decode`: after decoding `bitmap`, if `ref.rotDegrees != 0` rotate via `Matrix().apply { postRotate(rot.toFloat()) }` + `Bitmap.createBitmap(...)`; compute `rotatedW/rotatedH` from the rotated bitmap.
  - **Size by scaling the bitmap** (NOT `setBounds` — Aztec's `adjustBounds` recomputes from the drawable's intrinsic size after measurement, ignoring bounds): `defaultWidth = min(rotatedW, maxWidth)`; `targetWidth = round(wPercent/100f * defaultWidth).coerceAtLeast(1)`; `targetHeight = round(targetWidth * rotatedH.toFloat() / rotatedW).coerceAtLeast(1)`; if `targetWidth != rotatedW` → `bitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)`. Wrap as `BitmapDrawable(null, bitmap)` so `intrinsicWidth == targetWidth`. (Keep the existing `setBounds` call too — harmless and correct for the pre-measurement window; the intrinsic size is what sticks post-measure.)
  - Thread the `ref` through the async download branch too (same `w`/`rot` applied to a downloaded file).
  - **Also update `resolve(source, maxWidth)`** (the insert/re-decode entry point used by the `insertImage` action and Task 9): parse `parseImageRef(source)` and apply the same `w`/`rot` scaling+rotation in its `decode` call, so a resized/rotated re-decode produces the correctly-sized drawable. (Its param is currently named `imageId` but receives a full source string — rename to `source` for clarity.)
- [ ] **Step 4: Run → PASS**, and re-run `TesseraImageLoaderTest` fully (the existing sync-delivery test must stay green). NOTE: this loader-level test proves the intrinsic size is correct; the ON-SCREEN size (Aztec honoring it) is what the Task 1 spike and Task 10 on-device pass confirm.
- [ ] **Step 5: Commit** — `git add TesseraImageLoader.kt TesseraImageLoaderTest.kt && git commit -m "feat(images): render w% size and 90° rotation from the ref"`

### Task 4: Converter round-trip for parameterized refs

**Files:**
- Modify: `app/src/main/java/app/tessera/core/text/MarkdownHtmlConverter.kt` (`IMAGE_LINE` regex + the `markdownToHtml` image branch; `consumeImage` needs no change — it copies `src` verbatim)
- Test: `app/src/test/java/app/tessera/core/text/MarkdownHtmlConverterTest.kt` (append)

- [ ] **Step 1: Failing tests.** The STORED markdown always uses a raw `&` (`![](tessera-img://abc-1?w=50&rot=90)`). Assert: this line round-trips through the converter (md→html→md unchanged); bare `![](tessera-img://abc-1)` still round-trips (defaults NOT added); a parameterized ref adjacent to a bullet list and to a blank line round-trips. PLUS an **ampersand-normalization** test derived from the spike (b): if the spike showed Aztec emits `&amp;`, add a test that `htmlToMarkdown("<img src=\"tessera-img://abc-1?w=50&amp;rot=90\" />") == "![](tessera-img://abc-1?w=50&rot=90)"` (the stored form uses raw `&`).
- [ ] **Step 2: Run → FAIL.**
- [ ] **Step 3: Implement.** Extend `IMAGE_LINE` to capture the whole `tessera-img://…` payload incl. an optional query, e.g.:
  `Regex("""^!\[]\((tessera-img://[A-Za-z0-9-]+(?:\?[^)]*)?)\)$""")` — group 1 is the FULL source. In `markdownToHtml`, emit `addBlock("<img src=\"$srcForHtml\" />")` where `srcForHtml` is the captured source with `&`→`&amp;` (valid HTML attribute; per spike (b) — skip if the spike showed `&` survives raw). In `consumeImage`/`htmlToMarkdown`, when reading `src`, decode `&amp;`→`&` so the STORED markdown keeps the canonical raw-`&` form. (`IMG_SRC_ATTR` already extracts the `src` value; just `.replace("&amp;", "&")` on it.)
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
  - `dragToWidthPercent(startPercent, signedDx, defaultWidthPx)`: a POSITIVE `signedDx` grows `w`, negative shrinks; result clamped 10..100. E.g. start=100, defaultWidth=200, signedDx=-100 → 50; would-exceed-100 → 100; below 10 → 10. Formula: `newW = ((startPercent/100 * defaultWidth + signedDx) / defaultWidth * 100)`. The caller (Task 9) passes an OUTWARD-positive delta — for a left/top handle the raw drag dx is negated so both corners grow the image when dragged outward.
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
- [ ] **Step 3: Detect tap on an image.** On the `AztecText`, install a tap detector (e.g. a `GestureDetector` in a `setOnTouchListener` that `return false`s so normal editing/caret still work) that on single-tap-up computes `offset = editor.getOffsetForPosition(x,y)`, reads `editor.editableText.getSpans(offset, offset, AztecImageSpan::class.java).firstOrNull()`. **Validate the tap actually landed on the image**, not merely near it: `getOffsetForPosition` snaps to the NEAREST offset even for a tap in whitespace beside/below the image, so compute the span's rect (Task 8 helper) and confirm the tap `(x,y)` is inside it before selecting — otherwise clear the selection. When valid → build `SelectedImage` (spanStart via `editable.getSpanStart(span)`, `source` from `span.attributes.getValue("src")` decoded `&amp;`→`&`, `rect`, `wPercent`/`rotDegrees` via `parseImageRef`).
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
  Each handle's `pointerInput` passes an OUTWARD-positive `signedDx` (negate raw dx for left/top handles — see Task 6).
  - **onResizeDelta(signedDx):** live preview — `newW = dragToWidthPercent(sel.wPercent, signedDx, defaultWidthPx)`; find the span (`editable.getSpans` at `sel.spanStart`), swap its drawable for one scaled to `newW` (re-run the loader's decode — see below), `editor.invalidate()`, and update `sel.rect` so handles track. Do NOT flush per frame.
  - **onResizeCommit():** write the canonical src `withWidthPercent(sel.source, newW)` into the span's attributes (`span.attributes.setValue("src", newSrc)`), then `flushHtmlNow()`. Update `sel` (source/wPercent).
  - **onRotate():** `newRot = (sel.rotDegrees + 90) % 360`; `newSrc = withRotation(sel.source, newRot)`; set the span's src attribute + swap its drawable (re-decoded with the new rotation) + `flushHtmlNow()`; update `sel`.
  - **Re-decode/re-bound API:** use the SAME entry point the existing `insertImage` action already uses to turn a source into a drawable — `(imageGetter as? TesseraImageLoader)?.resolve(newSrc, width)` (a `suspend` fun; launch on the existing `caretScope` like `insertImage` does at RichNoteBody.kt:789). `resolve` already applies the `w`/`rot` from the source (Task 3). Confirm the exact call by reading the `insertImage` action first; do NOT invent a new decode path.
  This reuses the exact span-mutation + `flushHtmlNow` pattern from `insertImage`, so both the fast-path (`state.content`) and block-stack (per-block `onMarkdownChange`) layouts persist correctly.
- [ ] **Step 3: Strings** — add EN + RU content descriptions.
- [ ] **Step 4: Compile + existing editor tests** — `./gradlew :app:compileDebugKotlin` and `./gradlew :app:testDebugUnitTest --tests 'app.tessera.ui.editor.*' --tests 'app.tessera.core.text.*'` (rerun NATIVE-graphics flakes in isolation).
- [ ] **Step 5: Commit** — `git add -A app/src/main/java/app/tessera/ui/editor/components app/src/main/res && git commit -m "feat(editor): image selection overlay — drag-resize + rotate 90°"`

### Task 10: On-device verification + polish

- [ ] **Step 1: Build + install** — `./gradlew :app:assembleDebug` then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 2: Manual checklist** (drive via the app; screenshot each): tap an image → outline + handles + rotate button appear; drag a corner → resizes with locked aspect, handles track; release → size persists across leaving+reopening the note; rotate button → image turns 90° each tap and persists; tap outside → overlay clears; a Viewer/read-only shared note shows NO handles; a note with the SAME image inserted twice → tapping each selects the correct one. Confirm the stored content shows `?w=..&rot=..` only when non-default (`adb shell run-as app.tessera sqlite3 … "SELECT content FROM notes WHERE …"`).
- [ ] **Step 3: Fix any issues found**, re-verify.
- [ ] **Step 4: Full unit suite** — `./gradlew :app:testDebugUnitTest` — green modulo the isolated-and-confirmed NATIVE-graphics flake.
- [ ] **Step 5: Final commit + use superpowers:finishing-a-development-branch.**
