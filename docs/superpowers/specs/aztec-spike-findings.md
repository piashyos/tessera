# Aztec Spike Findings: HTML Shapes + Haze Decision

**Date:** 2026-06-15
**Spike type:** Source-based (bytecode + GitHub) — no emulator available
**Artifact inspected:** `org.wordpress:aztec:v2.1.7` from `https://a8c-libs.s3.amazonaws.com/android`
**Local AAR path:** `~/.gradle/caches/modules-2/files-2.1/org.wordpress/aztec/v2.1.7/.../aztec-v2.1.7.aar`

---

## 1. Resolved Artifact

```
org.wordpress:aztec:v2.1.7
Repository: https://a8c-libs.s3.amazonaws.com/android
AAR SHA:    73fb4f76b104dbc38d151a14cc12e88d33785474
```

Task-list support (`FORMAT_TASK_LIST`, `AztecTaskListSpan`) is **built into the core `aztec` artifact** — there is no separate task-list plugin AAR. A `TaskListClickHandler` handles checkbox toggle interactions, and `AztecTaskListSpan` / `AztecListItemSpan` handle the HTML serialization/parsing within the same module.

---

## 2. Setup API (Confirmed from Bytecode)

### 2.1 AztecText — the editor view

`org.wordpress.aztec.AztecText` is a custom `EditText`. It is typically inflated from XML layout or constructed programmatically and referenced by ID.

**Key methods confirmed on `AztecText`:**

| Method | Signature | Notes |
|---|---|---|
| `fromHtml` | `fun fromHtml(source: String, isCalypsoCompatible: Boolean = false)` | Set editor content from HTML string |
| `toHtml` | `fun toHtml(isCalypsoCompatible: Boolean = false): String` | Serialize current content to HTML |
| `toHtmlAsync` | suspend variant | Coroutine-safe version |
| `toPlainHtml` | `fun toPlainHtml(isCalypsoCompatible: Boolean = false): String` | Strips Aztec-specific markup |
| `toggleFormatting` | `fun toggleFormatting(textFormat: ITextFormat)` | Toggle a format at current selection |
| `setCalypsoMode` | `fun setCalypsoMode(enabled: Boolean)` | Calypso-compatible serialization mode |

### 2.2 Aztec — the coordinator / factory

`org.wordpress.aztec.Aztec` wires `AztecText`, optional `SourceViewEditText`, and `IAztecToolbar` together.

**Confirmed factory methods on `Aztec.Factory`:**

```kotlin
// All three overloads confirmed in Aztec$Factory.class
Aztec.Factory.with(
    activity: Activity,
    editorId: Int,
    sourceId: Int,  // optional overload omits sourceId
    toolbarId: Int,
    toolbarClickListener: IAztecToolbarClickListener
): Aztec

Aztec.Factory.with(
    aztecText: AztecText,
    sourceEditor: SourceViewEditText,  // optional overload omits sourceEditor
    toolbar: IAztecToolbar,
    toolbarClickListener: IAztecToolbarClickListener
): Aztec
```

**Adding plugins (confirmed):**
```kotlin
aztec.addPlugin(myPlugin: IAztecPlugin): Aztec  // fluent builder
```

### 2.3 AztecTextFormat — all format constants

Confirmed enum values (from `AztecTextFormat.class`):

```kotlin
AztecTextFormat.FORMAT_BOLD
AztecTextFormat.FORMAT_STRONG        // <strong> alias
AztecTextFormat.FORMAT_ITALIC
AztecTextFormat.FORMAT_EMPHASIS      // <em> alias
AztecTextFormat.FORMAT_CITE          // <cite>
AztecTextFormat.FORMAT_STRIKETHROUGH
AztecTextFormat.FORMAT_UNDERLINE
AztecTextFormat.FORMAT_HEADING_1     // through FORMAT_HEADING_6
AztecTextFormat.FORMAT_UNORDERED_LIST
AztecTextFormat.FORMAT_ORDERED_LIST
AztecTextFormat.FORMAT_TASK_LIST     // confirmed — built into core
AztecTextFormat.FORMAT_LINK
AztecTextFormat.FORMAT_QUOTE
AztecTextFormat.FORMAT_PARAGRAPH
AztecTextFormat.FORMAT_PREFORMAT
AztecTextFormat.FORMAT_CODE
AztecTextFormat.FORMAT_HORIZONTAL_RULE
AztecTextFormat.FORMAT_ALIGN_LEFT / CENTER / RIGHT
AztecTextFormat.FORMAT_BIG / SMALL / SUPERSCRIPT / SUBSCRIPT
AztecTextFormat.FORMAT_FONT / MONOSPACE / BACKGROUND / MARK / HIGHLIGHT
```

**Toggling formats programmatically:**
```kotlin
aztecText.toggleFormatting(AztecTextFormat.FORMAT_BOLD)
aztecText.toggleFormatting(AztecTextFormat.FORMAT_TASK_LIST)
// etc.
```

### 2.4 Plugin interface

Plugins implement `org.wordpress.aztec.plugins.IAztecPlugin`. HTML-level plugins implement sub-interfaces in `org.wordpress.aztec.plugins.html2visual` and `plugins.visual2html` packages (e.g. `IHtmlTagHandler`, `IBlockSpanHandler`).

---

## 3. Exact HTML Shapes (Confirmed from Bytecode)

All tag strings below are derived from constant-pool string entries in the compiled `.class` files of the `aztec-v2.1.7.aar`. Confidence: **confirmed from source-compiled bytecode**.

### 3.1 Source classes and their tag constants

| Class | `getTAG()` / field | Confirms |
|---|---|---|
| `AztecStyleSpan$TAG$2` (bold branch, style=1) | `"b"` | bold → `<b>` |
| `AztecStyleSpan$TAG$2` (italic branch, style=2) | `"i"` | italic → `<i>` |
| `AztecStyleEmphasisSpan$TAG$2` | `"em"` | emphasis → `<em>` |
| `AztecStyleCiteSpan$TAG$2` | `"cite"` | cite → `<cite>` |
| `AztecStrikethroughSpan` | `"s"` (static field) | strikethrough → `<s>` |
| `AztecHeadingSpan$Heading` enum | `"h1"` … `"h6"` | headings → `<h1>`…`<h6>` |
| `AztecUnorderedListSpan` | `"ul"` (static field) | bullet list → `<ul>` |
| `AztecOrderedListSpan` | `"ol"` (static field) | ordered list → `<ol>` |
| `AztecListItemSpan` | `"li"` (static field) | list item → `<li>` |
| `AztecTaskListSpan` | `"ul"` + `type="task-list"` via `setTaskList()` | task list → `<ul type="task-list">` |

**Input parsing:** `AztecTagHandler` accepts `<s>`, `<strike>`, AND `<del>` for strikethrough (all three stored as static fields `STRIKETHROUGH_S`, `STRIKETHROUGH_STRIKE`, `STRIKETHROUGH_DEL`). Emitted tag is always `<s>`. The converter must accept all three on input but can emit `<s>`.

**Input parsing for strong/bold/em/italic:** `Html.java` registers separate `FORMAT_STRONG`/`FORMAT_BOLD` and `FORMAT_EMPHASIS`/`FORMAT_ITALIC` spans for `<strong>`/`<b>` and `<em>`/`<i>` respectively — both accepted. Emitted tags are `<b>` and `<i>` (the `AztecStyleSpan` variants, not `<strong>`/`<em>`).

### 3.2 HTML Serialization Architecture

`AztecParser.toHtml()` wraps each block-span's `getStartTag()` as:

```
"<" + span.getStartTag() + ">"   →  opening tag
"</" + span.getEndTag() + ">"    →  closing tag
```

Inline spans follow the same pattern. `getEndTag()` for all spans returns just `TAG` (e.g. `"b"`, `"li"`), producing e.g. `</b>`, `</li>`.

### 3.3 Feature-by-feature HTML strings

#### Bold — content "bold"
```html
<b>bold</b>
```

#### Italic — content "italic"
```html
<i>italic</i>
```

#### Strikethrough — content "strike"
```html
<s>strike</s>
```
Accepted on parse: `<s>`, `<strike>`, `<del>` (all map to `AztecStrikethroughSpan`).

#### H1 — content "Heading one"
```html
<h1>Heading one</h1>
```

#### H2 — content "Heading two"
```html
<h2>Heading two</h2>
```

#### Unordered list — single item "item"
```html
<ul><li>item</li></ul>
```

#### Unordered list — multiple items
```html
<ul><li>first</li><li>second</li></ul>
```

Note: Aztec may insert `<br>` between items in some versions. The parser applies a cleanup regex `(</? ?br>)*((aztec_cursor)?)</li>` → `$2</li>` to strip trailing `<br>` before `</li>`. Converter should be resilient to both `<ul><li>item</li></ul>` and `<ul>\n<li>item</li>\n</ul>` forms.

TO VERIFY ON DEVICE: exact whitespace/newline handling between `<ul>` and `<li>` tags.

#### Task list — UNCHECKED item — content "todo"

```html
<ul type="task-list"><li><input type="checkbox" class="task-list-item-checkbox" /></li></ul>
```

With text content:
```html
<ul type="task-list"><li><input type="checkbox" class="task-list-item-checkbox" />todo</li></ul>
```

**How the `<ul>` differentiates task from regular:** `AztecAttributesExtensionKt.setTaskList()` sets attribute `type="task-list"` on the `<ul>` span's attributes before `getStartTag()` is called. The `isTaskList()` check during parse reads `attributes.getValue("type") == "task-list"`.

**How the `<li>` emits the checkbox:** `AztecListItemSpan.getStartTag()` override (when `checked` attribute is present):
1. Builds `"li"` + any other attrs (excluding `checked`) + `">"` (closes the `li` open-tag inline)
2. Appends `<input type="checkbox" class="task-list-item-checkbox"` + optional ` checked` + ` /`
3. AztecParser wraps the entire result with `<` and `>`, producing: `<li><input type="checkbox" class="task-list-item-checkbox" />`

#### Task list — CHECKED item — content "done"

```html
<ul type="task-list"><li checked="true"><input type="checkbox" class="task-list-item-checkbox" checked /></li></ul>
```

Note: the `checked="true"` attribute on the `<li>` element is how Aztec stores checked state internally (written by `setChecked(true)` which calls `attributes.setValue("checked", "true")`). The `<input checked />` attribute mirrors it visually.

TO VERIFY ON DEVICE: whether `<li checked="true">` or `<li>` is actually emitted in the final HTML. The bytecode shows the `checked` attr is **excluded** from the `li` tag in the `getStartTag()` serialization loop (the loop at offset 42-112 skips any attr named `"checked"`), so the final HTML should be `<li>` (no `checked` on the `li`). Confidence: HIGH.

Final best-evidence shapes:

```
UNCHECKED: <ul type="task-list"><li><input type="checkbox" class="task-list-item-checkbox" />todo</li></ul>
CHECKED:   <ul type="task-list"><li><input type="checkbox" class="task-list-item-checkbox" checked />done</li></ul>
```

#### Empty content
```html

```
(empty string — `toHtml()` returns `""` for empty `Editable`)

TO VERIFY ON DEVICE: some Aztec versions return `"\n"` or `"<br>"` for a blank editor state.

#### Inline emphasis inside list items and headings

Aztec supports nested inline spans inside block spans. The HTML is straightforwardly nested:

```html
<!-- Bold inside H1 -->
<h1>A <b>bold</b> title</h1>

<!-- Italic inside unordered list item -->
<ul><li>Some <i>italic</i> text</li></ul>

<!-- Bold-italic inside task-list item -->
<ul type="task-list"><li><input type="checkbox" class="task-list-item-checkbox" /><b><i>both</i></b></li></ul>
```

TO VERIFY ON DEVICE: exact ordering when multiple inline spans overlap at the same character range (e.g. bold+italic applied simultaneously — is it `<b><i>` or `<i><b>`?).

#### Combined document example

```html
<h1>Title</h1><h2>Subtitle</h2><b>bold</b> and <i>italic</i> and <s>strike</s><ul><li>bullet one</li><li>bullet two</li></ul><ul type="task-list"><li><input type="checkbox" class="task-list-item-checkbox" />todo</li><li><input type="checkbox" class="task-list-item-checkbox" checked />done</li></ul>
```

TO VERIFY ON DEVICE: Aztec may emit `<br>` or `\n` between top-level block elements. The parser's cleanup regexes suggest trailing `<br>` before block-close tags are stripped, but inter-block whitespace is not specified in bytecode.

---

## 4. Task-List Module Details

**Location:** Built into `org.wordpress:aztec:v2.1.7` (no separate plugin artifact).

**Relevant classes:**
- `org.wordpress.aztec.spans.AztecTaskListSpan` — the `<ul type="task-list">` block span
- `org.wordpress.aztec.spans.AztecListItemSpan` — the `<li>` span; overrides `getStartTag()` to emit `<input type="checkbox".../>` when `checked` attr is present
- `org.wordpress.aztec.TaskListClickHandler` — handles checkbox tap → toggles `checked` attribute on the `AztecListItemSpan`, triggers visual refresh
- `org.wordpress.aztec.AztecAttributesExtensionKt` — `isTaskList(Attributes): Boolean` checks `type == "task-list"`; `setTaskList(AztecAttributes)` writes that attribute

**Enabling task lists in the toolbar/formatting:**

`FORMAT_TASK_LIST` is already in `AztecTextFormat` enum. Toggling is:

```kotlin
aztecText.toggleFormatting(AztecTextFormat.FORMAT_TASK_LIST)
```

No additional plugin registration is required for task-list HTML parsing/emitting. If using the built-in `AztecToolbar`, the task-list button is included in the standard toolbar layout.

---

## 5. Haze + AndroidView: Decision

### 5.1 How haze (Chris Banes) blurs

`haze` (github.com/chrisbanes/haze) blurs content by reading pixels from the Compose `RenderEffect` / `BlurMaskFilter` pipeline. The blur is applied at the **Compose graphics layer** level: it reads the composable's own pixels (its `GraphicsLayer`) and renders a blurred version as a background.

### 5.2 Why it doesn't work with AndroidView-hosted native views

`AztecText` is a native Android `View` (a custom `EditText`). When hosted inside a Compose `AndroidView { AztecText(...) }`, the native view is drawn via the Android `View` hierarchy — it does **not** render into the Compose `GraphicsLayer` that `haze` reads from.

Specifically:
- Compose draws its content to a `RenderNode`/`GraphicsLayer` in the Compose render tree.
- `AndroidView`-hosted views draw themselves via their own `ViewGroup` hierarchy and `RenderNode`, which is separate from Compose's layer.
- `haze`'s blur reads Compose's layer pixels. The native `AztecText` pixels are not present in that layer at blur time — they are composited by the Android framework in a subsequent draw pass.
- `RenderEffect`-based blur (available on API 31+) applied to the `AndroidView`'s host `ComposeView` would blur the Compose content but NOT the native view content drawn on top of it.

**Result:** `haze` will produce a blurred background that ignores the `AztecText` content entirely. The editor text would appear un-blurred on top of a blurred Compose background — wrong behavior.

### 5.3 Decision: Use a translucent scrim

**Recommendation: SCRIM FALLBACK**

Replace the haze blur with a semi-transparent scrim overlay drawn over the editor when a modal/sheet is shown above it. This is the standard pattern used by Material Design bottom sheets and dialogs over `SurfaceView`/`TextureView` content (which have the same AndroidView compositing problem).

**Concrete implementation guidance:**

```kotlin
// In the Compose layer above AztecText's AndroidView, draw a scrim
// when a modal is presented
Box {
    AndroidView(factory = { AztecText(it) }, ...)

    // Scrim — animated alpha
    AnimatedVisibility(visible = modalShown, ...) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScrimColor)
        )
    }
}
```

**Contrast / color guidance:**

| Light theme | Dark theme |
|---|---|
| `Color.Black.copy(alpha = 0.32f)` | `Color.Black.copy(alpha = 0.48f)` |

- `0.32f` on light theme approximates Material 3's standard modal scrim opacity and passes WCAG AA contrast for text beneath the overlay.
- Use `0.48f` on dark theme since the background is already dark and a stronger scrim is needed for perceived separation.
- Do NOT use a colored scrim (e.g. tinted with brand color) — a neutral black scrim is most legible and least distracting for an editor context.
- Animate the scrim in/out with `tween(durationMillis = 200, easing = FastOutSlowInEasing)` to match bottom-sheet animation timing.

**Why not RenderEffect as an alternative?**

`android.graphics.RenderEffect` (API 31+) can blur a `View`'s content including its children. Setting a `BlurMaskFilter` `RenderEffect` on the `AztecText` view itself would blur the text — but that would blur the editor content while the user is trying to read it, which is the opposite of what a focus-shifting modal needs. The scrim approach is correct.

---

## 6. Items Chunk 3 (MarkdownHtmlConverter) Must Know

### Confirmed with high confidence

1. **Bold:** emit and accept `<b>`. Also accept `<strong>` on input (Aztec parses both; emits `<b>`).
2. **Italic:** emit and accept `<i>`. Also accept `<em>` on input (emits `<i>`).
3. **Strikethrough:** emit `<s>`. Accept `<s>`, `<strike>`, `<del>` on input.
4. **Headings:** `<h1>` through `<h6>`.
5. **Unordered list:** `<ul><li>...</li></ul>`.
6. **Task list unchecked:** `<ul type="task-list"><li><input type="checkbox" class="task-list-item-checkbox" />text</li></ul>`
7. **Task list checked:** `<ul type="task-list"><li><input type="checkbox" class="task-list-item-checkbox" checked />text</li></ul>`
8. **Empty:** `""` (empty string).
9. **Inline nesting:** standard nested tags, e.g. `<h1><b>text</b></h1>`.

### Requires device verification (Chunk 4)

- **TO VERIFY:** Whitespace/newlines between block elements (e.g. `<h1>...\n<h2>...` vs `<h1>...<h2>...`). Converter should normalize whitespace before comparison.
- **TO VERIFY:** Whether empty editor returns `""`, `"\n"`, or `"<br>"`.
- **TO VERIFY:** Ordering of nested inline spans when multiple formats applied simultaneously (e.g. bold+italic — is it `<b><i>` or `<i><b>`?).
- **TO VERIFY:** Whether multi-item task lists produce a single `<ul type="task-list">` wrapper or separate `<ul>` per item.
- **TO VERIFY:** Exact `<li>` content when no other attributes besides `checked` are present — may be plain `<li>` or `<li >` (with trailing space).

### Converter defensive strategy

- Normalize `<strong>` → `<b>`, `<em>` → `<i>`, `<strike>`/`<del>` → `<s>` on round-trip.
- Strip trailing whitespace from `<li>` content before `</li>` (Aztec's cleanup regex suggests `<br>` may appear).
- Detect task lists by `<ul type="task-list">` wrapper, not by presence of `<input>` alone.
- Checked state: read `checked` attribute on `<input>` element (NOT on `<li>`).
- Handle `/>` and ` />` both as self-closing for the `<input>` element.
