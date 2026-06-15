# Notes-List Visual Polish Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish the notes-list and editor chrome — fix the FAB shadow/inner-circle artifacts, redesign note cards (drop the avatar, add dates, icon swipe actions), add a list/tile view toggle, group notes by date, build a collapsing blurred header, relocate tag filters to a dedicated expanding bar, add a search clear button, and split the editor header into two capsules.

**Architecture:** Pure UI + view-state work. No data-layer changes. Logic that can be unit-tested (date grouping, view-mode toggle, search-clear visibility) is pushed into pure functions / `NotesListViewModel` state and tested with JUnit5 + Turbine. Purely visual tweaks (shadow clipping, blur, capsule shape) are verified with Roborazzi screenshot tests and Compose-preview inspection.

**Tech Stack:** Jetpack Compose (BOM 2024.10.00), Material3, Haze 1.2.2 (glass blur), Lucide icons 1.1.0, Hilt, JUnit5/Jupiter, Turbine, Roborazzi, Compose UI test.

**Cross-plan note:** Task 6 (collapsing header) renders a *title string*. Plan 3 (Folders) will later feed the selected folder name into this header. For now the header shows the active folder name placeholder = the app title ("Tessera") / "All Notes". Keep the title a parameter so Plan 3 only has to pass a value.

**Conventions to follow (observed in the codebase):**
- Screens are stateless composables that receive `state` + lambdas; ViewModels expose a `StateFlow`. Mirror existing `NotesListScreen` signature style.
- Test tags live in `app/src/main/java/app/tessera/core/ui/UiTestTags.kt`. Add new ones there, never inline string literals.
- New user-facing strings go in `app/src/main/res/values/strings.xml` referenced via `R.string.*`.
- Unit tests: JUnit5 (`org.junit.jupiter.api.Test`, `Assertions.*`), `StandardTestDispatcher`, Turbine `.test {}`. Instrumented/UI tests: `createAndroidComposeRule<ComponentActivity>()`.
- Run unit tests with: `./gradlew :app:testDebugUnitTest`. Run a single class with `--tests`.
- Commit after every green step. DRY, YAGNI, TDD.

---

## Chunk 1: FAB artifact + shadow-clipping fix (spec item 1)

The "new note" FAB is `GlassFab` → `FloatingCapsule` with `CircleShape`. Two defects:
- An inner shadow + faint white circle: caused by `FloatingCapsule`'s highlight/border layers (`capsuleHighlightBrush`, the inner border `Box`) being applied to a small circle.
- The drop shadow clips during the slide-out animation because the shadow `Modifier.shadow` is applied *inside* the `AnimatedVisibility` whose `expandHorizontally`/`shrinkHorizontally` clips its bounds.

### Task 1.1: Add a `showGlassDecor` flag to FloatingCapsule (kill inner shadow + white circle for the FAB)

**Files:**
- Modify: `app/src/main/java/app/tessera/core/ui/components/FloatingCapsule.kt`
- Modify: `app/src/main/java/app/tessera/core/ui/components/GlassFab.kt`
- Test: `app/src/test/java/app/tessera/core/ui/components/GlassFabRenderTest.kt` (new, Roborazzi)

- [ ] **Step 1: Write the failing screenshot test**

```kotlin
// app/src/test/java/app/tessera/core/ui/components/GlassFabRenderTest.kt
package app.tessera.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tessera.core.ui.theme.TesseraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class GlassFabRenderTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fabHasNoInnerCircleArtifact() {
        composeRule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.padding(24.dp)) {
                    GlassFab(onClick = {}) { Text("+") }
                }
            }
        }
        composeRule.onRoot().captureRoboImage()
    }
}
```

- [ ] **Step 2: Run it to confirm it fails (no baseline image / current artifact captured)**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.components.GlassFabRenderTest"`
Expected: Generates a baseline screenshot under `app/src/test/screenshots/`. Visually inspect it — you should currently see the inner white ring + inner shadow. This is the "before".

- [ ] **Step 3: Add `showGlassDecor` parameter to FloatingCapsule**

In `FloatingCapsule.kt`, add a parameter and gate the highlight + inner-border layers:

```kotlin
@Composable
fun FloatingCapsule(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    backgroundColor: Color = capsuleTintColor(),
    borderColor: Color = CapsuleBorder,
    blurRadius: Dp = 14.dp,
    shadowElevation: Dp = 4.dp,
    hazeState: HazeState? = null,
    showGlassDecor: Boolean = true,   // NEW
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    content: @Composable BoxScope.() -> Unit,
) {
```

Wrap the highlight `Box` and the inner-border `Box` in `if (showGlassDecor) { ... }` (lines ~107–123). Leave the haze layer and outer `Surface` shape untouched.

- [ ] **Step 4: Set `showGlassDecor = false` in GlassFab**

In `GlassFab.kt`, pass `showGlassDecor = false` to the `FloatingCapsule(...)` call. Keep `blurRadius`/`shadowElevation` as-is.

- [ ] **Step 5: Re-run the screenshot test and verify the artifact is gone**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.components.GlassFabRenderTest" -Proborazzi.record=true`
Expected: New baseline. Inspect `app/src/test/screenshots/...GlassFabRenderTest...png` — the inner ring and inner shadow are gone; only the outer drop shadow remains.

- [ ] **Step 6: Verify other FloatingCapsule callers are unchanged**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS. The default `showGlassDecor = true` preserves every existing capsule (header, search bar, editor capsules).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/components/FloatingCapsule.kt \
        app/src/main/java/app/tessera/core/ui/components/GlassFab.kt \
        app/src/test/java/app/tessera/core/ui/components/GlassFabRenderTest.kt \
        app/src/test/screenshots
git commit -m "fix(ui): remove inner shadow and white ring artifact from GlassFab"
```

### Task 1.2: Stop the FAB shadow from clipping during the slide animation

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt:220-247`

The shadow clips because `Modifier.shadow` is applied to the `GlassFab` *inside* `AnimatedVisibility` (whose `expand/shrinkHorizontally` clips children to the animated width). Fix: drive FAB appearance with the existing `fabAlpha`/scale via `graphicsLayer` only, and remove the `expand/shrinkHorizontally` clipping. Give the FAB's parent enough padding so the elevation shadow has room.

- [ ] **Step 1: Replace the AnimatedVisibility wrapper with an always-laid-out, alpha/scale-animated FAB**

In `NotesListScreen.kt`, replace the `AnimatedVisibility(...) { GlassFab(...) }` block (lines ~220-247) with:

```kotlin
val fabScale by animateFloatAsState(
    targetValue = if (isSearchFocused) 0.6f else 1f,
    animationSpec = spring(dampingRatio = 0.92f, stiffness = 520f),
    label = "fabScale",
)
GlassFab(
    onClick = onCreateNote,
    size = 48.dp,
    contentDescription = stringResource(R.string.create_note),
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
        .testTag(UiTestTags.CreateNoteFab),
) {
    Text(
        text = "+",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
    )
}
```

Key changes: removed `AnimatedVisibility` (which clipped the shadow), added `clip = false` to `shadow`, and animate via `graphicsLayer` (alpha already 0 when focused, so it disappears without bounds clipping). Remove now-unused `AnimatedVisibility`, `expandHorizontally`, `shrinkHorizontally` imports if no longer referenced.

- [ ] **Step 2: Confirm `clip = false` is a valid arg for `Modifier.shadow`**

The Compose `Modifier.shadow(elevation, shape, clip, ambientColor, spotColor)` overload exists in this BOM. Confirm compile: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manually verify (preview/emulator) that the shadow no longer clips when search focuses/unfocuses**

There is no automated test for animation-frame clipping; verify on an emulator: focus the search field (FAB shrinks/fades, shadow stays round, no rectangular cut), unfocus (FAB returns). State explicitly in the PR that this was verified manually.

- [ ] **Step 4: Run the existing UI test that taps the FAB to confirm no regression**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "app.tessera.ui.notes.NotesListScreenTest.fabTapInvokesCreate"` (or the Robolectric equivalent if that test runs under unit tests).
Expected: PASS — the FAB is still present and clickable.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt
git commit -m "fix(ui): prevent new-note FAB shadow clipping during search animation"
```

---

## Chunk 2: Note-card redesign — drop avatar, add date, icon swipe actions (spec item 2, part A)

### Task 2.1: Add a date formatter and surface a display date on the card

**Files:**
- Create: `app/src/main/java/app/tessera/ui/notes/NoteDateFormat.kt`
- Test: `app/src/test/java/app/tessera/ui/notes/NoteDateFormatTest.kt` (new)

Decision: cards show **updatedAt** (the note's last-touched time), formatted relative ("Today", "Yesterday") or as a short date.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/app/tessera/ui/notes/NoteDateFormatTest.kt
package app.tessera.ui.notes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class NoteDateFormatTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 6, 15)

    private fun ms(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `today renders as Today`() {
        assertEquals("Today", formatNoteDate(ms(today), today, zone))
    }

    @Test
    fun `yesterday renders as Yesterday`() {
        assertEquals("Yesterday", formatNoteDate(ms(today.minusDays(1)), today, zone))
    }

    @Test
    fun `older same-year renders as day and month`() {
        assertEquals("3 Jun", formatNoteDate(ms(LocalDate.of(2026, 6, 3)), today, zone))
    }

    @Test
    fun `previous year includes the year`() {
        assertEquals("3 Jun 2025", formatNoteDate(ms(LocalDate.of(2025, 6, 3)), today, zone))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.notes.NoteDateFormatTest"`
Expected: FAIL — `formatNoteDate` unresolved.

- [ ] **Step 3: Implement the formatter**

```kotlin
// app/src/main/java/app/tessera/ui/notes/NoteDateFormat.kt
package app.tessera.ui.notes

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun formatNoteDate(
    epochMs: Long,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(dayMonth)
        else -> date.format(dayMonthYear)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.notes.NoteDateFormatTest"`
Expected: PASS (all 4).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/NoteDateFormat.kt \
        app/src/test/java/app/tessera/ui/notes/NoteDateFormatTest.kt
git commit -m "feat(notes): add relative date formatter for note cards"
```

### Task 2.2: Redesign NoteCard — remove avatar, add date row, keep tags

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/components/NoteCard.kt`
- Test: `app/src/test/java/app/tessera/ui/notes/components/NoteCardRenderTest.kt` (new, Roborazzi + semantics)

- [ ] **Step 1: Write a semantics test asserting the date is shown and the avatar is gone**

```kotlin
// app/src/test/java/app/tessera/ui/notes/components/NoteCardRenderTest.kt
package app.tessera.ui.notes.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class NoteCardRenderTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun nowMs() =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun showsRelativeDate() {
        composeRule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                NoteCard(
                    note = Note(
                        id = NoteId("n1"), title = "Title", content = "Body",
                        tags = listOf(Tag("work")), isPinned = false,
                        createdAt = nowMs(), updatedAt = nowMs(),
                        deletedAt = null, syncedAt = null, ownerId = null,
                    ),
                )
            }
        }
        composeRule.onNodeWithText("Today").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.notes.components.NoteCardRenderTest"`
Expected: FAIL — "Today" not found (card has no date yet).

- [ ] **Step 3: Rewrite NoteCard layout**

Remove the avatar `Surface` (lines ~51-72) and the wrapping `Row`. New body: a `Column` with the pinned label (now an icon, see Task 2.3 styling) + title + preview + a footer `Row` with the tag chips on the left and the date on the right:

```kotlin
Column(
    modifier = (if (onClick == null) modifier else modifier.clickable(onClick = onClick)),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Text(
        text = note.title.ifBlank { stringResource(R.string.note_untitled) },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = note.previewText() ?: stringResource(R.string.note_empty_preview),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowRow(
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            note.tags.take(4).forEach { tag -> TagChip(name = tag.name) }
        }
        Text(
            text = formatNoteDate(note.updatedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

Add the import `import app.tessera.ui.notes.formatNoteDate`. Keep `previewText()` and `TagChip` private helpers. Remove the now-unused `CircleShape`/`Surface` avatar imports.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.notes.components.NoteCardRenderTest"`
Expected: PASS.

- [ ] **Step 5: Update the existing NoteCard previews if they reference the avatar**

The previews only call `NoteCard(note = ...)`, so they still compile. Re-run all unit tests: `./gradlew :app:testDebugUnitTest`. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/components/NoteCard.kt \
        app/src/test/java/app/tessera/ui/notes/components/NoteCardRenderTest.kt
git commit -m "feat(notes): redesign note card - drop avatar, add updated date"
```

### Task 2.3: Replace swipe-action text labels with icons

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/components/SwipeableNoteCard.kt`
- Test: `app/src/androidTest/java/app/tessera/ui/notes/NotesListScreenTest.kt` (existing — assert icons still trigger the same callbacks via test tags)

- [ ] **Step 1: Confirm the existing swipe test asserts behavior via test tags, not labels**

Read `NotesListScreenTest.swipeLeftRevealsPinActionAndTogglesPin`. It uses `UiTestTags.revealPinAction(...)` / `revealDeleteAction(...)` — tag-based, so swapping label→icon must keep these tags. No test change needed; this step is to confirm the contract.

- [ ] **Step 2: Change `SwipeAction` to render an icon instead of a `Text` label**

In `SwipeableNoteCard.kt`, change `SwipeAction` signature from `label: String` to `icon: ImageVector` + `contentDescription: String`, and render an `Icon`:

```kotlin
@Composable
private fun SwipeAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    container: Color,
    content: Color,
    width: androidx.compose.ui.unit.Dp,
    testTag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = content)
    }
}
```

Update the two call sites: pin action uses `Lucide.Pin` (or `Lucide.PinOff` when `note.isPinned`), delete uses `Lucide.Trash2`. Pass the existing `stringResource(R.string.note_action_*)` as `contentDescription`. Add imports `com.composables.icons.lucide.Lucide`, `Pin`, `PinOff`, `Trash2`, `androidx.compose.material3.Icon`.

- [ ] **Step 3: Build and run the existing swipe UI test**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.notes.NotesListScreenTest"` (Robolectric) — or `connectedDebugAndroidTest` if instrumented.
Expected: PASS — pin/delete still toggled via tags.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/components/SwipeableNoteCard.kt
git commit -m "feat(notes): use icons for pin/delete swipe actions"
```

---

## Chunk 3: Grouping by date + list/tile view toggle (spec item 2, part B)

### Task 3.1: Pure grouping function (group notes by updatedAt day-bucket)

**Files:**
- Create: `app/src/main/java/app/tessera/ui/notes/NoteGrouping.kt`
- Test: `app/src/test/java/app/tessera/ui/notes/NoteGroupingTest.kt` (new)

Grouping rule (matches screenshot 1): buckets in order **Pinned**, **Today**, **Yesterday**, **This Week**, **This Month**, **Earlier**. Pinned notes always group under "Pinned" regardless of date. Within a group keep incoming order (already pinned-first, updatedAt-desc from the DAO).

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/app/tessera/ui/notes/NoteGroupingTest.kt
package app.tessera.ui.notes

import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class NoteGroupingTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 6, 15)

    private fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun note(id: String, day: LocalDate, pinned: Boolean = false) =
        Note(NoteId(id), id, "", emptyList(), pinned, ms(day), ms(day), null, null, null)

    @Test
    fun `pinned notes group under Pinned regardless of date`() {
        val groups = groupNotesByDate(
            listOf(note("p", today.minusDays(40), pinned = true), note("t", today)),
            today, zone,
        )
        assertEquals(NoteGroup.Pinned, groups.first().group)
        assertEquals("p", groups.first().notes.single().id.value)
    }

    @Test
    fun `today yesterday and earlier buckets are ordered`() {
        val groups = groupNotesByDate(
            listOf(
                note("a", today),
                note("b", today.minusDays(1)),
                note("c", today.minusDays(200)),
            ),
            today, zone,
        )
        assertEquals(listOf(NoteGroup.Today, NoteGroup.Yesterday, NoteGroup.Earlier), groups.map { it.group })
    }

    @Test
    fun `empty input yields no groups`() {
        assertEquals(emptyList<NoteSection>(), groupNotesByDate(emptyList(), today, zone))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.notes.NoteGroupingTest"`
Expected: FAIL — `groupNotesByDate`/`NoteGroup`/`NoteSection` unresolved.

- [ ] **Step 3: Implement grouping**

```kotlin
// app/src/main/java/app/tessera/ui/notes/NoteGrouping.kt
package app.tessera.ui.notes

import app.tessera.domain.model.Note
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class NoteGroup { Pinned, Today, Yesterday, ThisWeek, ThisMonth, Earlier }

data class NoteSection(val group: NoteGroup, val notes: List<Note>)

fun groupNotesByDate(
    notes: List<Note>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<NoteSection> {
    if (notes.isEmpty()) return emptyList()
    val startOfWeek = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val startOfMonth = today.withDayOfMonth(1)

    fun bucket(note: Note): NoteGroup {
        if (note.isPinned) return NoteGroup.Pinned
        val date = Instant.ofEpochMilli(note.updatedAt).atZone(zone).toLocalDate()
        return when {
            date == today -> NoteGroup.Today
            date == today.minusDays(1) -> NoteGroup.Yesterday
            !date.isBefore(startOfWeek) -> NoteGroup.ThisWeek
            !date.isBefore(startOfMonth) -> NoteGroup.ThisMonth
            else -> NoteGroup.Earlier
        }
    }

    return notes
        .groupBy(::bucket)
        .toList()
        .sortedBy { (group, _) -> group.ordinal }
        .map { (group, groupNotes) -> NoteSection(group, groupNotes) }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.notes.NoteGroupingTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Add the group-header string resources**

Add to `app/src/main/res/values/strings.xml`:
```xml
<string name="note_group_pinned">Pinned</string>
<string name="note_group_today">Today</string>
<string name="note_group_yesterday">Yesterday</string>
<string name="note_group_this_week">This week</string>
<string name="note_group_this_month">This month</string>
<string name="note_group_earlier">Earlier</string>
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/NoteGrouping.kt \
        app/src/test/java/app/tessera/ui/notes/NoteGroupingTest.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(notes): add date-bucket grouping for the notes list"
```

### Task 3.2: Add a view-mode (List/Tiles) toggle to NotesListViewModel state

**Files:**
- Create: `app/src/main/java/app/tessera/ui/notes/NotesViewMode.kt`
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListState.kt`
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListViewModel.kt`
- Test: `app/src/test/java/app/tessera/ui/notes/NotesListViewModelTest.kt` (extend)

- [ ] **Step 1: Write the failing test (toggle flips the mode in state)**

Add to `NotesListViewModelTest`:

```kotlin
@Test
fun `toggling view mode flips between Tiles and List`() =
    runTest(dispatcher.scheduler) {
        val repository = FakeNotesRepository(initialNotes = listOf(sampleNote("n1")))
        val viewModel = repository.createViewModel()

        viewModel.state.test {
            awaitItem() // Loading
            val content = awaitItem() as NotesListState.Content
            assertEquals(NotesViewMode.Tiles, content.viewMode)

            viewModel.onToggleViewMode()
            val next = awaitItem() as NotesListState.Content
            assertEquals(NotesViewMode.List, next.viewMode)
        }
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.notes.NotesListViewModelTest"`
Expected: FAIL — `NotesViewMode`/`viewMode`/`onToggleViewMode` unresolved.

- [ ] **Step 3: Add the enum**

```kotlin
// app/src/main/java/app/tessera/ui/notes/NotesViewMode.kt
package app.tessera.ui.notes

enum class NotesViewMode { Tiles, List }
```

- [ ] **Step 4: Thread viewMode through state**

In `NotesListState.kt`, add `val viewMode: NotesViewMode` to both `Empty` and `Content`. In `NotesListViewModel.kt`:
- add `private val viewMode = MutableStateFlow(NotesViewMode.Tiles)`
- add `viewMode` to the `combine(...)` (combine supports up to 5 flows; you currently have 4, so add it as the 5th — if you exceed 5, wrap two source flows into a `combine` pair first).
- pass `viewMode` into `toUiState(...)` and set it on `Empty`/`Content`.
- add:
```kotlin
fun onToggleViewMode() {
    viewMode.update { if (it == NotesViewMode.Tiles) NotesViewMode.List else NotesViewMode.Tiles }
}
```

Update `toUiState` signature + the `query()/availableTags()/selectedTags()` helpers in `NotesListScreen.kt` and add a `viewMode()` helper analogous to the others.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.notes.NotesListViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/NotesViewMode.kt \
        app/src/main/java/app/tessera/ui/notes/NotesListState.kt \
        app/src/main/java/app/tessera/ui/notes/NotesListViewModel.kt \
        app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt \
        app/src/test/java/app/tessera/ui/notes/NotesListViewModelTest.kt
git commit -m "feat(notes): add list/tiles view-mode toggle to list state"
```

### Task 3.3: Render grouped sections + honor view mode in NotesContent

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt:326-394`
- Test: `app/src/androidTest/java/app/tessera/ui/notes/NotesListScreenTest.kt` (extend — group header visible; toggle changes column count)

- [ ] **Step 1: Write a UI test asserting a group header renders**

Add a test that supplies a `Content` state with one note dated today and asserts `onNodeWithText("Today")` (group header) is displayed. (Mirror the existing `NotesListScreenTest` setup helpers `contentState(...)`/`note(...)`.)

- [ ] **Step 2: Run it to verify it fails**

Run the test class; expected FAIL — no header text yet.

- [ ] **Step 3: Replace the flat grid with grouped sections + view-mode-aware columns**

In `NotesContent`, compute `val sections = groupNotesByDate(state.notes)` and render with `LazyVerticalStaggeredGrid` whose `columns` is `StaggeredGridCells.Fixed(if (viewMode == NotesViewMode.Tiles) 2 else 1)`. For each section emit a full-span header item then the section's note items:

```kotlin
val columns = if (viewMode == NotesViewMode.Tiles) 2 else 1
LazyVerticalStaggeredGrid(
    columns = StaggeredGridCells.Fixed(columns),
    modifier = Modifier.fillMaxSize().testTag(UiTestTags.NotesGrid),
    contentPadding = PaddingValues(bottom = 120.dp),
    verticalItemSpacing = 12.dp,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    sections.forEach { section ->
        item(span = StaggeredGridItemSpan.FullLine, key = "header-${section.group}") {
            SectionHeader(group = section.group)
        }
        items(items = section.notes, key = { it.id.value }) { note ->
            SwipeableNoteCard(
                note = note,
                modifier = Modifier.animateItem(),
                onClick = { onNoteClick(note.id) },
                onTogglePin = { onTogglePin(note.id) },
                onDelete = { onDeleteNote(note) },
            )
        }
    }
}
```

Add a `SectionHeader` composable mapping each `NoteGroup` to its string resource. Pass `viewMode` into `NotesContent` from the caller. Import `StaggeredGridItemSpan`.

- [ ] **Step 4: Run the UI test to verify it passes**

Expected: PASS — "Today" header displayed.

- [ ] **Step 5: Run the full UI test class for regressions**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.notes.NotesListScreenTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt \
        app/src/androidTest/java/app/tessera/ui/notes/NotesListScreenTest.kt
git commit -m "feat(notes): render date-grouped sections with list/tiles layout"
```

---

## Chunk 4: Search clear button (spec item 9)

### Task 4.1: Add a trailing clear button to the search field

**Files:**
- Modify: `app/src/main/java/app/tessera/core/ui/components/TesseraTextField.kt` (add optional `trailingIcon`)
- Modify: `app/src/main/java/app/tessera/ui/notes/components/BottomSearchBar.kt`
- Modify: `app/src/main/java/app/tessera/core/ui/UiTestTags.kt` (add `SearchClear`)
- Test: `app/src/androidTest/java/app/tessera/ui/notes/SearchClearTest.kt` (new)

- [ ] **Step 1: Add the test tag**

In `UiTestTags.kt` add: `const val SearchClear = "search_clear"`.

- [ ] **Step 2: Write the failing UI test**

```kotlin
// app/src/androidTest/java/app/tessera/ui/notes/SearchClearTest.kt
package app.tessera.ui.notes

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.UiTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchClearTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun clearButtonAppearsWithQueryAndClearsIt() {
        var query = "kotlin"
        composeRule.setContent {
            NotesListScreen(
                state = NotesListState.Content(
                    notes = emptyList(), pinnedCount = 0, query = query,
                    selection = emptySet(), availableTags = emptyList(),
                    selectedTags = emptySet(), viewMode = NotesViewMode.Tiles,
                ),
                snackbarHostState = SnackbarHostState(),
                onQueryChange = { query = it },
                onTagToggle = {}, onCreateNote = {},
            )
        }
        composeRule.onNodeWithTag(UiTestTags.SearchClear).assertIsDisplayed().performClick()
        assertEquals("", query)
    }
}
```

(If `Content` with empty notes maps to `Empty` rendering, supply one note instead and assert clear still works; adjust to match the real state contract.)

- [ ] **Step 3: Run it to verify it fails**

Run the test; expected FAIL — `SearchClear` node absent.

- [ ] **Step 4: Add `trailingIcon` support to TesseraTextField**

Add an optional `trailingIcon: (@Composable () -> Unit)? = null` parameter; render it in a `Row` after `innerTextField()` inside the decoration box. Keep existing `leadingIcon` behavior.

- [ ] **Step 5: Wire the clear button in BottomSearchBar**

In `BottomSearchBar`, pass a `trailingIcon` to the search `TesseraTextField` shown only when `query.isNotEmpty()`:

```kotlin
trailingIcon = if (query.isNotEmpty()) {
    {
        IconButton(
            onClick = { onQueryChange("") },
            modifier = Modifier.size(28.dp).testTag(UiTestTags.SearchClear),
        ) {
            Icon(
                imageVector = Lucide.X,
                contentDescription = stringResource(R.string.search_clear),
                modifier = Modifier.size(16.dp),
            )
        }
    }
} else null,
```

Add `R.string.search_clear` to strings.xml and the Lucide.X / Icon / IconButton imports.

- [ ] **Step 6: Run the test to verify it passes**

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/components/TesseraTextField.kt \
        app/src/main/java/app/tessera/ui/notes/components/BottomSearchBar.kt \
        app/src/main/java/app/tessera/core/ui/UiTestTags.kt \
        app/src/androidTest/java/app/tessera/ui/notes/SearchClearTest.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(search): add clear button to the notes search field"
```

---

## Chunk 5: Collapsing blurred header (spec item 4)

Replace `TopHeaderCapsule` (avatar + "Tessera" + subtitle + settings) with a header showing the **active folder name** (param; defaults to "All Notes") that **shrinks on scroll**, sits over a **blurred gradient backdrop** (same treatment as the bottom search scrim), with the **settings icon to the right** of the title.

### Task 5.1: Scroll-progress → title scale mapping (pure function)

**Files:**
- Create: `app/src/main/java/app/tessera/ui/notes/HeaderScroll.kt`
- Test: `app/src/test/java/app/tessera/ui/notes/HeaderScrollTest.kt` (new)

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/app/tessera/ui/notes/HeaderScrollTest.kt
package app.tessera.ui.notes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HeaderScrollTest {
    @Test fun `no scroll is full size`() = assertEquals(1f, headerTitleScale(0, 200), 0.0001f)
    @Test fun `past threshold is collapsed`() = assertEquals(0.7f, headerTitleScale(400, 200), 0.0001f)
    @Test fun `half threshold is halfway`() = assertEquals(0.85f, headerTitleScale(100, 200), 0.0001f)
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.notes.HeaderScrollTest"`
Expected: FAIL — unresolved.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/app/tessera/ui/notes/HeaderScroll.kt
package app.tessera.ui.notes

const val HEADER_COLLAPSED_SCALE = 0.7f

fun headerTitleScale(scrollPx: Int, thresholdPx: Int): Float {
    if (thresholdPx <= 0) return HEADER_COLLAPSED_SCALE
    val progress = (scrollPx.toFloat() / thresholdPx).coerceIn(0f, 1f)
    return 1f - ((1f - HEADER_COLLAPSED_SCALE) * progress)
}
```

- [ ] **Step 4: Run the test to verify it passes**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/HeaderScroll.kt \
        app/src/test/java/app/tessera/ui/notes/HeaderScrollTest.kt
git commit -m "feat(notes): add scroll-to-scale mapping for collapsing header"
```

### Task 5.2: Build the collapsing header composable

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt`
- Test: Roborazzi render test `app/src/test/java/app/tessera/ui/notes/CollapsingHeaderRenderTest.kt` (new)

- [ ] **Step 1: Write a render test (title + settings present, full and collapsed)**

Capture two screenshots: `scrollPx = 0` and `scrollPx = 400`. Assert the title text node and `UiTestTags.OpenSettings` exist in both.

- [ ] **Step 2: Run it to verify it fails (component does not exist yet)**

Run the class; expected FAIL/compile error.

- [ ] **Step 3: Replace TopHeaderCapsule**

New `CollapsingHeader(title: String, scrollPx: Int, onOpenSettings: () -> Unit)`:
- Reuse the existing `EdgeFadeScrim` (`ScrimEdge.Top`) over the same `hazeState` already created in `NotesListScreen`, mirroring the bottom scrim, so the header backdrop is blurred + gradient.
- Title `Text` with `Modifier.graphicsLayer { val s = headerTitleScale(scrollPx, thresholdPx); scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0f, 0.5f) }`.
- Settings `IconButton` (`Lucide.Settings`, `UiTestTags.OpenSettings`) aligned to the right via a `Row` with `Arrangement.SpaceBetween`.
- `title` parameter default `stringResource(R.string.notes_all_notes)` (add this string = "All Notes").

Wire the screen: hoist a `LazyStaggeredGridState` (`rememberLazyStaggeredGridState()`), pass `state.firstVisibleItemScrollOffset + estimate` OR simpler: track a cumulative scroll via the grid state's `firstVisibleItemIndex`/`firstVisibleItemScrollOffset`. Use `derivedStateOf { gridState.firstVisibleItemScrollOffset + gridState.firstVisibleItemIndex * 200 }` as `scrollPx` (approximate is fine for the visual effect). Move the grid state out of `NotesContent` so the header can read it.

- [ ] **Step 4: Run the render test to verify it passes**

Expected: PASS — title + settings present at both scroll values.

- [ ] **Step 5: Update existing tests referencing the old header**

Search for `OpenSettings` usages in tests; the tag is preserved so they should pass. Run `./gradlew :app:testDebugUnitTest`. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt \
        app/src/test/java/app/tessera/ui/notes/CollapsingHeaderRenderTest.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(notes): collapsing blurred header with folder title and right-aligned settings"
```

---

## Chunk 6: Relocate tag filters to an expanding tag bar (spec item 5)

Move tag filter chips OUT of the search input. Add a button **above the FAB** that horizontally expands a scrolling list of tag chips **above the search input**. The tag list must not sit in its own bordered container, and **each tag chip is individually blurred** (glassmorphism per chip).

### Task 6.1: Remove the FlowRow tag filters from BottomSearchBar

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/components/BottomSearchBar.kt`
- Test: existing search tests still pass.

- [ ] **Step 1: Delete the `if (showFilters && availableTags.isNotEmpty()) { FlowRow {...} }` block** and drop the now-unused `availableTags`, `selectedTags`, `onTagToggle`, `showFilters` params (they move to the new tag bar). Keep the `Column` wrapper minimal or collapse to just the text field.

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: compile errors at the `BottomSearchBar(...)` call site in `NotesListScreen` — fix in Task 6.3. (Do this task + 6.2 + 6.3 as one commit since the signature change spans files.)

### Task 6.2: Build a `TagFilterBar` with per-chip blur and horizontal scroll

**Files:**
- Create: `app/src/main/java/app/tessera/ui/notes/components/TagFilterBar.kt`
- Modify: `app/src/main/java/app/tessera/core/ui/UiTestTags.kt` (add `TagFilterToggle`, `tagFilterChip(name)`)
- Test: `app/src/androidTest/java/app/tessera/ui/notes/TagFilterBarTest.kt` (new)

- [ ] **Step 1: Add test tags** — `const val TagFilterToggle = "tag_filter_toggle"` and `fun tagFilterChip(name: String) = "tag_filter_chip_$name"`.

- [ ] **Step 2: Write the failing UI test** — render `TagFilterBar(expanded = true, availableTags = listOf("work","ideas"), selectedTags = emptySet(), ...)`; assert `tagFilterChip("work")` is displayed and clicking it invokes `onTagToggle("work")`.

- [ ] **Step 3: Run it to verify it fails.** Expected: FAIL — component missing.

- [ ] **Step 4: Implement `TagFilterBar`**

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagFilterBar(
    expanded: Boolean,
    availableTags: List<String>,
    selectedTags: Set<String>,
    onTagToggle: (String) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = expanded && availableTags.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            availableTags.forEach { tag ->
                val selected = tag in selectedTags
                FloatingCapsule(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onTagToggle(tag) }
                        .testTag(UiTestTags.tagFilterChip(tag)),
                    shape = CircleShape,
                    hazeState = hazeState,
                    blurRadius = 18.dp,
                    backgroundColor = if (selected)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    else CapsuleTintLight, // theme-aware tint handled inside capsule default if null
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("#$tag", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
```

Per-chip blur = each chip is its own `FloatingCapsule` with `hazeState`, and there is **no** outer container/border (the `Row` has no background). Imports: `horizontalScroll`, `AnimatedVisibility`, `expandVertically`, `shrinkVertically`, `FloatingCapsule`, `HazeState`, etc.

- [ ] **Step 5: Run the test to verify it passes.** Expected: PASS.

### Task 6.3: Add the toggle button above the FAB and wire the bar into the screen

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt`

- [ ] **Step 1: Add local `var tagBarExpanded by remember { mutableStateOf(false) }`** in `NotesListScreen`.

- [ ] **Step 2: Stack a tag-toggle `GlassFab` directly above the new-note FAB.** In the bottom-right `Row`, replace the single FAB with a `Column(verticalArrangement = spacedBy(12.dp))` containing: a small `GlassFab(size = 40.dp, onClick = { tagBarExpanded = !tagBarExpanded }, testTag = TagFilterToggle)` showing `Lucide.Tag`, then the existing new-note FAB.

- [ ] **Step 3: Place `TagFilterBar` above the bottom search capsule.** Insert it into the bottom-anchored `Column`/`Box` so it renders directly above `BottomSearchBar`, passing `expanded = tagBarExpanded`, `availableTags = state.availableTags()`, `selectedTags = state.selectedTags()`, `onTagToggle`, and the shared `hazeState`.

- [ ] **Step 4: Update the `BottomSearchBar(...)` call** to the trimmed signature (query + onQueryChange + onFocusChanged + hazeState only).

- [ ] **Step 5: Build + run full suite.**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (fix any remaining call-site compile errors).

- [ ] **Step 6: Add the `Lucide.Tag` import and a `notes_filter_tags` content-description string.**

- [ ] **Step 7: Commit (whole chunk together)**

```bash
git add app/src/main/java/app/tessera/ui/notes/components/BottomSearchBar.kt \
        app/src/main/java/app/tessera/ui/notes/components/TagFilterBar.kt \
        app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt \
        app/src/main/java/app/tessera/core/ui/UiTestTags.kt \
        app/src/androidTest/java/app/tessera/ui/notes/TagFilterBarTest.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(notes): move tag filters into expandable blurred tag bar above search"
```

---

## Chunk 7: Split the editor header into two capsules (spec item 6)

Currently one `FloatingCapsule` holds back (left) and pin+delete (right). Split into two side-by-side capsules: a left capsule with the back button, a right capsule with pin + delete.

### Task 7.1: Two-capsule editor header

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt:173-251`
- Test: `app/src/androidTest/java/app/tessera/ui/editor/NoteEditorScreenTest.kt` (existing — back/pin/delete tags must still work)

- [ ] **Step 1: Confirm existing editor test asserts EditorBack/EditorPin/EditorDelete via tags.** Read `NoteEditorScreenTest`. The split must preserve those tags.

- [ ] **Step 2: Replace the single capsule with a `Row(SpaceBetween)` of two `FloatingCapsule`s**

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    FloatingCapsule(
        modifier = Modifier.shadow(12.dp, RoundedCornerShape(28.dp), clip = false,
            ambientColor = FloatingShadowAmbient, spotColor = FloatingShadowSpot),
        blurRadius = 24.dp, shadowElevation = 3.dp, hazeState = hazeState,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp).testTag(UiTestTags.EditorBack)) {
            Icon(Lucide.ArrowLeft, stringResource(R.string.editor_back),
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
    FloatingCapsule(
        modifier = Modifier.shadow(12.dp, RoundedCornerShape(28.dp), clip = false,
            ambientColor = FloatingShadowAmbient, spotColor = FloatingShadowSpot),
        blurRadius = 24.dp, shadowElevation = 3.dp, hazeState = hazeState,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onTogglePin, modifier = Modifier.size(40.dp).testTag(UiTestTags.EditorPin)) {
                Icon(Lucide.Pin, stringResource(if (state.isPinned) R.string.editor_unpin else R.string.editor_pin),
                    tint = if (state.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp).testTag(UiTestTags.EditorDelete)) {
                Icon(Lucide.Trash2, stringResource(R.string.editor_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}
```

Replace the outer single-capsule `FloatingCapsule { Row(SpaceBetween) {...} }` (lines ~181-250) with the `Row` above.

- [ ] **Step 3: Run the editor UI test for regressions**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.editor.NoteEditorScreenTest"`
Expected: PASS — back/pin/delete still tagged and clickable.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt
git commit -m "feat(editor): split header into separate left and right capsules"
```

---

## Final verification

- [ ] **Run the entire unit + screenshot suite:** `./gradlew :app:testDebugUnitTest`
- [ ] **Run instrumented UI tests** (if available device): `./gradlew :app:connectedDebugAndroidTest`
- [ ] **Lint/compile:** `./gradlew :app:assembleDebug`
- [ ] **Manual emulator pass:** new-note FAB (no artifacts, shadow intact through animation), grouped list + tile/list toggle, dates on cards, icon swipe actions, collapsing blurred header, expandable tag bar, search clear button, split editor header.
- [ ] Confirm every checkbox above is checked and report a summary using superpowers:verification-before-completion (run the commands, paste real output — do not claim success without it).
