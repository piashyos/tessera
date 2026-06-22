# Editor UX Polish — Plan A

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tighten the note editor UX: make selection-only inline formatting reliable, collapse the markdown toolbar into a single blurred dropdown that exposes every format, slide the editor tag chip row in from the right with a spring, round out the back-button capsule, and shrink the add-tag icon.

**Architecture:** All work lives inside `app/src/main/java/app/tessera/ui/editor/` (and the shared `core/ui/components/`). Selection preservation is handled in `RichNoteBody` by capturing AztecText's selection range before invoking a `RichBodyActions` callback and restoring it after — the toolbar buttons don't need to know about Aztec. The new format menu is a `MarkdownFormatMenu` composable rendered via Compose `Popup` with a shared `HazeState` so the dropdown surface visually matches the toolbar capsule.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, `dev.chrisbanes.haze`, `org.wordpress.aztec` (already in tree). No new dependencies required.

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt` | Modify | Expose `editor.selectionStart/End` capture-restore inside each `RichBodyActions` method; add `H3/H4/H5/H6` + `PARAGRAPH` toggles and a `currentFormats()` query for the dropdown. |
| `app/src/main/java/app/tessera/ui/editor/components/MarkdownToolbar.kt` | Modify | Drop the row of icon buttons; render a single "Format" trigger icon + the existing tag toggle. Trigger opens `MarkdownFormatMenu`. |
| `app/src/main/java/app/tessera/ui/editor/components/MarkdownFormatMenu.kt` | Create | The blurred anchored dropdown that lists every markdown format with current selection state highlighting. |
| `app/src/main/java/app/tessera/ui/editor/components/TagChipsExpandable.kt` | Modify | Reverse anchor from `Bottom`/`(1f,1f)` to `End`/right-anchored slide-in; replace tween defaults with a light spring. |
| `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt` | Modify | Wrap the back IconButton in a `CircleShape` capsule; shrink the add-tag plus icon; rewire `MarkdownToolbar` call site with the new signature; place `TagChipsExpandable` so it's right-anchored to the tag-toggle FAB. |
| `app/src/main/res/values/strings.xml` | Modify | Add `toolbar_format`, `toolbar_paragraph`, `toolbar_h3`, `toolbar_h4`, `toolbar_h5`, `toolbar_h6` strings. |
| `app/src/main/res/values-ru/strings.xml` (if it exists) | Modify | Russian translations for the new strings. |
| `app/src/test/java/app/tessera/ui/editor/components/MarkdownFormatMenuTest.kt` | Create | Compose UI test: menu opens on trigger, shows highlighted state for currently-active format(s), dispatches the right callback. |
| `app/src/test/java/app/tessera/ui/editor/components/TagChipsExpandableTest.kt` | Create | Compose UI test: expansion uses the right anchor (transformOrigin x=1f, content shifts in from the right). |

---

## Chunk 1: Selection-aware inline formatting

### Task 1: Capture-restore selection around every RichBodyActions call

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt:35-43` (interface), `:189-197` (action wiring)

**Background:** Tapping a Compose `IconButton` shifts focus away from the AztecText View. AztecText then collapses its selection — so subsequent `editor.toggleFormatting(FORMAT_BOLD)` applies to the cursor's paragraph (the whole line) instead of the previously selected range. Capturing `selectionStart`/`selectionEnd` *inside the action closure* (which runs synchronously from the Compose tap before focus actually changes is reliable) and re-applying it via `editor.setSelection(start, end)` before `toggleFormatting` makes the toolbar selection-aware.

- [ ] **Step 1: Add the helper to each RichBodyActions implementation**

Modify `RichNoteBody.kt` action wiring (lines 189-197):

```kotlin
onActionsReady(object : RichBodyActions {
    private inline fun withPreservedSelection(block: () -> Unit) {
        val start = editor.selectionStart
        val end = editor.selectionEnd
        block()
        if (start in 0..editor.editableText.length &&
            end in 0..editor.editableText.length &&
            start != end) {
            editor.setSelection(start, end)
        }
    }

    override fun toggleBold() = withPreservedSelection {
        editor.toggleFormatting(AztecTextFormat.FORMAT_BOLD)
    }
    override fun toggleItalic() = withPreservedSelection {
        editor.toggleFormatting(AztecTextFormat.FORMAT_ITALIC)
    }
    override fun toggleStrikethrough() = withPreservedSelection {
        editor.toggleFormatting(AztecTextFormat.FORMAT_STRIKETHROUGH)
    }
    override fun toggleHeading1() = withPreservedSelection {
        editor.toggleFormatting(AztecTextFormat.FORMAT_HEADING_1)
    }
    override fun toggleHeading2() = withPreservedSelection {
        editor.toggleFormatting(AztecTextFormat.FORMAT_HEADING_2)
    }
    override fun toggleBulletList() = withPreservedSelection {
        editor.toggleFormatting(AztecTextFormat.FORMAT_UNORDERED_LIST)
    }
    override fun toggleTaskList() = withPreservedSelection {
        editor.toggleFormatting(AztecTextFormat.FORMAT_TASK_LIST)
    }
})
```

- [ ] **Step 2: Build to verify the file compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Smoke test on emulator**

Run: `./scripts/run-debug-on-emulator.sh`
In the app:
1. Create or open a note with several words on one line.
2. Long-press to start a selection on the middle word, expand to cover only that word.
3. Tap **Bold** in the toolbar.
4. Verify: only the selected word becomes bold; the rest of the line stays plain.
5. Repeat for **Italic** and **Strikethrough**.

If the whole line still bolds → the focus loss happens before the action closure runs. Fallback: store the selection on the AztecText's `OnSelectionChangedListener` (use `addOnSelectionChangedListener`) into a local `var lastSelection: IntRange?` and read from there inside `withPreservedSelection`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt
git commit -m "fix(editor): preserve selection across toolbar format toggles"
```

---

## Chunk 2: Extend `RichBodyActions` for the full markdown surface

### Task 2: Add the missing heading levels + paragraph + format-state query

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt:35-43`

- [ ] **Step 1: Extend the interface**

Replace the `RichBodyActions` interface at `RichNoteBody.kt:35-43`:

```kotlin
interface RichBodyActions {
    fun toggleBold()
    fun toggleItalic()
    fun toggleStrikethrough()
    fun toggleParagraph()
    fun toggleHeading1()
    fun toggleHeading2()
    fun toggleHeading3()
    fun toggleHeading4()
    fun toggleHeading5()
    fun toggleHeading6()
    fun toggleBulletList()
    fun toggleTaskList()

    /**
     * Formats currently applied at the editor's selection / cursor position.
     * The toolbar dropdown reads this to highlight active entries.
     */
    fun activeFormats(): Set<AztecTextFormat>
}
```

- [ ] **Step 2: Implement the new actions and the `activeFormats` query**

Inside the `onActionsReady` object in `RichNoteBody.kt`, add (using the same `withPreservedSelection` helper from Task 1):

```kotlin
override fun toggleParagraph() = withPreservedSelection {
    editor.toggleFormatting(AztecTextFormat.FORMAT_PARAGRAPH)
}
override fun toggleHeading3() = withPreservedSelection {
    editor.toggleFormatting(AztecTextFormat.FORMAT_HEADING_3)
}
override fun toggleHeading4() = withPreservedSelection {
    editor.toggleFormatting(AztecTextFormat.FORMAT_HEADING_4)
}
override fun toggleHeading5() = withPreservedSelection {
    editor.toggleFormatting(AztecTextFormat.FORMAT_HEADING_5)
}
override fun toggleHeading6() = withPreservedSelection {
    editor.toggleFormatting(AztecTextFormat.FORMAT_HEADING_6)
}

private val queryFormats = listOf(
    AztecTextFormat.FORMAT_BOLD,
    AztecTextFormat.FORMAT_ITALIC,
    AztecTextFormat.FORMAT_STRIKETHROUGH,
    AztecTextFormat.FORMAT_PARAGRAPH,
    AztecTextFormat.FORMAT_HEADING_1,
    AztecTextFormat.FORMAT_HEADING_2,
    AztecTextFormat.FORMAT_HEADING_3,
    AztecTextFormat.FORMAT_HEADING_4,
    AztecTextFormat.FORMAT_HEADING_5,
    AztecTextFormat.FORMAT_HEADING_6,
    AztecTextFormat.FORMAT_UNORDERED_LIST,
    AztecTextFormat.FORMAT_TASK_LIST,
)
override fun activeFormats(): Set<AztecTextFormat> =
    queryFormats.filterTo(mutableSetOf()) {
        editor.contains(it, editor.selectionStart, editor.selectionEnd)
    }
```

> `AztecText.contains(format, start, end)` is the public API; verify the exact signature by quickly grepping `~/.gradle/caches/modules-2/files-2.1/org.wordpress/aztec/v2.1.7/.../classes.jar` if needed.

- [ ] **Step 3: Update the placeholder branch (inspection mode)**

`RichNoteBody.kt` returns early in `LocalInspectionMode.current`. The `onActionsReady` is never called there, so no change needed. Confirm by reading the file.

- [ ] **Step 4: Build & verify**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt
git commit -m "feat(editor): expose H3-H6, paragraph, and activeFormats on RichBodyActions"
```

---

## Chunk 3: Custom blurred format dropdown

### Task 3: Create `MarkdownFormatMenu`

**Files:**
- Create: `app/src/main/java/app/tessera/ui/editor/components/MarkdownFormatMenu.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Background:** The dropdown is a `Popup` anchored beneath (or above, if no space) the toolbar's format trigger. The surface uses `Modifier.hazeEffect(hazeState)` with the same `frostColor` the popup pattern in `TesseraPopup.kt:150-152` uses, so it visually matches the floating capsule. Items show a tinted background when their format is in `activeFormats()`.

- [ ] **Step 1: Add the strings**

In `app/src/main/res/values/strings.xml`, add:

```xml
<string name="toolbar_format">Format</string>
<string name="toolbar_paragraph">Paragraph</string>
<string name="toolbar_h3">Heading 3</string>
<string name="toolbar_h4">Heading 4</string>
<string name="toolbar_h5">Heading 5</string>
<string name="toolbar_h6">Heading 6</string>
```

If `values-ru/strings.xml` already has the other toolbar strings translated, add the same keys there with Russian text:

```xml
<string name="toolbar_format">Формат</string>
<string name="toolbar_paragraph">Абзац</string>
<string name="toolbar_h3">Заголовок 3</string>
<string name="toolbar_h4">Заголовок 4</string>
<string name="toolbar_h5">Заголовок 5</string>
<string name="toolbar_h6">Заголовок 6</string>
```

(If `values-ru/strings.xml` does not exist, skip — i18n comes in Plan D.)

- [ ] **Step 2: Write the menu composable**

Create `MarkdownFormatMenu.kt`:

```kotlin
package app.tessera.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.tessera.R
import app.tessera.core.ui.theme.FloatingShadowAmbient
import app.tessera.core.ui.theme.FloatingShadowSpot
import com.composables.icons.lucide.Bold
import com.composables.icons.lucide.Heading1
import com.composables.icons.lucide.Heading2
import com.composables.icons.lucide.Heading3
import com.composables.icons.lucide.Heading4
import com.composables.icons.lucide.Heading5
import com.composables.icons.lucide.Heading6
import com.composables.icons.lucide.Italic
import com.composables.icons.lucide.List
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pilcrow
import com.composables.icons.lucide.Strikethrough
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import org.wordpress.aztec.AztecTextFormat

data class FormatEntry(
    val format: AztecTextFormat,
    val icon: ImageVector,
    val labelRes: Int,
    val onClick: () -> Unit,
)

@Composable
fun MarkdownFormatMenu(
    visible: Boolean,
    activeFormats: Set<AztecTextFormat>,
    actions: RichBodyActions,
    onDismiss: () -> Unit,
    hazeState: HazeState,
) {
    if (!visible) return

    val entries = listOf(
        FormatEntry(AztecTextFormat.FORMAT_PARAGRAPH, Lucide.Pilcrow, R.string.toolbar_paragraph, actions::toggleParagraph),
        FormatEntry(AztecTextFormat.FORMAT_HEADING_1, Lucide.Heading1, R.string.toolbar_h1, actions::toggleHeading1),
        FormatEntry(AztecTextFormat.FORMAT_HEADING_2, Lucide.Heading2, R.string.toolbar_h2, actions::toggleHeading2),
        FormatEntry(AztecTextFormat.FORMAT_HEADING_3, Lucide.Heading3, R.string.toolbar_h3, actions::toggleHeading3),
        FormatEntry(AztecTextFormat.FORMAT_HEADING_4, Lucide.Heading4, R.string.toolbar_h4, actions::toggleHeading4),
        FormatEntry(AztecTextFormat.FORMAT_HEADING_5, Lucide.Heading5, R.string.toolbar_h5, actions::toggleHeading5),
        FormatEntry(AztecTextFormat.FORMAT_HEADING_6, Lucide.Heading6, R.string.toolbar_h6, actions::toggleHeading6),
        FormatEntry(AztecTextFormat.FORMAT_BOLD, Lucide.Bold, R.string.toolbar_bold, actions::toggleBold),
        FormatEntry(AztecTextFormat.FORMAT_ITALIC, Lucide.Italic, R.string.toolbar_italic, actions::toggleItalic),
        FormatEntry(AztecTextFormat.FORMAT_STRIKETHROUGH, Lucide.Strikethrough, R.string.toolbar_strikethrough, actions::toggleStrikethrough),
        FormatEntry(AztecTextFormat.FORMAT_UNORDERED_LIST, Lucide.List, R.string.toolbar_bullet, actions::toggleBulletList),
        FormatEntry(AztecTextFormat.FORMAT_TASK_LIST, Lucide.ListChecks, R.string.toolbar_checklist, actions::toggleTaskList),
    )

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
        popupPositionProvider = aboveAnchorPositionProvider(),
    ) {
        Surface(
            modifier = Modifier
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = FloatingShadowAmbient,
                    spotColor = FloatingShadowSpot,
                )
                .clip(RoundedCornerShape(20.dp))
                .hazeEffect(state = hazeState) {
                    backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                    blurRadius = 24.dp
                },
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.0f),
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                entries.forEach { entry ->
                    FormatRow(entry = entry, active = entry.format in activeFormats) {
                        entry.onClick()
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatRow(entry: FormatEntry, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(entry.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

private fun aboveAnchorPositionProvider() = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // Anchor to the trigger button: bottom-left of the popup sits 8dp above the
        // top of the anchor, horizontally aligned to the anchor's left edge.
        val gapPx = 24 // ~8dp on mdpi; tighten if needed
        val x = anchorBounds.left.coerceAtMost(windowSize.width - popupContentSize.width)
        val y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}
```

> **Icon availability:** `com.composables.icons.lucide.Pilcrow`, `Heading3..Heading6` — confirm they exist in the lucide-icons-compose dependency by checking `~/.gradle/caches/.../lucide-*.jar` or grepping the source set. If any are missing, swap to `Lucide.Type` / `Lucide.Text` as a fallback and add a `// TODO: replace with Pilcrow once available` comment.

- [ ] **Step 3: Build & verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If any lucide icon constant doesn't exist, fix the import and retry.

- [ ] **Step 4: Commit (menu component only, not yet wired)**

```bash
git add app/src/main/java/app/tessera/ui/editor/components/MarkdownFormatMenu.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-ru/strings.xml
git commit -m "feat(editor): add blurred format dropdown menu component"
```

### Task 4: Replace the toolbar row with a single format trigger

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/components/MarkdownToolbar.kt`
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt:313-322`

- [ ] **Step 1: Rewrite `MarkdownToolbar`**

Replace the entire `MarkdownToolbar.kt` body:

```kotlin
@Composable
fun MarkdownToolbar(
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onOpenMenu,
            modifier = Modifier
                .size(34.dp)
                .testTag(UiTestTags.EditorFormatMenuTrigger),
        ) {
            Icon(
                imageVector = Lucide.Type,
                contentDescription = stringResource(R.string.toolbar_format),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
```

> Add `EditorFormatMenuTrigger` to `UiTestTags.kt` (single new const). Remove the now-unused `Bold`/`Italic`/`Strikethrough`/`Heading1`/`Heading2`/`List`/`ListChecks` imports.

- [ ] **Step 2: Wire the menu into NoteEditorScreen**

In `NoteEditorScreen.kt`, just above the bottom `FloatingCapsule` block, add (inside the same `Column`):

```kotlin
var formatMenuVisible by remember { mutableStateOf(false) }
```

(use `rememberSaveable` if you want it to survive config changes, but it's modal so `remember` is fine.)

Replace the existing `MarkdownToolbar(...)` invocation (lines 313-322) with:

```kotlin
MarkdownToolbar(
    onOpenMenu = { formatMenuVisible = true },
    modifier = Modifier.weight(1f),
)
```

After the `FloatingCapsule { … }` that holds the toolbar, render the menu:

```kotlin
val currentActions = actions
if (currentActions != null) {
    MarkdownFormatMenu(
        visible = formatMenuVisible,
        activeFormats = remember(formatMenuVisible) {
            // Re-query each time the menu opens
            if (formatMenuVisible) currentActions.activeFormats() else emptySet()
        },
        actions = currentActions,
        onDismiss = { formatMenuVisible = false },
        hazeState = hazeState, // the same HazeState the screen already creates
    )
}
```

> If the screen does not currently have a top-level `HazeState`, hoist one to the top of `NoteEditorScreen` (`val hazeState = remember { HazeState() }`) and apply `Modifier.hazeSource(hazeState)` on the scrollable body Surface (the file already has a similar `popupHazeState` per the explore report — reuse it if it covers the body).

- [ ] **Step 3: Update strings**

`toolbar_format` should already exist from Task 3. Confirm.

- [ ] **Step 4: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Smoke test**

Run: `./scripts/run-debug-on-emulator.sh`
1. Open a note. Tap the format trigger in the toolbar. The dropdown should appear ABOVE the toolbar capsule, blurred, with the surface continuous with the toolbar capsule's haze.
2. Tap each format option and confirm:
   - The corresponding format is applied to the current selection (or paragraph for headings/lists).
   - The menu closes.
   - Re-opening the menu shows the just-applied format highlighted.
3. Tap outside the menu — it dismisses.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/components/MarkdownToolbar.kt \
        app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt \
        app/src/main/java/app/tessera/core/ui/UiTestTags.kt
git commit -m "feat(editor): collapse markdown toolbar into single blurred format dropdown"
```

### Task 5: Compose UI test for `MarkdownFormatMenu`

**Files:**
- Create: `app/src/test/java/app/tessera/ui/editor/components/MarkdownFormatMenuTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package app.tessera.ui.editor.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.wordpress.aztec.AztecTextFormat

@RunWith(RobolectricTestRunner::class)
class MarkdownFormatMenuTest {
    @get:Rule val composeRule = createComposeRule()

    private val recordedToggles = mutableListOf<AztecTextFormat>()

    private val fakeActions = object : RichBodyActions {
        override fun toggleBold() { recordedToggles += AztecTextFormat.FORMAT_BOLD }
        override fun toggleItalic() { recordedToggles += AztecTextFormat.FORMAT_ITALIC }
        override fun toggleStrikethrough() { recordedToggles += AztecTextFormat.FORMAT_STRIKETHROUGH }
        override fun toggleParagraph() { recordedToggles += AztecTextFormat.FORMAT_PARAGRAPH }
        override fun toggleHeading1() { recordedToggles += AztecTextFormat.FORMAT_HEADING_1 }
        override fun toggleHeading2() { recordedToggles += AztecTextFormat.FORMAT_HEADING_2 }
        override fun toggleHeading3() { recordedToggles += AztecTextFormat.FORMAT_HEADING_3 }
        override fun toggleHeading4() { recordedToggles += AztecTextFormat.FORMAT_HEADING_4 }
        override fun toggleHeading5() { recordedToggles += AztecTextFormat.FORMAT_HEADING_5 }
        override fun toggleHeading6() { recordedToggles += AztecTextFormat.FORMAT_HEADING_6 }
        override fun toggleBulletList() { recordedToggles += AztecTextFormat.FORMAT_UNORDERED_LIST }
        override fun toggleTaskList() { recordedToggles += AztecTextFormat.FORMAT_TASK_LIST }
        override fun activeFormats(): Set<AztecTextFormat> = setOf(AztecTextFormat.FORMAT_BOLD)
    }

    @Test fun `menu shows every format and dispatches the right callback`() {
        var dismissed = false
        composeRule.setContent {
            MarkdownFormatMenu(
                visible = true,
                activeFormats = setOf(AztecTextFormat.FORMAT_BOLD),
                actions = fakeActions,
                onDismiss = { dismissed = true },
                hazeState = HazeState(),
            )
        }

        composeRule.onNodeWithText("Bold").assertIsDisplayed()
        composeRule.onNodeWithText("Heading 3").assertIsDisplayed()

        composeRule.onNodeWithText("Italic").performClick()
        assert(AztecTextFormat.FORMAT_ITALIC in recordedToggles)
        assert(dismissed)
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew :app:testDebugUnitTest --tests app.tessera.ui.editor.components.MarkdownFormatMenuTest`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/app/tessera/ui/editor/components/MarkdownFormatMenuTest.kt
git commit -m "test(editor): cover MarkdownFormatMenu rendering and dispatch"
```

---

## Chunk 4: Right-anchored tag chip row with spring

### Task 6: Slide the editor tag chips in from the right

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/components/TagChipsExpandable.kt`
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt:287-292` (alignment)

- [ ] **Step 1: Update the animation spec**

Replace the `AnimatedVisibility` block in `TagChipsExpandable.kt:43-50`:

```kotlin
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally

// inside the composable:
val springExpand = spring<IntSize>(
    dampingRatio = 0.7f,    // gentle bounce
    stiffness = Spring.StiffnessMediumLow,
)
val springScale = spring<Float>(
    dampingRatio = 0.7f,
    stiffness = Spring.StiffnessMediumLow,
)

AnimatedVisibility(
    visible = expanded,
    enter = fadeIn() +
        expandHorizontally(animationSpec = springExpand, expandFrom = Alignment.End) +
        scaleIn(transformOrigin = TransformOrigin(1f, 0.5f), initialScale = 0.85f, animationSpec = springScale),
    exit = fadeOut() +
        shrinkHorizontally(animationSpec = springExpand, shrinkTowards = Alignment.End) +
        scaleOut(transformOrigin = TransformOrigin(1f, 0.5f), targetScale = 0.85f, animationSpec = springScale),
    modifier = modifier,
)
```

> Remove the unused `expandVertically` / `shrinkVertically` imports.

- [ ] **Step 2: Right-align the row content**

Still inside `TagChipsExpandable`, the inner `Row` currently `fillMaxWidth()` with default horizontal arrangement. Change to:

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState(), reverseScrolling = true),
    horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End),
    verticalAlignment = Alignment.CenterVertically,
) {
    // chips order: existing tags first, then the "+" add button on the FAR RIGHT
    // (or reverse — design decision: keep "+" closest to the FAB it expands from)
    ...
}
```

- [ ] **Step 3: Confirm the screen-level placement**

In `NoteEditorScreen.kt:287-292`, the `TagChipsExpandable` already sits in a full-width `Column` above the toolbar. Since the chips now align right and slide from the right edge, no further screen change is needed — the row visually anchors next to the tag FAB which lives on the toolbar's right.

If on inspection the chips look too far left (e.g., when only one chip exists), wrap the `TagChipsExpandable` in `Box(modifier = Modifier.align(Alignment.End))` instead of allowing it to fill the width.

- [ ] **Step 4: Build & smoke test**

Run: `./scripts/run-debug-on-emulator.sh`

1. Open a note with at least 3 tags.
2. Tap the tag toggle (the `#` FAB).
3. Verify: chips slide in from the right with a slight bounce, with the rightmost chip sitting closest to the FAB.
4. Tap again to collapse — chips slide back to the right.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/components/TagChipsExpandable.kt \
        app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt
git commit -m "feat(editor): tag chips slide in from the right with spring animation"
```

### Task 7: Compose UI test for the chip animation anchor

**Files:**
- Create: `app/src/test/java/app/tessera/ui/editor/components/TagChipsExpandableTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package app.tessera.ui.editor.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TagChipsExpandableTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `expanded tag row shows all tags`() {
        composeRule.setContent {
            TagChipsExpandable(
                expanded = true,
                tags = listOf("alpha", "beta", "gamma"),
                onRemoveTag = {},
                onAddTagClick = {},
            )
        }
        composeRule.onNodeWithText("#alpha").assertIsDisplayed()
        composeRule.onNodeWithText("#beta").assertIsDisplayed()
        composeRule.onNodeWithText("#gamma").assertIsDisplayed()
    }
}
```

> A pixel-accurate test of the slide direction is brittle; this just sanity-checks the rendering. The visual smoke test in Task 6 covers anchor verification.

- [ ] **Step 2: Run the test**

Run: `./gradlew :app:testDebugUnitTest --tests app.tessera.ui.editor.components.TagChipsExpandableTest`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/app/tessera/ui/editor/components/TagChipsExpandableTest.kt
git commit -m "test(editor): cover TagChipsExpandable rendering"
```

---

## Chunk 5: Back button + add-tag icon polish

### Task 8: Round back button capsule

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt:194-225` (the back-button `FloatingCapsule`)

**Background:** The back button currently sits in a 28dp-rounded `FloatingCapsule`. The user wants a fully circular capsule for the single-icon button so it visually reads as a coin.

- [ ] **Step 1: Change the wrapping FloatingCapsule shape**

Replace the back-button `FloatingCapsule` (`NoteEditorScreen.kt:194-225`):

```kotlin
FloatingCapsule(
    modifier = Modifier.shadow(
        elevation = 12.dp,
        shape = CircleShape,
        ambientColor = FloatingShadowAmbient,
        spotColor = FloatingShadowSpot,
    ),
    blurRadius = 24.dp,
    shadowElevation = 3.dp,
    hazeState = null,
    shape = CircleShape, // NEW — overrides default 28dp rounded
    contentPadding = PaddingValues(0.dp),
) {
    IconButton(
        onClick = onBack,
        modifier = Modifier
            .size(44.dp)
            .testTag(UiTestTags.EditorBack),
    ) {
        Icon(
            imageVector = Lucide.ArrowLeft,
            contentDescription = stringResource(R.string.editor_back),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}
```

> Verify `FloatingCapsule.kt` accepts a `shape: Shape` parameter. If not, add one with `RoundedCornerShape(28.dp)` as the default to stay backwards-compatible. Search the file: `~/.config/superpowers/worktrees/notes/wysiwyg-editor-and-sync/app/src/main/java/app/tessera/core/ui/components/FloatingCapsule.kt`.

- [ ] **Step 2: If FloatingCapsule has no shape parameter, add it**

Modify `FloatingCapsule.kt` to take `shape: Shape = RoundedCornerShape(28.dp)`, propagate to the `.shadow(shape = shape)`, `.clip(shape)`, and any haze container clip. Build and verify all existing call sites still compile (the default keeps them unchanged).

- [ ] **Step 3: Smoke test**

Run: `./scripts/run-debug-on-emulator.sh`. Open an existing note. The back button should be a perfect circle now.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt \
        app/src/main/java/app/tessera/core/ui/components/FloatingCapsule.kt
git commit -m "feat(editor): circular back-button capsule"
```

### Task 9: Smaller add-tag icon

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/components/TagChipsExpandable.kt:84-96` (the `+` Surface inside the row)

- [ ] **Step 1: Shrink the icon and surface**

In `TagChipsExpandable.kt`, change:

```kotlin
Surface(
    onClick = onAddTagClick,
    shape = CircleShape,
    color = MaterialTheme.colorScheme.secondaryContainer,
    modifier = Modifier.size(28.dp),       // was 34.dp
) {
    Icon(
        imageVector = Lucide.Plus,
        contentDescription = stringResource(R.string.editor_add_tag),
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(14.dp),   // was 18.dp
    )
}
```

- [ ] **Step 2: Smoke test**

`./scripts/run-debug-on-emulator.sh`. Expand the tag row — the `+` is visibly smaller, still tappable. Confirm chips and `+` align cleanly.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/components/TagChipsExpandable.kt
git commit -m "feat(editor): shrink in-editor add-tag icon"
```

---

## Final integration check

### Task 10: End-to-end manual pass

- [ ] **Step 1: Run the app**

Run: `./scripts/run-debug-on-emulator.sh`

- [ ] **Step 2: Walk the editor**

1. Create a new note.
2. Type two paragraphs. Select a single word. Tap **Format** in the toolbar → tap **Bold**. Only the selected word becomes bold.
3. Re-open **Format** → **Bold** is highlighted (active state) because the selection is bold.
4. Tap **Heading 2**. The paragraph containing the cursor becomes H2.
5. Open the tag row from the toolbar. Add a tag. Chips slide in from the right with a small bounce.
6. The back button is a circle. Tap it.
7. Navigate back to the note. Toggling a checkbox still persists (regression check on Plan-A pre-work).

If any step fails, fix in place, re-test, then proceed.

- [ ] **Step 3: Run full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all PASS.

- [ ] **Step 4: Run lint**

Run: `./gradlew :app:lintDebug`
Expected: no new errors. (Existing warnings unchanged.)

- [ ] **Step 5: Branch-finish handoff**

@superpowers:finishing-a-development-branch

---

## Risks / Open Questions

- **Selection capture timing (Task 1):** if `withPreservedSelection` reads `selectionStart`/`End` AFTER focus already collapsed, the fix won't work. Fallback path documented in Step 3.
- **Lucide icons (Task 3):** `Pilcrow`/`Heading3..6` availability depends on the bundled lucide-icons-compose version. Confirm and substitute if needed; the plan calls this out inline.
- **`AztecText.contains(format, start, end)` (Task 2):** the exact signature may differ. Confirm via `javap` on the AAR before relying on it. Substitute with `selectedStyles()` if needed.
- **`FloatingCapsule` shape param (Task 8):** if the current API doesn't expose `shape`, Step 2 adds it backwards-compatibly.

These are flagged so the implementing agent doesn't get stuck silently.
