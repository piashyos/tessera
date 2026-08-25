# 05 — Экраны, навигация, UX

> Все экраны и взаимодействия. Информационная архитектура и набор действий = как в Android, но вёрстка и паттерны — идиоматичные macOS (NavigationSplitView, toolbar, контекстные меню, popover вместо bottom sheet, без FAB/свайпов как основного механизма).

---

## 5.1. Структура навигации

Android: drawer + 3 экрана (List / Editor / Settings) с Navigation-Compose.

**macOS: `NavigationSplitView` (две/три колонки) + Settings scene.**

```
NavigationSplitView {
    Sidebar()                 // системные разделы + дерево папок (левая колонка)
} content: {
    NotesListColumn()         // список заметок выбранного раздела (средняя колонка)
} detail: {
    NoteEditorView() / EmptyDetail()   // редактор выбранной заметки (правая колонка)
}
```

- **Трёхколоночный layout** — естественный для Mac «mail-style» (sidebar → список → детально). Это решает и проблему «editor как отдельный экран».
- Колонки скрываются/показываются (toolbar toggle, ⌘-сочетания).
- Settings — отдельная **Settings scene** (открывается ⌘, и через меню Tessera → Settings…), нативное окно настроек.
- Поиск — не отдельный полноэкранный overlay (как Android), а **search field в toolbar** списка + режим результатов в средней колонке. (Альтернатива — `.searchable`.)

---

## 5.2. Sidebar (левая колонка) — замена Drawer

Список разделов (`List` с `selection`):

**Системные разделы (сверху):**
1. **All Notes** — `tray` (Inbox) — все неархивные.
2. **Pinned** — `pin`.
3. **Archive** — `archivebox`.
4. **Trash** — `trash`.
5. **Shared** — `person.2` — **показывать только если есть расшаренные** (`hasSharedNotes`).

**Папки (секция «Folders»):**
- дерево: корневые папки, дети — один уровень.
- каждая папка: иконка `folder` + имя; контекстное меню (right-click): «New subfolder», «Rename», «Delete» (с подтверждением).
- кнопка «New Folder» (+ в заголовке секции или кнопка снизу).

**Низ sidebar:**
- Аккаунт-строка (аватар + имя или «Not signed in») → открывает Settings (или меню).
- (Settings также доступен через ⌘, и меню-бар.)

Выбор раздела/папки управляет содержимым средней колонки и заголовком.

---

## 5.3. Средняя колонка — список заметок

### Toolbar (верх колонки / окна)
- **Заголовок раздела:** «All Notes» / «Pinned» / «Archive» / «Trash» / «Shared» / имя папки / «N selected».
- **Search field** (`.searchable` или кастомный) — фильтр по тексту (debounce 300ms).
- **Фильтр по тегу** — кнопка/`Menu` (tint primary если активен), single-select. (В Android — ряд чипов; на Mac — popover/menu со списком тегов, либо строка чипов под toolbar.)
- **New Note** — кнопка `square.and.pencil` (вместо FAB). Также ⌘N.
- В режиме выделения: «Move to folder» (`folder`), «Delete» (`trash`, error tint).

### Тело списка (`List` / `LazyVStack`)
**Вид:** список карточек (List). `NotesViewMode` (list/tiles) — list по умолчанию; tiles (грид) можно реализовать через `LazyVGrid` (Android имел enum, но UI только list — на Mac можно дать переключатель вида как улучшение, опционально).

**Группировка по дате (`groupNotesByDate`)** — секции с заголовками:
1. **Pinned** (isPinned, без даты)
2. **Today**
3. **Yesterday**
4. **This Week** (с понедельника)
5. **This Month** (с 1-го)
6. **Earlier**

Заголовок секции: titleMedium semibold.

**Пустые состояния:**
- нет заметок: «No notes yet» / «No results for "{query}"» + подсказка.
- загрузка: `ProgressView`.

### Карточка заметки (NoteCard)
Содержит:
- **левая полоса** (3pt primary) если pinned;
- **title** (1 строка, эллипсис);
- **preview** (1 строка, Markdown очищен до плейн-текста);
- **теги** (до 3 инлайн-чипов, «+N» при переполнении);
- **аватары участников** (стек) если shared;
- **дата** справа.

Состояние выделения (selection mode): фон `secondaryContainer` + рамка 2pt primary.

### Действия с заметкой (macOS-идиоматично)
Android: свайпы (archive/delete) + long-press → bottom sheet. На macOS:
- **Контекстное меню (right-click)** на карточке — основной механизм. Пункты (NoteActionsSheet → context menu):
  - Open
  - Select (вход в режим выделения)
  - Pin / Unpin
  - Move to Folder… (submenu со списком папок)
  - Add Tags… (submenu/popover)
  - Duplicate
  - Archive / Unarchive
  - Make Shared (если owner && !shared && !trash && !archive)
  - — divider —
  - Delete (destructive)
- **Свайп-действия** (опционально, нативные `.swipeActions` в List) — Archive (leading) и Delete (trailing) как дополнение, не единственный путь. Delete отключён для shared без прав (`swipeToDeleteEnabled=false`).
- **Trash-режим:** контекстное меню = Restore + Delete Permanently.
- **Двойной клик / Enter** — открыть в редакторе (detail).

### Режим выделения (batch)
- Вход: через контекстное меню «Select» или ⌘-клик/Shift-клик (множественный выбор `List selection`).
- Заголовок toolbar: «N selected».
- Действия: Delete (batch, с undo), Move to folder, Restore (в trash).
- Выход: Esc / «Done».

### Undo
- После soft-delete/archive — показать **undo** (нативно: `UndoManager` + toast/notification, либо строка с «Undo» внизу списка). На Mac предпочтительно интегрировать с `UndoManager` (⌘Z).

---

## 5.4. Правая колонка — редактор

Полностью описан в **doc 04**. Здесь — навигационная обвязка:

- **Toolbar редактора (в окне):**
  - Pin/Unpin (если canEditContent).
  - Overflow `…` меню:
    - Manage access (если shared)
    - Make private / unshare (если shared && owner)
    - Leave note (если shared && non-owner) — с подтверждением
    - Delete for everyone (если owner/manager) — с подтверждением, destructive
    - Make shared (если private && owner)
    - — divider —
    - Delete note (destructive)
  - Кнопки форматирования (Format toolbar, doc 04) — либо в этом же toolbar, либо floating.
- **EditorMeta** (крошки папки + дата), **TagChips**, **тело** (RichTextView/блоки).
- **MembersSheet** — на Mac как `.sheet` или отдельная панель: список участников (аватар, имя, email, бейдж Owner, селектор роли, кнопка remove), секция инвайта (email-поле + кнопка + статусы).
- Закрытие/смена выбранной заметки → `flush()` автосейва.

**Пустой detail:** если ничего не выбрано — placeholder («Select a note» / «Create a note»).

---

## 5.5. Поиск

Android — полноэкранный overlay. macOS — интеграция в список + расширенные фильтры.

- **Search field** в toolbar средней колонки (debounce 300ms).
- **Фильтры** (popover «Filters» или строка под полем):
  - Folder: All / конкретная
  - Tag: — / конкретный
  - Period: All / Today / Week / Month (`DatePeriod`)
- **Подсказки при пустом запросе** (опц. панель):
  - Recent (до 8, MRU; удаление по × ; «Clear history»)
  - Popular tags (тап → фильтр по тегу)
- Сабмит запроса → добавить в recentSearches.
- Результаты — тот же список карточек, отфильтрованный.

> Можно также завести глобальный ⌘F (фокус в search field) и ⌘⇧F (расширенный поиск с фильтрами).

---

## 5.6. Settings (Settings scene)

Нативное окно настроек (вкладки или список секций). Секции:

### Account
- Аватар + имя + email (или «Not signed in» + «Sign in to sync your notes across devices.»).
- **Sign in with Google** (если не вошёл; спиннер при загрузке).
- **Sign out** (если вошёл).
- Сообщение об ошибке.

### Sync (только если вошёл)
- Статус: Synced ✓ / Syncing… / Offline / Sync failed (+ Retry).
- «Last synced N minutes ago».

### Appearance
- **Theme:** System / Light / Dark (picker/segmented).
- ~~Dynamic color~~ — **опускаем на macOS** (нет API обоев; см. doc 06).
- **Language:** System default / English / Русский.

### Data
- Export notes (см. doc 08 — реализовать, в Android placeholder).
- Import… (placeholder → реализовать на Mac, doc 08).
- Empty trash (destructive; реализовать).

### About
- «Tessera 1.0.0» (версия).
- Open source licenses.

### Account switch dialog
При входе с данными другого аккаунта:
- «Different account» — «These notes belong to another account. Move them to the new account, or delete them locally and start fresh.»
- Кнопки: Move to new account / Delete local notes / Cancel.
- (Логика — doc 03 §3.8.)

---

## 5.7. Состояния (State-структуры)

`NotesListState` (как Android, sealed → в Swift enum с ассоциированными данными или struct + phase):
```swift
struct NotesListState {
    var phase: Phase                 // .loading / .empty(query, availableTags) / .content
    var notes: [Note] = []
    var pinnedCount: Int = 0
    var selection: Set<NoteID> = []
    var selectionMode: Bool = false
    var availableTags: [String] = []
    var folders: [Folder] = []
    var folderTree: [FolderTreeNode] = []
    var selectedFolderId: FolderID? = nil
    var activeSection: Section        // all/pinned/archive/trash/shared/folder
    var searchQuery: String = ""
    var selectedTagFilter: String? = nil
    var datePeriod: DatePeriod = .all
    var recentSearches: [String] = []
    var hasSharedNotes: Bool = false
    var syncStatus: SyncStatus = .idle
    enum Phase { case loading, empty, content }
}
```

---

## 5.8. Относительные даты (NoteDateFormat)

`formatNoteDate(epochMs, today, zone, locale, todayLabel, yesterdayLabel) -> String`:
- сегодня → «Today» / «Сегодня»
- вчера → «Yesterday» / «Вчера»
- этот год → «2 Dec» / «2 дек»
- иначе → «2 Dec 2023»

Реализация: `Calendar` + `DateFormatter`/`Date.FormatStyle`, локаль из настроек. Подписи берём из String Catalog.

---

## 5.9. Маппинг Android-паттернов → macOS

| Android | macOS |
|---------|-------|
| Navigation Drawer | Sidebar (левая колонка NavigationSplitView) |
| FAB «создать» | toolbar `square.and.pencil` + ⌘N |
| Bottom sheet (NoteActionsSheet) | контекстное меню (right-click) |
| Swipe to archive/delete | `.swipeActions` (доп.) + контекстное меню (основное) |
| Полноэкранный SearchScreen | search field в toolbar + фильтры-popover |
| ModalBottomSheet (theme/lang/members) | `.sheet` / popover / Settings scene |
| Snackbar + Undo | `UndoManager` (⌘Z) + краткий toast |
| Long-press (haptics) | right-click |
| Top app bar | window toolbar |
| TagPickerPopup | `Popover` |

---

## 5.10. Локализация UI

- String Catalog (`.xcstrings`) с en + ru (портировать строки из Android `strings.xml` en/ru).
- Язык: из настроек (`languageTag`); пусто = системный. На macOS переключение языка в рантайме ограничено — использовать `Bundle` с выбранной локалью или `AppleLanguages` override; минимально — применить при следующем запуске + немедленно к датам/форматам через явную `Locale`.
- Даты, числа — через выбранную `Locale`.

---

## 5.11. Доступность (HIG)

- Полная клавиатурная навигация (Tab/стрелки/Enter/Esc).
- VoiceOver-метки на карточках, кнопках, чекбоксах.
- Dynamic Type (масштаб текста) — использовать relative `Font` где возможно.
- Контраст и focus ring по системным стандартам.
- Поддержка «Reduce Motion» (упрощать анимации).

---

## 5.12. Контрольный список UI

- [ ] NavigationSplitView (sidebar / list / editor) + Settings scene.
- [ ] Sidebar: 5 системных разделов (Shared условно), дерево папок, контекстные меню папок, New Folder.
- [ ] Список: группировка по дате (6 секций), пустые/загрузка состояния, карточка (полоса/title/preview/теги/аватары/дата).
- [ ] Действия заметки через контекстное меню (+ опц. swipeActions), trash-режим Restore/Delete.
- [ ] Режим множественного выделения + batch-действия + undo (⌘Z).
- [ ] Поиск: field + фильтры (folder/tag/period) + recent + popular tags.
- [ ] Редактор-обвязка: toolbar (pin, overflow с шеринг-действиями, format), MembersSheet, EditorMeta, TagChips.
- [ ] Settings: Account / Sync / Appearance / Language / Data / About + account-switch dialog.
- [ ] Относительные даты (en/ru).
- [ ] Локализация (.xcstrings en+ru), доступность.
