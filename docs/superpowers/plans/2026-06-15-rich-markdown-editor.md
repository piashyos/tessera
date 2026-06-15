# Full Markdown Rich-Text Editor Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the note editor into a real WYSIWYG markdown editor: tapping anywhere in the content focuses the field and opens the keyboard; toolbar buttons apply formatting to the *selection* (instead of inserting literal `**` characters as visible text); and add bold, italic, strikethrough, links, and heading sizes.

**Architecture:** The current editor stores markdown as a `String` and edits it with a plain `BasicTextField`, so toolbar actions just splice in literal markdown tokens (the exact defect in the spec). Replace the body editor with a `RichTextState`-backed editor from **`com.mohamedrejeb.richeditor:richeditor-compose`** (the de-facto Compose markdown WYSIWYG library). The stored format stays **markdown text** for sync/back-compat: on load `richTextState.setMarkdown(note.content)`; on edit, push `richTextState.toMarkdown()` back into the ViewModel's `content`. Toolbar buttons call `RichTextState` formatting APIs against the current selection.

**Tech Stack:** richeditor-compose (verify latest compatible release — `1.0.0-rc13` is known to work with Compose 1.7.x / BOM 2024.10.00 and Kotlin 2.0.21), Jetpack Compose, Hilt, JUnit5, Compose UI test / Robolectric, Turbine.

**Scope note:** The existing interactive checklist (`ChecklistBody` rendering tappable checkboxes from `- [ ]` lines) is replaced by the rich editor. Checkbox *toggling* via the rendered checkbox UI is dropped in favor of full markdown editing; `- [ ]` text still round-trips as markdown. Call this out in the PR. The `onToggleChecklist` ViewModel path becomes unused — remove it only after confirming no other caller.

**Conventions:** Same as the sibling plans. Prefer testing a thin pure mapping layer where possible; use Compose UI tests for editor integration. Commit after every green step. DRY, YAGNI, TDD.

---

## Chunk 1: Add the rich-editor dependency

### Task 1.1: Declare and wire the library

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the version + library to the catalog**

In `gradle/libs.versions.toml`:
```toml
# [versions]
richeditor = "1.0.0-rc13"   # verify latest compatible with Compose 1.7.x before pinning

# [libraries]
richeditor-compose = { module = "com.mohamedrejeb.richeditor:richeditor-compose", version.ref = "richeditor" }
```

- [ ] **Step 2: Add the dependency to the app module**

In `app/build.gradle.kts` `dependencies { }`, add: `implementation(libs.richeditor.compose)`.

- [ ] **Step 3: Sync + confirm it resolves**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep richeditor`
Expected: the artifact resolves. If `1.0.0-rc13` fails to resolve or conflicts with the Compose runtime, try the latest `1.0.0-rcNN` or stable `1.x` and re-run.

- [ ] **Step 4: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build(editor): add richeditor-compose dependency"
```

---

## Chunk 2: Markdown round-trip helper + state plumbing

### Task 2.1: A pure helper proving markdown survives the rich-text round-trip

**Files:**
- Create: `app/src/main/java/app/tessera/ui/editor/RichMarkdown.kt`
- Test: `app/src/test/java/app/tessera/ui/editor/RichMarkdownTest.kt` (new, Robolectric — RichTextState needs the Compose runtime)

`RichTextState` is the editing source of truth, but the ViewModel needs `String` markdown. This helper centralizes the conversion + a debounce-friendly equality check so we don't fire spurious saves when the markdown is unchanged.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/app/tessera/ui/editor/RichMarkdownTest.kt
package app.tessera.ui.editor

import com.mohamedrejeb.richeditor.model.RichTextState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RichMarkdownTest {
    @Test
    fun `bold markdown round-trips`() {
        val state = RichTextState()
        state.setMarkdown("**bold** and _italic_")
        val out = state.toMarkdown()
        assertTrue(out.contains("**bold**"), "expected bold markdown, got: $out")
    }

    @Test
    fun `markdownChanged detects a real edit`() {
        val state = RichTextState()
        state.setMarkdown("hello")
        assertEquals(false, markdownChanged(state, "hello"))
        state.setMarkdown("hello world")
        assertEquals(true, markdownChanged(state, "hello"))
    }
}
```

(If JUnit5 + Robolectric conflict in this module, fall back to the JUnit4 vintage engine — `@RunWith(RobolectricTestRunner::class)` already implies JUnit4 `@Test`. Use `org.junit.Test`/`org.junit.Assert` instead.)

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.editor.RichMarkdownTest"`
Expected: FAIL — `markdownChanged` unresolved.

- [ ] **Step 3: Implement the helper**

```kotlin
// app/src/main/java/app/tessera/ui/editor/RichMarkdown.kt
package app.tessera.ui.editor

import com.mohamedrejeb.richeditor.model.RichTextState

fun markdownChanged(state: RichTextState, previous: String): Boolean =
    state.toMarkdown() != previous
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.editor.RichMarkdownTest"`
Expected: PASS. (If `toMarkdown()` emits `*bold*` instead of `**bold**`, relax the assertion to check the library's actual bold output — confirm the chosen library version's markdown dialect.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/RichMarkdown.kt \
        app/src/test/java/app/tessera/ui/editor/RichMarkdownTest.kt
git commit -m "feat(editor): markdown round-trip helper for rich text"
```

### Task 2.2: Replace the body editor with a RichTextState editor

**Files:**
- Rewrite: `app/src/main/java/app/tessera/ui/editor/components/ChecklistBody.kt` → new `RichBody.kt` (or repurpose the file)
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt`
- Test: `app/src/androidTest/java/app/tessera/ui/editor/RichBodyTest.kt` (new)

- [ ] **Step 1: Write the failing UI test (typing updates the markdown callback)**

```kotlin
// app/src/androidTest/java/app/tessera/ui/editor/RichBodyTest.kt
package app.tessera.ui.editor.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import app.tessera.core.ui.UiTestTags
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RichBodyTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun typingEmitsMarkdown() {
        var markdown = ""
        composeRule.setContent {
            val state = rememberRichTextState()
            RichBody(
                state = state,
                onMarkdownChange = { markdown = it },
            )
        }
        composeRule.onNodeWithTag(UiTestTags.EditorBodyField).performTextInput("hello")
        composeRule.waitForIdle()
        assertTrue(markdown.contains("hello"))
    }
}
```

- [ ] **Step 2: Run it to verify it fails.** Expected: FAIL — `RichBody` missing.

- [ ] **Step 3: Implement `RichBody`** using `BasicRichTextEditor` (un-decorated, to match the existing minimalist look):

```kotlin
// app/src/main/java/app/tessera/ui/editor/components/RichBody.kt
package app.tessera.ui.editor.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import app.tessera.core.ui.UiTestTags
import app.tessera.ui.editor.markdownChanged
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor

@Composable
fun RichBody(
    state: RichTextState,
    onMarkdownChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state) {
        snapshotFlow { state.annotatedString }
            .collect { onMarkdownChange(state.toMarkdown()) }
    }
    BasicRichTextEditor(
        state = state,
        modifier = modifier.fillMaxWidth().testTag(UiTestTags.EditorBodyField),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    )
}
```

(Confirm the actual package paths for `BasicRichTextEditor`/`RichTextState`/`rememberRichTextState` in the resolved library version; adjust imports.)

- [ ] **Step 4: Wire it into NoteEditorScreen**

- Hoist `val richTextState = rememberRichTextState()`.
- On initial load, set the markdown once: `LaunchedEffect(state.isLoading) { if (!state.isLoading) richTextState.setMarkdown(state.content) }` (guard against re-setting on every recomposition / autosave echo — only set when content arrives, not on each keystroke).
- Replace the `ChecklistBody(...)` call with `RichBody(state = richTextState, onMarkdownChange = onContentChange, modifier = Modifier.fillMaxWidth().weight(1f))`.
- Remove the now-unused `onToggleChecklist` from the screen signature if nothing else uses it (and from the ViewModel after Step 6).

- [ ] **Step 5: Run the UI test to verify it passes.** Expected: PASS.

- [ ] **Step 6: Remove dead checklist code if unreferenced**

Search for `onToggleChecklist`, `toggleChecklistLine`, `parseChecklist`, `ChecklistBody`, `editorChecklistItem`. If only the editor used them, delete `ChecklistBody.kt`, the toggle handling in `NoteEditorViewModel.onToggleChecklist`, and `Checklist.kt` (keep `Checklist.kt` only if other code/tests still use it — `ChecklistTest.kt` exists, so either keep the parser + its test, or delete both together). Run `./gradlew :app:testDebugUnitTest` to confirm nothing else breaks.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/ \
        app/src/androidTest/java/app/tessera/ui/editor/RichBodyTest.kt
git commit -m "feat(editor): replace plain body with rich-text markdown editor"
```

---

## Chunk 3: Tap-anywhere-to-focus

### Task 3.1: Focus the editor and open the keyboard when tapping empty content

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt`
- Test: `app/src/androidTest/java/app/tessera/ui/editor/EditorFocusTest.kt` (new)

The editor body currently only focuses when you tap the (short) text. Make the whole scrollable content area focus the body editor and show the keyboard on tap.

- [ ] **Step 1: Write the failing UI test**

```kotlin
// app/src/androidTest/java/app/tessera/ui/editor/EditorFocusTest.kt
package app.tessera.ui.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.UiTestTags
import org.junit.Rule
import org.junit.Test

class EditorFocusTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingEmptyContentFocusesBody() {
        composeRule.setContent {
            NoteEditorScreen(
                state = NoteEditorState(content = "", isLoading = false),
                onTitleChange = {}, onContentChange = {}, onAddTag = {},
                onRemoveTag = {}, onTogglePin = {}, onInsertFormat = {},
                onBack = {}, onDelete = {},
                // adjust to the real (post-checklist-removal) signature
            )
        }
        // tap a low/empty region of the content surface
        composeRule.onNodeWithTag(UiTestTags.EditorContentArea).performClick()
        composeRule.onNodeWithTag(UiTestTags.EditorBodyField).assertIsFocused()
    }
}
```

- [ ] **Step 2: Add `EditorContentArea` test tag** to `UiTestTags.kt`: `const val EditorContentArea = "editor_content_area"`.

- [ ] **Step 3: Run it to verify it fails.** Expected: FAIL.

- [ ] **Step 4: Implement tap-to-focus**

- Add `val bodyFocusRequester = remember { FocusRequester() }` and attach `.focusRequester(bodyFocusRequester)` to the `RichBody` modifier.
- Make the content `Column` fill height and add a clickable layer that requests focus + shows the keyboard:

```kotlin
val keyboard = LocalSoftwareKeyboardController.current
Column(
    modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .testTag(UiTestTags.EditorContentArea)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            bodyFocusRequester.requestFocus()
            keyboard?.show()
        },
    ...
)
```

Ensure the `RichBody` has `.weight(1f)` (or a large min-height) so the tappable area extends below the text to the bottom of the screen. Imports: `FocusRequester`, `focusRequester`, `LocalSoftwareKeyboardController`, `MutableInteractionSource`, `clickable`.

- [ ] **Step 5: Run the test to verify it passes.** Expected: PASS.

- [ ] **Step 6: Manual check** on an emulator: tapping any blank area below the text opens the keyboard with the caret in the body. State this was verified manually.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt \
        app/src/main/java/app/tessera/core/ui/UiTestTags.kt \
        app/src/androidTest/java/app/tessera/ui/editor/EditorFocusTest.kt
git commit -m "feat(editor): tap anywhere in content to focus and open keyboard"
```

---

## Chunk 4: Toolbar formatting that applies to the selection

Replace the token-inserting `MarkdownToolbar` with one that calls `RichTextState` formatting APIs so the selected text is actually styled (no more visible `**` characters).

### Task 4.1: Define the formatting action set

**Files:**
- Create: `app/src/main/java/app/tessera/ui/editor/components/MarkdownFormat.kt`
- Test: `app/src/test/java/app/tessera/ui/editor/components/MarkdownFormatTest.kt` (new, Robolectric)

- [ ] **Step 1: Write the failing test (applying Bold to a selection yields bold markdown)**

```kotlin
// app/src/test/java/app/tessera/ui/editor/components/MarkdownFormatTest.kt
package app.tessera.ui.editor.components

import androidx.compose.ui.text.TextRange
import com.mohamedrejeb.richeditor.model.RichTextState
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MarkdownFormatTest {
    @Test
    fun boldFormatsSelection() {
        val state = RichTextState()
        state.setMarkdown("hello")
        state.selection = TextRange(0, 5)
        applyFormat(state, MarkdownFormat.Bold)
        assertTrue(state.toMarkdown().contains("**hello**"))
    }

    @Test
    fun headingFormatsLine() {
        val state = RichTextState()
        state.setMarkdown("title")
        state.selection = TextRange(0, 5)
        applyFormat(state, MarkdownFormat.Heading1)
        assertTrue(state.toMarkdown().trimStart().startsWith("#"))
    }
}
```

- [ ] **Step 2: Run it to verify it fails.** Expected: FAIL — symbols missing.

- [ ] **Step 3: Implement the action enum + dispatcher**

```kotlin
// app/src/main/java/app/tessera/ui/editor/components/MarkdownFormat.kt
package app.tessera.ui.editor.components

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.mohamedrejeb.richeditor.model.RichTextState

enum class MarkdownFormat { Bold, Italic, Strikethrough, Heading1, Heading2, BulletList, Checklist }

fun applyFormat(state: RichTextState, format: MarkdownFormat) {
    when (format) {
        MarkdownFormat.Bold -> state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
        MarkdownFormat.Italic -> state.toggleSpanStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
        MarkdownFormat.Strikethrough -> state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
        MarkdownFormat.Heading1 -> state.toggleSpanStyle(SpanStyle(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)).also {
            // headings: use the library's paragraph/header API if available; otherwise prefix "# "
        }
        MarkdownFormat.Heading2 -> { /* see note */ }
        MarkdownFormat.BulletList -> state.toggleUnorderedList()
        MarkdownFormat.Checklist -> { /* library may lack checkbox lists; insert "- [ ] " at line start */ }
    }
}
```

IMPORTANT for the executor: the exact `RichTextState` API names depend on the resolved library version. Verify against the library source: bold/italic/strikethrough are `toggleSpanStyle(SpanStyle(...))`; lists are `toggleUnorderedList()` / `toggleOrderedList()`; headings may be `toggleSpanStyle` with a large `fontSize` or a dedicated paragraph API. Adjust the `when` body so the two tests above pass, then add the remaining cases. Keep this function the single source of truth for formatting.

- [ ] **Step 4: Run the tests to verify they pass** (tune the implementation to the real API until green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/components/MarkdownFormat.kt \
        app/src/test/java/app/tessera/ui/editor/components/MarkdownFormatTest.kt
git commit -m "feat(editor): selection-based markdown formatting actions"
```

### Task 4.2: Rebuild MarkdownToolbar to call applyFormat

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/components/MarkdownToolbar.kt`
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt`
- Test: `app/src/androidTest/java/app/tessera/ui/editor/ToolbarFormatTest.kt` (new)

- [ ] **Step 1: Write the failing UI test (select text, tap Bold, markdown becomes bold — no literal `**` shown as plain text)**

Render the editor with content "hello", programmatically set selection (or type then select-all via `performTextInput`/semantics), tap the Bold toolbar button (`UiTestTags.toolbarBold`), assert the emitted markdown contains `**hello**`.

- [ ] **Step 2: Add toolbar test tags** to `UiTestTags.kt`: `toolbarBold`, `toolbarItalic`, `toolbarStrikethrough`, `toolbarH1`, `toolbarH2`, `toolbarBullet`, `toolbarLink` (as `const val`s).

- [ ] **Step 3: Run it to verify it fails.** Expected: FAIL.

- [ ] **Step 4: Rewrite MarkdownToolbar**

Change the signature from `onInsertToken: (String) -> Unit` to `onFormat: (MarkdownFormat) -> Unit` (+ a separate `onAddLink: () -> Unit` for links, see Chunk 5). Render an icon button per action using Lucide icons (`Bold`, `Italic`, `Strikethrough`, `Heading1`, `Heading2`, `List`, `Link`), each tagged and calling `onFormat(...)`. Make the row horizontally scrollable so all actions fit.

- [ ] **Step 5: Wire it in NoteEditorScreen** — replace `MarkdownToolbar(onInsertToken = onInsertToken)` with `MarkdownToolbar(onFormat = { applyFormat(richTextState, it) }, onAddLink = { linkDialogVisible = true })`. Remove the old `onInsertToken` plumbing from the screen + ViewModel if unused.

- [ ] **Step 6: Run the test to verify it passes.** Expected: PASS — formatting applies to the selection, no literal markup is shown as text.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/components/MarkdownToolbar.kt \
        app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt \
        app/src/main/java/app/tessera/core/ui/UiTestTags.kt \
        app/src/androidTest/java/app/tessera/ui/editor/ToolbarFormatTest.kt
git commit -m "feat(editor): toolbar applies formatting to selection (bold/italic/strike/heading/list)"
```

---

## Chunk 5: Link insertion

### Task 5.1: Link dialog + addLink

**Files:**
- Create: `app/src/main/java/app/tessera/ui/editor/components/AddLinkPopup.kt`
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt`
- Test: `app/src/androidTest/java/app/tessera/ui/editor/AddLinkTest.kt` (new)

- [ ] **Step 1: Write the failing UI test** — open the link popup, enter text "Anthropic" + url "https://anthropic.com", confirm, assert the editor markdown contains `[Anthropic](https://anthropic.com)`.

- [ ] **Step 2: Add test tags** — `addLinkText`, `addLinkUrl`, `addLinkConfirm`.

- [ ] **Step 3: Run it to verify it fails.** Expected: FAIL.

- [ ] **Step 4: Implement `AddLinkPopup`** with `TesseraPopup` (reuse swipe-dismiss + blur from the tag-modal plan): two `TesseraTextField`s (display text, URL) and a confirm button. On confirm call back `onAddLink(text, url)`.

- [ ] **Step 5: Wire it** — in `NoteEditorScreen`, on confirm call `richTextState.addLink(text = text, url = url)` (verify the exact API name/params in the resolved library — it may be `addLink(text, url)` or require a selection). Add `var linkDialogVisible by remember { mutableStateOf(false) }` toggled by the toolbar link button.

- [ ] **Step 6: Run the test to verify it passes.** Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/components/AddLinkPopup.kt \
        app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt \
        app/src/main/java/app/tessera/core/ui/UiTestTags.kt \
        app/src/androidTest/java/app/tessera/ui/editor/AddLinkTest.kt
git commit -m "feat(editor): insert markdown links via a dialog"
```

---

## Chunk 6: Autosave correctness with the rich editor

### Task 6.1: Ensure load doesn't fight autosave (no echo loop)

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt`
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorViewModel.kt`
- Test: `app/src/test/java/app/tessera/ui/editor/NoteEditorViewModelTest.kt` (extend if present)

The risk: `onMarkdownChange` (from `RichBody`) pushes into `onContentChange` → state.content updates → the load `LaunchedEffect` could re-`setMarkdown` and reset the caret, or autosave could fire on the initial programmatic `setMarkdown`.

- [ ] **Step 1: Guard the initial setMarkdown** so it runs exactly once when the note finishes loading (key the `LaunchedEffect` on a one-shot flag, e.g. `LaunchedEffect(noteLoadedKey)`), not on every `state.content` change.

- [ ] **Step 2: Confirm autosave only fires on user edits**

Verify (test or manual) that opening an existing note does NOT immediately trigger a save (the `setMarkdown` → `snapshotFlow` emission should be treated as the baseline). If the ViewModel test framework allows, assert no `updateNote` call occurs until a real keystroke. Use `markdownChanged(...)` from Task 2.1 to gate `onContentChange` if needed.

- [ ] **Step 3: Run the editor test suite**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.editor.*"`
Expected: PASS.

- [ ] **Step 4: Commit (if changed)**

```bash
git add app/src/main/java/app/tessera/ui/editor/
git commit -m "fix(editor): load markdown once and avoid autosave echo with rich editor"
```

---

## Final verification

- [ ] **Editor unit/Robolectric tests:** `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.editor.*"` — paste passing output.
- [ ] **Editor UI tests:** `./gradlew :app:connectedDebugAndroidTest --tests "app.tessera.ui.editor.*"` (if a device is available).
- [ ] **Full build:** `./gradlew :app:assembleDebug`
- [ ] **Manual end-to-end on emulator:**
  - Tap any empty area → keyboard opens, caret in body.
  - Select text, tap Bold/Italic/Strikethrough → text restyles, no literal `**`/`_`/`~~` shown.
  - Apply H1/H2 → larger heading text.
  - Insert a link → renders as a tappable/styled link, stored as `[text](url)`.
  - Reopen the note → all formatting persists (markdown round-trip), no autosave-on-open data loss.
- [ ] Report with superpowers:verification-before-completion — real command output only, and explicitly note which checks were manual (the library-API-dependent steps) vs automated.
```
