# Swipe-to-reveal Actions + Restore/Undo Fix — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the swipe-to-dismiss note card with a swipe-to-reveal card whose actions fire only on an explicit button tap, and fix the broken restore/undo path so a deleted note can be brought back.

**Architecture:** A new `SwipeAction` value type describes each revealable button. `TesseraSwipeableNoteCard` is rewritten on Compose Foundation `AnchoredDraggable` (3 anchors: LeftRevealed ↔ Settled ↔ RightRevealed); the swipe only slides the card to expose fixed-width buttons, and tapping a button runs the action. `NotesListScreen` builds the per-view action lists and owns a single "open card id" so only one card is open at a time. Part B is debugging-first: reproduce the restore failure, add a failing test at the layer the reproduction implicates, then fix.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2025.01.00, Foundation 1.7.x `AnchoredDraggable`), Material3, Room, JUnit4 (androidTest / Compose UI test), JUnit5 (`SyncRepositoryImplTest`), Roborazzi.

**Skills:** @modern-jetpack-compose for all Compose work; @superpowers:test-driven-development for every task; @superpowers:systematic-debugging for Chunk 2.

**Pre-existing context the implementer must read first:**
- Spec: `docs/superpowers/specs/2026-07-03-swipe-actions-and-trash-restore-design.md`
- `app/src/main/java/app/tessera/ui/notes/components/TesseraSwipeableNoteCard.kt` (current dismiss impl — full rewrite target)
- `app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt` (call site ~669–711; dispatch handlers ~166–220, 369–376)
- `app/src/main/java/app/tessera/core/ui/UiTestTags.kt`
- Memory note "Roborazzi screenshot-test gotchas" (use `captureScreenRoboImage` for modals; `boundsInRoot` can't measure M3 touch targets).

---

## Chunk 1: Part A — Swipe-to-reveal card

**File Structure:**
- Create: `app/src/main/java/app/tessera/ui/notes/components/SwipeAction.kt` — the action value type (icon, label, colors, enabled, onClick).
- Modify: `app/src/main/java/app/tessera/ui/notes/components/TesseraSwipeableNoteCard.kt` — full rewrite to swipe-to-reveal.
- Modify: `app/src/main/java/app/tessera/core/ui/UiTestTags.kt` — add tags for revealed action buttons.
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt` — build `leftActions`/`rightActions` per view, hoist open-card state, collapse on scroll.
- Test: `app/src/androidTest/java/app/tessera/ui/notes/components/TesseraSwipeableNoteCardTest.kt` (new) and additions to `app/src/androidTest/java/app/tessera/ui/notes/NotesListScreenTest.kt`.

### Task 1: `SwipeAction` model + test tags

**Files:**
- Create: `app/src/main/java/app/tessera/ui/notes/components/SwipeAction.kt`
- Modify: `app/src/main/java/app/tessera/core/ui/UiTestTags.kt`

- [ ] **Step 1: Add test tags and remove the stale ones**

`UiTestTags.kt` already has two stale tags from a prior reveal attempt at lines
~50/52 — `revealPinAction(noteId)` and `revealDeleteAction(noteId)`. **Delete
both** and add a single generic helper next to `noteCard`:

```kotlin
fun swipeAction(noteId: String, key: String): String = "swipe_action_${key}_$noteId"
```

Then `grep -rn "revealPinAction\|revealDeleteAction" app/src` and remove any
remaining references (there should be none).

- [ ] **Step 2: Create the `SwipeAction` value type**

```kotlin
package app.tessera.ui.notes.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One revealable swipe button. Built at the call site from the current view so the card stays
 * dumb about views. [key] is stable per logical action ("archive", "pin", "delete", "restore",
 * "unarchive", "delete_forever") and is used for the test tag.
 */
data class SwipeAction(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val container: Color,
    val content: Color,
    val onClick: () -> Unit,
)
```

There is deliberately **no `enabled` field**: the call site simply omits an
action that shouldn't appear (e.g. Delete when `!canDeleteFromList()`), and an
empty action list makes that swipe direction inert. This keeps the component
free of disabled-state rendering.

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (no usages yet).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/components/SwipeAction.kt app/src/main/java/app/tessera/core/ui/UiTestTags.kt
git commit -m "feat(swipe): add SwipeAction model + action test tags"
```

### Task 2: Rewrite `TesseraSwipeableNoteCard` as swipe-to-reveal (failing test first)

**Files:**
- Test: `app/src/androidTest/java/app/tessera/ui/notes/components/TesseraSwipeableNoteCardTest.kt` (create)
- Modify: `app/src/main/java/app/tessera/ui/notes/components/TesseraSwipeableNoteCard.kt`

- [ ] **Step 1: Write the failing tests**

Create `TesseraSwipeableNoteCardTest.kt`. Cover the three guarantees from the spec:

```kotlin
package app.tessera.ui.notes.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.UiTestTags
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class TesseraSwipeableNoteCardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val noteId = "n1"

    @Test
    fun swipeAloneDoesNotFireAnyAction() {
        var fired = false
        setCard(onDeleteFired = { fired = true })
        rule.onNodeWithTag(UiTestTags.noteCard(noteId)).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertFalse("swipe must not mutate; only a button tap does", fired)
    }

    @Test
    fun swipeLeftRevealsDeleteButton_tapFires() {
        var fired = false
        setCard(onDeleteFired = { fired = true })
        rule.onNodeWithTag(UiTestTags.noteCard(noteId)).performTouchInput { swipeLeft() }
        rule.onNodeWithTag(UiTestTags.swipeAction(noteId, "delete")).performClick()
        assertEquals(true, fired)
    }

    @Test
    fun swipeRightRevealsArchiveButton_tapFires() {
        var fired = false
        setCard(onArchiveFired = { fired = true })
        rule.onNodeWithTag(UiTestTags.noteCard(noteId)).performTouchInput { swipeRight() }
        rule.onNodeWithTag(UiTestTags.swipeAction(noteId, "archive")).performClick()
        assertEquals(true, fired)
    }

    private fun setCard(
        onArchiveFired: () -> Unit = {},
        onDeleteFired: () -> Unit = {},
    ) {
        rule.setContent {
            MaterialTheme {
                Surface {
                    TesseraSwipeableNoteCard(
                        noteId = noteId,
                        title = "Title",
                        preview = "Preview",
                        dateLabel = "today",
                        tags = emptyList(),
                        isPinned = false,
                        isOpen = false,
                        onOpenChange = {},
                        onClick = {},
                        onLongClick = {},
                        leftActions = listOf(
                            SwipeAction("archive", Lucide.Archive, "Archive",
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.onSecondaryContainer, onArchiveFired),
                        ),
                        rightActions = listOf(
                            SwipeAction("delete", Lucide.Trash2, "Delete",
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.onErrorContainer, onDeleteFired),
                        ),
                        modifier = Modifier.width(360.dp),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "app.tessera.ui.notes.components.TesseraSwipeableNoteCardTest"` (these live under `app/src/androidTest`, same as `NotesListScreenTest`, and need an instrumented device/emulator — the `AnchoredDraggable` drag physics require it). Ensure an emulator is running.
Expected: FAIL — new `TesseraSwipeableNoteCard` signature does not exist yet.

- [ ] **Step 3: Rewrite the component**

Replace the entire body of `TesseraSwipeableNoteCard.kt`. Use @modern-jetpack-compose. Key points:
- New signature: `noteId: String`, `leftActions: List<SwipeAction>`, `rightActions: List<SwipeAction>`, `isOpen: Boolean`, `onOpenChange: (Boolean) -> Unit`, plus existing `title/preview/dateLabel/tags/isPinned/onClick/onLongClick/selected/avatars/modifier`. Remove `onArchive`, `onDelete`, `swipeToDeleteEnabled`, `startToEndIcon` (their intent now lives in the action lists built at the call site).
- Anchors via `AnchoredDraggable` (Foundation 1.7.x — verify the exact constructor against the resolved version):

```kotlin
enum class RevealAnchor { LeftRevealed, Settled, RightRevealed }
```

  Foundation 1.7.x's `AnchoredDraggableState` constructor requires
  `positionalThreshold`, `velocityThreshold`, and `snapAnimationSpec` (the
  no-arg overload arrived later), e.g.:

```kotlin
val density = LocalDensity.current
val state = remember {
    AnchoredDraggableState(
        initialValue = RevealAnchor.Settled,
        positionalThreshold = { distance -> distance * 0.5f },
        velocityThreshold = { with(density) { 125.dp.toPx() } },
        snapAnimationSpec = tween(),
        decayAnimationSpec = exponentialDecay(),
    )
}
```

  (Verify the exact parameter set against the resolved Foundation version; if
  the signature differs, adapt — the anchors/offset/`updateAnchors` calls below
  are stable.) Use a fixed per-button width as a named constant
  `private val SwipeButtonWidth = 72.dp` so the anchor math and the Roborazzi
  baseline agree; left/right row width = `SwipeButtonWidth * actions.size`.
  Convert to px and set anchors: `state.updateAnchors(DraggableAnchors { Settled at 0f; if (leftActions.isNotEmpty()) LeftRevealed at leftWidthPx; if (rightActions.isNotEmpty()) RightRevealed at -rightWidthPx })`.
  A side with no actions gets no anchor → that swipe direction is inert (spec
  requirement). A partial drag that doesn't reach an anchor settles back to
  `Settled`, so no button stays revealed and no action fires — this backs the
  "swipe alone never mutates" test.
- Layout: `Box` containing (a) the left action row aligned `CenterStart`, (b) the right action row aligned `CenterEnd`, (c) the foreground `TesseraNoteCard` offset by `state.requireOffset()` via `Modifier.offset { IntOffset(x, 0) }` and `Modifier.anchoredDraggable(state, Orientation.Horizontal)`.
- Each action button: an `IconButton`/clickable `Box` tinted with `action.container`/`action.content`, width `SwipeButtonWidth`, `Modifier.testTag(UiTestTags.swipeAction(noteId, action.key))`, `onClick = { action.onClick(); onOpenChange(false) }`. (No disabled state — the call site simply omits actions that don't apply.)
- Sync open state both ways, guarding against a feedback loop: inbound
  `LaunchedEffect(isOpen) { if (!isOpen && state.settledValue != RevealAnchor.Settled) state.animateTo(RevealAnchor.Settled) }`; outbound, observe settle changes and only report a real transition —
  `LaunchedEffect(Unit) { snapshotFlow { state.settledValue }.collect { val open = it != RevealAnchor.Settled; if (open != isOpen) onOpenChange(open) } }`.
- Tapping the card body while open collapses instead of navigating: in `TesseraNoteCard`'s `onClick`, if open → `onOpenChange(false)`, else `onClick()`.

- [ ] **Step 4: Run tests to verify they pass**

Run: same command as Step 2.
Expected: PASS (all three).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/components/TesseraSwipeableNoteCard.kt app/src/androidTest/java/app/tessera/ui/notes/components/TesseraSwipeableNoteCardTest.kt
git commit -m "feat(swipe): reveal action buttons instead of swipe-to-dismiss"
```

### Task 3: Wire per-view actions + single-open coordination in `NotesListScreen`

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt`
- Test: `app/src/androidTest/java/app/tessera/ui/notes/NotesListScreenTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `NotesListScreenTest.kt` a test that opening one card and then swiping another collapses the first (single-open). Minimal version — assert the delete action of card A disappears after card B is swiped open:

```kotlin
@Test
fun openingSecondCardCollapsesFirst() {
    composeRule.setContent {
        NotesListScreen(
            state = contentState(listOf(note("a"), note("b"))),
            snackbarHostState = SnackbarHostState(),
            onQueryChange = {}, onTagToggle = {}, onToggleViewMode = {}, onCreateNote = {},
        )
    }
    composeRule.onNodeWithTag(UiTestTags.noteCard("a")).performTouchInput { swipeLeft() }
    composeRule.onNodeWithTag(UiTestTags.swipeAction("a", "delete")).assertIsDisplayed()
    composeRule.onNodeWithTag(UiTestTags.noteCard("b")).performTouchInput { swipeLeft() }
    composeRule.onNodeWithTag(UiTestTags.swipeAction("a", "delete")).assertDoesNotExist()
}
```

- [ ] **Step 2: Run to verify it fails**

Run the `NotesListScreenTest` via `./gradlew :app:connectedDebugAndroidTest --tests "app.tessera.ui.notes.NotesListScreenTest"` (emulator required).
Expected: FAIL — call site still passes the old params / no open-state coordination.

**Important scope note:** the `LazyColumn` + `items(...)` block (~669–711) lives
inside a **private `NotesBody` composable** (`NotesListScreen.kt:600`), which is
called from **two** sites — the main list (~365) and the search-results path
(~435). The open-state and per-note action builder must be threaded through
`NotesBody`, not added to the outer `NotesListScreen` scope directly.

- [ ] **Step 3: Change `NotesBody` to own open-state + build actions**
- `NotesBody` already receives `onArchive: (Note) -> Unit` and
  `onDeleteNote: (Note) -> Unit` (real param names, `NotesListScreen.kt:604-605`),
  and — crucially — these lambdas are **already view-aware** at the call sites:
  line ~371-372 routes `onArchive` to trash→restore / archive→unarchive, and
  line ~376 routes `onDeleteNote` to trash→permanent-delete / else→soft-delete.
  So the action builder should reuse these callbacks directly rather than
  re-deriving the view branch (avoids double-gating). Additionally pass the pin
  handler `onTogglePin: (NoteId) -> Unit` (real screen param at line ~97, wired
  at ~457) so the right panel can offer Pin/Unpin. `isTrashView`/`isArchiveView`/
  `isSharedView` are derivable from `state` and are only needed to decide *which*
  buttons to show (not what they do).
- Inside `NotesBody`, above the `LazyColumn`:
  `var openNoteId by rememberSaveable { mutableStateOf<String?>(null) }` and
  `val listState = rememberLazyListState()` (attach to the `LazyColumn`), plus
  `LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) openNoteId = null }`.
- Build the action lists per note from the current view. Follow the spec's table
  and gating:
  - Pin shown only when `!isTrashView && !isArchiveView && !isSharedView`
    (matches line ~473). **Pin is a toggle** — label/icon derive from
    `note.isPinned` (`Pin`/"Pin" vs `PinOff`/"Unpin"); onClick = `onTogglePin(note.id)`.
  - Delete/permanent-delete shown only when `state.isTrashView || note.canDeleteFromList()`.
  - **Left action** (always one, onClick = `onArchive(note)` — already view-aware):
    icon/label by view — trash→`ArchiveRestore`/"Restore", archive→
    `ArchiveRestore`/"Unarchive", else→`Archive`/"Archive".
  - **Right actions** (onClick for the delete button = `onDeleteNote(note)` —
    already routes to permanent-delete in trash): trash→`[Trash2 / "Delete forever"]`;
    else→`[Pin/Unpin?]` + `[Trash2 / "Delete"?]` (omit empties per the gating above).
  - Use `secondaryContainer`/`onSecondaryContainer` (archive-like, incl. pin) and
    `errorContainer`/`onErrorContainer` (delete) colors.
- Pass to the card: `noteId = note.id.value`, `leftActions`, `rightActions`,
  `isOpen = openNoteId == note.id.value`,
  `onOpenChange = { open -> openNoteId = if (open) note.id.value else null }`.

- [ ] **Step 4: Run tests to verify pass**

Run the full `NotesListScreenTest`.
Expected: PASS, and the previously-existing tests still pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt app/src/androidTest/java/app/tessera/ui/notes/NotesListScreenTest.kt
git commit -m "feat(swipe): build per-view actions and single-open coordination"
```

### Task 4: Update callers + screenshot coverage

**Files:**
- Modify: any other caller of `TesseraSwipeableNoteCard` (grep first), e.g. `MainActivity`/preview/screenshot tests.
- Test: existing Roborazzi test for the card if present.

- [ ] **Step 1: Find remaining callers**

Run: `grep -rn "TesseraSwipeableNoteCard" app/src`
Fix every call to the new signature.

- [ ] **Step 2: Build the whole app**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Add/adjust a Roborazzi screenshot** of the left-revealed and right-revealed states (respect the memory-note gotchas: use `captureScreenRoboImage` if the row is modal-like; do not assert on `boundsInRoot`).

- [ ] **Step 4: Run screenshot verification**

Run: `./gradlew :app:verifyRoborazziDebug` (or the repo's screenshot task).
Expected: PASS (record new baselines if needed with the repo's record task, then commit baselines).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "test(swipe): screenshots for revealed states; update callers"
```

---

## Chunk 2: Part B — Fix restore / undo (debugging-first)

**Use @superpowers:systematic-debugging. Do NOT write a fix before the reproduction pins the layer.**

**File Structure (fix location depends on findings):**
- Likely test: `app/src/test/java/app/tessera/data/repository/SyncRepositoryImplTest.kt` and/or a `NoteDao` test under `app/src/androidTest/...`.
- Likely fix: `app/src/main/java/app/tessera/data/repository/SyncRepositoryImpl.kt` (`mergeRemote` / `mergeRemoteShared`) and/or `app/src/main/java/app/tessera/data/local/dao/NoteDao.kt`.

### Task 5: Reproduce and pin the root cause

- [ ] **Step 1: Reproduce on-device / emulator**

Delete a note, tap Snackbar "Undo" → observe. Then delete another, open Trash, Restore → observe. Do this once **offline** (sign out / airplane mode) and once **online**. Record for each: does the note reappear, and does it survive ~10s (a sync cycle)?

- [ ] **Step 2: Bisect layer from the result**
- If it fails **offline** → bug is local: inspect `NoteDao.restore` execution, `RestoreNoteUseCase`, `NotesListViewModel.restoreNote`, and that `observeActive` re-emits. Add temporary `Log.d` in `restoreNote`.
- If it fails **only online** → bug is sync replay: add `Log.d` in `SyncRepositoryImpl.observeRemote` (~116–133) and in the merge guards (`mergeRemote` ~244, `mergeRemoteShared` ~282) logging `id, local.updatedAt, remote.updatedAt, remote.deletedAt`. Watch whether a stale tombstone snapshot re-upserts the note after restore.

- [ ] **Step 3: Write down the confirmed root cause** in the debug notes before touching code (one sentence + the exact line).

- [ ] **Step 4: Commit the investigation notes** (if the repo keeps a debug log) or proceed — no code change yet.

### Task 6: Failing test at the implicated layer

- [ ] **Step 1: Write the failing test**

Pick the file matching the root cause. Example for the sync-replay case, added to `SyncRepositoryImplTest.kt` (JUnit5, fakes already exist):

```kotlin
@Test
fun restoredNoteSurvivesStaleRemoteTombstoneMerge() = runTest {
    val dao = SyncFakeNoteDao()
    // Note was deleted (T1) then restored locally (T2 > T1), pending push.
    // `entity(...)` is the existing helper in this test file (~lines 39-57) — reuse it.
    dao.upsert(entity(id = "n1", updatedAt = 2L, deletedAt = null, syncedAt = null))
    val remote = FakeRemoteNotesDataSource()
    // Remote still holds the tombstone from the delete at T1. The real fake exposes
    // `store(note)` (feeds fetchAll) — NOT setAll. Fill only the fields RemoteNote needs.
    remote.store(RemoteNote(id = "n1", title = "t", content = "c", updatedAt = 1L, deletedAt = 1L))
    val repo = create(noteDao = dao, remote = remote)

    repo.mergeRemote(uid, remote.fetchAll(uid.value))

    val after = dao.getById("n1")
    assertNull("restore must survive a stale remote tombstone", after?.deletedAt)
}
```

Confirm the `RemoteNote` field set against the real data class. **Note:** the
current private-path guard `local.updatedAt >= remote.updatedAt` (line ~244)
*already* protects a restore where `T2 > T1`, so this exact test may pass
unchanged — that is why Task 5's reproduction comes first. The real failure is
more likely the equal-timestamp / coarse-`Clock` case or the shared path
(`mergeRemoteShared`, strict `>` at ~282) or `observeRemote` concurrency; write
the failing test for whichever the reproduction actually implicates. If the
reproduction was local-only, write the DAO test instead: restore clears
`deletedAt`, the row leaves `observeDeleted` and appears in `observeActive`.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.data.repository.SyncRepositoryImplTest"`
Expected: FAIL, reproducing the bug.

- [ ] **Step 3: Commit the failing test** (optional, or fold into the fix commit).

### Task 7: Fix and verify

- [ ] **Step 1: Implement the minimal fix** at the confirmed line. Likely candidates:
- In `mergeRemote` / `mergeRemoteShared`: when the local row is newer OR has `syncedAt == null` (unpushed local restore), never let a remote row with `deletedAt != null` overwrite it — i.e. skip re-applying a remote tombstone over a pending local restore. Keep the change scoped to the guard; do not restructure the merge.
- Or, if local-only: fix the `restore` query / use-case / VM emission.

- [ ] **Step 2: Run the new test**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.data.repository.SyncRepositoryImplTest"`
Expected: PASS.

- [ ] **Step 3: Run the full unit + affected android tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, no regressions.
If the reproduction was local-only and the regression test landed under
`androidTest` (a DAO test), also run the affected connected test:
`./gradlew :app:connectedDebugAndroidTest --tests "<that DAO test>"`.

- [ ] **Step 4: Manual re-verification** — repeat the Task 5 reproduction; confirm both Undo and trash Restore now bring the note back and it survives a sync cycle. Use @superpowers:verification-before-completion (run it, observe, then claim).

- [ ] **Step 5: Commit**

Use the confirmed root-cause sentence from Task 5 Step 3 as the commit body so
it's captured even without a debug log:

```bash
git add -A
git commit -m "fix(restore): keep pending local restore from being clobbered by stale remote tombstone

Root cause: <one-line confirmed cause from Task 5 Step 3>"
```

---

## Definition of done
- Swipe never mutates data; only a tapped revealed button does. Left = Archive/Unarchive/Restore; right = Pin+Delete / Delete / Delete-forever per view; empty side inert; one card open at a time; scroll and body-tap collapse.
- Both Snackbar "Undo" and trash "Restore" reliably bring a deleted note back (private and shared), surviving a sync cycle, with a regression test guarding it.
- `./gradlew :app:assembleDebug` and the app's unit + Compose UI + Roborazzi test tasks all pass.
