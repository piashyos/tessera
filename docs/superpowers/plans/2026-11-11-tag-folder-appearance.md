# Оформление тегов и папок — план

> Ведёт оркестратор по скиллу `feature-team`; срезы исполняют ролевые сессии. Спека:
> `docs/superpowers/specs/2026-11-11-tag-folder-appearance-design.md` (далее «спека», `§N`). Коммит — только по команде пользователя.

**Цель:** цвет и иконка тега (долгое нажатие на чип), имя/иконка/цвет папки («⋮» в сайдбаре), подкраска заметок цветом папки вне папки; синк через Firestore.
**Архитектура:** Room 7→8 (+ неуникальный индекс имени папки, `@Upsert`), `TagRepository`, коллекция `users/{uid}/tags`; UI-модель оформления в `core/ui` (палитра, иконки, `LocalTagAppearances`), экранная часть в `ui/notes` и `ui/navigation`.
**Стек:** Kotlin, Room 2.8.4, Firestore, Compose M3, Lucide 1.1.0, Roborazzi.

---

## 1. Источники и окружение

- Спека — §3 модель оформления, §4 данные/синк/правила, §5 UI, §6 тесты. Макета нет: видимое — из спеки и §5 этого плана; состояния — скрины пользователю.
- Worktree `/Users/vorislove/.config/superpowers/worktrees/notes/tag-folder-appearance`, ветка `feature/tag-folder-appearance` от `master` @ `6dd1d2b`.
- codebase-memory: `project = "Users-vorislove-.config-superpowers-worktrees-notes-tag-folder-appearance"`.
- Устройство: `emulator-5556` (AVD `Small_Phone`, API 36) — у команды; `emulator-5554` — у пользователя. Настоящая клавиатура на 5556 — только с `show_ime_with_hard_keyboard 1` + `stylus_handwriting_enabled 0` (вернуть в конце), Gboard 15.1.08.
- Связь с ролями — ТОЛЬКО файлами (memory `feature-team-cross-mode-messaging`): отчёты `.claude/local/msg-to-orchestrator-<роль>-<N>.md`, команды — `--resume` с `msg-*.md`.
- Рабочая папка `.claude/local/tag-folder-appearance-ref/`.

## 2. Спека против макета

Макета нет. Решения по видимому — раздел 5 (строки) и D5 (палитра); первый результат `ui` — скрин палитры, чипа с иконкой и подкрашенной карточки (light/dark) пользователю до среза `feature`.

## 3. Решения

- **D1. Решения пользователя (2026-11-11):** синк тегов через Firestore; иконки — Lucide + эмодзи; подкраска — мягкая заливка карточки; вход в правку папки — «⋮» вместо кнопок; подкраска везде, кроме списка одной папки.
- **D2. Room 7→8 — одна миграция, владелец `data` (Chunk 1):** колонки `folders.color/icon`, `tags.color/icon/updatedAt/syncedAt`; индекс `index_folders_name` неуникальный; `FolderDao.upsert` → `@Upsert`.
  - Утверждение спеки «REPLACE по тому же PK каскадно снимает `note_folders` и обнуляет `parentId` подпапок» — из эксперимента субагента, в этом репо не наблюдалось. Chunk 1 начинает с **красного** теста на реальном in-memory Room при текущем `upsert` (REPLACE): удалённое изменение папки → `note_folders` и `parentId` подпапки целы. Красный — подтверждение, правка `@Upsert` делает зелёным. Зелёный сразу — записать в отчёт, `@Upsert` всё равно ставится (обновление на месте безопаснее), утверждение в спеке помечается «не подтвердилось».
- **D3. Правила Firestore** деплоит пользователь. Блок тегов в синке изолирован своим `runCatching` (§4.3), до деплоя синк заметок и папок не ломается.
- **D4. Смена аккаунта** (`clearAllTables`, `NotesDatabaseClearTest`) стирает и `tags` — поэтому `pushLocalChanges` пушит ожидающие теги (§4.3), после входа pull возвращает оформление.
- **D5. Палитра (§3.1) — правило, не 60 hex вручную.** Оттенки: red 4, orange 26, yellow 46, green 135, teal 175, blue 212, indigo 236, purple 276, pink 330, brown 24 (у brown насыщенность ×0.5). Тоны — формулой текущего `rememberTagChipColors`: light `container = hsl(h, .52, .86)`, `content = hsl(h, .55, .30)`; dark `container = hsl(h, .30, .30)`, `content = hsl(h, .45, .82)`. `cardTint`: light `hsl(h, .45, .94)`, dark `hsl(h, .20, .19)`. Тест контраста §3.1 решает: не проходит — роль сдвигает lightness этого цвета не больше чем на ±0.06 и пишет итог в отчёт.
- **D6. Высота попапа оформления с клавиатурой.** У `TesseraPopup` на `master` предел — 60 % экрана без учёта клавиатуры; попап оформления — первый с полем ввода и высоким содержимым. Проверка в Chunk 5 (qa): заголовок и поле имени видны при поднятой клавиатуре на 5556 (720×1280) и в геометрии Pixel 9 Pro. Не видны → вопрос пользователю: правка предела зависит от `TesseraPopup` фичи `editor-touch-and-format-sheet` (не влита), порядок мержа.
- **D7. «Изменить» в сайдбаре — как «+» сейчас:** сайдбар не закрывается, попап §5.1 поверх него (попапы `NotesListScreen` — соседи сайдбара). После «Сохранить» (`Saved`) попап закрывается, сайдбар остаётся открытым — изменения видны в нём сразу.
- **D8. Существующие тесты, чей контракт меняет спека** (правка — не ослабление, для `review`):
  - `CreateSubfolderUseCaseTest` — `Result<Folder>` → `FolderSaveResult` (§4.1);
  - фейки `test/fakes/FakeFolderRepository.kt`, `domain/usecase/FakeFolderRepo.kt` — новые методы интерфейса;
  - `NotesListViewModelTest`, `NoteEditorViewModelTest` — `renameFolder` → `updateFolder` (через фейки);
  - `TesseraChipRowTest` — только если меняется разметка при `onChipLongClick == null` (не должна);
  - скриншоты сайдбара (`notes_drawer_*` в `NotesListScreenshotTest`) — «⋮» вместо «+»/«удалить» (§5.3). `NotesListScreenshotTest` красный уже на базе (12/18) — см. раздел 8.
- **D9. Порядок ролей** (модуль один): `data` Chunk 1 (локально) → `data` Chunk 2 (синк и правила) → `ui` Chunk 3 → `feature` Chunk 4 → `qa` Chunk 5. `review` — после каждого среза, финальный — новой сессией. Компиляция ломается у вызова в `ui/**` из-за смены API репозитория — `data` вправе сделать минимальную механическую правку вызова (то же поведение) и перечисляет её в отчёте; экранную логику пишет `feature`.

## 4. Реестр компонентов

| Элемент | Ресёрч (codebase-memory) | Решение | Роль |
| --- | --- | --- | --- |
| Попап с размытым фоном | `TesseraPopup` (core/ui/components), его потребители `FolderNamePopup`, `SearchOptionPopup`, `TagPickerPopup` | Берём `TesseraPopup` без изменений; новый `ui/notes/components/AppearancePopup.kt` заменяет `FolderNamePopup` (§5.1) | feature |
| Поле имени | `FloatingCapsule` + `TesseraTextField` (как в `FolderNamePopup`) | Берём | feature |
| Цвет тега | `core/ui/tessera/TagColors.kt` (`rememberTagChipColors`) | Расширяем: `LocalTagAppearances`, `TagStyle`, `chipColors()`, `cardTint()`, `rememberTagIcon` — сигнатура `rememberTagChipColors` прежняя | ui |
| Палитра | нет | Новый `core/ui/theme/AccentPalette.kt` (hex/HSL допустимы только в theme) | ui |
| Иконка сущности | нет; Lucide уже в зависимостях | Новый `core/ui/tessera/EntityIcon.kt` | ui |
| Строка фильтров тегов | `TesseraChipRow` | Необязательный `onChipLongClick = null`; при не-null — свой чип тега (§5.2) | ui |
| Карточка заметки | `TesseraNoteCard`, обёртка `ui/notes/components/TesseraSwipeableNoteCard` | `TesseraNoteCard(tint: Color? = null)` — ui; проброс в обёртке — feature | ui / feature |
| Строка папки сайдбара | `TesseraNotesDrawer.DrawerFolderRow` | Правка в ui/notes (§5.3), `DropdownMenu` M3 | feature |

## 5. Строки (en / ru)

| Ключ | en | ru |
| --- | --- | --- |
| `folders_new` (есть) | — | — (заголовок «Новая папка») |
| `folders_edit_title` | Folder | Папка |
| `folders_edit` | Edit | Изменить |
| `folders_more` | Folder actions | Действия с папкой |
| `folders_name_taken` | A folder with this name already exists | Папка с таким именем уже есть |
| `appearance_color` | Color | Цвет |
| `appearance_icon` | Icon | Иконка |
| `appearance_emoji` | Emoji | Эмодзи |
| `appearance_reset` | Reset | Сбросить |
| `appearance_color_auto` | Auto | Авто |
| `appearance_color_none` | None | Нет |
| `appearance_icon_none` | No icon | Без иконки |
| `color_red` … `color_brown` | Red, Orange, Yellow, Green, Teal, Blue, Indigo, Purple, Pink, Brown | Красный, Оранжевый, Жёлтый, Зелёный, Бирюзовый, Синий, Индиго, Фиолетовый, Розовый, Коричневый |
| `icon_folder` … `icon_gift` (порядок §3.2) | Folder, Home, Work, Book, Idea, Star, Heart, Shopping, Travel, Car, Sport, Food, Coffee, Music, Photo, Games, Study, Code, Art, Money, Calendar, Time, Flag, Bookmark, Tag, People, Kids, Pets, Nature, Gift | Папка, Дом, Работа, Книга, Идея, Звезда, Сердце, Покупки, Путешествия, Машина, Спорт, Еда, Кофе, Музыка, Фото, Игры, Учёба, Код, Творчество, Деньги, Календарь, Время, Флаг, Закладка, Метка, Люди, Дети, Питомцы, Природа, Подарок |

Кнопки «Отмена»/«Сохранить» — существующие `folders_cancel` / `folders_save`; «Новая подпапка» / «Удалить» — `folders_create_subfolder` / `folders_delete`. Строки `color_*` и `icon_*` — `contentDescription` кружков и ячеек. Других текстов нет.

## 6. Срезы

### Chunk 0: база (оркестратор)

- [x] Числа unit/Roborazzi базы `6dd1d2b` — те же, что у фичи `editor-touch-and-format-sheet` (раздел 8 её плана): реально красные `ShareReceiverViewModelTest` (1), `AccessibilityAuditTest.settingsCompactTargetsAreDocumented`; golden `NotesListScreenshotTest` 12/18, `TesseraSwipeableNoteCardScreenshotTest` 4/4, `SettingsScreenshotTest` 6/8, `MembersSheetScreenshotTest` 3/5, `TesseraShowcaseScreenshotTest` 1/1.
- [x] (2026-11-11: NotesList 12/18, SwipeableNoteCard 4/4 красные — как в базе; ChipRow 2/0, NoteCard 5/0; 16 `_actual` в `ref/baseline-actual/`) В этом worktree на базе — `verifyRoborazziDebug` по одному классу `NotesListScreenshotTest`, `TesseraSwipeableNoteCardScreenshotTest`, `TesseraChipRowTest`, `TesseraNoteCardTest`; `*_actual.png` → `ref/baseline-actual/` — «до» для инварианта «без кастомизации как сейчас» (сравнение пиксель в пиксель с `_actual` после фичи).
- [x] `cd firestore-rules-tests && npm test` на базе — 26/26 (в worktree `node_modules` — симлинк на основной чекаут, не стейджить).

### Chunk 1: данные локально (`data`)

- [x] **Принято 2026-11-11** (ed41e1fd → 8137b2eb): D2 подтверждён красным на REPLACE; ревью-1 0/2 заметно (updateFolder на двойниках v7, androidTest-хелпер) + мьютекс — исправлены; прогон оркестратора 305/0.

Зона: `domain/**`, `data/**` (кроме `data/remote/**` — Chunk 2), `core/di/**`, `app/schemas/**`, `test/fakes/**`, их тесты; минимальные механические правки вызовов в `ui/**` по D9.
1. Красный тест D2 (`FolderUpsertKeepsAssignmentsTest`, in-memory Room) на текущем `upsert`.
2. Домен §4.1: `Folder.color/icon`, `TagAppearance`, `TagRepository`, `FolderSaveResult`, `updateFolder`, `createFolder/createSubfolder(color, icon)`, `observeFolderAssignments`; юзкейсы (`UpdateFolderUseCase`, `ObserveTagAppearancesUseCase`, `SetTagAppearanceUseCase`, `ObserveFolderAssignmentsUseCase`; `CreateSubfolderUseCase` → `FolderSaveResult` с `Invalid`).
3. Room §4.2: сущности, `Migration7To8`, `version = 8`, `8.json`, DAO (`TagDao.setAppearance/applyRemote/observeAppearances/getPendingSync/markSynced`, `FolderDao.update/observeAllAssignments`, `@Upsert`), `Migration7To8Test`.
4. Реализации: `FolderRepositoryImpl` (проверка имени среди живых без учёта регистра, `NameTaken`), `TagRepositoryImpl` (+ `requestSync`), биндинг в `core/di`.
5. Тесты §6.1 (кроме синка): `Migration7To8Test`, `FolderRepositoryImplTest`, `TagRepositoryImplTest`, D2; правки D8.
- Готово, когда: модуль компилируется; `gw.sh :app:testDebugUnitTest --tests 'app.tessera.data.*' --tests 'app.tessera.domain.*' --tests 'app.tessera.ui.notes.*' --tests 'app.tessera.ui.editor.NoteEditorViewModelTest' --rerun` — числа; `8.json` в диффе.

### Chunk 2: синк и правила (`data`)

- [x] **Принято 2026-11-11** (8137b2eb → 240a79dd): ревью-2 0/0, 3 мелочи исправлены; прогон оркестратора data.* 189/0, data+domain+VM 319/0, правила 33/33 (база 26), androidTest компилируется. Вне чанка, на решение пользователя: `pushLocalChanges` при смене аккаунта шлёт ожидающее предыдущего в облако нового — как уже для заметок/папок.

Зона: `data/remote/**`, `data/repository/SyncRepositoryImpl.kt`, `firestore.rules`, `firestore-rules-tests/**`, их тесты.
- `RemoteFolder.color/icon`, `RemoteTag`, `FirestoreTagsClient`/`RemoteTagsDataSource` (+ DI), `SyncRepositoryImpl` (§4.3: блок тегов после заметок в своём `runCatching`, `mergeRemoteTags` под `mergeMutex` в транзакции через `applyRemote`, `tagsFlow` в `observeRemote`, теги в `pushLocalChanges` в своём `runCatching`), правила + тесты правил.
- Готово, когда: `SyncRepositoryImplTest` (+ кейсы §6.1) зелёный; `npm test` в `firestore-rules-tests` — числа, не хуже базы.

### Chunk 3: оформление в `core/ui` (`ui`)

- [x] **Принято 2026-11-12** (a97f4587 → 1ed5803f → eb30de64): палитра одобрена пользователем; ревью-3 1 блокер (высота TagChip 32 vs 48 dp) + 3 заметно — исправлены; прогон оркестратора core.ui 88/0, без кастомизации NotesList 18/0, SwipeableCard 4/0, NoteEditor 10/0 (после ff на master @ 15b3171 со свежими golden); golden `TagAppearanceScreenshotTest`, `AppearanceShowcaseScreenshotTest` записаны оркестратором.

Зона: `core/ui/**` и тесты.
- `AccentPalette.kt` (D5) + тест контраста; `EntityIcon.kt` (+ `isAcceptableEmoji`) + тест; `TagColors.kt` (`LocalTagAppearances`, `TagStyle`, `chipColors()`, `cardTint()`, `rememberTagIcon`); `TesseraChipRow(onChipLongClick)`; `TesseraNoteCard(tint)` + иконки в чипах карточки.
- **Первый результат** (до остального): превью/скриншот-тест light+dark — 10 кружков палитры, чип тега с Lucide-иконкой и с эмодзи, подкрашенная карточка с чипом того же цвета → оркестратор показывает пользователю.
- Готово, когда: тесты `app.tessera.core.ui.*` зелёные, verify `TesseraChipRowTest`/`TesseraNoteCardTest` прежние при дефолтах, новые скриншоты — без golden.

### Chunk 4: экраны (`feature`)

- [x] **Принято 2026-11-12** (340e1463 → 8c3a63b3): ревью-4 1 блокер (кнопки попапа под сгибом → `TesseraPopup(footer)` + высота с клавиатурой) + 3 заметно — исправлены; прогон оркестратора ui.* 854/2 (база), правки 41/0, androidTest компилируется; NotesList 18/0 после перезаписи 4 `notes_drawer_*» («⋮» вместо «+»/корзины, compare просмотрен); golden `AppearanceScreenshotTest` (16) записан оркестратором.

Зона: `ui/**`, `res/**`, тесты.
- `AppearancePopup` (§5.1, заменяет `FolderNamePopup`), сайдбар «⋮» (§5.3, D7), `NotesListScreen`/`NotesListViewModel`/`NotesListState` (долгое нажатие тега, правка/создание папки с `FolderSaveResult`, `noteTints` и признак `tinted` по ветке `notesFlow` §5.4), проброс `tint` в `TesseraSwipeableNoteCard`, `TagAppearanceViewModel` + `CompositionLocalProvider` в `NotesNavGraph`, иконка тега в `TagChipsExpandable` и `SearchScreen.TagPill`, строки раздела 5.
- Тесты: `noteTints`, VM (`tinted` в папке/поиске), скриншоты §6.3.
- Готово, когда: `app.tessera.ui.*` зелёные; verify — без кастомизации пиксели `_actual` = `ref/baseline-actual/` (кроме строк папок сайдбара); новые скриншоты — без golden.

### Chunk 5: QA (`qa`)

- [x] **Принято 2026-11-12** (1db5862a; отчёт `docs/superpowers/qa/tag-folder-appearance.md`): `TagFolderAppearanceTest` 5/0 на финальном коде; D6 ок (720×1280, Pixel 9 Pro, ×1.3); D7 ок; регресс FormatSheet 6/0, EditorTouchOutside 12/0, GlassPopupShots 1/0, NativeEditorShots 1/0; пиксели цветов совпадают с палитрой; 4 палитры × light/dark читаются. Визуальная мелочь (сетка иконок на 360 dp) исправлена (99c3443d, `AppearancePopupNarrowTest`). Финальное ревью (c30dba59): 0 блокеров, 1 заметно (иконка 12 dp в мелких чипах) — спека уточнена.

- §6.4 на 5556 с настоящей клавиатурой; D6 (попап оформления с клавиатурой: 720×1280 и геометрия Pixel 9 Pro 1280×2856/480); D7 (правка из открытого сайдбара); light/dark × палитры; шрифт ×1.3; скрины для пользователя (макета нет).
- Отчёт `docs/superpowers/qa/tag-folder-appearance.md`.

## 7. Финал

Полный unit + verify (сравнение с базой), `npm test`, сам на устройстве главный сценарий, финальный `review` новой сессией, advisor, отчёт пользователю (в т.ч. «задеплой правила», совместимость §4.3).

## 8. Долг, вопросы, кандидаты

- Унаследованный долг — раздел 8 плана `2026-11-11-editor-touch-and-format-sheet.md` (та же база).
- Вопрос пользователю (в отчёте по первой фиче): перезаписать ли на `master` красные на базе golden (список, свайп-карточка, настройки, участники, витрина) до этой фичи — иначе Roborazzi не защищает именно те экраны, что она меняет; до решения инвариант держится сравнением `_actual` (Chunk 0).
- Кандидаты: `FolderPickerPopup` без иконок/цветов (вне рамок); дубли имён от синка двух устройств — две папки (§7 спеки).
