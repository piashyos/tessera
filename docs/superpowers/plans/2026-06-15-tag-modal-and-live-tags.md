# Tag Modal & Live Tag State Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the bug where a newly added tag only appears on the notes list after an app restart, and polish the "Add tag" modal — swipe-to-dismiss, larger header, spacing between header and input, an explicit "Add tag" button, and a blurred backdrop.

**Architecture:** The live-tag bug is a Room observation gap: `NotesRepositoryImpl.observeNotes()` maps `noteDao.observeActive()`, but a note's tags live in the separate `note_tags`/`tags` tables. Room only re-emits `observeActive()` when the `notes` table changes — so a tag link change (writing `note_tags`) does not re-trigger the per-note `tagsForNote(...)` lookup, leaving the list stale until the next notes-table read (restart). Fix: combine the notes flow with an observable cross-ref flow so the mapping re-runs whenever tags change. The modal changes are confined to `TesseraPopup` (gesture) and `TagPickerPopup` (layout).

**Tech Stack:** Room 2.6.1 (+ room-testing, in-memory DB under Robolectric), Hilt, Kotlin coroutines/Flow, JUnit5, Turbine, Jetpack Compose, Haze.

**Conventions:** Same as the notes-list plan — JUnit5 + Turbine for logic, `createAndroidComposeRule<ComponentActivity>()` for UI, Robolectric in-memory Room for the repository test (the project already depends on `androidx-room-testing` and runs Robolectric in unit tests). Commit after every green step.

---

## Chunk 1: Live tag updates — repository observes tag changes

### Task 1.1: Add an observable cross-ref flow to TagDao

**Files:**
- Modify: `app/src/main/java/app/tessera/data/local/dao/TagDao.kt`

- [ ] **Step 1: Add a Flow query over the `note_tags` table**

```kotlin
@Query("SELECT * FROM note_tags")
fun observeAllCrossRefs(): kotlinx.coroutines.flow.Flow<List<NoteTagCrossRef>>
```

(Room registers `note_tags` as an observed table, so this flow re-emits on any link insert/delete.)

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (KSP regenerates the DAO impl).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/tessera/data/local/dao/TagDao.kt
git commit -m "feat(data): observe note-tag cross-refs for live tag updates"
```

### Task 1.2: Make `observeNotes()` re-map when tags change (failing test first)

**Files:**
- Test: `app/src/test/java/app/tessera/data/repository/NotesRepositoryTagSyncTest.kt` (new, Robolectric in-memory Room)
- Modify: `app/src/main/java/app/tessera/data/repository/NotesRepositoryImpl.kt`

- [ ] **Step 1: Write the failing test (in-memory Room; tag added shows up without a fresh subscription)**

```kotlin
// app/src/test/java/app/tessera/data/repository/NotesRepositoryTagSyncTest.kt
package app.tessera.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import app.tessera.core.util.Clock
import app.tessera.data.local.NotesDatabase
import app.tessera.data.mapper.NoteMapper
import app.tessera.domain.model.Tag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotesRepositoryTagSyncTest {
    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepositoryImpl

    private val fixedClock = object : Clock {
        private var t = 1_000L
        override fun nowMs(): Long = t++   // monotonically increasing so updatedAt changes
    }

    @BeforeEach
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NotesDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = NotesRepositoryImpl(db.noteDao(), db.tagDao(), NoteMapper(), fixedClock)
    }

    @AfterEach
    fun tearDown() = db.close()

    @Test
    fun `adding a tag re-emits the note with the new tag without resubscribing`() = runTest {
        val note = repo.createNote().copy(title = "Has title", content = "Body")
        repo.updateNote(note)

        repo.observeNotes().test {
            val initial = awaitItem().single()
            assertEquals(emptyList<Tag>(), initial.tags)

            repo.updateNote(initial.copy(tags = listOf(Tag("work"))))

            val updated = awaitItem().single()
            assertEquals(listOf("work"), updated.tags.map { it.name })
        }
    }
}
```

NOTE for the executor: verify the real `Clock` interface name/method (it is injected as `app.tessera.core.util.Clock`). If JUnit5 + Robolectric runner conflict in this module, fall back to `@RunWith(RobolectricTestRunner::class)` with JUnit4 `@Test`/`@Before`/`@After` (the project already has the vintage engine on the classpath). Use whichever the build resolves — the assertion logic is the point.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.data.repository.NotesRepositoryTagSyncTest"`
Expected: FAIL — second `awaitItem()` times out or returns the note still with empty tags (the stale-tag bug reproduced).

- [ ] **Step 3: Fix `observeNotes()` to combine notes + cross-refs**

In `NotesRepositoryImpl.kt`, change `observeNotes()`:

```kotlin
override fun observeNotes(): Flow<List<Note>> =
    combine(noteDao.observeActive(), tagDao.observeAllCrossRefs()) { notes, _ -> notes }
        .map { notes -> notes.map { note -> toDomain(note) }.filterNot(Note::isBlankDraft) }
```

Add imports `kotlinx.coroutines.flow.combine`. The cross-ref flow re-emits when `note_tags` changes, forcing `toDomain` (and thus `tagsForNote`) to re-run with fresh links.

- [ ] **Step 4: Also persist tags before the note row in updateNote (ordering safety)**

Still in `updateNote(...)`, move `replaceTags(persisted)` to run **before** `noteDao.upsert(...)` so that any emission triggered by the note-row change already reads the new links:

```kotlin
replaceTags(persisted)
noteDao.upsert(noteMapper.toEntity(persisted))
return persisted
```

(`replaceTags` uses `note.id`, not the persisted row, so reordering is safe.)

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.data.repository.NotesRepositoryTagSyncTest"`
Expected: PASS — the new tag appears on the second emission.

- [ ] **Step 6: Run the full data-layer suite for regressions**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.data.*"`
Expected: PASS (existing `NoteMapperTest`, `SyncRepositoryImplTest`).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/tessera/data/repository/NotesRepositoryImpl.kt \
        app/src/test/java/app/tessera/data/repository/NotesRepositoryTagSyncTest.kt
git commit -m "fix(tags): live-update notes list when tags change (no restart needed)"
```

### Task 1.3: Verify the editor's availableTags also refreshes live

**Files:**
- Inspect: `app/src/main/java/app/tessera/ui/editor/NoteEditorViewModel.kt:67-78`

- [ ] **Step 1: Confirm the editor uses `observeNotesUseCase().collect`**

The editor's `availableTags` come from `observeNotesUseCase()`, which delegates to `repo.observeNotes()`. With Task 1.2's fix, that flow now re-emits on tag changes — so no editor change is required. This step is verification only; no code change.

- [ ] **Step 2: (If a test exists) run NoteEditorViewModelTest**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.editor.*"`
Expected: PASS. No commit (no change).

---

## Chunk 2: Swipe-to-dismiss for the popup

### Task 2.1: Add downward-swipe dismissal to TesseraPopup

**Files:**
- Modify: `app/src/main/java/app/tessera/core/ui/components/TesseraPopup.kt`
- Test: `app/src/androidTest/java/app/tessera/ui/components/TesseraPopupSwipeTest.kt` (new)

- [ ] **Step 1: Write the failing UI test (swiping the card down dismisses it)**

```kotlin
// app/src/androidTest/java/app/tessera/ui/components/TesseraPopupSwipeTest.kt
package app.tessera.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import app.tessera.core.ui.UiTestTags
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TesseraPopupSwipeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun swipeDownDismissesPopup() {
        var dismissed = false
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                TesseraPopup(
                    visible = true,
                    onDismiss = { dismissed = true },
                    hazeState = remember { HazeState() },
                ) { Text("Content") }
            }
        }
        composeRule.onNodeWithTag(UiTestTags.PopupCard).performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        assertTrue(dismissed)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.components.TesseraPopupSwipeTest"` (or connected). Expected: FAIL — swipe does nothing.

- [ ] **Step 3: Add a vertical drag handler to the card**

In `TesseraPopup.kt`, track a drag offset and dismiss when dragged past a threshold. Add to the card `Surface` modifier chain:

```kotlin
val dragOffsetY = remember { androidx.compose.animation.core.Animatable(0f) }
val dismissThresholdPx = with(density) { 120.dp.toPx() }
val scope = rememberCoroutineScope()
// ...
modifier = Modifier
    .align(Alignment.BottomCenter)
    .animateEnterExit(...)
    .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
    .pointerInput(Unit) {
        detectVerticalDragGestures(
            onVerticalDrag = { _, delta ->
                scope.launch { dragOffsetY.snapTo((dragOffsetY.value + delta).coerceAtLeast(0f)) }
            },
            onDragEnd = {
                if (dragOffsetY.value > dismissThresholdPx) {
                    dismiss()
                } else {
                    scope.launch { dragOffsetY.animateTo(0f, spring()) }
                }
            },
        )
    }
    // ...rest unchanged
```

Reset `dragOffsetY` to 0 via `LaunchedEffect(visible) { if (visible) dragOffsetY.snapTo(0f) }`. Add imports: `pointerInput`, `detectVerticalDragGestures`, `offset`, `IntOffset`, `rememberCoroutineScope`, `launch`, `Animatable`, `roundToInt`, `LaunchedEffect`.

- [ ] **Step 4: Run the test to verify it passes**

Expected: PASS — swipe down sets `dismissed = true`.

- [ ] **Step 5: Verify existing popup behavior (tap scrim, back button) still dismisses**

Run any existing popup/tag-picker tests: `./gradlew :app:testDebugUnitTest --tests "*TagPicker*" --tests "*Popup*"`. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/components/TesseraPopup.kt \
        app/src/androidTest/java/app/tessera/ui/components/TesseraPopupSwipeTest.kt
git commit -m "feat(ui): swipe-down to dismiss TesseraPopup"
```

---

## Chunk 3: Add-tag modal polish (header, spacing, explicit add button)

### Task 3.1: Add vertical spacing to the popup content column

**Files:**
- Modify: `app/src/main/java/app/tessera/core/ui/components/TesseraPopup.kt`

- [ ] **Step 1: Add a `verticalArrangement` to the content Column**

In the card's inner `Column` (lines ~114-120), add `verticalArrangement = Arrangement.spacedBy(16.dp)` so the header, input, and button are spaced apart. Add the `Arrangement` import.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/components/TesseraPopup.kt
git commit -m "style(ui): space out TesseraPopup content vertically"
```

### Task 3.2: Bigger header + explicit "Add tag" button in TagPickerPopup

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/components/TagPickerPopup.kt`
- Modify: `app/src/main/java/app/tessera/core/ui/UiTestTags.kt` (add `AddTagConfirm`)
- Test: `app/src/androidTest/java/app/tessera/ui/editor/TagPickerPopupTest.kt` (new)

- [ ] **Step 1: Add the test tag** — `const val AddTagConfirm = "add_tag_confirm"`.

- [ ] **Step 2: Write the failing UI test (typing + tapping the button adds the tag)**

```kotlin
// app/src/androidTest/java/app/tessera/ui/editor/TagPickerPopupTest.kt
package app.tessera.ui.editor.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.tessera.core.ui.UiTestTags
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TagPickerPopupTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun confirmButtonAddsTypedTag() {
        var added: String? = null
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                TagPickerPopup(
                    visible = true,
                    availableTags = emptyList(),
                    onAddTag = { added = it },
                    onDismiss = {},
                    hazeState = remember { HazeState() },
                )
            }
        }
        composeRule.onNodeWithTag(UiTestTags.ReusableTextField).performTextInput("urgent")
        composeRule.onNodeWithTag(UiTestTags.AddTagConfirm).performClick()
        assertEquals("urgent", added)
    }
}
```

(Confirm the `TesseraTextField` exposes `UiTestTags.ReusableTextField`; if not, add that tag to the field inside the popup.)

- [ ] **Step 3: Run it to verify it fails**

Expected: FAIL — no `AddTagConfirm` button.

- [ ] **Step 4: Update TagPickerPopup layout**

- Header: change `style = MaterialTheme.typography.titleMedium` → `style = MaterialTheme.typography.headlineSmall` with `fontWeight = FontWeight.SemiBold`.
- After the input `FloatingCapsule`, add a full-width "Add tag" button bound to `submit`:

```kotlin
Button(
    onClick = submit,
    enabled = newTag.isNotBlank(),
    modifier = Modifier.fillMaxWidth().testTag(UiTestTags.AddTagConfirm),
) {
    Text(stringResource(R.string.editor_add_tag))
}
```

(Spacing between header → input → button now comes from Task 3.1's `verticalArrangement`.) Add imports `Button`, `FontWeight`, `testTag`.

- [ ] **Step 5: Run the test to verify it passes**

Expected: PASS.

- [ ] **Step 6: Run the editor test suite for regressions**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.editor.*"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/components/TagPickerPopup.kt \
        app/src/main/java/app/tessera/core/ui/UiTestTags.kt \
        app/src/androidTest/java/app/tessera/ui/editor/TagPickerPopupTest.kt
git commit -m "feat(editor): larger header and explicit Add-tag button in tag modal"
```

---

## Chunk 4: Confirm backdrop blur

### Task 4.1: Verify (and if needed strengthen) the popup backdrop blur

**Files:**
- Inspect/Modify: `app/src/main/java/app/tessera/core/ui/components/TesseraPopup.kt:73-86`

- [ ] **Step 1: Confirm the scrim already applies `hazeEffect`**

The scrim `Box` calls `.hazeEffect(state = hazeState) { backgroundColor = frostColor }` then a `Color.Black.copy(alpha = 0.25f)` overlay. The blur depends on the popup being given a `hazeState` whose `hazeSource` wraps the editor content — confirm `NoteEditorScreen` passes `popupHazeState` (it does, line ~299) and the content `Surface` has `.hazeSource(popupHazeState)` (it does, line ~106).

- [ ] **Step 2: If the blur is too subtle, add an explicit `blurRadius`**

Inside the `hazeEffect` block add `blurRadius = 24.dp` (matching the editor capsules) and optionally `tints = listOf(HazeTint(frostColor.copy(alpha = 0.4f)))`. This is an aesthetic tweak — verify visually on an emulator.

- [ ] **Step 3: Manual verification + commit (only if changed)**

Open the editor, tap +, confirm the content behind the modal is blurred. If you changed the radius/tint:
```bash
git add app/src/main/java/app/tessera/core/ui/components/TesseraPopup.kt
git commit -m "style(ui): strengthen tag-modal backdrop blur"
```

---

## Final verification

- [ ] **Repository fix is the core deliverable** — re-run `./gradlew :app:testDebugUnitTest --tests "app.tessera.data.repository.NotesRepositoryTagSyncTest"` and paste the passing output.
- [ ] **Full suite:** `./gradlew :app:testDebugUnitTest`
- [ ] **Manual end-to-end:** open a note, add a brand-new tag via the modal, press back to the list — the tag filter chip appears immediately (no restart). Swipe the modal down to dismiss. Confirm bigger header, spacing, the Add-tag button, and the blurred backdrop.
- [ ] Report with superpowers:verification-before-completion — real command output, not assertions of success.
