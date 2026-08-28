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
- `<id>` is everything between `tessera-img://` and `?`. Every existing `imageId` parse site must take `substringBefore('?')` (loader, downloader, upload worker source, cleanup reference scan). The manifest/DB key stays the bare id.
- Markdown line: `![](tessera-img://<id>?w=50&rot=90)`.

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
- `editor.setOnImageTappedListener { attrs -> ... }` — resolve the tapped `AztecImageSpan` (its `src` + span start offset).
- Pure helper `imageSpanScreenRect(editor, spanStart)`: span start → `Layout` line → x/top + the drawable bounds → a `Rect` in the AndroidView's coordinate space (padding/scroll corrected).
- Lift `SelectedImage(id, source, rect, wPercent, rotDegrees)` into Compose state. Recompute the rect on scroll, IME inset change, and content re-render. Tap elsewhere in the editor clears it.
- Draw the overlay in the Compose `Box` wrapping the `AndroidView`: rounded outline, 4 corner handles, one rotate button above the image, positioned from `rect`.

### 4. Drag-resize + rotate (interaction → content edit)
- Corner drag: pure `dragToWidthPercent(startPercent, dragDx, editorWidthPx)` → new `w` clamped 10–100 (aspect locked, so horizontal delta drives it). Live preview: update the span drawable bounds + `invalidate()` so the image resizes under the handles during the drag.
- On release: write the new `w` into the image line's query in `state.content` (replace/insert `?w=`), routed through the normal `onMarkdownChange` flush.
- Rotate button: `rot = (rot + 90) % 360` → update the ref's `rot` param in content → re-render (loader rotates). Overlay rect recomputes after the re-render.

### 5. Persistence / sync
- Every size/rotate change is a content edit of the `tessera-img://id?...` line → existing autosave + Firestore sync. Recipients and other devices read the params from the synced content and render identically. No new storage.

## Testing
- **Converter** (`MarkdownHtmlConverterTest`): `![](tessera-img://id?w=50&rot=90)` round-trips; defaults are omitted on output; bare ref still round-trips; image-with-params adjacent to list/blank lines round-trips.
- **`parseImageRef`** (pure): parses id/w/rot, applies defaults, clamps out-of-range, ignores unknown params, tolerates missing query.
- **Loader**: `rot=90` swaps bounds orientation; `w=50` halves display width vs `w=100`; row lookup uses the bare id (query ignored). On-device instrumented check that a rotated local image decodes.
- **Geometry/interaction** (pure): `dragToWidthPercent` mapping + clamps; `imageSpanScreenRect` for a known layout.
- **Overlay visual**: verified on device (Roborazzi optional given known native-graphics flakiness).

## Risks / open items
- **Aztec sizing override (validate first).** Aztec's `AztecDynamicImageSpan.adjustBounds` scales the image to the editor width by aspect ratio in its measured branch. Our `w%` sets a smaller drawable bounds; **step 0 of the plan is a spike** confirming Aztec renders at our bounds rather than recomputing to full width. If it overrides, introduce a `TesseraImageSpan` subclass whose `getSize`/`adjustBounds` honors the stored `w%`.
- **Overlay alignment** during scroll / keyboard animation / re-render is the fiddly part; the rect recomputes on those events. Acceptable to redraw slightly behind a fast scroll.
- **Live-drag vs. Aztec span bounds:** the drag preview mutates the current drawable's bounds directly; the authoritative size is only committed to content on release (keeps the flush path from firing per drag frame).
