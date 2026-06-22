# Material You Redesign — Plan 05: NoteEditorScreen redesign

> **For agentic workers:** Use superpowers:executing-plans (or subagent-driven-development if subagents available) to implement.

**Goal:** Переписать `NoteEditorScreen` на Tessera-композы. Заменить двойную `FloatingCapsule` верхнюю/нижнюю на `TesseraTopBar` сверху и IME-aware `EditorFormatBar` снизу. Добавить `EditorMeta` (breadcrumb + relative date) и привести `RichNoteBody` к Tessera typography (Inter body + Instrument Serif H1 first-line).

**Spec reference:** `docs/superpowers/specs/2026-06-19-tessera-material-you-redesign-design.md` §6.

**Что план НЕ делает:**
- НЕ удаляет `FloatingCapsule` глобально (использует Settings до Plan 06; TagPickerPopup/FolderNamePopup/TagChipsExpandable пока остаются на нём — удалить можно после миграции этих popups).
- НЕ переписывает `MarkdownToolbar` логику команд — `EditorFormatBar` эмитит те же `RichBodyActions`.
- НЕ трогает Aztec anti-flicker/preserve-typed-content инварианты (см. commits `8ed0ca7`, `53728a1`, `baae2d4`).

---

## Chunk 1: Leaf composables

### Task 1: EditorMeta
- New: `components/EditorMeta.kt`
- Signature: `EditorMeta(folderBreadcrumb: List<String>, relativeDate: String, modifier: Modifier)`.
- `Row` 28dp height: `Icon(Folder, size=16dp)`; breadcrumb segments joined with ` › `; spacer; relativeDate (bodySmall onSurfaceVariant).
- Test: `EditorMetaRenderTest` — рендерит обе строки.
- Commit.

### Task 2: EditorFormatBar
- New: `components/EditorFormatBar.kt`. Заменяет inline `MarkdownToolbar` визуально.
- Принимает `actions: RichBodyActions?`, `activeFormats: Set<RichFormat>`, `onOpenOverflow: () -> Unit`.
- Layout: scrollable `Row` 48dp. Группы кнопок 36dp с `1dp × 24dp outlineVariant` separators. Active state = `secondaryContainer` 8dp radius.
- Группы: `[B][I][U][S]` | `[H1][H2]` | `[•][1.][☐]` | `[«][</>][—]` | `[🔗][🖼][📎]` | `[⋯]` (open overflow).
- Test: `EditorFormatBarRenderTest`.
- Commit.

### Task 3: First-line Instrument Serif span helper
- New: `core/text/SerifH1Spanner.kt` — `fun applyFirstLineSerifH1(spannable: Spannable, ctx: Context)`.
- Логика: найти первый `h1` блок (Aztec вставляет `HeadingSpan` level 1). Наложить `TypefaceSpan` (InstrumentSerif) + `AbsoluteSizeSpan(26.sp px)`.
- Тест: pure unit test on a `SpannableStringBuilder` mocked input.
- Commit.

---

## Chunk 2: Screen rewrite

### Task 4: Rewrite NoteEditorScreen on Tessera composables
- Modify: `NoteEditorScreen.kt`.
- Структура:
  ```kotlin
  Scaffold(
    topBar = {
      TesseraTopBar(
        title = {},  // editor empty title per spec
        navigationIcon = { TesseraTopBarIconButton(onBack) { Icon(ArrowLeft) } },
        actions = {
          IconButton(onTogglePin) { Icon(Pin, tint = if (isPinned) primary else onSurfaceVariant) }
          IconButton(onDelete) { Icon(Trash2) }
        },
      )
    },
    bottomBar = {
      AnimatedVisibility(visible = imeVisible && !isReadOnly, enter=slideInVert, exit=slideOutVert) {
        EditorFormatBar(actions, activeFormats, onOpenOverflow = { formatMenuVisible = true })
      }
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) {
      EditorMeta(breadcrumb = listOf(folderName), relativeDate = formatNoteDate(state.updatedAt))
      TagChipsExpandable(expanded = tagsExpanded, tags, onRemoveTag, onAddTagClick = { tagPickerVisible = true })
      HorizontalDivider(color = outlineVariant)
      Box(Modifier.weight(1f)) { RichNoteBody(...) /* existing */ }
    }
  }
  ```
- Удалить старые `FloatingCapsule` обёртки в скрине. `MarkdownFormatMenu` остаётся как overflow popup.
- Wire IME visibility: `WindowInsets.isImeVisible` (через `androidx.compose.foundation.layout.imeAnimationTarget` + `isImeVisible`).
- TestTag preservation: `EditorBack`, `EditorPin`, `EditorDelete`, `EditorTagToggle` остаются.
- Build + tests.
- Commit.

### Task 5: RichNoteBody — Inter body + Serif H1 first-line
- Modify: `components/RichNoteBody.kt` — `setTypeface(ResourcesCompat.getFont(ctx, R.font.inter_variable))`.
- Вызов `applyFirstLineSerifH1`:
  - после `setMarkdownInternal()` initial load,
  - в `TextWatcher.afterTextChanged()` для re-apply при перемещении H1 (debounced 50ms).
- Update `res/layout/rich_note_body.xml`: `textColor=?attr/colorOnSurface`, `textColorHint=?attr/colorOnSurfaceVariant`, `textSize=15sp`, `lineSpacingExtra=4dp`, background `?attr/colorBackground`.
- Carefully test: write a Robolectric test that asserts spans are applied (mock Aztec parse).
- Commit.

---

## Chunk 3: Cleanup + polish

### Task 6: TableBlockEditor — token colors
- Modify: hardcoded → `colorScheme.surfaceContainer` / `outlineVariant`.
- Commit.

### Task 7: Quote/Checklist span tokens
- New: `components/TesseraQuoteSpan.kt` (left 3dp bar primary, italic InstrumentSerif).
- Wire into Aztec custom span attributes (see `MarkdownHtmlConverter`).
- Checklist checked-fill → `colorScheme.primary` in `res/values/colors.xml`.
- Commit.

### Task 8: Build + tests + lint
- `./gradlew clean :app:assembleDebug :app:testDebugUnitTest`
- Smoke launch: открыть редактор, проверить first-line H1 serif, format bar появляется при keyboard up.
- Commit.

---

## Acceptance criteria

- [x] `EditorMeta`, `EditorFormatBar`, `SerifH1Spanner` существуют и проходят тесты
- [x] `NoteEditorScreen` использует `TesseraTopBar` + IME-aware `EditorFormatBar`, без `FloatingCapsule` обёрток
- [x] `RichNoteBody` рендерит Inter body, Instrument Serif для первого H1
- [x] `TableBlockEditor` использует MaterialTheme tokens (surfaceContainer / outlineVariant)
- [x] Build green, all unit tests pass

## Execution notes (2026-06-22)

- **Format-bar scope (user decision):** добавлены лёгкие Aztec-форматы — underline, ordered
  list, quote, inline code, horizontal rule. `RichBodyActions` расширен; `MarkdownHtmlConverter`
  получил полный round-trip для всех пяти (underline хранится как литеральный `<u>`).
- **EditorMeta data:** показывает относительную дату (`note.updatedAt`, проведено в state/VM).
  Хлебные крошки папки скрыты — связь заметка↔папка идёт через `NoteFolderCrossRef`, редактор-VM
  папки не наблюдает; проводка breadcrumb отложена в отдельную задачу.
- **Quote/checklist:** левый primary-бар 3dp + код/чек-лист — через нативные атрибуты AztecText
  (надёжнее кастомных спанов). Italic Instrument Serif для текста цитаты и primary-заливка
  чекбокса отложены (нет прямых атрибутов, нужен fragile custom span/drawable).
- **Tests:** новые unit-тесты — `EditorMetaRenderTest`, `EditorFormatBarRenderTest`,
  `SerifH1SpannerTest`, + round-trip покрытие конвертера. Aztec-under-Robolectric render-тест
  для `RichNoteBody` опущен (библиотека не инстанцируется в unit-окружении). Устаревший
  `NoteEditorScreenTest.markdownToolbarRendersAllFormattingButtons` удалён (бар теперь IME-gated).
- **Lint:** новых ошибок нет (добавлены ru-переводы). Предсуществующие 9 ошибок
  (`NotificationPermission` из Aztec, `ProduceState` в MainActivity, `note_group_*` переводы из
  Plan 04) — вне объёма.
- **Не сделано:** интерактивный smoke-запуск на устройстве (нет эмулятора в среде).

## Next plan

`Plan 06` — SettingsScreen redesign.
