# In-Editor Image Resize & Rotate — Design

**Goal:** Tapping an image in the note editor shows a selection outline with corner handles; dragging a handle resizes the image (aspect ratio locked); a rotate button turns it 90° per tap. Size and rotation persist and sync across devices and shared-note members.

**Status:** Approved for planning (2026-08-28).

## Decisions

- **Interaction:** tap image → selection outline + 4 corner handles + a rotate button (variant B — draggable handles, not a toolbar/sheet). Tap outside clears the selection.
- **Size semantics:** width as a **percent of the editor content width** (`w`), 10–100, height derived from the (rotated) aspect ratio. Freshly inserted images are 100%. Percent (not pixels) so it renders identically across screen sizes and syncs cleanly.
- **Rotation:** `rot ∈ {0, 90, 180, 270}`; each rotate-button tap adds 90°. Applied at **render time** (rotate the decoded bitmap) — the stored Drive file is never re-encoded.
- **Persistence:** encode both params in the markup ref. They ride the note content through the existing autosave/sync path — **no Room migration, no manifest change.**

## Markup format

An image reference becomes:

```
tessera-img://<id>?w=<10..100>&rot=<0|90|180|270>
```

- Defaults (`w=100`, `rot=0`) are **omitted** — an untouched image stays `![](tessera-img://<id>)`.
- `<id>` is everything between `tessera-img://` and `?`. The manifest/DB key stays the bare id.
- Markdown line: `![](tessera-img://<id>?w=50&rot=90)`.

**Parse-site audit (verified against the codebase).** The ONLY forward parse of a `tessera-img://…` string taken from note *content* is `TesseraImageLoader.parseImageId` — it must take `substringBefore('?')` (and a new `parseImageRef` splits out `w`/`rot`). The other sites do **not** need changes:
- `DriveImageDownloader.download(imageId)` and `ImageUploadWorker` operate on **bare DB ids** (`row.imageId`), never on content strings.
- `FindOrphanImages.findOrphans` matches `content.contains("tessera-img://" + bareId)`; a query-bearing ref still *contains* the bare marker, so it already matches — add a regression test to lock this in.
- `MarkdownPreview.IMAGE_REF_LINE` (`tessera-img://[^)]*\)`) is already query-tolerant.
- **Canonicalization:** the write side keeps the span's `src` canonical — it drops `w`/`rot` when they equal the defaults (never writes `?w=100&rot=0`). So `MarkdownHtmlConverter.consumeImage` can keep copying `src` verbatim; no default-stripping needed in the converter.

## Components

### 1. `MarkdownHtmlConverter` (markup round-trip)
- Extend `IMAGE_LINE` to allow an optional `?...` query after the id.
- `markdownToHtml`: emit `<img src="tessera-img://<id>?w=..&rot=..">` verbatim (full source incl. query).
- `htmlToMarkdown`: already parses only `src` → emit `![](tessera-img://<id>?w=..&rot=..)`. Emit params only when non-default (canonical clean form otherwise) so round-trip is idempotent.
- Aztec preserves the `src` attribute verbatim across `fromHtml`/`toHtml` (already verified for the bare form) — the query survives.

### 2. `TesseraImageLoader` (render)
- New pure helper `parseImageRef(source): { id, wPercent, rotDegrees }` (`w` default 100, `rot` default 0; clamps/validates). Row/file lookups use `id`.
- `decode`: after decoding the file, if `rot != 0` rotate the bitmap via `Matrix` (90/180/270). Aspect ratio for display comes from the **rotated** dimensions.
- Display width = `round(wPercent/100 * maxWidth)` where `maxWidth` is the width Aztec passes to `loadImage`; height keeps the rotated aspect ratio. `setBounds` accordingly. The synchronous-local delivery (existing) still applies.

### 3. Tap-to-select + overlay geometry (`RichNoteBody`)
- **Editable only.** The overlay is gated on the existing `editable` flag (`RichNoteBody(editable = !state.isReadOnly)`). For a Viewer (`editable == false`) the tap listener/overlay are NOT installed — a viewer can't persist a change, so it must get no handles.
- `editor.setOnImageTappedListener { attrs -> ... }` — resolve the tapped `AztecImageSpan` (its `src` + span start offset).
- Pure helper `imageSpanScreenRect(editor, spanStart)`: span start → `Layout` line → x/top + the drawable bounds → a `Rect` in the AndroidView's coordinate space (padding/scroll corrected).
- Lift `SelectedImage(spanStart, source, rect, wPercent, rotDegrees)` into per-instance state (each `RichNoteBody` owns its own selection). Recompute the rect on scroll, IME inset change, and re-render. Tap elsewhere clears it.
- Draw the overlay in the Compose `Box` wrapping THIS instance's `AndroidView`: rounded outline, 4 corner handles, one rotate button above the image, positioned from `rect`. Works identically in the fast-path (single editor) and the block-stack path (one `RichNoteBody` per text block) because the overlay is scoped to the instance whose span was tapped.

### 4. Drag-resize + rotate — write through the span, flush through `toHtml`
- **Mechanism (committed):** a resize/rotate mutates the tapped `AztecImageSpan` **in place** — update its `AztecAttributes` `src` to the new canonical `tessera-img://id?w=..&rot=..` (defaults dropped) AND swap its drawable (re-decoded for a new `rot`, re-bounded for a new `w`) — then call the existing `flushHtmlNow()`. That runs `editor.toHtml()` → the span serializes with the new `src` → `htmlToMarkdown` → `onMarkdownChange(md)`. This is the SAME path `insertImage` (`RichNoteBody.kt:779-802`) already uses, so it works for BOTH editor layouts (the fast path writes `state.content`; a block editor writes its block slice back via `joinBlocksIntoMarkdown`). Because `flushHtmlNow` sets `lastLoadedMarkdownRef` before emitting, the ensuing recomposition does NOT trigger a `fromHtml` reload — the mutated span keeps its identity and the selection/scroll are undisturbed.
- Corner drag: pure `dragToWidthPercent(startPercent, dragDx, editorWidthPx)` → new `w` clamped 10–100 (aspect locked; horizontal delta drives it). Live preview mutates ONLY the drawable bounds + `invalidate()` per frame (no flush); the new `w` is committed to the span + flushed once on release.
- Rotate button: `rot = (rot + 90) % 360` → mutate span src + drawable, flush once.

### 5. Persistence / sync
- Every committed size/rotate change rides the existing flush → autosave → Firestore sync (fast path via `state.content`, block path via the block's `onMarkdownChange`). Recipients and other devices read the params from the synced content and render identically. No new storage.
- **Undo/redo (accepted limitation for v1):** because the change is a manual span mutation (not an Aztec editing op), Aztec's in-editor undo/redo stack does not revert a resize/rotate. The change IS persisted/undoable at the content level (re-tap and resize/rotate back), just not via the ↶/↷ buttons. Documented, not fixed in v1.

## Testing
- **Converter** (`MarkdownHtmlConverterTest`): `![](tessera-img://id?w=50&rot=90)` round-trips; defaults are omitted on output; bare ref still round-trips; image-with-params adjacent to list/blank lines round-trips.
- **`parseImageRef`** (pure): parses id/w/rot, applies defaults, clamps out-of-range, ignores unknown params, tolerates missing query.
- **Loader**: `rot=90` swaps bounds orientation; `w=50` halves display width vs `w=100`; row lookup uses the bare id (query ignored). On-device instrumented check that a rotated local image decodes.
- **`FindOrphanImages`**: a reference `![](tessera-img://id?w=50&rot=90)` still counts the image as referenced (guards the `contains(bareId)` assumption).
- **Geometry/interaction** (pure): `dragToWidthPercent` mapping + clamps; `imageSpanScreenRect` for a known layout.
- **Overlay visual**: verified on device (Roborazzi optional given known native-graphics flakiness).

## Risks / open items
- **Aztec sizing override + query survival (validate first).** Aztec's `AztecDynamicImageSpan.adjustBounds` scales the image to the editor width by aspect ratio in its measured branch. Our `w%` sets a smaller drawable bounds; **step 0 of the plan is a spike** that confirms BOTH (a) Aztec renders at our smaller bounds rather than recomputing to full width, AND (b) a `?w=..&rot=..` query on the `src` survives Aztec's `fromHtml`/`toHtml` round-trip verbatim (the linchpin of the whole persistence path). If (a) fails, introduce a `TesseraImageSpan` subclass whose `getSize`/`adjustBounds` honors the stored `w%`. If (b) fails, fall back to storing params in the DB/manifest (the rejected option B) — decide before building the UI.
- **Overlay alignment** during scroll / keyboard animation / re-render is the fiddly part; the rect recomputes on those events. Acceptable to redraw slightly behind a fast scroll.
- **Live-drag vs. Aztec span bounds:** the drag preview mutates the current drawable's bounds directly; the authoritative size is only committed to content on release (keeps the flush path from firing per drag frame).
