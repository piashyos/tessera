# 02 — Модель данных и локальное хранилище

> Локальная БД на **SwiftData** (SQLite). Точный перенос Room-схемы v6: 7 сущностей, индексы, FK, FTS-поиск, миграции, нормализация. Все алгоритмы (деривация заголовка, нормализация тегов, soft-delete) переносятся 1:1.

---

## 2.1. Обзор схемы (Room v6 → SwiftData)

Сущности Android (Room):
1. `notes` (NoteEntity)
2. `tags` (TagEntity)
3. `note_tags` (NoteTagCrossRef) — M:N заметка↔тег
4. `notes_fts` (FTS4 shadow) — полнотекстовый поиск
5. `folders` (FolderEntity) — самоссылающаяся иерархия
6. `note_folders` (NoteFolderCrossRef) — 1 папка на заметку
7. `shared_members` (SharedMemberEntity) — зеркало участников шеринга

**Ключевые принципы (общие с Android):**
- ID — строковые UUID, генерируются локально.
- Время — миллисекунды эпохи (`Int64`), не `Date` (для побитовой совместимости с Firestore/Android). Конвертация в `Date` только на слое отображения.
- Soft-delete: `deletedAt != nil`.
- Dirty-tracking: `syncedAt == nil` ⇒ ждёт push.
- `ownerId == nil` ⇒ локальный черновик (не вошедший пользователь).

> **Замечание по SwiftData и связям.** SwiftData умеет relationships, но для точного контроля (составные ключи `note_tags`/`note_folders`, каскады, FTS) проще моделировать junction'ы как самостоятельные `@Model` без неявных inverse-связей, повторяя реляционную модель Room. Каскады реализуем вручную в Store-слое (SwiftData `.cascade` deleteRule применяем где это естественно — `shared_members` при удалении note).

---

## 2.2. NoteEntity

```swift
@Model
final class NoteEntity {
    @Attribute(.unique) var id: String          // UUID (PK)
    var title: String                            // выводится из content
    var content: String                          // канонический Markdown
    var isPinned: Bool
    var createdAt: Int64                          // ms
    var updatedAt: Int64                          // ms
    var deletedAt: Int64?                         // ms, nil = активна
    var syncedAt: Int64?                          // ms, nil = pending sync
    var ownerId: String?                          // nil = локальный черновик
    var isArchived: Bool                          // default false
    var shareState: String                        // "PRIVATE" | "SHARED"
    var myRole: String?                           // OWNER/MANAGER/EDITOR/VIEWER когда SHARED

    // Каскад на участников шеринга
    @Relationship(deleteRule: .cascade, inverse: \SharedMemberEntity.note)
    var members: [SharedMemberEntity] = []

    init(...) { ... }

    static let SHARE_STATE_PRIVATE = "PRIVATE"
    static let SHARE_STATE_SHARED  = "SHARED"
}
```

**Индексы (через `#Index`, macOS 15+; на 14 — полагаемся на запросы + при необходимости raw SQLite индекс):**
- `(ownerId, updatedAt)`
- `(isPinned, updatedAt)`
- `(deletedAt)`

```swift
#Index<NoteEntity>([\.ownerId, \.updatedAt], [\.isPinned, \.updatedAt], [\.deletedAt])
```

---

## 2.3. TagEntity

```swift
@Model
final class TagEntity {
    @Attribute(.unique) var id: String     // UUID
    @Attribute(.unique) var name: String   // уникально глобально, trimmed, case-preserved
    init(...) { }
}
```

## 2.4. NoteTagCrossRef (note_tags)

Составной PK `(noteId, tagId)`. SwiftData не поддерживает составные PK напрямую — используем суррогатный уникальный ключ `"\(noteId)#\(tagId)"`.

```swift
@Model
final class NoteTagCrossRef {
    @Attribute(.unique) var key: String   // "noteId#tagId"
    var noteId: String
    var tagId: String
    init(noteId: String, tagId: String) {
        self.noteId = noteId; self.tagId = tagId
        self.key = "\(noteId)#\(tagId)"
    }
}
```
Индекс по `tagId` для обратных поисков.

## 2.5. FolderEntity (folders)

```swift
@Model
final class FolderEntity {
    @Attribute(.unique) var id: String
    @Attribute(.unique) var name: String   // уникально
    var parentId: String?                   // nil = корень (само-ссылка)
    var createdAt: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var syncedAt: Int64?
    init(...) { }
}
```
> Поведение Room: FK `parentId → folders(id)` ON DELETE SET NULL. На macOS реализуем вручную в `FolderStore.softDelete`: при удалении папки — обнулять `parentId` у детей (или каскадить soft-delete по политике; Android делает soft-delete вложенных **заметок**, см. §2.10).

## 2.6. NoteFolderCrossRef (note_folders)

`UNIQUE(noteId)` — одна папка на заметку. FK `folderId → folders(id)` ON DELETE CASCADE.

```swift
@Model
final class NoteFolderCrossRef {
    @Attribute(.unique) var noteId: String   // уникален → одна папка на заметку
    var folderId: String
    init(noteId: String, folderId: String) { ... }
}
```

## 2.7. SharedMemberEntity (shared_members)

Составной PK `(noteId, uid)` → суррогат `"noteId#uid"`. Каскад от note.

```swift
@Model
final class SharedMemberEntity {
    @Attribute(.unique) var key: String   // "noteId#uid"
    var noteId: String
    var uid: String
    var displayName: String
    var email: String
    var photoUrl: String?
    var role: String                       // OWNER/MANAGER/EDITOR/VIEWER
    var joinedAt: Int64
    var note: NoteEntity?                   // inverse для каскада
    init(...) { ... }
}
```

---

## 2.8. Полнотекстовый поиск (FTS)

Android: Room FTS4 shadow-таблица `notes_fts(title, content)` с триггерами; запрос — prefix-match `[token*]`.

**macOS — два варианта:**

### Вариант A (рекомендуемый): SQLite FTS5 напрямую
SwiftData использует SQLite под капотом, но не даёт FTS из коробки. Открываем **отдельное** SQLite-соединение (GRDB или системный `sqlite3`) к тому же файлу или к собственному индекс-файлу:

```sql
CREATE VIRTUAL TABLE notes_fts USING fts5(
    noteId UNINDEXED,
    title,
    content,
    tokenize = 'unicode61'
);
```
- Синхронизируем индекс из Store-слоя: при `upsert`/`softDelete`/`hardDelete` заметки — обновляем строку FTS (вставка/замена/удаление). (Эквивалент Room-триггеров, но в коде.)
- Запрос: токенизируем пользовательский ввод, экранируем операторы, добавляем `*` к каждому токену для prefix-match — как `toFtsQuery` в Android:
  ```
  "foo bar" → foo* bar*
  ```
- Поиск возвращает `noteId`, по которым тянем `NoteEntity` из SwiftData; фильтруем активные (`deletedAt == nil && !isArchived`).

### Вариант B: без FTS
`#Predicate { $0.title.localizedStandardContains(q) || $0.content.localizedStandardContains(q) }`. Проще, но медленнее на больших объёмах и без prefix-ранжирования. Допустимо для MVP, заменить на FTS5 при росте данных.

> **Решение:** реализовать **Вариант A** (FTS5) для паритета производительности и поведения prefix-match. `NotesFTSIndex.swift` инкапсулирует индекс.

---

## 2.9. Хранилища (Store-слой = Room DAO)

Каждый Store оборачивает `ModelContext` и повторяет методы соответствующего DAO. Reactive-методы возвращают `AsyncStream`/используются `@Query` во View.

### NoteStore (≈ NoteDao)
| Метод | Семантика |
|-------|-----------|
| `observeActive()` | `deletedAt == nil && !isArchived`, sort: isPinned desc, updatedAt desc |
| `observeArchived()` | `deletedAt == nil && isArchived` |
| `observeDeleted()` | `deletedAt != nil`, sort deletedAt desc |
| `observeShared()` | `shareState == SHARED` |
| `observe(id:)` | один поток заметки |
| `search(query:)` | через NotesFTSIndex → активные |
| `get(id:)` | one-shot |
| `getPendingSync()` | `syncedAt == nil` |
| `getSharedNotes()` | `shareState == SHARED` |
| `currentOwnerId()` | distinct ownerId limit 1 |
| `hasForeignNotes(uid:)` | exists note с ownerId != uid и не в shared_members(uid) |
| `upsert(_:)` / `upsertAll(_:)` | insert-or-replace |
| `softDelete(id:ts:)` | deletedAt=ts, updatedAt=ts, syncedAt=nil |
| `restore(id:ts:)` | deletedAt=nil, updatedAt=ts, syncedAt=nil |
| `setPinned(id:_:ts:)` | + clear syncedAt |
| `setArchived(id:_:ts:)` | + clear syncedAt |
| `setShareState(id:_:myRole:)` | сохраняет таймстемпы |
| `adoptOwnerAndShareState(id:ownerId:shareState:myRole:)` | для легаси-заметок |
| `markSynced(id:ts:)` | syncedAt=ts |
| `markPendingSync(id:ts:)` | syncedAt=nil, updatedAt=ts |
| `hardDelete(id:)` | удалить строку (+ FTS, + cross-refs) |
| `adoptLocalNotes(uid:)` | ownerId=uid where ownerId==nil, syncedAt=nil |
| `reassignAllNotes(uid:)` | ownerId=uid для всех |
| `deleteByOwner(uid:)` | удалить где ownerId==uid |
| `purgeOldTombstones(threshold:)` | удалить deletedAt < threshold |

### TagStore, FolderStore, SharedMemberStore — аналогично DAO (см. doc 00 research-карту). Ключевые:
- `SharedMemberStore.replaceForNote(noteId:, members:)` — **транзакция**: удалить все + вставить пакет (атомарная замена зеркала). В SwiftData оборачиваем в один `try context.save()`.
- `FolderStore.observeFolderTree()` — строим дерево: корни (parentId==nil) + дети по parentId (один уровень).

---

## 2.10. Ключевые бизнес-алгоритмы (pure, перенос 1:1)

### Деривация заголовка из content
Android выводит `title` из content при каждом `updateNote`. Логика (восстановить точно из `NotesRepositoryImpl`/util): первая непустая строка, очищенная от Markdown-маркеров заголовка (`#`), trimmed. Перенести как `deriveTitle(from content: String) -> String`.

### Нормализация тегов
`normalizeTags`: trim каждого, удалить пустые, dedupe, сортировка **case-insensitive**. Перенести как чистую функцию. Применяется и к `[Tag]`, и к `[String]`.

### replaceTags(note)
1. Очистить все cross-refs заметки.
2. Найти существующие теги по именам, создать недостающие (insert-or-ignore по `name`).
3. Связать все теги с заметкой.

### updateNote
```
if note.content.isBlank → hardDelete(note) ; return   // пустая заметка удаляется
title = deriveTitle(content)
tags = normalizeTags(tags)
updatedAt = clock.nowMs(); syncedAt = nil
upsert(entity); replaceTags(note); requestSync()
```

### createNote(ownerId:)
```
id = UUID(); now = clock.nowMs()
NoteEntity(title:"", content:"", isPinned:false, createdAt:now, updatedAt:now,
           deletedAt:nil, syncedAt:nil, ownerId:ownerId, isArchived:false,
           shareState:"PRIVATE", myRole:nil)
upsert; requestSync; return domain Note
```
> ВАЖНО (commit `45ba903`): при создании заметки залогиненным пользователем **ставить `ownerId`** сразу, иначе заметка не шарится.

### deleteFolder(id)
soft-delete папки **и** soft-delete всех заметок в этой папке (Android `FolderRepositoryImpl.deleteFolder`). Перенести.

### Blank-draft фильтрация
`observeNotes()` фильтрует пустые черновики (blank content) из выдачи списка.

---

## 2.11. Маппинг Entity ↔ Domain ↔ Remote

`NoteMapper` (см. doc 03 для Remote DTO):
- `toDomain(entity, tagNames, members)` → `Note` (строит `ShareState` из `shareState`/`myRole`/members).
- `toEntity(note)` → `NoteEntity` (раскладывает ShareState в строки).
- `toRemote(entity, tagNames, folderId)` → `RemoteNote`.
- `toEntity(remote, ownerId, syncedAt)` → `NoteEntity`.

`SharedNoteMapper`:
- `myRole(remote, myUid)` → `ShareRole` (или `.viewer` по умолчанию).
- `toEntity(remote, myUid, syncedAt)` → всегда `shareState=SHARED`.
- `toMemberEntities(remote)` → `[SharedMemberEntity]`.
- `toRemoteContent(entity, tagNames)` → `RemoteSharedNote` (members/memberIds пустые — контент-only push).

---

## 2.12. Миграции

Android прошёл миграции 1→6. На macOS стартуем сразу со **схемы v6** (нет легаси-данных на новом клиенте), НО:
- Закладываем `VersionedSchema` + `SchemaMigrationPlan` инфраструктуру SwiftData с самого начала, чтобы будущие изменения были безопасны.
- Текущая стартовая схема = `SchemaV1` (соответствует Room v6 по полям).
- DataStore-миграция темы (легаси `LIGHT_STATIC`/`DARK_STATIC` → theme + dynamicColor) на macOS **не нужна** (нет легаси prefs); просто реализуем целевую модель (см. doc 06 — dynamicColor опускаем).

```swift
enum SchemaV1: VersionedSchema {
    static var versionIdentifier = Schema.Version(1, 0, 0)
    static var models: [any PersistentModel.Type] {
        [NoteEntity.self, TagEntity.self, NoteTagCrossRef.self,
         FolderEntity.self, NoteFolderCrossRef.self, SharedMemberEntity.self]
    }
}
enum TesseraMigrationPlan: SchemaMigrationPlan {
    static var schemas: [any VersionedSchema.Type] { [SchemaV1.self] }
    static var stages: [MigrationStage] { [] }
}
```

---

## 2.13. UserPreferences (DataStore → UserDefaults)

```swift
@Observable @MainActor
final class UserPreferences {
    // theme
    var themeMode: ThemeMode { get set }          // AUTO/LIGHT/DARK
    // dynamicColor — на macOS опускаем (см. doc 06)
    var languageTag: String { get set }           // "" = system, "en", "ru"
    var notesViewMode: NotesViewMode { get set }  // list / tiles
    var recentSearches: [String] { get }          // max 8, MRU

    func addRecentSearch(_ q: String)             // dedupe, cap 8
    func removeRecentSearch(_ q: String)
    func clearRecentSearches()
}
```
Хранилище: `UserDefaults.standard` (или suite для App Group). Ключи: `theme_mode`, `language_tag`, `notes_view_mode`, `recent_searches` (newline-joined, как в Android).

---

## 2.14. Clock

```swift
protocol Clock: Sendable { func nowMs() -> Int64 }
struct SystemClock: Clock { func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) } }
```
Используется везде, где Android вызывает `clock.nowMs()` — для тестируемости merge/sync.

---

## 2.15. Контрольный список переноса данных

- [ ] 6 `@Model` сущностей с точными полями и типами (`Int64` для времени).
- [ ] Суррогатные уникальные ключи для составных PK (`note_tags`, `shared_members`).
- [ ] Каскад note → shared_members.
- [ ] Ручной каскад folder → note_folders, folder → soft-delete заметок.
- [ ] FTS5-индекс + синхронизация из Store.
- [ ] Все методы Store по таблице §2.9.
- [ ] Pure-функции: deriveTitle, normalizeTags, replaceTags, toFtsQuery.
- [ ] Мапперы Entity↔Domain↔Remote.
- [ ] VersionedSchema/MigrationPlan скелет.
- [ ] UserPreferences на UserDefaults.
- [ ] Clock-абстракция.
