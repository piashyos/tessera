# Material You Redesign — Plan 06: SettingsScreen redesign

> **For agentic workers:** Use superpowers:executing-plans.

**Goal:** Переписать `SettingsScreen` на `LargeTopAppBar` + Tessera-секции (`SettingsGroup`, `SettingsRow`, `SyncStatusRow`, `ThemePickerSheet`). Удалить consuming of `FloatingCapsule` в settings.

**Spec:** §7.

**Что НЕ делает:**
- НЕ удаляет `FloatingCapsule` глобально (editor TagPickerPopup, TagChipsExpandable, FolderNamePopup ещё используют). После Plans 05/06 — отдельный chore-задача для удаления.
- НЕ реализует Export/Import flows — placeholder rows.

---

## Chunk 1: Leaf composables

### Task 1: SettingsGroup
- New: `components/SettingsGroup.kt` — `Column { surfaceContainer rounded 16dp; content with auto-divider between rows }`.
- API: `@Composable fun SettingsGroup(label: String? = null, content: @Composable ColumnScope.() -> Unit)`.
- Section label (если есть) = primary 10sp uppercase, padding-bottom 8dp.
- Commit.

### Task 2: SettingsRow
- New: `components/SettingsRow.kt`
- API: `SettingsRow(leadingIcon, title, subtitle?, trailing: { Value | Switch | Chevron }, onClick)`
- 56dp min height, padding 16dp.
- Commit.

### Task 3: SyncStatusRow + SyncIndicator
- New: `core/sync/SyncIndicator.kt` (sealed class Ok/Syncing/Offline/Error).
- New: `core/sync/SyncManager.kt` — `syncStatusFlow: Flow<SyncIndicator>` (combine WorkManager `WorkInfo`, `ConnectivityManager`, `SyncRepository.lastSync`/`lastError`).
- New: `components/SyncStatusRow.kt` — special-case `SettingsRow` rendering for `SyncIndicator`.
- Tests: VM-level test with fake flows.
- Commit.

### Task 4: ThemePickerSheet
- New: `components/ThemePickerSheet.kt` — `TesseraBottomSheet` with theme radios (System/Light/Dark) + dynamic color switch (SDK≥31) + preview card.
- Commit.

---

## Chunk 2: Screen rewrite

### Task 5: Rewrite SettingsScreen
- Modify: `SettingsScreen.kt` — `LargeTopAppBar` (collapsible, Settings title в `Instrument Serif headlineLarge 28sp`) + `LazyColumn` секций:
  - `[ACCOUNT]` — `ProfileCard` (avatar + name + email + signout button)
  - `[SYNC]` — `SyncStatusRow`
  - `[APPEARANCE]` — Theme picker, Dynamic color switch, Editor font size
  - `[DATA]` — Export/Import/Empty trash
  - `[ABOUT]` — Version + open source licenses
- Drop all `FloatingCapsule` usages в settings.
- Commit.

### Task 6: Hook SettingsViewModel state
- VM exposes `syncStatus: StateFlow<SyncIndicator>` from `SyncManager`.
- Tests updated.
- Commit.

---

## Chunk 3: Cleanup

### Task 7: Build + tests
- `./gradlew clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
- Smoke launch: settings opens, theme picker works, sync status updates.
- Commit.

---

## Acceptance criteria

- [x] `SettingsGroup`, `SettingsRow`, `SyncStatusRow`, `ThemePickerSheet` существуют (+ `LanguagePickerSheet`)
- [x] `SettingsScreen` использует только Tessera-композы (FloatingCapsule удалён из settings)
- [x] `SyncManager` combines WorkManager + connectivity (см. notes — `lastSync`/`lastError` не реализованы)
- [x] Settings smoke green (`:app:assembleDebug` + `:app:testDebugUnitTest` зелёные; `lintDebug` — только 9 предсуществующих ошибок)

## Execution notes (2026-06-22)

Расхождения плана/спеки с кодом и принятые решения (подтверждены пользователем):

- **Sync (вопрос 1 → вариант A).** Спека ссылалась на `SyncRepository.lastSync`/`lastError` — этих
  полей не существует. `SyncIndicator` выводится чистым маппером из состояния уникальной
  WorkManager-работы (`getWorkInfosForUniqueWorkFlow("tessera-sync")`) + нового
  `ConnectivityObserver`. Флоу добавлен в **существующий** `app/tessera/sync/SyncManager`
  (реализует новый `SyncStatusProvider`), а не во второй класс `core/sync/SyncManager`.
  `Ok.lastSyncAtMs` — best-effort (WorkInfo не отдаёт точное время завершения).
- **Тема (вопрос 2 → вариант A).** `ThemePickerSheet` адаптирован под текущий 5-значный
  `ThemeMode` (System=`AUTO` + dynamic-свитч переключает `_DYNAMIC`/`_STATIC`); рефактора префов нет.
- **APPEARANCE/DATA/ABOUT (вопрос 3).** Editor font size не добавлялся (нет префа). Language
  сохранён как row → `LanguagePickerSheet`. Export/Import/Empty trash/Licenses — placeholder-строки.
- Старые `ThemePicker`/`LanguagePicker` (grid tiles) и их строки удалены.

## Next

`Plan 07` — QA + testing pass.
