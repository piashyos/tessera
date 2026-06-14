# Reusable Input + Popup Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two reusable Compose components — `TesseraTextField` (uniform input) and `TesseraPopup` (custom keyboard-aware, blurred+dimmed overlay) — and adopt them in the search bar and tag picker.

**Architecture:** Both components live in `core/ui/components`. The popup is an in-composition overlay (not a system `Dialog`) so it can blur the screen via the `dev.chrisbanes.haze` library (v1.2.2). The editor wraps its whole visible content in a dedicated popup `HazeState` source; the popup backdrop samples it. Domain data stays in the ViewModel; only transient popup-visibility is local UI state.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, dev.chrisbanes.haze 1.2.2.

**Spec:** [`docs/superpowers/specs/2026-06-14-reusable-input-and-popup-design.md`](../specs/2026-06-14-reusable-input-and-popup-design.md)

---

## File Structure

- Create: `app/src/main/java/app/tessera/core/ui/components/TesseraTextField.kt` — reusable borderless input.
- Create: `app/src/main/java/app/tessera/core/ui/components/TesseraPopup.kt` — reusable overlay popup.
- Create: `app/src/main/java/app/tessera/ui/editor/components/TagPickerPopup.kt` — tag-picker content hosted in `TesseraPopup`.
- Modify: `app/src/main/java/app/tessera/ui/notes/components/BottomSearchBar.kt` — use `TesseraTextField`.
- Modify: `app/src/main/java/app/tessera/ui/editor/components/TagChips.kt` — drop `ModalBottomSheet`; chips + "+" button emitting `onAddTagClick`.
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt` — wrap content in popup `hazeSource`; host `TagPickerPopup`; own `tagPickerVisible`.
- Modify: `app/src/main/java/app/tessera/core/ui/UiTestTags.kt` — add `PopupScrim`, `PopupCard`.
- Test: `app/src/androidTest/java/app/tessera/core/ui/components/TesseraTextFieldTest.kt`
- Test: `app/src/androidTest/java/app/tessera/core/ui/components/TesseraPopupTest.kt`

---

## Chunk 1: Reusable components + adoption

### Task 1: `TesseraTextField`

**Files:**
- Create: `app/src/main/java/app/tessera/core/ui/components/TesseraTextField.kt`
- Modify: `app/src/main/java/app/tessera/core/ui/UiTestTags.kt`
- Test: `app/src/androidTest/java/app/tessera/core/ui/components/TesseraTextFieldTest.kt`

- [ ] **Step 1: Add test tag**

In `UiTestTags.kt` add inside the object:
```kotlin
const val ReusableTextField = "reusable_text_field"
```

- [ ] **Step 2: Write the failing instrumented test**

`TesseraTextFieldTest.kt`:
```kotlin
package app.tessera.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TesseraTextFieldTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsPlaceholderWhenEmpty() {
        composeRule.setContent {
            TesseraTheme(dynamicColor = false) {
                TesseraTextField(value = "", onValueChange = {}, placeholder = "Search notes")
            }
        }
        composeRule.onNodeWithText("Search notes").assertIsDisplayed()
    }

    @Test
    fun typingInvokesOnValueChange() {
        var captured = ""
        composeRule.setContent {
            TesseraTheme(dynamicColor = false) {
                var text by remember { mutableStateOf("") }
                TesseraTextField(
                    value = text,
                    onValueChange = { text = it; captured = it },
                    placeholder = "hint",
                    modifier = Modifier.testTag(UiTestTags.ReusableTextField),
                )
            }
        }
        composeRule.onNodeWithTag(UiTestTags.ReusableTextField).performTextInput("hello")
        assertEquals("hello", captured)
    }

    @Test
    fun imeActionInvokesCallback() {
        var fired = false
        composeRule.setContent {
            TesseraTheme(dynamicColor = false) {
                TesseraTextField(
                    value = "x",
                    onValueChange = {},
                    placeholder = "hint",
                    imeAction = ImeAction.Done,
                    onImeAction = { fired = true },
                    modifier = Modifier.testTag(UiTestTags.ReusableTextField),
                )
            }
        }
        composeRule.onNodeWithTag(UiTestTags.ReusableTextField).performImeAction()
        assertTrue(fired)
    }
}
```

- [ ] **Step 3: Run test, verify it fails**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL (unresolved `TesseraTextField`).

- [ ] **Step 4: Implement `TesseraTextField.kt`**

```kotlin
package app.tessera.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

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
) {
    val action: KeyboardActions = KeyboardActions(
        onDone = { onImeAction() },
        onSearch = { onImeAction() },
        onGo = { onImeAction() },
        onSend = { onImeAction() },
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = action,
        decorationBox = { innerTextField ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}
```
(Remove the unused `padding` import if the IDE flags it.)

- [ ] **Step 5: Run tests, verify pass**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "app.tessera.core.ui.components.TesseraTextFieldTest"`
Expected: 3 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/components/TesseraTextField.kt \
        app/src/main/java/app/tessera/core/ui/UiTestTags.kt \
        app/src/androidTest/java/app/tessera/core/ui/components/TesseraTextFieldTest.kt
git commit -m "feat(ui): reusable TesseraTextField"
```

### Task 2: `TesseraPopup`

**Files:**
- Create: `app/src/main/java/app/tessera/core/ui/components/TesseraPopup.kt`
- Modify: `app/src/main/java/app/tessera/core/ui/UiTestTags.kt`
- Test: `app/src/androidTest/java/app/tessera/core/ui/components/TesseraPopupTest.kt`

- [ ] **Step 1: Add test tags**

In `UiTestTags.kt`:
```kotlin
const val PopupScrim = "popup_scrim"
const val PopupCard = "popup_card"
```

- [ ] **Step 2: Write the failing instrumented test**

`TesseraPopupTest.kt`:
```kotlin
package app.tessera.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.theme.TesseraTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TesseraPopupTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hiddenWhenNotVisible() {
        composeRule.setContent {
            TesseraTheme(dynamicColor = false) {
                TesseraPopup(visible = false, onDismiss = {}, hazeState = HazeState()) {
                    Text("popup body")
                }
            }
        }
        composeRule.onNodeWithText("popup body").assertDoesNotExist()
    }

    @Test
    fun showsContentWhenVisible() {
        composeRule.setContent {
            TesseraTheme(dynamicColor = false) {
                TesseraPopup(visible = true, onDismiss = {}, hazeState = HazeState()) {
                    Text("popup body")
                }
            }
        }
        composeRule.onNodeWithText("popup body").assertIsDisplayed()
    }

    @Test
    fun scrimTapInvokesDismiss() {
        var dismissed = false
        composeRule.setContent {
            TesseraTheme(dynamicColor = false) {
                TesseraPopup(visible = true, onDismiss = { dismissed = true }, hazeState = HazeState()) {
                    Text("popup body")
                }
            }
        }
        composeRule.onNodeWithTag(UiTestTags.PopupScrim).performClick()
        assertTrue(dismissed)
    }
}
```

- [ ] **Step 3: Run test, verify it fails**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL (unresolved `TesseraPopup`).

- [ ] **Step 4: Implement `TesseraPopup.kt`**

```kotlin
package app.tessera.core.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.UiTestTags
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

@Composable
fun TesseraPopup(
    visible: Boolean,
    onDismiss: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val maxCardHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    val dismiss: () -> Unit = {
        focusManager.clearFocus(force = true)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        BackHandler(enabled = visible, onBack = dismiss)

        Box(modifier = Modifier.fillMaxSize()) {
            // Blurred + dimmed backdrop; tap to dismiss
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeEffect(state = hazeState)
                    .background(Color.Black.copy(alpha = 0.25f))
                    .testTag(UiTestTags.PopupScrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = dismiss,
                    ),
            )

            // Bottom-anchored card, 12dp side margins, 12dp gap above keyboard/bottom
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(spring()) { it },
                exit = slideOutVertically(spring()) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .testTag(UiTestTags.PopupCard)
                        // card taps must not reach the scrim
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = maxCardHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        content = content,
                    )
                }
            }
        }
    }
}
```

The card wraps its content but caps at 60% of screen height (`maxCardHeight`) and scrolls internally when a long tag list + keyboard would otherwise overflow.

- [ ] **Step 5: Run tests, verify pass**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "app.tessera.core.ui.components.TesseraPopupTest"`
Expected: 3 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/components/TesseraPopup.kt \
        app/src/main/java/app/tessera/core/ui/UiTestTags.kt \
        app/src/androidTest/java/app/tessera/core/ui/components/TesseraPopupTest.kt
git commit -m "feat(ui): reusable TesseraPopup overlay"
```

### Task 3: Adopt `TesseraTextField` in `BottomSearchBar`

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/components/BottomSearchBar.kt`

- [ ] **Step 1: Replace the inline `BasicTextField`**

In `BottomSearchBar.kt`, replace the `BasicTextField(...)` (with its `decorationBox`) with:
```kotlin
TesseraTextField(
    value = query,
    onValueChange = onQueryChange,
    placeholder = stringResource(R.string.search_hint),
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 36.dp)
        .onFocusChanged { onFocusChanged(it.isFocused) }
        .testTag(UiTestTags.SearchField),
    imeAction = ImeAction.Search,
)
```
Remove now-unused imports (`BasicTextField`, `KeyboardActions`/`KeyboardOptions` if unused, `SolidColor`, `Box`/`Alignment` if unused) and add `import app.tessera.core.ui.components.TesseraTextField` and `import androidx.compose.ui.text.input.ImeAction`. Keep `SearchField` test tag and `onFocusChanged` exactly as above.

- [ ] **Step 2: Verify build + existing list test still passes**

Run: `./gradlew :app:assembleDebug` then `./gradlew :app:connectedDebugAndroidTest --tests "app.tessera.ui.notes.NotesListScreenTest"`
Expected: BUILD SUCCESSFUL; list tests PASS (search field tag intact).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/components/BottomSearchBar.kt
git commit -m "refactor(ui): search bar uses TesseraTextField"
```

### Task 4: Tag picker → `TesseraPopup`

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/components/TagChips.kt`
- Create: `app/src/main/java/app/tessera/ui/editor/components/TagPickerPopup.kt`
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt`

- [ ] **Step 1: Simplify `TagChips` to chips + "+" trigger**

Change `TagChips` signature to:
```kotlin
fun TagChips(
    tags: List<String>,
    onRemoveTag: (String) -> Unit,
    onAddTagClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```
Keep the scrollable chip row + the circular "+" `Surface`, but the "+" now calls `onAddTagClick()`. Delete the internal `showSheet` state, the `ModalBottomSheet` block, the `newTagText` state, and `availableTags`/`onAddTag` params (they move to `TagPickerPopup`). Remove now-unused imports (`ModalBottomSheet`, `OutlinedTextField`, `rememberModalBottomSheetState`, `AssistChip`, `KeyboardActions/Options`, etc.).

**Update ALL three `TagChips` previews** in the same file (they currently pass `availableTags`/`onAddTag` and will no longer compile): remove `availableTags = ...` and `onAddTag = {}`, and add `onAddTagClick = {}`. Keep `tags`/`onRemoveTag`. Example:
```kotlin
TagChips(
    tags = listOf("work", "kotlin"),
    onRemoveTag = {},
    onAddTagClick = {},
)
```

- [ ] **Step 2: Create `TagPickerPopup.kt`**

```kotlin
package app.tessera.ui.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.components.TesseraPopup
import app.tessera.core.ui.components.TesseraTextField
import dev.chrisbanes.haze.HazeState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPickerPopup(
    visible: Boolean,
    availableTags: List<String>,
    onAddTag: (String) -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    var newTag by remember(visible) { mutableStateOf("") }
    val submit: () -> Unit = {
        val trimmed = newTag.trim()
        if (trimmed.isNotBlank()) {
            onAddTag(trimmed)
            newTag = ""
        }
    }

    TesseraPopup(
        visible = visible,
        onDismiss = onDismiss,
        hazeState = hazeState,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.editor_add_tag),
            style = MaterialTheme.typography.titleMedium,
        )
        TesseraTextField(
            value = newTag,
            onValueChange = { newTag = it },
            placeholder = stringResource(R.string.editor_add_tag),
            modifier = Modifier.fillMaxWidth(),
            imeAction = ImeAction.Done,
            onImeAction = submit,
        )
        if (availableTags.isNotEmpty()) {
            Text(
                text = "Existing tags",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                availableTags.forEach { tag ->
                    AssistChip(onClick = { onAddTag(tag) }, label = { Text("#$tag") })
                }
            }
        }
    }
}
```
(Add light/dark previews following the existing component style.)

- [ ] **Step 3: Wire into `NoteEditorScreen`**

- Add a popup `HazeState`: `val popupHazeState = remember { HazeState() }` (alongside the existing `hazeState`).
- Wrap ALL current children of the root `Box` (the content `Surface`, `EdgeFadeScrim`, top capsule `Column`, bottom capsule `Column`) inside an inner `Box(modifier = Modifier.fillMaxSize().hazeSource(popupHazeState))`. Keep the existing capsule `hazeState`/`hazeSource` untouched inside.
- Add local `var tagPickerVisible by remember { mutableStateOf(false) }`.
- Update the `TagChips(...)` call to the new signature: pass `onRemoveTag = onRemoveTag` and `onAddTagClick = { tagPickerVisible = true }` (drop `availableTags`/`onAddTag`).
- After the inner wrapper Box (as the LAST child of the root `Box`, so it draws on top) add:
```kotlin
TagPickerPopup(
    visible = tagPickerVisible,
    availableTags = state.availableTags,
    onAddTag = { onAddTag(it); tagPickerVisible = false },
    onDismiss = { tagPickerVisible = false },
    hazeState = popupHazeState,
)
```
Imports: `dev.chrisbanes.haze.hazeSource` is already imported; add `androidx.compose.runtime.{getValue,setValue,mutableStateOf}` and `app.tessera.ui.editor.components.TagPickerPopup`.

- [ ] **Step 4: Build + run editor tests**

Run: `./gradlew :app:assembleDebug` then `./gradlew :app:connectedDebugAndroidTest --tests "app.tessera.ui.editor.NoteEditorScreenTest"`
Expected: BUILD SUCCESSFUL; editor screen tests PASS (tag chips + "+" still present; existing tags-related assertions unaffected).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/components/TagChips.kt \
        app/src/main/java/app/tessera/ui/editor/components/TagPickerPopup.kt \
        app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt
git commit -m "feat(editor): tag picker uses TesseraPopup + TesseraTextField"
```

### Task 5: Full verification

- [ ] **Step 1: Run the whole suite**

Run:
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:connectedDebugAndroidTest`
- `./gradlew :app:assembleDebug`
Expected: all PASS / BUILD SUCCESSFUL.

- [ ] **Step 2: Manual emulator check**

Install, open a note, tap the "+" tag button:
- popup rises above the keyboard with ~12dp margins all around;
- background is blurred + slightly dimmed;
- typing a tag + Done adds it; tapping an existing-tag chip adds it;
- back press and scrim tap dismiss the popup and hide the keyboard.
Also confirm the search bar input looks/behaves unchanged.

- [ ] **Step 3: Commit any doc/progress updates**

```bash
git add -A
git commit -m "test(ui): verify reusable input + popup"
```

**End of plan.** Search and tag input share one component; the tag picker is a custom blurred popup above the keyboard.
