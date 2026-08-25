# 08 — План реализации, тестирование, критерии приёмки

> Фазовый roadmap порта. Каждая фаза — самодостаточный инкремент с критериями готовности. Порядок выбран так, чтобы как можно раньше получить работающий local-first каркас, затем синк, затем шеринг.

---

## 8.1. Принципы

1. **Local-first сначала.** Сперва приложение работает офлайн (CRUD + редактор + поиск), потом подключаем синк, потом шеринг. Точно как эволюция Android (миграции 1→6).
2. **Pure-логика — через тесты.** Конвертер Markdown, нормализация тегов, деривация заголовка, merge/sync, разрешения ролей — покрываем юнит-тестами до/во время реализации (перенести Android-тесты как контрольные).
3. **Контракт бэкенда — священен.** Любое изменение схемы Firestore/правил запрещено. Тестировать на тестовом аккаунте против реального проекта `notes-d3abc` (или эмулятора Firestore с теми же правилами).
4. **Каждая фаза мержится отдельно**, с прохождением acceptance-чеклиста.

---

## 8.2. Фазы

### Фаза 0 — Каркас проекта
- Xcode-проект, таргет macOS 14, SPM-зависимости (Firebase, GoogleSignIn).
- Структура каталогов (doc 01), `AppContainer`, `AppDelegate`, `TesseraApp` (пустой NavigationSplitView).
- Бандл шрифтов, Color Sets, `TesseraFont/Color/Spacing/Shape/Icon` (doc 06).
- `GoogleService-Info.plist`, Info.plist (URL scheme), entitlements, App Sandbox.
- **DoD:** приложение запускается, показывает пустой трёхколоночный layout, тема переключается.

### Фаза 1 — Локальная модель данных
- 6 `@Model` сущностей, `SwiftDataStack`, `VersionedSchema` (doc 02).
- Store-слой (NoteStore/TagStore/FolderStore/SharedMemberStore) — все методы.
- FTS5-индекс (`NotesFTSIndex`) + синхронизация из Store.
- Pure-функции: deriveTitle, normalizeTags, replaceTags, toFtsQuery, Clock.
- UserPreferences (UserDefaults).
- **DoD:** юнит-тесты на Store CRUD, FTS-поиск, нормализацию; деривацию заголовка.

### Фаза 2 — Markdown-движок
- `Markdown ⇄ [InlineDoc]` (чистые функции, диалект Android) — doc 04 §4.7.
- `[InlineDoc] ⇄ NSAttributedString`.
- GFM-таблицы: TableGrid, parse/serialize, split/join блоков.
- **DoD:** round-trip тесты на корпусе (все блочные/инлайн форматы, таблицы, пустые строки, edge-cases из Android-тестов). Расхождений с Android-диалектом нет.

### Фаза 3 — Notes List + базовый CRUD (local-only)
- Sidebar (системные разделы + дерево папок) — doc 05.
- Список: группировка по дате, карточки, пустые/загрузка.
- Контекстные меню заметок: create/pin/archive/delete/restore/duplicate/move/tags.
- Папки: создать/переименовать/переместить/удалить (+ soft-delete вложенных заметок).
- Поиск: field + фильтры (folder/tag/period) + recent + popular tags.
- Режим выделения + batch + Undo (⌘Z).
- **DoD:** полный офлайн-CRUD, поиск, фильтры, папки, теги работают; ownerId=nil.

### Фаза 4 — Редактор
- `RichTextView` (NSTextView), toolbar, format-команды, активные форматы.
- Чек-боксы (attachment toggle), таблицы (TableBlockEditor с ресайзом), block-stack path.
- Автосейв 500ms, flush при закрытии, удаление пустой, no-op guard.
- TagChips/popover, EditorMeta (крошки+дата).
- Меню Format + горячие клавиши (doc 07).
- **DoD:** создание/редактирование/форматирование заметки 1:1 с Android; контент совместим (открыть на Android — без искажений).

### Фаза 5 — Авторизация + синк (private)
- Firebase macOS App, GoogleSignIn, Auth state listener, persist.
- DTO + Firestore-клиенты (notes/folders), мапперы.
- SyncRepository: push/pull/merge (private + folders), tombstone purge, permanentlyDelete.
- SyncManager (adoption, account isolation), SyncScheduler, NWPathMonitor.
- Realtime listeners (notes/folders).
- Settings: Account (sign in/out), Sync status, account-switch dialog.
- **DoD:** вход Google, заметки/папки синхронизируются с Android-клиентом двусторонне; LWW; офлайн-правки догоняют; смена аккаунта изолирует данные.

### Фаза 6 — Шеринг
- sharedNotes DTO/клиент, userDirectory (usersByEmail lookup).
- SharedNotesGateway: share/unshare/invite/changeRole/removeMember/leave.
- mergeRemoteShared (eviction, rollback-on-downgrade, member mirror).
- shared realtime listener.
- UI: MembersSheet, overflow-действия редактора, роли, read-only для Viewer, аватары на карточке, раздел Shared в sidebar.
- Соблюдение security rules (content-only push, leftSelf, атомарные батчи).
- **DoD:** полный сценарий шеринга кросс-платформенно (Android ↔ Mac): инвайт, роли, выход, unshare, delete-for-all; права соблюдаются; rules не отклоняют легальные операции.

### Фаза 7 — Платформенная полировка
- Полное меню-бар + все горячие клавиши.
- Drag&Drop (заметка→папка/trash/archive, импорт/экспорт через Finder).
- Settings: Appearance (theme/language), About, Open source licenses.
- Доступность (VoiceOver, Dynamic Type, full keyboard access, Reduce Motion), локализация (en/ru .xcstrings).
- **DoD:** HIG-чеклист пройден; VoiceOver-проход основных сценариев; ru/en переключаются.

### Фаза 8 — Фичи, отсутствовавшие в Android, + релиз
- **Export notes** (в Android placeholder): экспорт всех/выбранных заметок в `.md` (zip с папками-структурой) через NSSavePanel.
- **Import** `.md`/`.txt` через NSOpenPanel/drop.
- **Empty trash** (реальная реализация: permanentlyDelete всех в Корзине).
- Privacy manifest, нотаризация, App Store метаданные/иконка `.icns`.
- **DoD:** экспорт/импорт/очистка корзины работают; сборка подписана и нотаризована.

---

## 8.3. Тестирование

### Юнит-тесты (Swift Testing / XCTest) — перенести из Android где есть
| Область | Что проверять |
|---------|---------------|
| MarkdownConverter | round-trip всех форматов; диалект совпадает с Android (golden-файлы) |
| TableParser | GFM parse/serialize, метаданные, экранирование `\|`, невалидные → nil |
| Tags | normalizeTags (trim/dedupe/sort case-insensitive) |
| Title | deriveTitle из разных content |
| FTS | toFtsQuery (prefix, экранирование операторов) |
| SyncRepository | mergeRemote (LWW, skip-shared), mergeRemoteShared (eviction, rollback-on-downgrade, member mirror) — с mock-Clock и in-memory store |
| ShareRole | canEditContent/canDeleteForAll/canManageAccess/canLeave |
| Account | hasForeignData, adoption, isolation |

### Интеграционные
- Против **Firebase Local Emulator Suite** с теми же `firestore.rules` — проверить, что все клиентские операции проходят/отклоняются правильно (особенно sharedNotes: content-only push, leftSelf, delete-for-all по ролям).
- Кросс-платформенный тест: создать/изменить на Android, проверить на Mac, и наоборот.

### UI / E2E
- XCUITest на ключевые сценарии: создать заметку, форматировать, искать, расшарить, сменить роль.
- Snapshot-тесты карточек/редактора (light/dark).

### Ручной чеклист совместимости
- [ ] Заметка с таблицей, созданная на Android, открывается на Mac без искажений и наоборот.
- [ ] Все Markdown-форматы round-trip между платформами.
- [ ] Tombstone (удаление) распространяется и очищается через 30 дней.

---

## 8.4. Риски и решения

| Риск | Решение |
|------|---------|
| Markdown-диалект разойдётся с Android | Golden-тесты + общий корпус; перенести Android unit-тесты конвертера дословно |
| SwiftData ограничения (составные PK, FTS, живые стримы) | Суррогатные ключи, отдельный SQLite FTS5, явные стримы из Store (doc 02) |
| Firestore Int64 round-trip | Тесты сериализации; явная обработка number |
| Security rules отклонят легальные операции | Тесты против эмулятора; строго content-only push и leftSelf (doc 03) |
| NSTextView сложность (чекбоксы, блоки) | InlineDoc-промежуточная модель; attachment для чекбоксов; инкрементально |
| Google Sign-In на macOS (redirect/URL scheme) | REVERSED_CLIENT_ID в Info.plist; тест на чистой машине |
| Конкурентность Swift 6 + SwiftData Sendable | ModelContext на фоновом акторе, передавать PersistentIdentifier |

---

## 8.5. Definition of Done (весь порт)

- [ ] Все фичи из матрицы паритета (doc 00 §0.5) реализованы или явно отмечены как осознанно опущенные (dynamicColor).
- [ ] Двусторонняя синхронизация с Android-клиентом на одном аккаунте без потерь и искажений.
- [ ] Шеринг полностью функционален кросс-платформенно, security rules соблюдаются.
- [ ] HIG: меню-бар, горячие клавиши, окна, drag&drop, доступность, тёмная тема.
- [ ] Локализация en/ru.
- [ ] Тесты (unit + emulator + UI) зелёные.
- [ ] Сборка подписана/нотаризована, App Sandbox + Privacy manifest.

---

## 8.6. Оценочная последовательность зависимостей

```
Ф0 каркас
 └─ Ф1 данные ──┬─ Ф2 markdown ── Ф4 редактор ─┐
                └─ Ф3 список/CRUD ──────────────┤
                                                 ├─ Ф5 auth+sync ── Ф6 шеринг ── Ф7 платформа ── Ф8 релиз
```
Ф2 и Ф3 можно вести параллельно после Ф1. Ф4 требует Ф2+Ф3. Синк (Ф5) требует Ф1 (модель) и желательно Ф3/Ф4 (есть что синкать).
