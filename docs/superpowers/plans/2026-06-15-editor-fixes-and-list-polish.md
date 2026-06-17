# Editor edit-bug fix + Notes-list polish Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore typing inside the new Aztec WYSIWYG body (currently dead on tap), strip Markdown markers from note-card previews, expose the grid/list view toggle that already exists in the ViewModel, give tiles proper internal padding, anchor the notes-list tag-filter animation to its button, and lift the editor's tag chips into an expandable row above the formatting toolbar — matching the notes-list interaction model.

**Architecture:** Surgical fixes on top of the WYSIWYG editor + sync work that just landed.
- *Editor edit-bug:* programmatic `AztecText(context)` skipped the styled-attr initialization the lib needs for focus/IME. Switch to `LayoutInflater.from(context).inflate(R.layout.rich_note_body, ...)` against a 4-line XML stub that supplies `historyEnable`/`textColor`/`textColorHint`, then run `Aztec.Factory.with(editor, NoOpToolbar, NoOpListener)` to wire history + IME callbacks. No public API change for `RichNoteBody`.
- *Card preview:* introduce a pure `markdownToPreviewPlainText(String): String` in `app/src/main/java/app/tessera/core/text/`, reuse it from `NoteCard.previewText`. Strips `#`/`*`/`~`/`- [ ]`/`-` block + inline markers in one regex pass; no third-party dependency.
- *View-mode toggle:* `NotesListViewModel.onToggleViewMode()` already exists. Plumb it through `NotesListScreen` and add a `GlassFab` toggle next to the existing tag-filter FAB (`UiTestTags.ViewModeToggle`), icon swaps between `LayoutGrid`/`Rows3`.
- *Tile padding:* SwipeableNoteCard learns a single `isTile: Boolean` param; in tile mode it applies horizontal+vertical inner padding inside the card surface so neighbours stop hugging.
- *Notes-list tag anim:* keep `AnimatedVisibility`, but expand from `Alignment.Top` with `transformOrigin` at the FAB's anchor (right edge). The FAB stays in its own column; the bar's transform origin is the new lever.
- *Editor tag list:* extract a small `TagChipsExpandable` composable mirroring `TagFilterBar`'s API (expand/collapse via tag FAB). The bottom capsule splits into a `Column { AnimatedVisibility(chips); Row(toolbar + addTagButton) }`.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.10.00), WordPress Aztec `v2.1.7`, Lucide icons, JUnit 5 + Robolectric for unit tests, Compose androidTest.

**Source spec / context:**
- `docs/superpowers/plans/2026-06-15-wysiwyg-editor-and-firestore-sync.md` — the WYSIWYG plan that introduced `RichNoteBody`, `MarkdownToolbar`, scrim fallback.
- `docs/superpowers/specs/aztec-spike-findings.md` — Aztec API, exact HTML shapes (§3), haze decision (§5).

**Conventions:**
- `BasicTextField`, Lucide icons (`com.composables:icons-lucide-android`), `FloatingCapsule`/`haze` for capsules.
- Tests: JVM unit tests use **JUnit 5 (jupiter)**; Robolectric uses JUnit 4. Compose androidTest is JUnit 4.
- Commit after every green step. DRY, YAGNI, TDD.
- Emulator is **not available**; instrumented runs and live-typing checks are flagged as **manual** at the final verification.

**Ordering rationale:** Chunk 1 (Aztec editable) unblocks every manual editor test downstream and ships value immediately. Chunk 2 (card preview) is independent of the editor and trivially testable. Chunks 3 + 4 (view-mode toggle + tile padding) share viewMode plumbing and ship together. Chunk 5 (notes-list tag anim) is a 5-line CSS-style tweak on top of an existing component. Chunk 6 (editor expandable tags) builds on the toolbar layout once everything below is green.

---

## Chunk 1: Aztec editable fix (critical)

`AztecText(context)` constructed programmatically skips the styled-attr init the lib uses to set up its history stack, text colors, and IME hooks — the view shows fromHtml content but refuses key events. Two-pronged fix: inflate from an XML stub that supplies the required attributes, then run `Aztec.Factory.with(editor, toolbar, listener)` with no-op toolbar + listener implementations to install the History plugin and wire the IME callback chain.

**File Structure for this chunk:**
- Create: `app/src/main/res/layout/rich_note_body.xml` — single `<org.wordpress.aztec.AztecText>` root with the required Aztec styled attrs.
- Modify: `app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt` — inflate the layout, call `Aztec.Factory.with`, set explicit `LayoutParams(MATCH_PARENT, WRAP_CONTENT)`, ensure focusability.
- (No test added: the bug is observable only on a real device; Aztec construction crashes Robolectric. Manual verification is required.)

### Task 1.1: Add the Aztec XML stub

`AztecText` reads its block/inline formatter colors, history toggle, and hint color from styled attrs that only get parsed when the view is **inflated**. The smallest possible stub:

**Files:**
- Create: `app/src/main/res/layout/rich_note_body.xml`

- [ ] **Step 1: Create the layout**

```xml
<?xml version="1.0" encoding="utf-8"?>
<org.wordpress.aztec.AztecText
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/aztec"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@android:color/transparent"
    android:gravity="top|start"
    android:hint="@string/editor_body_hint"
    android:textColor="?android:attr/textColorPrimary"
    android:textColorHint="?android:attr/textColorHint"
    android:textCursorDrawable="@null"
    android:inputType="textMultiLine|textCapSentences"
    android:imeOptions="actionDone|flagNoExtractUi"
    app:historyEnable="true"
    app:historySize="20" />
```

> The string `editor_body_hint` already exists (`R.string.editor_body_hint`, "Start writing…"). If it doesn't, add it next to the title hint in `values/strings.xml` and `values-ru/strings.xml` with sensible defaults (en: "Start writing…", ru: "Начни писать…"). Search the file before adding to avoid duplicates.

- [ ] **Step 2: Confirm resources compile**

Run: `./gradlew :app:processDebugResources`
Expected: BUILD SUCCESSFUL — the layout reference resolves and `R.layout.rich_note_body` is generated.

- [ ] **Step 3: Stage (do NOT commit yet)**

Land this together with Task 1.2 so the layout never exists without a consumer.

```bash
git add app/src/main/res/layout/rich_note_body.xml
```

### Task 1.2: Inflate Aztec + call `Aztec.Factory.with` in `RichNoteBody`

Replace `AztecText(context)` with `LayoutInflater.from(context).inflate(...) as AztecText`, then run the Aztec factory with a no-op toolbar and listener. The factory installs `History`, registers the IME callback chain, and (crucially) flips a few private booleans on `AztecText` that gate `onKeyDown` handling.

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt:65-99`

- [ ] **Step 1: Add the no-op toolbar + listener at the top of the file**

After the existing imports, add minimal stub implementations. Place them as `private` top-level objects (not nested inside `RichNoteBody`) so they're singletons:

```kotlin
// File-private no-ops: Aztec.Factory.with REQUIRES a toolbar + listener to install History/IME
// plumbing on the AztecText even when the UI uses a separate Compose toolbar.
private object NoOpAztecToolbar : org.wordpress.aztec.toolbar.IAztecToolbar {
    override fun toggleEditorMode() = Unit
    override fun enableFormatButtons(isEnabled: Boolean) = Unit
    override fun enableMediaMode(enabled: Boolean) = Unit
    override fun getSelectedHTMLForMode(): String = ""
    override fun setExpanded(expanded: Boolean) = Unit
    override fun setToolbarListener(listener: org.wordpress.aztec.toolbar.IAztecToolbarClickListener) = Unit
    override fun setEditor(
        editor: org.wordpress.aztec.AztecText,
        sourceEditor: org.wordpress.aztec.source.SourceViewEditText?,
    ) = Unit
    override fun addButton(plugin: org.wordpress.aztec.plugins.IToolbarButton) = Unit
    override fun hide() = Unit
    override fun show() = Unit
}

private object NoOpAztecToolbarClickListener : org.wordpress.aztec.toolbar.IAztecToolbarClickListener {
    override fun onToolbarFormatButtonClicked(
        format: org.wordpress.aztec.ITextFormat,
        isKeyboardShortcut: Boolean,
    ): Boolean = false

    override fun onToolbarCollapseButtonClicked() = Unit
    override fun onToolbarExpandButtonClicked() = Unit
    override fun onToolbarHeadingButtonClicked() = Unit
    override fun onToolbarHtmlButtonClicked() = Unit
    override fun onToolbarListButtonClicked() = Unit
    override fun onToolbarMediaButtonClicked(): Boolean = false
}
```

> If any method signature mismatches the version on the classpath, adapt to the actual interface (the file is small and the IDE will surface the missing/extra members). The methods themselves are intentionally no-ops.

- [ ] **Step 2: Replace the `AztecText(context)` constructor with inflation**

In the `factory` lambda of the `AndroidView`, swap construction for inflation and wire the Aztec factory after `fromHtml`:

```kotlin
val inflater = android.view.LayoutInflater.from(context)
val editor = inflater.inflate(app.tessera.R.layout.rich_note_body, null, false) as AztecText
editor.layoutParams = android.view.ViewGroup.LayoutParams(
    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
)

// Apply theme-aware styling
editor.setTextColor(textColor.toArgb())
editor.textSize = textSizeSp.value
editor.setPadding(0, 8, 0, 8)

// Wire History + IME callbacks via the Aztec factory (no-op toolbar/listener — the Compose
// MarkdownToolbar drives formatting through RichBodyActions). Without this call the view
// renders fromHtml content but ignores key events / cursor input.
org.wordpress.aztec.Aztec.Factory.with(
    editor,
    NoOpAztecToolbar,
    NoOpAztecToolbarClickListener,
)

// Load initial HTML AFTER Aztec.with so plugins/history see it as the starting state.
editor.fromHtml(html)
lastLoadedMarkdown.value = markdownKey

editor.isFocusable = true
editor.isFocusableInTouchMode = true
```

Imports to add (keep them top-level instead of fully qualified where used more than once):
```kotlin
import org.wordpress.aztec.Aztec
import org.wordpress.aztec.toolbar.IAztecToolbar
import org.wordpress.aztec.toolbar.IAztecToolbarClickListener
```
(Fully-qualified usage in the no-op stubs is fine too; pick one style.)

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

If the compile fails because `IAztecToolbar`/`IAztecToolbarClickListener` member signatures differ on the resolved Aztec version, fix the no-op stubs to match exactly. The compiler error names the missing/extra method.

- [ ] **Step 4: Stage everything together**

```bash
git add app/src/main/res/layout/rich_note_body.xml \
  app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt
```

(If `editor_body_hint` was added in Task 1.1, stage `strings.xml` files too.)

- [ ] **Step 5: Commit**

```bash
git commit -m "fix(editor): inflate AztecText with required attrs + Aztec.Factory.with to enable typing"
```

### Task 1.3: Manual sanity check (deferred to final verification)

- [ ] **Step 1: Note the gating manual check**

In the Final verification section, mark "open an editor on emulator, tap the body, type a paragraph, hit each toolbar button, confirm formatting renders WYSIWYG and the cursor stays in place". This was previously deferred for the WYSIWYG plan too; consolidate both manual lists at the end of this plan.

---

## Chunk 2: Strip Markdown markers from note-card previews

`NoteCard.previewText` returns the first non-blank trimmed line verbatim, which surfaces raw markers (`# Title`, `- [ ] todo`, `**bold**`) on the card. Add a pure stripper that drops block-level prefixes (`# `, `## `, `- `, `- [ ] `, `- [x] `) and inline markers (`**`, `*`, `~~`, `<...>` HTML tags) before truncation. Reuse the existing `MarkdownHtmlConverter` knowledge of the supported feature set — no dependency.

**File Structure for this chunk:**
- Create: `app/src/main/java/app/tessera/core/text/MarkdownPreview.kt` — `fun markdownToPreviewPlainText(markdown: String): String`.
- Test: `app/src/test/java/app/tessera/core/text/MarkdownPreviewTest.kt`.
- Modify: `app/src/main/java/app/tessera/ui/notes/components/NoteCard.kt:63-69, 109-113` — call the stripper before truncation.

### Task 2.1: TDD the stripper

The contract: a single line for the preview (first non-blank line after stripping). All supported block prefixes are removed, all inline markers are removed, HTML angle-bracketed tags are dropped (in case some note migrated through the editor still has stray `<b>...</b>`), and whitespace is collapsed.

**Files:**
- Create: `app/src/test/java/app/tessera/core/text/MarkdownPreviewTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package app.tessera.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarkdownPreviewTest {

    @Test
    fun `empty markdown maps to empty`() {
        assertEquals("", markdownToPreviewPlainText(""))
    }

    @Test
    fun `plain paragraph survives unchanged`() {
        assertEquals("Just some text.", markdownToPreviewPlainText("Just some text."))
    }

    @Test
    fun `h1 marker is stripped`() {
        assertEquals("Title", markdownToPreviewPlainText("# Title"))
    }

    @Test
    fun `h2 marker is stripped`() {
        assertEquals("Subtitle", markdownToPreviewPlainText("## Subtitle"))
    }

    @Test
    fun `bullet marker is stripped`() {
        assertEquals("item one", markdownToPreviewPlainText("- item one"))
    }

    @Test
    fun `unchecked task marker is stripped`() {
        assertEquals("buy milk", markdownToPreviewPlainText("- [ ] buy milk"))
    }

    @Test
    fun `checked task marker is stripped`() {
        assertEquals("call mom", markdownToPreviewPlainText("- [x] call mom"))
    }

    @Test
    fun `bold inline markers are stripped`() {
        assertEquals("important text", markdownToPreviewPlainText("**important** text"))
    }

    @Test
    fun `italic inline markers are stripped`() {
        assertEquals("hint text", markdownToPreviewPlainText("*hint* text"))
    }

    @Test
    fun `strikethrough inline markers are stripped`() {
        assertEquals("old text", markdownToPreviewPlainText("~~old~~ text"))
    }

    @Test
    fun `html tags are stripped`() {
        assertEquals("bold text", markdownToPreviewPlainText("<b>bold</b> text"))
    }

    @Test
    fun `first non-blank line wins, leading blanks skipped`() {
        val md = "\n\n# First heading\nignored second line"
        assertEquals("First heading", markdownToPreviewPlainText(md))
    }

    @Test
    fun `whitespace inside the line is preserved as a single space`() {
        assertEquals("a b c", markdownToPreviewPlainText("a   b\tc"))
    }

    @Test
    fun `combined markers strip cleanly`() {
        assertEquals("bold heading", markdownToPreviewPlainText("# **bold** heading"))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.text.MarkdownPreviewTest"`
Expected: FAIL — `markdownToPreviewPlainText` is unresolved.

- [ ] **Step 3: Implement the stripper**

Create `app/src/main/java/app/tessera/core/text/MarkdownPreview.kt`:

```kotlin
package app.tessera.core.text

private val BLOCK_PREFIX = Regex("""^\s*(?:#{1,6}\s+|-\s+\[[ xX]]\s+|-\s+|>\s+)""")
private val INLINE_BOLD = Regex("""\*\*([^*]+)\*\*""")
private val INLINE_ITALIC = Regex("""(?<!\*)\*([^*]+)\*(?!\*)""")
private val INLINE_STRIKE = Regex("""~~([^~]+)~~""")
private val HTML_TAG = Regex("""<[^>]+>""")
private val WHITESPACE = Regex("""\s+""")

/**
 * Produces a single-line, marker-free preview of canonical Markdown for note-card display.
 * Picks the first non-blank line, strips supported block prefixes (#, ##, -, - [ ], - [x]),
 * unwraps inline emphasis (**bold**, *italic*, ~~strike~~), removes any HTML tags (in case a
 * note round-tripped through Aztec and kept stray markup), and collapses internal whitespace.
 * Returns "" for empty/blank input.
 */
fun markdownToPreviewPlainText(markdown: String): String {
    val firstLine = markdown
        .lineSequence()
        .map(String::trim)
        .firstOrNull { it.isNotEmpty() }
        ?: return ""
    return firstLine
        .replace(BLOCK_PREFIX, "")
        .replace(INLINE_BOLD) { it.groupValues[1] }
        .replace(INLINE_ITALIC) { it.groupValues[1] }
        .replace(INLINE_STRIKE) { it.groupValues[1] }
        .replace(HTML_TAG, "")
        .replace(WHITESPACE, " ")
        .trim()
}
```

> If the italic regex misbehaves on Kotlin's regex engine (`(?<!\*)` lookbehind), simplify to two-pass: first remove `**…**`, then remove `*…*`. The current order already runs bold before italic, so the lookbehind is belt-and-suspenders — drop it if the engine complains.

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.text.MarkdownPreviewTest"`
Expected: PASS (14 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/core/text/MarkdownPreview.kt \
  app/src/test/java/app/tessera/core/text/MarkdownPreviewTest.kt
git commit -m "feat(notes): markdown-preview stripper for note cards"
```

### Task 2.2: Wire the stripper into `NoteCard`

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/components/NoteCard.kt:109-113` (replace `previewText`)
- Modify: `app/src/main/java/app/tessera/ui/notes/components/NoteCard.kt:63-69` (call site — no signature change)

- [ ] **Step 1: Replace `previewText` with a call to the stripper**

Change:

```kotlin
private fun Note.previewText(): String? =
    content
        .lineSequence()
        .map(String::trim)
        .firstOrNull { it.isNotEmpty() }
```

to:

```kotlin
private fun Note.previewText(): String? =
    markdownToPreviewPlainText(content).takeIf { it.isNotEmpty() }
```

Add the import: `import app.tessera.core.text.markdownToPreviewPlainText`.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Update / add a NoteCard preview test (optional but cheap)**

If `app/src/test/java/app/tessera/ui/notes/components/NoteCardPreviewTest.kt` (or similar JVM test) exists, add an assertion that a card with content `"# **Hi** world"` previews to `"Hi world"`. If no such test exists, skip — the stripper has its own test and `NoteCard` is otherwise covered by androidTest.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/components/NoteCard.kt
git commit -m "feat(notes): show markdown-free preview text on note cards"
```

---

## Chunk 3: View-mode toggle + tile padding

`NotesListViewModel.onToggleViewMode()` already swaps `Tiles ↔ List`, and the grid already honours the resulting column count. What's missing is the user-facing button + per-mode padding inside tile cards so neighbouring tiles get visual breathing room.

**File Structure for this chunk:**
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt` — add `onToggleViewMode: () -> Unit` parameter, render a `GlassFab` toggle next to the tag filter FAB; pass `isTile` down through `NotesContent` → `SwipeableNoteCard` → `NoteCard`.
- Modify: `app/src/main/java/app/tessera/ui/notes/components/SwipeableNoteCard.kt:124-134` — accept `isTile`, apply horizontal padding when true.
- Modify: `app/src/main/java/app/tessera/ui/notes/components/NoteCard.kt` — accept `isTile`, apply inner content padding when true.
- Modify: `app/src/main/java/app/tessera/ui/navigation/NotesNavGraph.kt` — wire `viewModel::onToggleViewMode` into the new screen parameter.
- Modify: `app/src/main/java/app/tessera/core/ui/UiTestTags.kt` — add `const val ViewModeToggle = "view_mode_toggle"`.
- Modify: `app/src/main/res/values/strings.xml` + `values-ru/strings.xml` — content description for the toggle.

### Task 3.1: Add the test tag + strings

**Files:**
- Modify: `app/src/main/java/app/tessera/core/ui/UiTestTags.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`

- [ ] **Step 1: Add the test tag**

Add inside `UiTestTags` (alphabetical or grouped with the other notes-list tags):

```kotlin
const val ViewModeToggle = "view_mode_toggle"
```

- [ ] **Step 2: Add strings**

`values/strings.xml`:

```xml
<string name="notes_view_mode_tiles">Show as tiles</string>
<string name="notes_view_mode_list">Show as list</string>
```

`values-ru/strings.xml`:

```xml
<string name="notes_view_mode_tiles">Плитка</string>
<string name="notes_view_mode_list">Список</string>
```

- [ ] **Step 3: Compile**

Run: `./gradlew :app:processDebugResources && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Stage (defer commit to Task 3.4)**

### Task 3.2: Plumb `onToggleViewMode` through the screen + render the toggle FAB

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt` (signature + bottom-right column, near lines 270-298)
- Modify: `app/src/main/java/app/tessera/ui/navigation/NotesNavGraph.kt` (call site)

- [ ] **Step 1: Add the parameter to `NotesListScreen`**

Open `NotesListScreen.kt` and add `onToggleViewMode: () -> Unit,` to the top-level `NotesListScreen` signature (near the other `on*` lambdas). Read the current signature and place it right after `onTagToggle` for grouping.

- [ ] **Step 2: Render the toggle FAB above the tag-filter FAB**

In the bottom-right `Column` (currently containing `GlassFab` for `TagFilterToggle` + `CreateNoteFab`), insert a new `GlassFab` ABOVE the tag-filter FAB:

```kotlin
val viewMode = state.viewMode()
val (toggleIcon, toggleDesc) = if (viewMode == NotesViewMode.Tiles) {
    Lucide.Rows3 to stringResource(R.string.notes_view_mode_list)
} else {
    Lucide.LayoutGrid to stringResource(R.string.notes_view_mode_tiles)
}
GlassFab(
    onClick = if (isSearchFocused) null else onToggleViewMode,
    size = 40.dp,
    contentDescription = toggleDesc,
    modifier = Modifier
        .graphicsLayer {
            alpha = fabAlpha
            scaleX = fabScale
            scaleY = fabScale
        }
        .shadow(
            elevation = 12.dp,
            shape = CircleShape,
            ambientColor = FloatingShadowAmbient,
            spotColor = FloatingShadowSpot,
            clip = false,
        )
        .testTag(UiTestTags.ViewModeToggle),
) {
    Icon(
        imageVector = toggleIcon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
    )
}
```

Add imports: `com.composables.icons.lucide.LayoutGrid`, `com.composables.icons.lucide.Rows3`. If `Rows3` does not exist in the icon set, fall back to `Lucide.List` for the "switch to list" state and `Lucide.LayoutGrid` for "switch to tiles".

- [ ] **Step 3: Wire the new parameter in `NotesNavGraph`**

Open `NotesNavGraph.kt`, find the `NotesListScreen(...)` call site (it has all the `on*` callbacks already). Add:

```kotlin
onToggleViewMode = viewModel::onToggleViewMode,
```

- [ ] **Step 4: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Stage (defer commit to Task 3.4)**

### Task 3.3: Per-mode padding on `NoteCard` / `SwipeableNoteCard`

Tiles need internal breathing room so adjacent cards in the staggered grid don't kiss. List rows already feel airy because they're full-width with vertical-only padding. Add an `isTile: Boolean` parameter (default `false`) to both card composables; in tile mode, apply `Modifier.padding(horizontal = 12.dp, vertical = 14.dp)` to the inner content column.

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/components/NoteCard.kt` (add `isTile` param + conditional padding on the outer `Column`)
- Modify: `app/src/main/java/app/tessera/ui/notes/components/SwipeableNoteCard.kt` (forward `isTile` to `NoteCard`)
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt` (pass `state.viewMode == NotesViewMode.Tiles` to `SwipeableNoteCard` inside the grid `items` block — find the existing `SwipeableNoteCard(...)` call and add the new param)

- [ ] **Step 1: Add `isTile` to `NoteCard`**

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteCard(
    note: Note,
    modifier: Modifier = Modifier,
    isTile: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
    val paddedModifier = if (isTile) {
        clickModifier.padding(horizontal = 14.dp, vertical = 14.dp)
    } else {
        clickModifier
    }
    Column(
        modifier = paddedModifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { /* existing body */ }
}
```

Don't forget the `import androidx.compose.foundation.layout.padding` if it's missing.

- [ ] **Step 2: Forward `isTile` through `SwipeableNoteCard`**

```kotlin
fun SwipeableNoteCard(
    note: Note,
    modifier: Modifier = Modifier,
    isTile: Boolean = false,
    onClick: () -> Unit = {},
    onTogglePin: () -> Unit = {},
    onDelete: () -> Unit = {},
) { /* ... */ }
```

In the inner `NoteCard(...)` call (lines 126-133), pass `isTile = isTile`.

- [ ] **Step 3: Pass `isTile` from the grid in `NotesListScreen`**

In `NotesContent`'s `LazyVerticalStaggeredGrid` items block, where each note is rendered, pass `isTile = state.viewMode == NotesViewMode.Tiles`. The call site is the existing `SwipeableNoteCard(note = note, ...)` block.

- [ ] **Step 4: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

### Task 3.4: Commit Chunk 3 as one compile-clean change

The three sub-tasks above are tightly coupled; bundle them.

- [ ] **Step 1: Run unit tests to confirm nothing regressed**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (same count as before + Chunk 2 additions).

- [ ] **Step 2: Stage everything and commit**

```bash
git add app/src/main/java/app/tessera/core/ui/UiTestTags.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-ru/strings.xml \
  app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt \
  app/src/main/java/app/tessera/ui/navigation/NotesNavGraph.kt \
  app/src/main/java/app/tessera/ui/notes/components/NoteCard.kt \
  app/src/main/java/app/tessera/ui/notes/components/SwipeableNoteCard.kt
git commit -m "feat(notes): user-facing tiles/list view-mode toggle with per-mode card padding"
```

---

## Chunk 4: Anchor the notes-list tag-filter animation to its button

`TagFilterBar` uses `expandVertically()` with default expand origin (center), so the chips bloom out of the middle of the bar rather than feeling like they pour out of the tag-toggle FAB on the right. Two small changes:
1. Switch to `expandVertically(expandFrom = Alignment.Top)` so the height grows from the top edge downward (origin is now the top of the bar — closer to the FAB which sits in the right column at the same vertical band).
2. Add a `graphicsLayer { transformOrigin = TransformOrigin(1f, 0f) }` to the chip row so scale-in feels anchored to the top-right corner under the FAB.

**File Structure for this chunk:**
- Modify: `app/src/main/java/app/tessera/ui/notes/components/TagFilterBar.kt:39-44`

### Task 4.1: Re-aim the enter/exit animation

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/components/TagFilterBar.kt:39-44`

- [ ] **Step 1: Replace the animation specs**

Change:

```kotlin
AnimatedVisibility(
    visible = expanded && availableTags.isNotEmpty(),
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
    modifier = modifier,
)
```

to:

```kotlin
AnimatedVisibility(
    visible = expanded && availableTags.isNotEmpty(),
    enter = fadeIn() +
        expandVertically(expandFrom = Alignment.Top) +
        scaleIn(transformOrigin = TransformOrigin(1f, 0f), initialScale = 0.85f),
    exit = fadeOut() +
        shrinkVertically(shrinkTowards = Alignment.Top) +
        scaleOut(transformOrigin = TransformOrigin(1f, 0f), targetScale = 0.85f),
    modifier = modifier,
)
```

Add the imports:

```kotlin
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.TransformOrigin
```

> The `TransformOrigin(1f, 0f)` puts the scale pivot at the top-right corner of the chip row — the right edge sits closest to the tag FAB column, so the animation reads as "pouring out of the button". The `initialScale = 0.85f` keeps the effect subtle (no popcorn-pop).

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/components/TagFilterBar.kt
git commit -m "feat(notes): anchor tag-filter expand animation to the toggle FAB"
```

> Visual verification is manual (no emulator). Verify on device that tapping the tag FAB makes the chips appear to grow from the top-right corner of the bar (closest to the FAB), not from the center.

---

## Chunk 5: Editor — expandable tag list above the toolbar

The bottom capsule packs `MarkdownToolbar + TagChips` into a single `Row`; with seven toolbar icons + multiple tag chips that row stays cramped and the add-tag button competes with the toolbar for attention. Match the notes-list interaction model: a thin tag FAB toggles an expandable `TagChipsExpandable` row above the toolbar, with the same scale-in-from-button animation.

**File Structure for this chunk:**
- Create: `app/src/main/java/app/tessera/ui/editor/components/TagChipsExpandable.kt` — wraps the existing `TagChips` content in an `AnimatedVisibility` (or, more pragmatic, render a horizontally-scrollable row of `FloatingCapsule` chips with `#tag`).
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt` — split the bottom capsule into `Column { tag row (animated); toolbar row }`; replace the inline `TagChips(...)` with a tag-toggle button that mirrors the notes-list FAB pattern.
- Modify: `app/src/main/res/values/strings.xml` + `values-ru/strings.xml` — toggle content description.
- Modify: `app/src/main/java/app/tessera/core/ui/UiTestTags.kt` — add `EditorTagToggle`.

### Task 5.1: Add the test tag + strings

- [ ] **Step 1: Add the test tag**

In `UiTestTags`:

```kotlin
const val EditorTagToggle = "editor_tag_toggle"
```

- [ ] **Step 2: Add strings**

`values/strings.xml`:

```xml
<string name="editor_tags_toggle">Toggle tags</string>
```

`values-ru/strings.xml`:

```xml
<string name="editor_tags_toggle">Показать теги</string>
```

- [ ] **Step 3: Stage (defer commit until 5.3 lands)**

### Task 5.2: Build `TagChipsExpandable`

A near-copy of the notes-list `TagFilterBar` but tailored to the editor's data shape (read-only `tags: List<String>` + remove callback + add-tag callback). The expansion animation matches Chunk 4.

**Files:**
- Create: `app/src/main/java/app/tessera/ui/editor/components/TagChipsExpandable.kt`

- [ ] **Step 1: Create the file**

```kotlin
package app.tessera.ui.editor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.components.FloatingCapsule
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X

@Composable
fun TagChipsExpandable(
    expanded: Boolean,
    tags: List<String>,
    onRemoveTag: (String) -> Unit,
    onAddTagClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn() +
            expandVertically(expandFrom = Alignment.Bottom) +
            scaleIn(transformOrigin = TransformOrigin(0f, 1f), initialScale = 0.85f),
        exit = fadeOut() +
            shrinkVertically(shrinkTowards = Alignment.Bottom) +
            scaleOut(transformOrigin = TransformOrigin(0f, 1f), targetScale = 0.85f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tags.forEach { tag ->
                FloatingCapsule(
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .clip(CircleShape),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#$tag", style = MaterialTheme.typography.labelMedium)
                        Surface(
                            onClick = { onRemoveTag(tag) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.0f),
                            modifier = Modifier
                                .size(20.dp),
                        ) {
                            Icon(
                                imageVector = Lucide.X,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }

            Surface(
                onClick = onAddTagClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = Lucide.Plus,
                    contentDescription = stringResource(R.string.editor_add_tag),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
```

> The pivot is `TransformOrigin(0f, 1f)` (bottom-left) because the toolbar capsule sits below the chip row and grows upward from it — the editor's spatial inversion of the notes list, where chips grow downward from the FAB. Verify the visual on device; flip pivots if it looks wrong.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

### Task 5.3: Refactor the editor bottom capsule

The bottom area becomes a `Column { TagChipsExpandable(visible = tagsExpanded); FloatingCapsule { Row(MarkdownToolbar + TagToggleButton) } }`.

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt` — restructure the bottom column starting near line 272.

- [ ] **Step 1: Add the toggle state**

Near the other `remember { mutableStateOf(...) }` calls in `NoteEditorScreen`, add:

```kotlin
var tagsExpanded by remember { mutableStateOf(false) }
```

- [ ] **Step 2: Restructure the bottom Column**

Replace the existing bottom `Column { FloatingCapsule { Row { MarkdownToolbar(...); TagChips(...) } } }` block with:

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.BottomCenter)
        .imePadding()
        .padding(horizontal = 20.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
) {
    TagChipsExpandable(
        expanded = tagsExpanded,
        tags = state.tags,
        onRemoveTag = onRemoveTag,
        onAddTagClick = { tagPickerVisible = true },
    )

    FloatingCapsule(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = FloatingShadowAmbient,
                spotColor = FloatingShadowSpot,
            ),
        blurRadius = 20.dp,
        shadowElevation = 4.dp,
        hazeState = null,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MarkdownToolbar(
                onBold = { actions?.toggleBold() },
                onItalic = { actions?.toggleItalic() },
                onStrikethrough = { actions?.toggleStrikethrough() },
                onHeading1 = { actions?.toggleHeading1() },
                onHeading2 = { actions?.toggleHeading2() },
                onBullet = { actions?.toggleBulletList() },
                onTaskList = { actions?.toggleTaskList() },
                modifier = Modifier.weight(1f),
            )
            Surface(
                onClick = { tagsExpanded = !tagsExpanded },
                shape = CircleShape,
                color = if (tagsExpanded) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                modifier = Modifier
                    .size(34.dp)
                    .testTag(UiTestTags.EditorTagToggle),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Lucide.Tag,
                        contentDescription = stringResource(R.string.editor_tags_toggle),
                        tint = if (tagsExpanded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
}
```

Required imports (add only the ones missing):
- `com.composables.icons.lucide.Tag`
- `androidx.compose.foundation.shape.CircleShape`
- `app.tessera.ui.editor.components.TagChipsExpandable`

Remove the import for `TagChips` if it's no longer used in this file. **Do not delete `TagChips.kt` itself yet** — it may still be referenced by previews or future work; if `grep -r "TagChips\b"` is clean after the refactor, delete it in a separate housekeeping commit.

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit Chunk 5 as one change**

```bash
git add app/src/main/java/app/tessera/core/ui/UiTestTags.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-ru/strings.xml \
  app/src/main/java/app/tessera/ui/editor/components/TagChipsExpandable.kt \
  app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt
git commit -m "feat(editor): expandable tag row above toolbar, matching notes-list pattern"
```

### Task 5.4: (Optional) Remove the now-unused `TagChips` composable

- [ ] **Step 1: Confirm no callers**

Run: `grep -rn "TagChips\b" app/src` — only `TagChips.kt` itself should match. Previews inside that file don't count as production callers.

- [ ] **Step 2: Delete the file if orphaned**

```bash
git rm app/src/main/java/app/tessera/ui/editor/components/TagChips.kt
./gradlew :app:compileDebugKotlin   # MUST be BUILD SUCCESSFUL
git commit -m "chore(editor): remove unused TagChips composable"
```

If any caller pops up (e.g. an unused preview elsewhere referencing it), keep the file and skip this task.

---

## Final verification

- [ ] `./gradlew :app:testDebugUnitTest` — all unit tests pass. Paste the summary `<testsuite tests="…" failures="0"/>` total.
- [ ] `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL.
- [ ] `./gradlew :app:connectedDebugAndroidTest --tests "app.tessera.ui.editor.*"` — editor instrumentation passes (if a device is available; **manual**).
- [ ] **Manual (Chunk 1 — critical):** open an editor on a real device, tap the body, type a sentence, hit Enter, type a second line, tap each toolbar button (Bold/Italic/Strike/H1/H2/Bullet/Task) and confirm:
  - Cursor moves with input; the IME shows.
  - Formatting visibly applies WYSIWYG.
  - Cursor does not jump to the start after each keystroke (echo-loop guard still holds).
  - Re-opening the note preserves formatting.
- [ ] **Manual (Chunk 2):** create a note with `# Title\n**bold** body` — confirm the card shows `Title` (or `bold body` if the title were on a later line) without `#`, `**`.
- [ ] **Manual (Chunk 3):** tap the view-mode FAB on the list — grid collapses to a single-column list; tap again, returns to tiles; tile cards have visible internal padding so adjacent cards no longer touch.
- [ ] **Manual (Chunk 4):** open the tag filter on the notes list — chips appear to scale/fade in from the top-right corner of the bar (under the tag FAB), not from the center.
- [ ] **Manual (Chunk 5):** in the editor, tap the new tag toggle in the bottom capsule — the tag chips row expands above the toolbar with a matching scale-from-corner animation; tapping `+` opens the existing tag picker; tapping a chip's `×` removes it; tapping the toggle again collapses the row.
- [ ] Report with superpowers:verification-before-completion — real command output only, explicitly flagging which checks were manual (Aztec-editable, animation feel, tile padding visual) vs automated.
