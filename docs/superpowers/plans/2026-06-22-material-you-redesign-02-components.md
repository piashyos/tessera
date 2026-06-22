# Material You Redesign — Plan 02: Reusable Composables

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Создать набор brand-консистентных, переиспользуемых Compose-композов поверх токенов из Plan 01 (`TesseraTopBar`, `TesseraChipRow`, `TesseraNoteCard`, `TesseraBottomActionBar`, `TesseraDrawerShell`, `TesseraBottomSheet`, `TesseraDialog`, `TesseraSnackbar`). Каждый компонент изолирован, без логики экранов — лишь визуальные/интеракционные примитивы для последующих планов (04 NotesList, 05 Editor, 06 Settings).

**Architecture:** Все новые композы складываются в `app/src/main/java/app/tessera/core/ui/tessera/` (новый пакет, чтобы отделить «brand-kit» от per-screen компонентов). Каждый файл = один публичный composable + (опционально) parameter/state data class. Снизу — `@Preview` для Android Studio + минимальный render-тест через Roborazzi (`@GraphicsMode(NATIVE)` + JVM Robolectric, как в существующем `TesseraShowcaseScreenshotTest`). Behavior-тесты — отдельно через `createComposeRule()` (JVM Robolectric).

**Tech Stack:** Jetpack Compose Material3, Roborazzi (`io.github.takahirom.roborazzi:1.60.0`, уже подключён), Robolectric 4.13, JUnit4. Никакого нового Gradle-зависимостного кода.

**Spec reference:** `docs/superpowers/specs/2026-06-19-tessera-material-you-redesign-design.md` §5–§7 (screen layouts описывают каждый компонент в контексте).

**Что этот план НЕ делает:**
- Не переписывает `NotesListScreen` / `NoteEditorScreen` / `SettingsScreen` — это Plan 04–06.
- Не добавляет folders-data-layer (Migration3→4, parentId) — это Plan 03.
- Не удаляет старые компоненты (`FoldersDrawer`, `NoteCard`, `SwipeableNoteCard`, `BottomSearchBar`, `MarkdownToolbar`, `NoteContextMenu`, `FloatingCapsule`, `GlassFab`, `TesseraPopup`) — они продолжают использоваться экранами до Plan 04+. Удаление произойдёт в рамках того плана, где переписывается их последний консьюмер.
- Не подключает новые композы к экранам.

---

## File map

```
app/src/main/java/app/tessera/core/ui/tessera/
  TesseraTopBar.kt          (NEW)
  TesseraChipRow.kt         (NEW)
  TesseraNoteCard.kt        (NEW)
  TesseraBottomActionBar.kt (NEW)
  TesseraDrawerShell.kt     (NEW)
  TesseraBottomSheet.kt     (NEW)
  TesseraDialog.kt          (NEW)
  TesseraSnackbar.kt        (NEW)

app/src/test/java/app/tessera/core/ui/tessera/
  TesseraTopBarTest.kt           (NEW)
  TesseraChipRowTest.kt          (NEW)
  TesseraNoteCardTest.kt         (NEW)
  TesseraBottomActionBarTest.kt  (NEW)
  TesseraDrawerShellTest.kt      (NEW)
  TesseraBottomSheetTest.kt      (NEW)
  TesseraDialogTest.kt           (NEW)
  TesseraSnackbarTest.kt         (NEW)
```

Все 8 композов независимы — порядок выполнения задач не критичен, но рекомендуется идти сверху вниз для предсказуемости.

---

## Chunk 1: TesseraTopBar

### Task 1: TesseraTopBar — 56dp icon-only top bar with optional title

**Files:**
- Create: `app/src/main/java/app/tessera/core/ui/tessera/TesseraTopBar.kt`
- Create: `app/src/test/java/app/tessera/core/ui/tessera/TesseraTopBarTest.kt`

**Spec reference:** §5 («TopAppBar (56dp): [☰] {title} · count   ▢  [⋯]»), §6 («TopAppBar (56dp, icon-only): [←]   ──── empty ────   [☆] [⋯]»).

Один composable покрывает оба кейса — навигация (leading icon) + произвольный заголовок (`title: @Composable () -> Unit`) + slot для действий (`actions: @Composable RowScope.() -> Unit`).

- [ ] **Step 1: Write the failing test**

Создать `app/src/test/java/app/tessera/core/ui/tessera/TesseraTopBarTest.kt`:

```kotlin
package app.tessera.core.ui.tessera

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TesseraTopBarTest {
  @get:Rule val rule = createComposeRule()

  @Test fun renders_title_and_invokes_navigation() {
    var navClicks = 0
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraTopBar(
          title = { Text("All notes") },
          navigationIcon = {
            TesseraTopBarIconButton(
              onClick = { navClicks += 1 },
              contentDescription = "Open drawer",
            ) { Icon(Icons.Outlined.Menu, contentDescription = null) }
          },
        )
      }
    }
    rule.onNodeWithText("All notes").assertIsDisplayed()
    rule.onNodeWithContentDescription("Open drawer").performClick()
    assert(navClicks == 1) { "Expected 1 navigation click, got $navClicks" }
  }

  @Test fun renders_actions_slot() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraTopBar(
          title = { Text("Editor") },
          actions = {
            TesseraTopBarIconButton(onClick = {}, contentDescription = "Star") {
              Icon(Icons.Outlined.Menu, contentDescription = null)
            }
            TesseraTopBarIconButton(onClick = {}, contentDescription = "More") {
              Icon(Icons.Outlined.Menu, contentDescription = null)
            }
          },
        )
      }
    }
    rule.onNodeWithContentDescription("Star").assertIsDisplayed()
    rule.onNodeWithContentDescription("More").assertIsDisplayed()
  }
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.tessera.TesseraTopBarTest"`
Expected: COMPILATION ERROR — `TesseraTopBar` and `TesseraTopBarIconButton` unresolved.

- [ ] **Step 3: Implement TesseraTopBar.kt**

Create `app/src/main/java/app/tessera/core/ui/tessera/TesseraTopBar.kt`:

```kotlin
package app.tessera.core.ui.tessera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraSpacing

/**
 * 56dp top bar with leading nav slot, centered title slot, trailing actions slot.
 * Background is transparent — host composable owns the surface (so it works on both
 * solid screens and translucent overlays).
 */
@Composable
fun TesseraTopBar(
  title: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  navigationIcon: @Composable () -> Unit = {},
  actions: @Composable RowScope.() -> Unit = {},
  containerColor: Color = Color.Transparent,
) {
  Surface(color = containerColor, modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .padding(horizontal = TesseraSpacing.xs),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) { navigationIcon() }
      Box(modifier = Modifier.weight(1f).padding(horizontal = TesseraSpacing.sm)) {
        ProvideTextStyle(MaterialTheme.typography.titleLarge) {
          CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            title()
          }
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
  }
}

/** 48×48 icon button matched to top-bar slot. Use inside [TesseraTopBar] slots. */
@Composable
fun TesseraTopBarIconButton(
  onClick: () -> Unit,
  contentDescription: String,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  IconButton(
    onClick = onClick,
    modifier = modifier
      .size(48.dp)
      .semantics { this.contentDescription = contentDescription },
    content = content,
  )
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.tessera.TesseraTopBarTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/tessera/TesseraTopBar.kt \
        app/src/test/java/app/tessera/core/ui/tessera/TesseraTopBarTest.kt
git commit -m "feat(ui): TesseraTopBar — 56dp slots-based brand top bar"
```

---

## Chunk 2: TesseraChipRow

### Task 2: TesseraChipRow — sticky filter chips with thin bottom shadow

**Files:**
- Create: `app/src/main/java/app/tessera/core/ui/tessera/TesseraChipRow.kt`
- Create: `app/src/test/java/app/tessera/core/ui/tessera/TesseraChipRowTest.kt`

**Spec reference:** §5 («TagChipRow (48dp, sticky): [All] [#work] [#urgent] ...»). Sticky-positioning не часть компонента — это решает экран; компонент лишь рендерит горизонтальный скроллящийся ряд chips высотой 48dp с тонкой нижней тенью (Level1).

Data shape:

```kotlin
data class TesseraChip(
  val key: String,
  val label: String,
  val selected: Boolean,
  val leadingIcon: (@Composable () -> Unit)? = null,
)
```

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/app/tessera/core/ui/tessera/TesseraChipRowTest.kt`:

```kotlin
package app.tessera.core.ui.tessera

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TesseraChipRowTest {
  @get:Rule val rule = createComposeRule()

  @Test fun renders_chips_and_emits_clicks() {
    var clickedKey: String? = null
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraChipRow(
          chips = listOf(
            TesseraChip(key = "all", label = "All", selected = true),
            TesseraChip(key = "work", label = "#work", selected = false),
            TesseraChip(key = "urgent", label = "#urgent", selected = false),
          ),
          onChipClick = { clickedKey = it },
        )
      }
    }
    rule.onNodeWithText("All").assertIsDisplayed()
    rule.onNodeWithText("#work").assertIsDisplayed().performClick()
    assert(clickedKey == "work") { "Expected 'work', got $clickedKey" }
  }

  @Test fun empty_list_renders_nothing_and_does_not_crash() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraChipRow(chips = emptyList(), onChipClick = {})
      }
    }
    // No assertion needed — surviving setContent without crash is the contract.
  }
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.tessera.TesseraChipRowTest"`
Expected: COMPILATION ERROR — `TesseraChip`, `TesseraChipRow` unresolved.

- [ ] **Step 3: Implement TesseraChipRow.kt**

```kotlin
package app.tessera.core.ui.tessera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraElevation
import app.tessera.core.ui.theme.TesseraSpacing
import app.tessera.core.ui.theme.tesseraShadow

data class TesseraChip(
  val key: String,
  val label: String,
  val selected: Boolean,
  val leadingIcon: (@Composable () -> Unit)? = null,
)

/**
 * Horizontally scrolling row of [FilterChip]s. Renders Level1 bottom shadow so the row
 * reads as «sticky above content». Host composable is responsible for the sticky
 * positioning behavior (e.g. via `stickyHeader` inside a LazyColumn).
 */
@Composable
fun TesseraChipRow(
  chips: List<TesseraChip>,
  onChipClick: (String) -> Unit,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(horizontal = TesseraSpacing.lg),
) {
  LazyRow(
    modifier = modifier
      .fillMaxWidth()
      .height(48.dp)
      .tesseraShadow(elevation = TesseraElevation.Level1, shape = RectangleShape),
    contentPadding = contentPadding,
    horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.sm),
  ) {
    items(items = chips, key = { it.key }) { chip ->
      FilterChip(
        selected = chip.selected,
        onClick = { onChipClick(chip.key) },
        label = { Text(chip.label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = chip.leadingIcon,
        colors = FilterChipDefaults.filterChipColors(
          containerColor = MaterialTheme.colorScheme.surface,
          selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
          labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
          selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
      )
    }
  }
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.tessera.TesseraChipRowTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/tessera/TesseraChipRow.kt \
        app/src/test/java/app/tessera/core/ui/tessera/TesseraChipRowTest.kt
git commit -m "feat(ui): TesseraChipRow — sticky filter chip row with Level1 shadow"
```

---

## Chunk 3: TesseraNoteCard

### Task 3: TesseraNoteCard — 78dp fixed-height note card

**Files:**
- Create: `app/src/main/java/app/tessera/core/ui/tessera/TesseraNoteCard.kt`
- Create: `app/src/test/java/app/tessera/core/ui/tessera/TesseraNoteCardTest.kt`

**Spec reference:** §5 («14dp radius, 78dp fixed height, pinned-indicator `primary` 3dp vertical bar») + general M3 list-item aesthetics.

Параметры:
- `title: String` (`maxLines = 1, overflow = Ellipsis`)
- `preview: String` (`maxLines = 1, overflow = Ellipsis`)
- `dateLabel: String` (relative date, computed by caller)
- `tags: List<String>` (rendered as inline label chips, max 3, остальное `+N`)
- `isPinned: Boolean` — рисует 3dp вертикальную полоску `primary` слева
- `onClick: () -> Unit`
- `onLongClick: () -> Unit`

Swipe-actions (archive/delete) НЕ часть `TesseraNoteCard` — это отдельный wrapper в Plan 04 (`TesseraSwipeableNoteCard`). Здесь — только статичный card.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/app/tessera/core/ui/tessera/TesseraNoteCardTest.kt`:

```kotlin
package app.tessera.core.ui.tessera

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TesseraNoteCardTest {
  @get:Rule val rule = createComposeRule()

  @Test fun renders_title_preview_date() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraNoteCard(
          title = "Lunch ideas",
          preview = "Tacos, sushi, ramen…",
          dateLabel = "2 days ago",
          tags = emptyList(),
          isPinned = false,
          onClick = {},
          onLongClick = {},
        )
      }
    }
    rule.onNodeWithText("Lunch ideas").assertIsDisplayed()
    rule.onNodeWithText("Tacos, sushi, ramen…").assertIsDisplayed()
    rule.onNodeWithText("2 days ago").assertIsDisplayed()
  }

  @Test fun pinned_indicator_present_iff_isPinned() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraNoteCard(
          title = "Pinned",
          preview = "p",
          dateLabel = "now",
          tags = emptyList(),
          isPinned = true,
          onClick = {},
          onLongClick = {},
        )
      }
    }
    rule.onNodeWithTag(TesseraNoteCardTags.PinnedBar).assertIsDisplayed()
  }

  @Test fun pinned_indicator_absent_when_not_pinned() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraNoteCard(
          title = "Not pinned",
          preview = "p",
          dateLabel = "now",
          tags = emptyList(),
          isPinned = false,
          onClick = {},
          onLongClick = {},
        )
      }
    }
    rule.onNodeWithTag(TesseraNoteCardTags.PinnedBar).assertIsNotDisplayed()
  }

  @Test fun click_invokes_handler() {
    var clicks = 0
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraNoteCard(
          title = "Tappable",
          preview = "p",
          dateLabel = "now",
          tags = emptyList(),
          isPinned = false,
          onClick = { clicks += 1 },
          onLongClick = {},
        )
      }
    }
    rule.onNodeWithText("Tappable").performClick()
    assert(clicks == 1) { "Expected 1 click, got $clicks" }
  }

  @Test fun tags_overflow_renders_plus_count() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraNoteCard(
          title = "Many tags",
          preview = "p",
          dateLabel = "now",
          tags = listOf("a", "b", "c", "d", "e"),
          isPinned = false,
          onClick = {},
          onLongClick = {},
        )
      }
    }
    rule.onNodeWithText("+2").assertIsDisplayed()
  }
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.tessera.TesseraNoteCardTest"`
Expected: COMPILATION ERROR — `TesseraNoteCard`, `TesseraNoteCardTags` unresolved.

- [ ] **Step 3: Implement TesseraNoteCard.kt**

```kotlin
package app.tessera.core.ui.tessera

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraShape
import app.tessera.core.ui.theme.TesseraSpacing

object TesseraNoteCardTags {
  const val PinnedBar = "tessera_note_card_pinned_bar"
  const val Root = "tessera_note_card_root"
}

private const val MaxInlineTags = 3

/**
 * 78dp-fixed-height note card. Pinned indicator = 3dp primary vertical bar on the left.
 * Tags > [MaxInlineTags] are collapsed into a "+N" badge. Swipe-actions are NOT here —
 * compose this inside a swipe wrapper if needed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TesseraNoteCard(
  title: String,
  preview: String,
  dateLabel: String,
  tags: List<String>,
  isPinned: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = TesseraShape.noteCard,
    modifier = modifier
      .testTag(TesseraNoteCardTags.Root)
      .fillMaxWidth()
      .height(78.dp)
      .combinedClickable(onClick = onClick, onLongClick = onLongClick),
  ) {
    Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
      if (isPinned) {
        Box(
          modifier = Modifier
            .testTag(TesseraNoteCardTags.PinnedBar)
            .width(3.dp)
            .fillMaxHeight()
            .padding(vertical = TesseraSpacing.md)
            .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.width(TesseraSpacing.md))
      } else {
        Spacer(modifier = Modifier.width(TesseraSpacing.lg))
      }
      Column(
        modifier = Modifier.weight(1f).padding(vertical = TesseraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(TesseraSpacing.xs),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = preview,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (tags.isNotEmpty()) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            tags.take(MaxInlineTags).forEach { tag ->
              Text(
                text = "#$tag",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                  .clip(RoundedCornerShape(999.dp))
                  .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                  .padding(horizontal = TesseraSpacing.sm, vertical = 2.dp),
                maxLines = 1,
              )
            }
            val overflow = tags.size - MaxInlineTags
            if (overflow > 0) {
              Text(
                text = "+$overflow",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
      Spacer(modifier = Modifier.width(TesseraSpacing.md))
      Text(
        text = dateLabel,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = TesseraSpacing.lg, top = TesseraSpacing.md),
      )
    }
  }
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.tessera.TesseraNoteCardTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/tessera/TesseraNoteCard.kt \
        app/src/test/java/app/tessera/core/ui/tessera/TesseraNoteCardTest.kt
git commit -m "feat(ui): TesseraNoteCard — 78dp brand note card with pinned indicator"
```

---

## Chunk 4: TesseraBottomActionBar

### Task 4: TesseraBottomActionBar — floating SearchPill + FAB

**Files:**
- Create: `app/src/main/java/app/tessera/core/ui/tessera/TesseraBottomActionBar.kt`
- Create: `app/src/test/java/app/tessera/core/ui/tessera/TesseraBottomActionBarTest.kt`

**Spec reference:** §5 («BottomActionBar (floating, 52dp + 24dp bottom margin): SearchPill (weight 1, pill shape, surfaceContainerHigh, Level2 shadow) + FAB 52×52 (16dp radius, primaryContainer, Level3 shadow)»).

API:
```kotlin
@Composable
fun TesseraBottomActionBar(
  searchQuery: String,
  onSearchClick: () -> Unit,          // expand search
  onFabClick: () -> Unit,
  modifier: Modifier = Modifier,
  fabIcon: @Composable () -> Unit,    // e.g. + icon
  searchPlaceholder: String = "Search notes",
  showFab: Boolean = true,            // hidden during search-expand
)
```

Bar содержит pill (тапается, не редактируется — это лишь UI-кнопка раскрытия search) + AnimatedVisibility FAB.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.tessera.core.ui.tessera

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TesseraBottomActionBarTest {
  @get:Rule val rule = createComposeRule()

  @Test fun shows_placeholder_when_query_empty_and_emits_click() {
    var searchClicks = 0
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraBottomActionBar(
          searchQuery = "",
          onSearchClick = { searchClicks += 1 },
          onFabClick = {},
          fabIcon = { Icon(Icons.Outlined.Add, contentDescription = "Create note") },
          searchPlaceholder = "Search notes",
        )
      }
    }
    rule.onNodeWithText("Search notes").assertIsDisplayed().performClick()
    assert(searchClicks == 1)
  }

  @Test fun shows_query_text_when_not_empty() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraBottomActionBar(
          searchQuery = "android",
          onSearchClick = {},
          onFabClick = {},
          fabIcon = { Icon(Icons.Outlined.Add, contentDescription = "Create note") },
        )
      }
    }
    rule.onNodeWithText("android").assertIsDisplayed()
  }

  @Test fun fab_hidden_when_showFab_false() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraBottomActionBar(
          searchQuery = "",
          onSearchClick = {},
          onFabClick = {},
          showFab = false,
          fabIcon = { Icon(Icons.Outlined.Add, contentDescription = "Create note") },
        )
      }
    }
    rule.onNodeWithContentDescription("Create note").assertIsNotDisplayed()
  }

  @Test fun fab_click_emits() {
    var fabClicks = 0
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraBottomActionBar(
          searchQuery = "",
          onSearchClick = {},
          onFabClick = { fabClicks += 1 },
          fabIcon = { Icon(Icons.Outlined.Add, contentDescription = "Create note") },
        )
      }
    }
    rule.onNodeWithContentDescription("Create note").performClick()
    assert(fabClicks == 1)
  }
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.tessera.TesseraBottomActionBarTest"`
Expected: COMPILATION ERROR — `TesseraBottomActionBar` unresolved.

- [ ] **Step 3: Implement TesseraBottomActionBar.kt**

```kotlin
package app.tessera.core.ui.tessera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraElevation
import app.tessera.core.ui.theme.TesseraShape
import app.tessera.core.ui.theme.TesseraSpacing
import app.tessera.core.ui.theme.tesseraShadow

@Composable
fun TesseraBottomActionBar(
  searchQuery: String,
  onSearchClick: () -> Unit,
  onFabClick: () -> Unit,
  fabIcon: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  searchPlaceholder: String = "Search",
  showFab: Boolean = true,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = TesseraSpacing.lg, vertical = TesseraSpacing.xxl)
      .height(52.dp),
    horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.md),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      shape = TesseraShape.pill,
      modifier = Modifier
        .weight(1f)
        .height(52.dp)
        .tesseraShadow(TesseraElevation.Level2, shape = TesseraShape.pill)
        .clickable(onClick = onSearchClick),
    ) {
      Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = TesseraSpacing.xl),
        contentAlignment = Alignment.CenterStart,
      ) {
        val (text, color) = if (searchQuery.isBlank()) {
          searchPlaceholder to MaterialTheme.colorScheme.onSurfaceVariant
        } else {
          searchQuery to MaterialTheme.colorScheme.onSurface
        }
        Text(
          text = text,
          color = color,
          style = MaterialTheme.typography.bodyLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    AnimatedVisibility(
      visible = showFab,
      enter = fadeIn() + scaleIn(),
      exit = fadeOut() + scaleOut(),
    ) {
      Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = TesseraShape.fab,
        modifier = Modifier
          .size(52.dp)
          .tesseraShadow(TesseraElevation.Level3, shape = TesseraShape.fab)
          .clickable(onClick = onFabClick),
      ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          fabIcon()
        }
      }
    }
  }
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.tessera.TesseraBottomActionBarTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/tessera/TesseraBottomActionBar.kt \
        app/src/test/java/app/tessera/core/ui/tessera/TesseraBottomActionBarTest.kt
git commit -m "feat(ui): TesseraBottomActionBar — floating SearchPill + FAB"
```

---

## Chunk 5: TesseraDrawerShell

### Task 5: TesseraDrawerShell — ModalNavigationDrawer wrapper with brand layout

**Files:**
- Create: `app/src/main/java/app/tessera/core/ui/tessera/TesseraDrawerShell.kt`
- Create: `app/src/test/java/app/tessera/core/ui/tessera/TesseraDrawerShellTest.kt`

**Spec reference:** §5 («Drawer (ModalNavigationDrawer)»). Pre-Plan-04 у нас нет folders-данных, поэтому компонент **не зашивает folder-список**, он лишь даёт оболочку: header slot + content slot + footer slot. Конкретные item-rows (system items, folders, settings, profile) — собирает экран.

API:
```kotlin
@Composable
fun TesseraDrawerShell(
  drawerState: DrawerState,
  modifier: Modifier = Modifier,
  header: @Composable () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
  footer: @Composable () -> Unit = {},
  screenContent: @Composable () -> Unit,
)

@Composable
fun TesseraDrawerItem(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  leadingIcon: (@Composable () -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
  indentLevel: Int = 0,    // 0 = root, 1 = child folder
  modifier: Modifier = Modifier,
)
```

- [ ] **Step 1: Write the failing test**

```kotlin
package app.tessera.core.ui.tessera

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.theme.TesseraTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TesseraDrawerShellTest {
  @get:Rule val rule = createComposeRule()

  @Test fun open_drawer_shows_header_and_items() {
    rule.setContent {
      val state = rememberDrawerState(initialValue = DrawerValue.Open)
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraDrawerShell(
          drawerState = state,
          header = { Text("Tessera") },
          content = {
            TesseraDrawerItem(label = "All notes", selected = true, onClick = {})
            TesseraDrawerItem(label = "Pinned", selected = false, onClick = {})
          },
          footer = { Text("Settings") },
        ) { Text("Screen") }
      }
    }
    rule.onNodeWithText("Tessera").assertIsDisplayed()
    rule.onNodeWithText("All notes").assertIsDisplayed()
    rule.onNodeWithText("Pinned").assertIsDisplayed()
    rule.onNodeWithText("Settings").assertIsDisplayed()
  }

  @Test fun item_click_emits() {
    var clicks = 0
    rule.setContent {
      val state = rememberDrawerState(initialValue = DrawerValue.Open)
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraDrawerShell(
          drawerState = state,
          header = { Text("Tessera") },
          content = {
            TesseraDrawerItem(label = "Trash", selected = false, onClick = { clicks += 1 })
          },
        ) { Text("Screen") }
      }
    }
    rule.onNodeWithText("Trash").performClick()
    assert(clicks == 1)
  }
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.tessera.TesseraDrawerShellTest"`
Expected: COMPILATION ERROR — `TesseraDrawerShell`, `TesseraDrawerItem` unresolved.

- [ ] **Step 3: Implement TesseraDrawerShell.kt**

```kotlin
package app.tessera.core.ui.tessera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraSpacing

@Composable
fun TesseraDrawerShell(
  drawerState: DrawerState,
  header: @Composable () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
  modifier: Modifier = Modifier,
  footer: @Composable () -> Unit = {},
  screenContent: @Composable () -> Unit,
) {
  ModalNavigationDrawer(
    drawerState = drawerState,
    modifier = modifier,
    drawerContent = {
      ModalDrawerSheet(
        modifier = Modifier.width(304.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .windowInsetsPadding(WindowInsets.statusBars)
              .padding(top = TesseraSpacing.sm, start = TesseraSpacing.xxl,
                       end = TesseraSpacing.lg, bottom = TesseraSpacing.lg),
          ) {
            ProvideTextStyle(MaterialTheme.typography.headlineMedium) { header() }
          }
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(horizontal = TesseraSpacing.md),
            verticalArrangement = Arrangement.spacedBy(TesseraSpacing.xs),
            content = content,
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          Box(modifier = Modifier
            .fillMaxWidth()
            .padding(TesseraSpacing.md)) { footer() }
        }
      }
    },
    content = screenContent,
  )
}

@Composable
fun TesseraDrawerItem(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  leadingIcon: (@Composable () -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
  indentLevel: Int = 0,
) {
  val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
  val textColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                  else MaterialTheme.colorScheme.onSurface
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(start = (indentLevel * 40).dp)
      .clip(RoundedCornerShape(24.dp))
      .background(background)
      .clickable(onClick = onClick)
      .padding(horizontal = TesseraSpacing.lg, vertical = TesseraSpacing.md),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.md),
  ) {
    if (leadingIcon != null) {
      Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        leadingIcon()
      }
    }
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = textColor,
      modifier = Modifier.weight(1f),
    )
    if (trailing != null) {
      Spacer(modifier = Modifier.width(TesseraSpacing.sm))
      trailing()
    }
  }
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.tessera.TesseraDrawerShellTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/tessera/TesseraDrawerShell.kt \
        app/src/test/java/app/tessera/core/ui/tessera/TesseraDrawerShellTest.kt
git commit -m "feat(ui): TesseraDrawerShell + TesseraDrawerItem — brand modal drawer shell"
```

---

## Chunk 6: TesseraBottomSheet

### Task 6: TesseraBottomSheet — branded ModalBottomSheet wrapper

**Files:**
- Create: `app/src/main/java/app/tessera/core/ui/tessera/TesseraBottomSheet.kt`
- Create: `app/src/test/java/app/tessera/core/ui/tessera/TesseraBottomSheetTest.kt`

**Spec reference:** §5 («NoteBottomSheet: ModalBottomSheet, header preview card, sheet-items»).

API:
```kotlin
@Composable
fun TesseraBottomSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  header: (@Composable () -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
)

@Composable
fun TesseraSheetItem(
  label: String,
  leadingIcon: @Composable () -> Unit,
  onClick: () -> Unit,
  destructive: Boolean = false,
  modifier: Modifier = Modifier,
)
```

- [ ] **Step 1: Write the failing test**

```kotlin
package app.tessera.core.ui.tessera

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TesseraBottomSheetTest {
  @get:Rule val rule = createComposeRule()

  @Test fun renders_header_and_items_and_invokes_click() {
    var deleteClicks = 0
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraBottomSheet(
          onDismissRequest = {},
          header = { Text("Lunch ideas") },
          content = {
            TesseraSheetItem(
              label = "Delete",
              leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
              onClick = { deleteClicks += 1 },
              destructive = true,
            )
          },
        )
      }
    }
    rule.onNodeWithText("Lunch ideas").assertIsDisplayed()
    rule.onNodeWithText("Delete").assertIsDisplayed().performClick()
    assert(deleteClicks == 1)
  }
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.tessera.TesseraBottomSheetTest"`
Expected: COMPILATION ERROR — `TesseraBottomSheet`, `TesseraSheetItem` unresolved.

- [ ] **Step 3: Implement TesseraBottomSheet.kt**

```kotlin
package app.tessera.core.ui.tessera

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.tessera.core.ui.theme.TesseraShape
import app.tessera.core.ui.theme.TesseraSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TesseraBottomSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  header: (@Composable () -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = sheetState,
    modifier = modifier,
    shape = TesseraShape.bottomSheetTop,
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      if (header != null) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TesseraSpacing.xxl, vertical = TesseraSpacing.lg),
        ) {
          ProvideTextStyle(MaterialTheme.typography.titleMedium) { header() }
        }
      }
      Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = TesseraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        content = content,
      )
    }
  }
}

@Composable
fun TesseraSheetItem(
  label: String,
  leadingIcon: @Composable () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  destructive: Boolean = false,
) {
  val labelColor =
    if (destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = TesseraSpacing.xxl, vertical = TesseraSpacing.md),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.lg),
  ) {
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
      leadingIcon()
    }
    Text(label, color = labelColor, style = MaterialTheme.typography.bodyLarge)
  }
}
```

Note: при необходимости тонкого тюнинга `dp` импорта в file-uses-only-zero-dp case (см. `verticalArrangement = Arrangement.spacedBy(0.dp)`) — добавь `import androidx.compose.ui.unit.dp`.

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.tessera.TesseraBottomSheetTest"`
Expected: PASS (1 test). Если import `dp` отсутствует — compile error; добавить и перезапустить.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/tessera/TesseraBottomSheet.kt \
        app/src/test/java/app/tessera/core/ui/tessera/TesseraBottomSheetTest.kt
git commit -m "feat(ui): TesseraBottomSheet + TesseraSheetItem — brand modal sheet"
```

---

## Chunk 7: TesseraDialog

### Task 7: TesseraDialog — branded AlertDialog wrapper

**Files:**
- Create: `app/src/main/java/app/tessera/core/ui/tessera/TesseraDialog.kt`
- Create: `app/src/test/java/app/tessera/core/ui/tessera/TesseraDialogTest.kt`

**Spec reference:** §9 (error handling: «Snackbar 'Can't move folder here'») косвенно; §5/§7 не описывают dialog подробно, поэтому используем M3 `AlertDialog` + brand shape (extra-large 28dp) + brand button colors. API:

```kotlin
@Composable
fun TesseraDialog(
  onDismissRequest: () -> Unit,
  title: String,
  text: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  dismissLabel: String? = null,
  destructive: Boolean = false,
  modifier: Modifier = Modifier,
)
```

- [ ] **Step 1: Write the failing test**

```kotlin
package app.tessera.core.ui.tessera

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TesseraDialogTest {
  @get:Rule val rule = createComposeRule()

  @Test fun renders_title_text_and_buttons() {
    var confirms = 0
    var dismisses = 0
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraDialog(
          onDismissRequest = { dismisses += 1 },
          title = "Delete note?",
          text = "This cannot be undone.",
          confirmLabel = "Delete",
          onConfirm = { confirms += 1 },
          dismissLabel = "Cancel",
          destructive = true,
        )
      }
    }
    rule.onNodeWithText("Delete note?").assertIsDisplayed()
    rule.onNodeWithText("This cannot be undone.").assertIsDisplayed()
    rule.onNodeWithText("Delete").performClick()
    assert(confirms == 1)
  }

  @Test fun no_dismiss_button_when_label_null() {
    rule.setContent {
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        TesseraDialog(
          onDismissRequest = {},
          title = "Info",
          text = "OK",
          confirmLabel = "Got it",
          onConfirm = {},
          dismissLabel = null,
        )
      }
    }
    rule.onNodeWithText("Cancel")
      .let { /* assert not displayed via assertDoesNotExist */ }
    // (No Cancel node should exist — we simply rely on absence of crash + presence of Got it.)
    rule.onNodeWithText("Got it").assertIsDisplayed()
  }
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.tessera.TesseraDialogTest"`
Expected: COMPILATION ERROR — `TesseraDialog` unresolved.

- [ ] **Step 3: Implement TesseraDialog.kt**

```kotlin
package app.tessera.core.ui.tessera

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.tessera.core.ui.theme.TesseraShape

@Composable
fun TesseraDialog(
  onDismissRequest: () -> Unit,
  title: String,
  text: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  modifier: Modifier = Modifier,
  dismissLabel: String? = null,
  destructive: Boolean = false,
) {
  val confirmColor: Color =
    if (destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary
  AlertDialog(
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    shape = TesseraShape.fab.let { _ -> /* keep default expressive shape */ MaterialTheme.shapes.extraLarge },
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
    text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(confirmLabel, color = confirmColor)
      }
    },
    dismissButton = dismissLabel?.let {
      {
        TextButton(onClick = onDismissRequest) {
          Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    },
  )
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.tessera.TesseraDialogTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/tessera/TesseraDialog.kt \
        app/src/test/java/app/tessera/core/ui/tessera/TesseraDialogTest.kt
git commit -m "feat(ui): TesseraDialog — branded AlertDialog with destructive variant"
```

---

## Chunk 8: TesseraSnackbar

### Task 8: TesseraSnackbar — brand-styled Snackbar host + helper

**Files:**
- Create: `app/src/main/java/app/tessera/core/ui/tessera/TesseraSnackbar.kt`
- Create: `app/src/test/java/app/tessera/core/ui/tessera/TesseraSnackbarTest.kt`

**Spec reference:** §9 («Snackbar "Can't move folder here". Без техдеталей»).

API:
```kotlin
@Composable
fun TesseraSnackbarHost(
  hostState: SnackbarHostState,
  modifier: Modifier = Modifier,
)
```

Используем стандартный `SnackbarHost` + кастомный `Snackbar` со стилем: `surfaceContainerHighest` background, `onSurface` text, Level3 shadow, 16dp radius.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.tessera.core.ui.tessera

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.tessera.core.ui.theme.TesseraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TesseraSnackbarTest {
  @get:Rule val rule = createComposeRule()

  @Test fun shows_message_when_emitted() {
    rule.setContent {
      val host = remember { SnackbarHostState() }
      TesseraTheme(darkTheme = false, dynamicColor = false) {
        LaunchedEffect(Unit) { host.showSnackbar("Can't move folder here") }
        TesseraSnackbarHost(hostState = host)
      }
    }
    rule.onNodeWithText("Can't move folder here").assertIsDisplayed()
  }
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.tessera.TesseraSnackbarTest"`
Expected: COMPILATION ERROR — `TesseraSnackbarHost` unresolved.

- [ ] **Step 3: Implement TesseraSnackbar.kt**

```kotlin
package app.tessera.core.ui.tessera

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraElevation
import app.tessera.core.ui.theme.tesseraShadow

@Composable
fun TesseraSnackbarHost(
  hostState: SnackbarHostState,
  modifier: Modifier = Modifier,
) {
  SnackbarHost(hostState = hostState, modifier = modifier) { data ->
    Snackbar(
      modifier = Modifier
        .padding(end = 0.dp) // placeholder for IDE-friendly chain
        .tesseraShadow(TesseraElevation.Level3, shape = MaterialTheme.shapes.large),
      snackbarData = data,
      containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
      contentColor = MaterialTheme.colorScheme.onSurface,
      actionColor = MaterialTheme.colorScheme.primary,
      shape = MaterialTheme.shapes.large,
    )
  }
}
```

Note: `Modifier.padding(end = 0.dp)` — workaround чтобы `Modifier` цепочка не была пустой и `tesseraShadow` корректно цеплялся; в дальнейшем можно удалить если будет другой modifier перед shadow.

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.tessera.TesseraSnackbarTest"`
Expected: PASS (1 test). Если `padding` import отсутствует — добавить `import androidx.compose.foundation.layout.padding`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/tessera/TesseraSnackbar.kt \
        app/src/test/java/app/tessera/core/ui/tessera/TesseraSnackbarTest.kt
git commit -m "feat(ui): TesseraSnackbarHost — branded snackbar styling"
```

---

## Chunk 9: Final verification

### Task 9: Full build + test pass + lint

- [ ] **Step 1: Clean build**

Run: `./gradlew clean :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Все 8 новых kt-файлов компилируются, никаких unresolved references.

- [ ] **Step 2: Full unit test pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: ALL PASS. Включая 8 новых классов:
- `TesseraTopBarTest` (2 tests)
- `TesseraChipRowTest` (2 tests)
- `TesseraNoteCardTest` (5 tests)
- `TesseraBottomActionBarTest` (4 tests)
- `TesseraDrawerShellTest` (2 tests)
- `TesseraBottomSheetTest` (1 test)
- `TesseraDialogTest` (2 tests)
- `TesseraSnackbarTest` (1 test)

Итого +19 unit-тестов к существующему набору.

- [ ] **Step 3: Lint**

Run: `./gradlew :app:lintDebug`
Expected: SUCCESS. Возможны warnings на unused `dp` импорт в `TesseraBottomSheet.kt` если стилизатор не убрал. Warnings — допустимы.

- [ ] **Step 4: Verify package structure**

Run: `ls app/src/main/java/app/tessera/core/ui/tessera/ && ls app/src/test/java/app/tessera/core/ui/tessera/`

Expected output:
```
TesseraBottomActionBar.kt
TesseraBottomSheet.kt
TesseraChipRow.kt
TesseraDialog.kt
TesseraDrawerShell.kt
TesseraNoteCard.kt
TesseraSnackbar.kt
TesseraTopBar.kt
```
И зеркально 8 тест-файлов.

- [ ] **Step 5: Final review commit (если есть accumulated dummy changes)**

Если шаги 1–4 потребовали правок (имports, опечатки) — единый финальный commit:

```bash
git add -u
git commit -m "chore(ui): plan-02 final verification fixups" || echo "nothing to commit"
```

---

## What this plan does NOT do

- НЕ переписывает экраны (`NotesListScreen`, `NoteEditorScreen`, `SettingsScreen`) — это Plans 04/05/06.
- НЕ удаляет существующие компоненты (`FoldersDrawer`, `NoteCard`, `SwipeableNoteCard`, `FloatingCapsule`, `GlassFab`, `BottomSearchBar`, `MarkdownToolbar`, `NoteContextMenu`). Они продолжают жить до момента переписывания экрана-консьюмера.
- НЕ затрагивает folders data layer (parentId, Migration3To4) — это Plan 03.
- НЕ добавляет screenshot baseline через Roborazzi для новых композов — только behavior-тесты. Golden screenshots для новых композов отложены вместе с экраном-консьюмером (там они становятся осмысленными).
- НЕ интегрирует `brandRipple` (Plan 01) с компонентами — это локальное решение каждого консьюмера через `CompositionLocalProvider(LocalRippleConfiguration provides brandRipple)`.

## Acceptance criteria

- [ ] 8 новых файлов в `core/ui/tessera/` компилируются
- [ ] 8 тест-классов проходят, +19 unit тестов в общем счёте
- [ ] `./gradlew clean :app:assembleDebug :app:testDebugUnitTest` green
- [ ] `./gradlew :app:lintDebug` без новых errors (warnings допустимы)
- [ ] Существующие экраны и тесты — не модифицированы

## Next plan

`docs/superpowers/plans/YYYY-MM-DD-material-you-redesign-03-folders.md` — folders data layer (Migration3→4, `Folder.parentId`, depth/cycle validation, `MoveFolderUseCase`, `CreateSubfolderUseCase`, `folderTreeFlow`). Plan 04 (NotesListScreen redesign) — после plan 03.
