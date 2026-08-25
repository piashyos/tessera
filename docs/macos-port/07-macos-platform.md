# 07 — Платформенные возможности macOS

> То, чего нет в Android-версии, но обязательно для нативного Mac-приложения по HIG: меню-бар, горячие клавиши, управление окнами, drag&drop, системные интеграции, sandbox/нотаризация.

---

## 7.1. Меню-бар (главное меню)

Реализуется через `.commands { }` в `App` scene. Обязательные меню по HIG:

### Tessera (app menu)
- About Tessera
- Settings… (⌘,) — открывает Settings scene
- Services
- Hide / Hide Others / Show All
- Quit Tessera (⌘Q)

### File
- New Note (⌘N) → создать черновик, открыть в редакторе
- New Folder (⇧⌘N)
- New Subfolder
- — 
- Duplicate (⌘D) — дублировать выбранную заметку
- — 
- Move to Folder… (submenu)
- — 
- Export Notes… (⇧⌘E) — см. doc 08
- Import… (⇧⌘I)
- — 
- Close (⌘W)
- Print… (⌘P) — печать заметки (бонус-фича Mac)

### Edit
- Undo (⌘Z) / Redo (⇧⌘Z) — интеграция с `UndoManager` (delete/archive/edit)
- Cut / Copy / Paste / Select All — стандартные (работают в редакторе и списке)
- — 
- Find (⌘F) — фокус в поиск
- Find Advanced… (⇧⌘F) — поиск с фильтрами

### Format (активно в редакторе)
- Bold (⌘B), Italic (⌘I), Underline (⌘U), Strikethrough (⌃⌘S)
- Code (⌘E)
- — 
- Heading 1 (⌘1) … Heading 6 (⌘6), Paragraph (⌘0)
- — 
- Bullet List (⇧⌘7), Numbered List (⇧⌘8), Task List (⇧⌘9)
- Quote (⌘'), Horizontal Rule
- Insert Table (⌃⌘T)

### View
- Toggle Sidebar (⌃⌘S или ⌘⌥S)
- Show All Notes / Pinned / Archive / Trash / Shared (быстрые переходы)
- — 
- As List / As Tiles (если реализуем grid)

### Note
- Pin / Unpin (⌘P или ⌃⌘P)
- Archive / Unarchive (⌘⌥A)
- Add Tags…
- — 
- Share… / Make Shared (⌘⇧S)
- Manage Access…
- Make Private (Unshare)
- Leave Note
- — 
- Delete (⌘⌫) / Delete for Everyone
- Restore (в trash)

### Window / Help — стандартные.

> Пункты меню активны/неактивны контекстно (по выбранной заметке, роли, разделу) — через `.disabled()` и фокус. Те же действия дублируются в toolbar и контекстных меню (doc 05).

---

## 7.2. Горячие клавиши (сводка)

| Действие | Сочетание |
|----------|-----------|
| New Note | ⌘N |
| New Folder | ⇧⌘N |
| Duplicate | ⌘D |
| Settings | ⌘, |
| Close window | ⌘W |
| Find | ⌘F |
| Advanced find | ⇧⌘F |
| Bold/Italic/Underline | ⌘B / ⌘I / ⌘U |
| Strikethrough | ⌃⌘S |
| Inline code | ⌘E |
| Heading 1–6 / Paragraph | ⌘1…⌘6 / ⌘0 |
| Lists | ⇧⌘7/8/9 |
| Insert table | ⌃⌘T |
| Pin | ⌃⌘P |
| Archive | ⌘⌥A |
| Make shared | ⌘⇧S |
| Delete | ⌘⌫ |
| Undo/Redo | ⌘Z / ⇧⌘Z |
| Toggle sidebar | ⌘⌥S |

---

## 7.3. Управление окнами

- **Главное окно:** `WindowGroup` с `NavigationSplitView`. Restorable размер/позиция (`@SceneStorage`).
- **Минимальный размер:** ~720×480; адаптивные колонки.
- **Settings scene** — отдельное системное окно настроек.
- **Открыть заметку в отдельном окне** (бонус, HIG-friendly): двойной клик/⌘-клик или меню «Open in New Window» → дополнительный `WindowGroup`/`openWindow(value: NoteID)`. Полезно для параллельного редактирования.
- **State restoration:** запоминать выбранный раздел/заметку между запусками.
- **Full-screen / Stage Manager** — поддержать нативно (ничего не блокировать).

---

## 7.4. Drag & Drop

- **Заметка → папка** (в sidebar): перетащить карточку на папку → `assignNote(noteId, folderId)`. `Transferable`/`onDrop`.
- **Заметка → Trash/Archive** (раздел в sidebar): drop → soft-delete/archive.
- **Реордер папок / перемещение в подпапку**: drag папки на папку → `moveFolder`.
- **Экспорт перетаскиванием**: drag заметки в Finder → создать `.md` файл (бонус, см. doc 08).
- **Импорт**: drop `.md`/`.txt` файла из Finder в список → создать заметку из содержимого.

`Note` и `Folder` сделать `Transferable` (с `.md`-представлением для заметки).

---

## 7.5. Системные интеграции

- **Sharing (NSSharingService):** «Share» через системное share-меню (бонус, не путать с in-app шерингом). Опционально.
- **Spotlight (CoreSpotlight):** индексировать заметки для системного поиска (бонус; учесть приватность — только локальные/свои).
- **Quick Look / Печать:** печать заметки (⌘P) рендером Markdown.
- **Notifications (UserNotifications):** уведомление при ошибке синка / приглашении в shared (опц.).
- **Dock badge / меню:** число несинхронизированных изменений (опц.).
- **Continuity / Handoff:** при будущем iOS-таргете — `NSUserActivity` для продолжения редактирования. Заложить `NSUserActivity` для текущей заметки.
- **Menu Bar Extra (опц.):** быстрый захват заметки (`MenuBarExtra` scene) — «quick capture» из слогана.

---

## 7.6. Жизненный цикл и фоновые задачи

- **WorkManager → замены:**
  - Триггерный синк после правок — `SyncScheduler` (doc 03).
  - Периодическая очистка tombstones (24h) — `NSBackgroundActivityScheduler` (репитер) + запуск при старте.
- **Сеть:** `NWPathMonitor` → при появлении сети инициировать синк, обновлять `SyncStatus` (Offline/Synced).
- **App lifecycle:** `applicationDidFinishLaunching` → Firebase configure, restore auth, start SyncManager. `applicationWillTerminate` / window close → flush автосейва.
- **Энергоэффективность:** не поллить; полагаться на Firestore listeners + событийный синк.

---

## 7.7. App Sandbox, entitlements, приватность

- **App Sandbox**: включён (требование App Store).
- Entitlements:
  - `com.apple.security.network.client` — Firebase/Google.
  - `com.apple.security.files.user-selected.read-write` — экспорт/импорт через NSOpenPanel/NSSavePanel.
  - Keychain access (Firebase Auth persistence).
  - (опц.) App Group / iCloud при будущем шаринге с iOS.
- **Info.plist:**
  - `CFBundleURLTypes` с `REVERSED_CLIENT_ID` (GoogleSignIn redirect).
  - `NSHumanReadableCopyright`, версия, категория Productivity.
  - Privacy usage strings по мере надобности.
- **Privacy manifest** (`PrivacyInfo.xcprivacy`): задекларировать сбор данных (email, контент заметок → синк), требуется для App Store.
- **ATS**: HTTPS-only (Firebase соответствует).

---

## 7.8. Распространение

- **Подпись и нотаризация** (Developer ID) для распространения вне App Store, либо App Store-сборка.
- Архитектуры: universal (arm64 + x86_64).
- Автообновление (вне App Store): Sparkle (опц.).
- CI: сборка через `xcodebuild`, тесты, нотаризация.

---

## 7.9. Контрольный список платформы

- [ ] Полное меню-бар (Tessera/File/Edit/Format/View/Note/Window/Help) с контекстной активацией.
- [ ] Горячие клавиши по таблице §7.2, интеграция с UndoManager.
- [ ] NavigationSplitView, restorable окно, опц. «note in new window».
- [ ] Drag&Drop: заметка→папка/trash/archive, перемещение папок, импорт/экспорт через Finder.
- [ ] SyncScheduler + NSBackgroundActivityScheduler (tombstones) + NWPathMonitor.
- [ ] App Sandbox + entitlements + Info.plist (URL scheme) + Privacy manifest.
- [ ] Подпись/нотаризация, universal binary.
- [ ] (Опц.) Spotlight, печать, MenuBarExtra quick capture, NSUserActivity.
