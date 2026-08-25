# 01 — Архитектура приложения

> Слои, структура проекта, поток данных, DI. Цель — повторить чистую слоистую архитектуру Android-версии (domain ↔ data ↔ ui), но на идиоматичном Swift/SwiftUI.

---

## 1.1. Архитектурный стиль

**MVVM + Clean-слои + однонаправленный поток данных (UDF).**

Android-версия построена по Clean Architecture:
- `domain/` — модели и интерфейсы репозиториев (pure Kotlin),
- `data/` — реализации (Room + Firestore), мапперы,
- `ui/` — экраны Compose + ViewModel'и со `State`,
- `core/` — preferences, текст, тема.

Порт сохраняет ту же декомпозицию. Для macOS:
- **View** — SwiftUI-вью, без логики, только рендер `State` и отправка intent'ов.
- **ViewModel** — `@Observable`-класс (`@MainActor`), держит иммутабельный `State` и публичные методы-intent'ы.
- **UseCase** (опц.) — для нетривиальных операций (share/leave/unshare с проверками), как в Android.
- **Repository** — протокол в domain, реализация в data.
- **DataSource** — SwiftData (локально) и Firestore (remote).

Поток: `View → ViewModel.intent() → UseCase/Repository → DataSource → (AsyncStream/Observation) → ViewModel.state → View`.

---

## 1.2. Целевая структура Swift-пакета

Монорепо/один Xcode-проект с модульной структурой через локальные SPM-пакеты (рекомендуется для границ слоёв и скорости сборки). Минимальный вариант — группы внутри одного таргета.

```
TesseraMac/
├── App/
│   ├── TesseraApp.swift              # @main, WindowGroup, Settings scene, Commands (меню-бар)
│   ├── AppContainer.swift            # композиционный root (DI), создаёт все зависимости
│   └── AppDelegate.swift             # NSApplicationDelegate: Firebase configure, lifecycle
│
├── Domain/                           # pure Swift, без Firebase/SwiftData импортов
│   ├── Model/
│   │   ├── Note.swift                # struct Note, NoteID
│   │   ├── Folder.swift              # Folder, FolderID, FolderTreeNode
│   │   ├── Tag.swift
│   │   ├── ShareState.swift          # enum ShareState { private, shared(members, myRole) }
│   │   ├── ShareRole.swift           # enum + computed permissions
│   │   ├── ShareMember.swift
│   │   ├── UserProfile.swift         # UserID, UserProfile
│   │   ├── DatePeriod.swift
│   │   └── SyncStatus.swift
│   ├── Repository/                   # протоколы
│   │   ├── NotesRepository.swift
│   │   ├── FolderRepository.swift
│   │   ├── SyncRepository.swift
│   │   ├── AuthRepository.swift
│   │   ├── AccountRepository.swift
│   │   └── SharedNotesGateway.swift
│   └── UseCase/
│       ├── ShareNoteUseCase.swift
│       ├── LeaveSharedNoteUseCase.swift
│       ├── UnshareNoteUseCase.swift
│       └── ...
│
├── Data/
│   ├── Local/
│   │   ├── SwiftDataStack.swift      # ModelContainer/ModelContext конфиг
│   │   ├── Entity/                   # @Model классы (см. doc 02)
│   │   │   ├── NoteEntity.swift
│   │   │   ├── TagEntity.swift
│   │   │   ├── FolderEntity.swift
│   │   │   ├── SharedMemberEntity.swift
│   │   │   └── CrossRefs.swift
│   │   ├── Dao/                      # обёртки запросов (повторяют NoteDao/TagDao/…)
│   │   │   ├── NoteStore.swift
│   │   │   ├── TagStore.swift
│   │   │   ├── FolderStore.swift
│   │   │   └── SharedMemberStore.swift
│   │   ├── FTS/
│   │   │   └── NotesFTSIndex.swift    # SQLite FTS5 (см. doc 02 §поиск)
│   │   └── Preferences/
│   │       └── UserPreferences.swift # UserDefaults-backed
│   ├── Remote/
│   │   ├── FirestoreNotesClient.swift
│   │   ├── FirestoreFoldersClient.swift
│   │   ├── FirestoreSharedNotesClient.swift
│   │   ├── FirestoreUserDirectory.swift
│   │   ├── Dto/                      # RemoteNote, RemoteSharedNote, RemoteFolder, RemoteShareMember
│   │   └── FirebaseAuthClient.swift
│   ├── Repository/                   # реализации протоколов domain
│   │   ├── NotesRepositoryImpl.swift
│   │   ├── FolderRepositoryImpl.swift
│   │   ├── SyncRepositoryImpl.swift
│   │   ├── SharedNotesGatewayImpl.swift
│   │   ├── AuthRepositoryImpl.swift
│   │   └── AccountRepositoryImpl.swift
│   ├── Mapper/
│   │   ├── NoteMapper.swift
│   │   └── SharedNoteMapper.swift
│   └── Sync/
│       ├── SyncManager.swift         # оркестратор: auth-state → sync lifecycle
│       └── SyncScheduler.swift       # debounce + Task-очередь (замена WorkManager)
│
├── Core/
│   ├── Text/
│   │   ├── MarkdownConverter.swift   # Markdown ↔ NSAttributedString (см. doc 04)
│   │   └── MarkdownBlocks/           # split на Text/Table-блоки, GFM-парсер
│   ├── Clock.swift                   # протокол Clock { nowMs() } (тестируемость)
│   └── Extensions/
│
├── Features/                         # UI по экранам (View + ViewModel)
│   ├── NotesList/
│   │   ├── NotesListView.swift
│   │   ├── NotesListViewModel.swift
│   │   ├── NotesListState.swift
│   │   └── Components/ (NoteCard, Sidebar, …)
│   ├── Editor/
│   │   ├── NoteEditorView.swift
│   │   ├── NoteEditorViewModel.swift
│   │   ├── NoteEditorState.swift
│   │   └── Components/ (FormatBar, RichTextView, TableBlock, TagChips, MembersSheet, …)
│   ├── Search/
│   ├── Settings/
│   └── Shared/                       # переиспользуемые вью, модификаторы
│
├── DesignSystem/
│   ├── Colors.swift                  # брендовые палитры (doc 06)
│   ├── Typography.swift
│   ├── Spacing.swift
│   ├── Shapes.swift
│   └── Icons.swift                   # маппинг Lucide → SF Symbols
│
└── Resources/
    ├── Fonts/ (Inter*, InstrumentSerif*)
    ├── Localizable (en, ru) — String Catalog (.xcstrings)
    ├── GoogleService-Info.plist      # из Firebase консоли (тот же проект notes-d3abc)
    └── Assets.xcassets (AppIcon, colors)
```

---

## 1.3. Dependency Injection

Android использует Hilt. На macOS — **ручной композиционный root** (`AppContainer`), создаваемый один раз в `AppDelegate`/`TesseraApp` и пробрасываемый через `@Environment`.

```swift
@MainActor
final class AppContainer {
    // Infrastructure
    let clock: Clock
    let prefs: UserPreferences
    let swiftData: SwiftDataStack

    // Remote
    let auth: AuthRepository
    let firestore: Firestore

    // Repositories
    let notesRepository: NotesRepository
    let folderRepository: FolderRepository
    let syncRepository: SyncRepository
    let sharedNotes: SharedNotesGateway
    let accountRepository: AccountRepository

    // Orchestration
    let syncManager: SyncManager

    init() { /* wire-up в правильном порядке */ }

    // Фабрики ViewModel'ей
    func makeNotesListViewModel() -> NotesListViewModel { ... }
    func makeEditorViewModel(noteId: NoteID) -> NoteEditorViewModel { ... }
    func makeSettingsViewModel() -> SettingsViewModel { ... }
}
```

Проброс:
```swift
@main
struct TesseraApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    var body: some Scene {
        WindowGroup { RootView().environment(appDelegate.container) }
        Settings { SettingsView() }   // ⌘, открывает Settings scene
        .commands { TesseraCommands() } // меню-бар (doc 07)
    }
}
```

> Альтернатива: пакеты `swift-dependencies` (Point-Free) или `Factory`. Для соответствия HIG и простоты — достаточно ручного контейнера.

---

## 1.4. Конкурентность (Swift 6 strict)

- Все ViewModel — `@MainActor @Observable`.
- Репозитории — `actor` или `Sendable`-классы; БД-операции вне main-потока.
- SwiftData `ModelContext`: используем фоновый контекст для синка (`ModelContainer.mainContext` — только для UI; для записи из синка — отдельный `ModelContext` на фоновом акторе). Внимательно с `Sendable` для `@Model` — передаём `PersistentIdentifier`, не сами объекты, между акторами.
- Потоки данных: оборачиваем наблюдение БД в `AsyncStream<[Note]>` (эквивалент Kotlin `Flow`). Для Firestore listener'ов — `AsyncThrowingStream` поверх `addSnapshotListener`.

---

## 1.5. Реактивность: эквиваленты Flow

| Kotlin | Swift |
|--------|-------|
| `Flow<T>` (cold) | `AsyncStream<T>` / `AsyncThrowingStream<T>` |
| `StateFlow<T>` (hot, value) | `@Observable` свойство + `AsyncStream` для подписчиков вне UI |
| `combine(a, b)` | `zip`/`merge` над `AsyncSequence` (swift-async-algorithms) либо ручная комбинация в ViewModel |
| `collect { }` | `for await x in stream { }` внутри `Task` |
| `debounce(500)` | свой debounce через `Task` + `Task.sleep` с отменой (см. SyncScheduler/autosave, doc 04) |
| `viewModelScope` | `Task`-ы, хранимые в ViewModel, отменяются в `deinit`/`onDisappear` |

**Наблюдение SwiftData → стрим.** SwiftData не даёт «живых» Combine-стримов из коробки на уровне произвольного запроса. Варианты:
1. В UI: использовать `@Query` напрямую во View (самый идиоматичный путь для списков).
2. В ViewModel: подписаться на `NSManagedObjectContextDidSave`-эквивалент / `ModelContext` notifications и перезапрашивать; либо опубликовать собственный `AsyncStream`, в который SyncManager/Store пушит после каждой мутации (контролируемая инвалидция). Рекомендуется гибрид: для простых списков — `@Query`; для редактора с merge-логикой — явный стрим через Store.

---

## 1.6. Соответствие компонентов Android → macOS

| Android | macOS |
|---------|-------|
| `Application` / Hilt modules | `AppDelegate` + `AppContainer` |
| `MainActivity` + Compose `setContent` | `TesseraApp` (App scene) + `RootView` |
| `NotesNavGraph` (Navigation-Compose) | `NavigationSplitView` + `NavigationStack` (doc 05) |
| `ViewModel` + `StateFlow<State>` | `@Observable` ViewModel + `State` struct |
| Room `@Dao` | `*Store.swift` поверх SwiftData `ModelContext` |
| Room `@Entity` | `@Model` классы |
| Room migrations | `SchemaMigrationPlan` (doc 02) |
| DataStore (prefs) | `UserDefaults` обёртка |
| WorkManager (SyncWorker) | `SyncScheduler` (Task-очередь) |
| WorkManager (DeleteTombstonesWorker, периодич.) | `NSBackgroundActivityScheduler` + при запуске |
| Firebase KTX | Firebase Apple SDK |
| Aztec rich editor | `NSTextView` (`NSViewRepresentable`) + MarkdownConverter |
| Lucide icons | SF Symbols |

---

## 1.7. Жизненный цикл и lifecycle-хуки

| Событие | Действие |
|---------|----------|
| App launch | `FirebaseApp.configure()`; восстановление сессии Auth; `SyncManager.start()`; purge локальных tombstones; реактивация realtime-listener'ов если signed-in. |
| Sign-in | `SyncManager.onSignedIn(uid)`: проверка foreign data → (push pending → clear cache) → adoption local notes → full sync → запуск listener'ов. |
| Sign-out | Остановить listener'ы; очистить сессию; локальные данные остаются (как в Android — заметки переходят в «локальный» режим с ownerId, но синк не идёт). |
| App background/inactive | macOS-приложение обычно не «убивается»; flush автосейва редактора при закрытии окна/вкладки. |
| Window close (editor) | `flush()` автосейва (синхронно), как `onDispose` в Compose. |
| Periodic (24h) | Purge tombstones > 30 дней (remote + local). |
| Network reachable | Триггер sync (NWPathMonitor). |

---

## 1.8. Обработка ошибок

- Repository-методы возвращают `Result<T, Error>` (как Kotlin `Result`) либо `throws`. Для совместимости с интентами — `throws` + перехват в ViewModel, запись в `state.error`.
- Сетевые ошибки синка не блокируют UI (local-first): операция остаётся pending (`syncedAt == nil`), повтор при следующем синке.
- Шеринг-операции: ошибки показываются через `shareError` в editor state (алерт), как в Android.
- Per-stream изоляция в realtime: падение одного listener'а (notes) не рушит другие (folders/shared) — каждый в своём `Task` с `try?`/логом.
