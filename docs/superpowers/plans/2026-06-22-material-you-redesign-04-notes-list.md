# Material You Redesign — Plan 04: NotesListScreen redesign

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Переписать `NotesListScreen` на новые brand-композы из Plan 02 (`TesseraDrawerShell`, `TesseraTopBar`, `TesseraChipRow`, `TesseraNoteCard`, `TesseraBottomActionBar`, `TesseraBottomSheet`) и folder tree из Plan 03 (`observeFolderTree`). Добавить state: `folderTree`, `searchExpanded`. Удалить устаревшие компоненты-консьюмеры (`FoldersDrawer`, `BottomSearchBar`, `FloatingCapsule`, `GlassFab`, `NoteCard`, `NoteContextMenu`, `TagFilterBar`, `SwipeableNoteCard` — переписать).

**Architecture:** `NotesListScreen` оборачивается в `TesseraDrawerShell` снаружи `Scaffold`. Внутри: `TesseraTopBar` + sticky `TesseraChipRow` (внутри `LazyColumn`'s `stickyHeader`) + список заметок (`TesseraSwipeableNoteCard` обёртывает `TesseraNoteCard`) + плавающий `TesseraBottomActionBar`. Долгий tap по карточке → `TesseraBottomSheet` (заменяет `NoteContextMenu`). Search expand: тап на pill открывает full-width search bar поверх top-bar; FAB исчезает (`AnimatedVisibility`).

**Tech Stack:** Compose Material3, новые Tessera-композы, существующий `NotesListViewModel` (расширяется), Hilt.

**Spec reference:** `docs/superpowers/specs/2026-06-19-tessera-material-you-redesign-design.md` §5 (NotesListScreen).

**Что этот план НЕ делает:**
- НЕ трогает `NoteEditorScreen` (Plan 05) и `SettingsScreen` (Plan 06).
- НЕ добавляет drag-to-reorder (spec §14: future).
- НЕ переписывает `NoteCard.kt` (используется в editor scroll preview) — он остаётся, лишь `SwipeableNoteCard` заменяется на `TesseraSwipeableNoteCard`.

---

## File map

```
app/src/main/java/app/tessera/
  ui/notes/NotesListState.kt        (MODIFY: + folderTree, searchExpanded)
  ui/notes/NotesListViewModel.kt    (MODIFY: combine observeFolderTree(), expand/collapse search, createSubfolder, moveFolder)
  ui/notes/NotesListScreen.kt       (REWRITE — большой)
  ui/notes/components/TesseraSwipeableNoteCard.kt  (NEW: replaces SwipeableNoteCard)
  ui/notes/components/TesseraNotesDrawer.kt        (NEW: header + system items + folder tree + footer using TesseraDrawerShell)
  ui/notes/components/NoteActionsSheet.kt          (NEW: TesseraBottomSheet items — Pin/Move/Tags/Duplicate/Share/Archive/Delete)
  ui/notes/components/ExpandedSearchBar.kt         (NEW: full-width search overlay above topbar)

  ui/notes/components/FoldersDrawer.kt             (DELETE — replaced by TesseraNotesDrawer)
  ui/notes/components/BottomSearchBar.kt           (DELETE — replaced by ExpandedSearchBar + bottom pill in TesseraBottomActionBar)
  ui/notes/components/SwipeableNoteCard.kt         (DELETE — replaced by TesseraSwipeableNoteCard)
  ui/notes/components/NoteContextMenu.kt           (DELETE — replaced by NoteActionsSheet)
  ui/notes/components/TagFilterBar.kt              (DELETE — replaced by TesseraChipRow inside screen)
  core/ui/components/FloatingCapsule.kt            (DELETE — no longer used)
  core/ui/components/GlassFab.kt                   (DELETE — no longer used)

app/src/test/java/app/tessera/ui/notes/
  NotesListViewModelTest.kt        (MODIFY: + searchExpand toggle, folderTree integration tests)
  NotesListSearchExpandTest.kt     (NEW — JVM Robolectric: pill tap → search bar visible, FAB hidden)
  NotesListDrawerTest.kt           (NEW: drawer renders folder tree with children indented)
```

NOTE: удаление компонентов производится **в самом конце**, после того как все консьюмеры мигрированы. Если что-то ещё ссылается на удаляемый файл — сначала перевести консьюмера, потом удалять.

---

## Chunk 1: ViewModel + State foundation

### Task 1: Extend NotesListState with folderTree + searchExpanded

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListState.kt`

- [ ] **Step 1: Add fields to sealed states**

```kotlin
package app.tessera.ui.notes

import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderTreeNode
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId

sealed interface NotesListState {
    val folders: List<Folder>
    val folderTree: List<FolderTreeNode>
    val selectedFolderId: FolderId?
    val searchExpanded: Boolean

    data object Loading : NotesListState {
        override val folders: List<Folder> = emptyList()
        override val folderTree: List<FolderTreeNode> = emptyList()
        override val selectedFolderId: FolderId? = null
        override val searchExpanded: Boolean = false
    }

    data class Empty(
        val query: String,
        val availableTags: List<String>,
        val selectedTags: Set<String>,
        val viewMode: NotesViewMode,
        override val folders: List<Folder> = emptyList(),
        override val folderTree: List<FolderTreeNode> = emptyList(),
        override val selectedFolderId: FolderId? = null,
        override val searchExpanded: Boolean = false,
    ) : NotesListState

    data class Content(
        val notes: List<Note>,
        val pinnedCount: Int,
        val query: String,
        val selection: Set<NoteId>,
        val selectionMode: Boolean,
        val availableTags: List<String>,
        val selectedTags: Set<String>,
        val viewMode: NotesViewMode,
        override val folders: List<Folder> = emptyList(),
        override val folderTree: List<FolderTreeNode> = emptyList(),
        override val selectedFolderId: FolderId? = null,
        override val searchExpanded: Boolean = false,
    ) : NotesListState
}
```

- [ ] **Step 2: Build check**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Default values (`= emptyList()`, `= false`) делают migration zero-impact для callers.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/NotesListState.kt
git commit -m "feat(notes-list): add folderTree + searchExpanded to NotesListState"
```

---

### Task 2: Wire folderTree + searchExpanded into ViewModel

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListViewModel.kt`

- [ ] **Step 1: Inject ObserveFolderTreeUseCase + MoveFolderUseCase + CreateSubfolderUseCase**

В constructor добавить:
```kotlin
private val observeFolderTree: ObserveFolderTreeUseCase,
private val moveFolder: MoveFolderUseCase,
private val createSubfolder: CreateSubfolderUseCase,
```

(Если в VM много use cases — следовать существующему стилю.)

- [ ] **Step 2: Combine folderTree into uiState**

Найти `combine(...)` сборку `uiState`. Добавить `observeFolderTree()` в список флоу-источников и проставлять `folderTree = tree` в каждый state branch (Loading/Empty/Content).

- [ ] **Step 3: Add searchExpand actions**

```kotlin
private val _searchExpanded = MutableStateFlow(false)

fun expandSearch() { _searchExpanded.value = true }
fun collapseSearch() {
    _searchExpanded.value = false
    setQuery("")
}
```

Включить `_searchExpanded` в combine, mapping в state.searchExpanded.

- [ ] **Step 4: Folder move + create-subfolder actions**

```kotlin
fun onMoveFolder(folderId: FolderId, newParentId: FolderId?) {
    viewModelScope.launch {
        val result = moveFolder(folderId, newParentId)
        if (result.isFailure) _events.emit(NotesListEvent.ShowSnackbar("Can't move folder here"))
    }
}

fun onCreateSubfolder(parentId: FolderId, name: String) {
    viewModelScope.launch {
        val result = createSubfolder(parentId, name)
        if (result.isFailure) _events.emit(NotesListEvent.ShowSnackbar("Can't create subfolder"))
    }
}
```

Если `_events` flow/channel ещё нет — добавить `NotesListEvent` sealed interface + SharedFlow.

- [ ] **Step 5: Update NotesListViewModelTest**

Добавить тесты:
- `expandSearch_sets_state_searchExpanded_true`
- `collapseSearch_clears_query_and_state`
- `folderTree_emitted_into_state`
- `onMoveFolder_failure_emits_snackbar_event`

Использовать `FakeFolderRepository` (расширить чтобы возвращать прогнозируемый folderTree).

- [ ] **Step 6: Build + run tests**

Run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "app.tessera.ui.notes.NotesListViewModelTest"`
Expected: ALL PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/NotesListViewModel.kt \
        app/src/test/java/app/tessera/ui/notes/NotesListViewModelTest.kt
git commit -m "feat(notes-list): VM observes folderTree, exposes searchExpand + folder move/create-subfolder"
```

---

## Chunk 2: New leaf composables

### Task 3: TesseraSwipeableNoteCard

**Files:**
- Create: `app/src/main/java/app/tessera/ui/notes/components/TesseraSwipeableNoteCard.kt`

Wraps `TesseraNoteCard` with swipe-to-archive (secondary container) and swipe-to-delete (error container). API:

```kotlin
@Composable
fun TesseraSwipeableNoteCard(
  title: String, preview: String, dateLabel: String, tags: List<String>, isPinned: Boolean,
  onClick: () -> Unit, onLongClick: () -> Unit,
  onArchive: () -> Unit, onDelete: () -> Unit,
  modifier: Modifier = Modifier,
)
```

Реализация — `SwipeToDismissBox` (Compose M3) с двумя action targets. Archive — swipe right; Delete — swipe left (требует confirmation через VM).

- [ ] **Step 1: Implementation** (см. существующий `SwipeableNoteCard.kt` для reference жестов — но не копировать, написать свежий поверх M3 SwipeToDismissBox)
- [ ] **Step 2: Test** — `TesseraSwipeableNoteCardTest.kt`: проверить что rendered title виден, swipe triggers callbacks (через `performTouchInput`).
- [ ] **Step 3: Commit**

---

### Task 4: TesseraNotesDrawer

**Files:**
- Create: `app/src/main/java/app/tessera/ui/notes/components/TesseraNotesDrawer.kt`

Composable, который рендерит содержимое drawer через `TesseraDrawerShell` с готовым layout:
- Header «Tessera» (Instrument Serif)
- System items: All / Pinned / Archive / Trash
- «FOLDERS» label + [+] для создания root folder
- LazyColumn по `folderTree`: для каждого root — `TesseraDrawerItem(indentLevel=0)`, для children — `indentLevel=1`
- Footer: Settings + Profile rows

Принимает:
```kotlin
@Composable
fun TesseraNotesDrawer(
  drawerState: DrawerState,
  folderTree: List<FolderTreeNode>,
  selectedFolderId: FolderId?,
  selectedSystemKey: String, // "all", "pinned", "archive", "trash"
  onSystemSelect: (String) -> Unit,
  onFolderSelect: (FolderId?) -> Unit,
  onCreateRootFolder: () -> Unit,
  onCreateSubfolder: (FolderId) -> Unit,
  onSettingsClick: () -> Unit,
  onProfileClick: () -> Unit,
  content: @Composable () -> Unit,
)
```

- [ ] **Step 1: Implementation** — оборачивает `TesseraDrawerShell`, заполняет slots.
- [ ] **Step 2: Test** — `TesseraNotesDrawerTest`: render folderTree → children visible с indentLevel=1.
- [ ] **Step 3: Commit**

---

### Task 5: NoteActionsSheet

**Files:**
- Create: `app/src/main/java/app/tessera/ui/notes/components/NoteActionsSheet.kt`

`TesseraBottomSheet` с header preview (заголовок заметки) + `TesseraSheetItem`-rows: Pin/Unpin, Move to folder, Add tags, Duplicate, Share, Archive, divider, Delete (destructive).

```kotlin
@Composable
fun NoteActionsSheet(
  noteTitle: String, isPinned: Boolean,
  onPinToggle: () -> Unit, onMoveToFolder: () -> Unit, onAddTags: () -> Unit,
  onDuplicate: () -> Unit, onShare: () -> Unit, onArchive: () -> Unit,
  onDelete: () -> Unit, onDismiss: () -> Unit,
)
```

- [ ] **Step 1: Implementation**
- [ ] **Step 2: Test** — items rendered + click invokes callbacks.
- [ ] **Step 3: Commit**

---

### Task 6: ExpandedSearchBar

**Files:**
- Create: `app/src/main/java/app/tessera/ui/notes/components/ExpandedSearchBar.kt`

Полноширинный search bar, появляется поверх top-bar при `searchExpanded`. Содержит TextField + leading back arrow (`onCollapse`) + recent searches (опционально).

```kotlin
@Composable
fun ExpandedSearchBar(
  query: String, onQueryChange: (String) -> Unit,
  onCollapse: () -> Unit, recentSearches: List<String> = emptyList(),
  onRecentSearchClick: (String) -> Unit = {},
)
```

- [ ] **Step 1: Implementation**
- [ ] **Step 2: Test** — back-arrow click triggers collapse, query echoes.
- [ ] **Step 3: Commit**

---

## Chunk 3: NotesListScreen rewrite

### Task 7: Rewrite NotesListScreen using new composables

**Files:**
- Rewrite: `app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt`

Подход: т.к. файл 950 LOC — переписываем за один заход, но сохраняем top-level public composable signature `fun NotesListScreen(state, onAction1, onAction2, ...)`.

Внешняя структура:

```kotlin
@Composable
fun NotesListScreen(
  state: NotesListState,
  /* ... все существующие callbacks + новые ... */
) {
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  TesseraNotesDrawer(
    drawerState = drawerState,
    folderTree = state.folderTree,
    selectedFolderId = state.selectedFolderId,
    /* ... */,
  ) {
    Scaffold(
      topBar = {
        TesseraTopBar(
          title = { Text(headerTitle(state)) },
          navigationIcon = { TesseraTopBarIconButton(/*menu*/...) { Icon(Menu) } },
          actions = { /* ⋯ */ },
        )
      },
      bottomBar = { /* nothing — bottomActionBar is floating in content */ },
      snackbarHost = { TesseraSnackbarHost(snackbarHostState) },
    ) { padding ->
      Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(/* notes list */) {
          stickyHeader {
            TesseraChipRow(chips = state.toTesseraChips(), onChipClick = ...)
          }
          items(state.notes, key = { it.id.value }) { note ->
            TesseraSwipeableNoteCard(/* ... */)
          }
        }
        TesseraBottomActionBar(
          modifier = Modifier.align(Alignment.BottomCenter),
          searchQuery = state.query,
          onSearchClick = onExpandSearch,
          onFabClick = onCreateNote,
          showFab = !state.searchExpanded,
          fabIcon = { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.create_note)) },
        )
        AnimatedVisibility(visible = state.searchExpanded, ...) {
          ExpandedSearchBar(query = state.query, onQueryChange = ..., onCollapse = onCollapseSearch)
        }
      }
    }
  }

  // Bottom sheet for note actions
  val sheetNote by remember { /* state */ }
  sheetNote?.let {
    NoteActionsSheet(/* ... */)
  }
}
```

- [ ] **Step 1: Capture existing public API**

```bash
sed -n '125,180p' app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt | grep -E "^\s*on[A-Z]|^\s*fun "
```

Запиши callbacks signature — они должны остаться pin-compatible (или явно migrated на screen-uses-callers).

- [ ] **Step 2: Rewrite NotesListScreen.kt**

Подготовь свежий файл, сохраняя имена параметров где возможно. Все вызовы старых компонентов (`FoldersDrawer`, `BottomSearchBar`, `SwipeableNoteCard`, `NoteContextMenu`, `TagFilterBar`, `FloatingCapsule`, `GlassFab`) — заменяются на новые. Внутри `NotesListScreen.kt` не оставлять private composables, выносить их в `components/`.

- [ ] **Step 3: Update NotesNavGraph и call-sites**

Если signature `NotesListScreen` изменилась — обновить место вызова (вероятно `NotesNavGraph.kt`).

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: All pass. Если old `NotesListScreenTest` ломается — переписать его с новой структурой.

- [ ] **Step 6: Smoke launch** на устройстве — проверить базовый flow: drawer открывается, заметки видны, FAB создаёт заметку, search expand.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt \
        app/src/main/java/app/tessera/ui/notes/components/ \
        app/src/test/java/app/tessera/ui/notes/
git commit -m "feat(notes-list): rewrite NotesListScreen on Tessera composables + folder tree drawer"
```

---

## Chunk 4: Delete deprecated components

### Task 8: Remove old components

**Files (delete):**
- `app/src/main/java/app/tessera/ui/notes/components/FoldersDrawer.kt`
- `app/src/main/java/app/tessera/ui/notes/components/BottomSearchBar.kt`
- `app/src/main/java/app/tessera/ui/notes/components/SwipeableNoteCard.kt`
- `app/src/main/java/app/tessera/ui/notes/components/NoteContextMenu.kt`
- `app/src/main/java/app/tessera/ui/notes/components/TagFilterBar.kt`
- `app/src/main/java/app/tessera/core/ui/components/FloatingCapsule.kt`
- `app/src/main/java/app/tessera/core/ui/components/GlassFab.kt`

- [ ] **Step 1: Verify no references**

```bash
for f in FoldersDrawer BottomSearchBar SwipeableNoteCard NoteContextMenu TagFilterBar FloatingCapsule GlassFab; do
  grep -rln "\\b$f\\b" app/src/main/ app/src/test/ app/src/androidTest/ 2>/dev/null | grep -v "components/$f.kt\|build" || true
done
```

Любая остающаяся ссылка → fix первой, потом continue.

- [ ] **Step 2: Delete files**

```bash
git rm app/src/main/java/app/tessera/ui/notes/components/{FoldersDrawer,BottomSearchBar,SwipeableNoteCard,NoteContextMenu,TagFilterBar}.kt
git rm app/src/main/java/app/tessera/core/ui/components/{FloatingCapsule,GlassFab}.kt
```

Если есть associated tests (`CapsuleScaleTest`, `GlassFabRenderTest`) — удалить вместе:
```bash
git rm app/src/test/java/app/tessera/core/ui/components/{CapsuleScaleTest,GlassFabRenderTest}.kt 2>/dev/null
```

- [ ] **Step 3: Build + tests**

Run: `./gradlew clean :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, ALL TESTS PASS.

- [ ] **Step 4: Commit**

```bash
git commit -m "chore(notes-list): remove deprecated components after Tessera migration"
```

---

## Chunk 5: Final verification

### Task 9: Final build + smoke + lint

- [ ] **Step 1: Clean build**

Run: `./gradlew clean :app:assembleDebug`

- [ ] **Step 2: Full tests**

Run: `./gradlew :app:testDebugUnitTest`

- [ ] **Step 3: Lint**

Run: `./gradlew :app:lintDebug`
Expected: No new errors. Deprecation warnings о уже удалённых легаси-токенах должны исчезнуть.

- [ ] **Step 4: Smoke launch**

На устройстве пройти весь flow:
1. Открыть приложение → notes list виден, шрифты ОК
2. Tap menu → drawer открыт, system items + folders (включая subfolders с indent) видны
3. Tap folder → notes filter применился
4. Long-press note → NoteActionsSheet открыт
5. Swipe note right → archive; swipe left → delete confirmation
6. Tap search pill → ExpandedSearchBar появился, FAB скрылся
7. Type query → notes filter
8. Back arrow → collapse, FAB вернулся

- [ ] **Step 5: Acceptance criteria check** (см. ниже)

---

## What this plan does NOT do

- НЕ редизайнит `NoteEditorScreen` (Plan 05) и `SettingsScreen` (Plan 06).
- НЕ удаляет старые legacy-токены `PrimaryLight`/`SurfaceLight`/etc — у них могут быть консьюмеры в editor/settings до Plan 05/06.
- НЕ добавляет drag-to-move папок (spec §14 future).

## Acceptance criteria

- [ ] `TesseraNotesDrawer`, `TesseraSwipeableNoteCard`, `NoteActionsSheet`, `ExpandedSearchBar` существуют и компилируются
- [ ] `NotesListScreen` использует только Tessera-композы, без `FoldersDrawer`/`BottomSearchBar`/`SwipeableNoteCard`/`NoteContextMenu`/`TagFilterBar`/`FloatingCapsule`/`GlassFab`
- [ ] `NotesListState` имеет `folderTree`, `searchExpanded`
- [ ] `NotesListViewModel` подключает `ObserveFolderTreeUseCase` + `MoveFolderUseCase` + `CreateSubfolderUseCase` + actions `expandSearch`/`collapseSearch`
- [ ] Удалены: `FoldersDrawer.kt`, `BottomSearchBar.kt`, `SwipeableNoteCard.kt`, `NoteContextMenu.kt`, `TagFilterBar.kt`, `FloatingCapsule.kt`, `GlassFab.kt`
- [ ] `./gradlew clean :app:assembleDebug :app:testDebugUnitTest` green
- [ ] Smoke на устройстве пройден

## Next plan

`docs/superpowers/plans/YYYY-MM-DD-material-you-redesign-05-editor.md` — `NoteEditorScreen` redesign: TesseraTopBar, EditorMeta, TagChipsExpandable, EditorFormatBar (заменяет MarkdownToolbar), RichNoteBody с serif-H1 span.
