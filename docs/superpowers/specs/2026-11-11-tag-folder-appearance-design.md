# Оформление тегов и папок: цвет, иконка, имя папки, подкраска заметок

- **Дата:** 2026-11-11.
- **Статус:** дизайн утверждён пользователем в сессии 2026-11-11; спека прошла ревью (одна итерация правок).
- **Slug:** `tag-folder-appearance`.
- **Порядок:** реализуется после `2026-11-11-editor-touch-and-format-sheet-design.md`. Обе спеки трогают `TesseraPopup`, эта — только как потребитель.
- **Апрув Gradle, манифеста и зависимостей не нужен:** ничего из этого не меняется, Lucide (`libs.icons.lucide` 1.1.0) уже подключён.
- **Правила Firestore** деплоит пользователь (`firebase deploy --only firestore:rules`). Сборка без задеплоенных правил не ломает синк заметок и папок (§4.3).

## 1. Что хочет пользователь

1. **Главный экран:** долгое нажатие на тег в строке фильтров открывает меню, где тегу меняют цвет и ставят иконку.
2. **Сайдбар:** у папки можно поменять имя, поставить иконку и задать цвет — в одном меню.
3. **Цвет папки** виден в сайдбаре и красит её заметки в общих списках; в самой папке заметки не красятся.

## 2. Что есть сейчас

| Что | Сейчас |
| --- | --- |
| Цвет тега | `core/ui/tessera/TagColors.kt` → `rememberTagChipColors(tag)`: оттенок из `tag.hashCode()`, тона HSL под light и dark. Вызывают: `TesseraChipRow` (строка фильтров), `TesseraNoteCard` (чипы карточки), `TagChipsExpandable` (редактор), `SearchScreen.TagPill` (популярные теги) |
| Тег в данных | Room `tags(id, name UNIQUE)`: id локальный (UUID), строки не удаляются, `insertTags` с `IGNORE`. `note_tags` без FK. В Firestore тег — только имя в массиве `tags` заметки. Домен — `Tag(name)` |
| Папка в данных | Room `folders(id, name UNIQUE, parentId, createdAt, updatedAt, deletedAt, syncedAt)`, tombstone (`softDelete`) живёт 30 дней. Firestore `users/{uid}/folders/{id}` ← `RemoteFolder`, пуш `batch.set` целиком, живой слушатель в `observeRemote`. Правила папок поля не проверяют. Заметка — максимум в одной папке (`note_folders.noteId UNIQUE`), вложенность — один уровень |
| Переименование | `FolderNamePopup` (режим rename), `RenameFolderUseCase`, `NotesListViewModel.renameFolder` есть, но `folderEditorTarget` в `NotesListScreen` не выставляется — входа нет |
| Имя-дубликат | `folders.name` UNIQUE, индекс покрывает и tombstone'ы. `FolderDao.rename` (UPDATE) на дубликате бросает `SQLiteConstraintException` из `viewModelScope.launch` — краш. `createFolder` → `upsert REPLACE` на дубликате **удаляет** старую строку, `note_folders` — каскадом. Тот же `REPLACE` в слиянии синка удаляет живую папку, если с другого устройства пришла одноимённая |
| Строка папки в сайдбаре | `TesseraNotesDrawer.DrawerFolderRow`: иконка `Folder`, имя, кнопки «+» (только у корневых) и «удалить» |
| Карточка | `TesseraNoteCard`: фон `surfaceContainerLow`, у выбранной — `secondaryContainer`. Вызывают: сетка (Tiles) напрямую и список через `TesseraSwipeableNoteCard` |

## 3. Модель оформления

### 3.1 Палитра

**Данные — `core/ui/theme/AccentPalette.kt` (новый).** Hex допустим только в `core/ui/theme`.
- `enum class AccentColor(val key: String)` из 10 значений: `red`, `orange`, `yellow`, `green`, `teal`, `blue`, `indigo`, `purple`, `pink`, `brown`.
- У каждого цвета на тему (light и dark) три тона:
  - `container` — фон чипа и кружка в попапе;
  - `content` — текст и иконка на `container`, иконка папки в сайдбаре;
  - `cardTint` — фон подкрашенной карточки. Он **заметно мягче `container`**, чтобы чип тега того же цвета не растворялся на карточке: в light светлее, в dark темнее.
- `AccentColor.fromKey(key: String?): AccentColor?` — неизвестный ключ (пришёл от более новой версии) даёт `null`.

**Отрисовка — в `core/ui/tessera/TagColors.kt`.** Так пакет `theme` не зависит от `tessera`.
- `@Composable fun AccentColor.chipColors(): TagChipColors` — `container`/`content` текущей темы по `MaterialTheme.colorScheme.background.luminance()`, как сейчас в `rememberTagChipColors`.
- `@Composable fun AccentColor.cardTint(): Color`.

**Контраст — unit-тест** по всем 10 цветам, в light и dark:
- `content` на `container` ≥ 4.5:1;
- `onSurface` на `cardTint` ≥ 4.5:1;
- `onSurfaceVariant` на `cardTint` ≥ 3:1 (превью и дата, вторичный текст).

`onSurface` и `onSurfaceVariant` берутся из всех четырёх палитр `Theme.kt` (`TesseraBrand*`, `Neutral*`, `Slate*`, `Sage*`) в обеих темах. Динамический цвет (Material You) тестом не покрывается: его `onSurface` и `onSurfaceVariant` близки к палитрам, это проверяется глазами на снимках.

### 3.2 Иконка — `core/ui/tessera/EntityIcon.kt` (новый)

Хранится строкой:
- `lucide:<key>` — иконка из набора;
- `emoji:<текст>` — одна графема;
- `null` — иконки нет.

**Набор Lucide** (ключ → `Lucide.*`, все есть в 1.1.0) — 30 иконок в этом порядке:

`folder` Folder, `house` House, `briefcase` Briefcase, `book-open` BookOpen, `lightbulb` Lightbulb,
`star` Star, `heart` Heart, `shopping-cart` ShoppingCart, `plane` Plane, `car` Car,
`dumbbell` Dumbbell, `utensils` Utensils, `coffee` Coffee, `music` Music, `camera` Camera,
`gamepad-2` Gamepad2, `graduation-cap` GraduationCap, `code` Code, `palette` Palette, `wallet` Wallet,
`calendar` Calendar, `clock` Clock, `flag` Flag, `bookmark` Bookmark, `tag` Tag,
`users` Users, `baby` Baby, `paw-print` PawPrint, `leaf` Leaf, `gift` Gift.

**API:**
- `sealed interface EntityIcon { data class Vector(val key: String, val image: ImageVector); data class Emoji(val text: String) }`.
- `parseEntityIcon(raw: String?): EntityIcon?` — неизвестный ключ Lucide или битая строка дают `null`.
- `fun isAcceptableEmoji(text: String): Boolean` — ровно одна графема (`BreakIterator.getCharacterInstance`), её первая кодовая точка не пробел (`isWhitespace`) и не буква (`isLetter`), и если кодовая точка в графеме одна, она ещё и не цифра (`isDigit`).
  - Проходят: keycap «1️⃣», ZWJ-последовательности, флаги.
  - Не проходят: «é» (e + U+0301), «1», « ».
- `@Composable fun EntityIconView(icon: EntityIcon, tint: Color, size: Dp)`: вектор красится в `tint`, эмодзи рисуется текстом размера `size` без перекраски.

Домен хранит только строки-ключи (§4.1) и не знает `Color`/`ImageVector`.

### 3.3 Значения по умолчанию

| Сущность | `color == null` | `icon == null` |
| --- | --- | --- |
| Тег | Цвет по хешу имени, как сейчас («Авто») | Перед именем «#», как сейчас |
| Папка | Нейтрально: иконка в цвете строки, заметки не подкрашиваются | Иконка `Folder`, как сейчас |

**Главное правило:** без кастомизации всё выглядит ровно как сейчас. Скриншоты не меняются, кроме строк папок в сайдбаре (§5.3) и, возможно, строки фильтров (§5.2) — их golden перезаписывает оркестратор после просмотра `*_compare.png`.

## 4. Данные

### 4.1 Домен

- **`Folder`** получает `color: String? = null` и `icon: String? = null`.
- **`domain/model/TagAppearance.kt`:** `data class TagAppearance(val name: String, val color: String?, val icon: String?)`.
- **`domain/repository/TagRepository`** (новый):
  - `fun observeAppearances(): Flow<Map<String, TagAppearance>>` — только теги, у которых задан цвет или иконка;
  - `suspend fun setAppearance(name: String, color: String?, icon: String?)`.
- **`FolderRepository`:**
  - `renameFolder` → `suspend fun updateFolder(id: FolderId, name: String, color: String?, icon: String?): FolderSaveResult`;
  - `createFolder(name, color, icon)` и `createSubfolder(parentId, name, color, icon)` возвращают `FolderSaveResult`;
  - `sealed interface FolderSaveResult { data class Saved(val folder: Folder); data object NameTaken; data class Invalid(val reason: String) }`;
  - имя проверяется без учёта регистра (после `trim`) **среди живых папок** (`deletedAt IS NULL`), кроме самой редактируемой. Tombstone имя не держит (§4.2);
  - пустое имя по-прежнему `require`;
  - `fun observeFolderAssignments(): Flow<Map<NoteId, FolderId>>` для подкраски (§5.4).
- **Юзкейсы** (`domain/usecase/FolderUseCases.kt`, новый `TagUseCases.kt`) возвращают тот же `FolderSaveResult`:
  - `CreateSubfolderUseCase` переводит свои проверки («parent not found», «depth») в `Invalid` — второго канала ошибок через `Result` больше нет;
  - `RenameFolderUseCase` → `UpdateFolderUseCase`;
  - новые `ObserveTagAppearancesUseCase`, `SetTagAppearanceUseCase`, `ObserveFolderAssignmentsUseCase`.
- **Подключение:** интерфейс в `domain/repository`, `TagRepositoryImpl` в `data/repository`, биндинг в `core/di/RepositoryModule`.

### 4.2 Room 7 → 8

`Migration7To8` (файл `data/local/Migration7To8.kt`, как `Migration6To7.kt`), `NotesDatabase.version = 8`:

```sql
ALTER TABLE folders ADD COLUMN color TEXT;
ALTER TABLE folders ADD COLUMN icon TEXT;
DROP INDEX IF EXISTS `index_folders_name`;
CREATE INDEX IF NOT EXISTS `index_folders_name` ON `folders` (`name`);
ALTER TABLE tags ADD COLUMN color TEXT;
ALTER TABLE tags ADD COLUMN icon TEXT;
ALTER TABLE tags ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0;
ALTER TABLE tags ADD COLUMN syncedAt INTEGER;
```

**Индекс имени папки — больше не уникальный.** Уникальность среди живых папок держит репозиторий (§4.1). Это убирает:
- краш при переименовании или создании с именем недавно удалённой папки;
- удаление чужой строки через `REPLACE`, при создании и при слиянии синка.

**Сущности.**
- `FolderEntity`: `Index("name")` без `unique`, поля `color`, `icon`.
- `TagEntity`: поля `color`, `icon`, `updatedAt = 0`, `syncedAt = null`. `NOT NULL DEFAULT 0` без `@ColumnInfo(defaultValue)` — как в миграциях 4→5 и 5→6, `Migration5To6Test` с тем же приёмом проходит.

**Семантика тега.**
- `updatedAt = 0` — оформление ни разу не задавалось: тег не синкается.
- `syncedAt == null && updatedAt > 0` — ждёт пуша.

**DAO.**
- `TagDao.setAppearance(name, color, icon, ts)` (`@Transaction`):
  - `INSERT OR IGNORE` строки с новым UUID, если имени нет;
  - затем `UPDATE tags SET color, icon, updatedAt = ts, syncedAt = NULL WHERE name = :name`.
- `TagDao.applyRemote(name, color, icon, updatedAt, syncedAt)` (`@Transaction`): та же пара `INSERT OR IGNORE` + `UPDATE` по имени. **Не `@Insert(REPLACE)`**: `REPLACE` по UNIQUE `name` удалил бы строку, а `note_tags` (без FK) остались бы сиротами.
- `TagDao.observeAppearances()` — `WHERE color IS NOT NULL OR icon IS NOT NULL`.
- `TagDao.getPendingSync()` — `WHERE syncedAt IS NULL AND updatedAt > 0`.
- `TagDao.markSynced(name, ts, updatedAt)` — отметка только той версии, что ушла (как `NoteDao.markSynced` в `3bec0ac`).
- `FolderDao.update(id, name, color, icon, ts)` — `updatedAt = ts`, `syncedAt = NULL`. `FolderDao.rename` удаляется.
- **`FolderDao.upsert` переходит с `@Insert(REPLACE)` на `@Upsert`** (Room 2.8.4).
  - Почему: SQLite выполняет `REPLACE` даже по тому же PK как DELETE + INSERT, и срабатывают FK-действия. Проверено на sqlite 3.53: `note_folders` папки уходят каскадом, у подпапок `parentId` становится NULL.
  - Значит, сейчас любое удалённое изменение папки (имя, цвет, иконка), применённое `mergeRemoteFolders`, снимает с неё заметки и отрывает подпапки.
  - `@Upsert` обновляет существующую строку на месте (UNIQUE по имени после §4.2 уже нет, конфликт возможен только по PK).
  - Остальные вызовы `upsert` (создание папки) от этого не меняются.
- `FolderDao.observeAllAssignments(): Flow<List<NoteFolderCrossRef>>`.

**Схема и тест.**
- Экспорт `app/schemas/app.tessera.data.local.NotesDatabase/8.json` в диффе.
- Тест `Migration7To8Test` по образцу `Migration6To7Test`:
  - данные папок, тегов и связей переживают миграцию;
  - новые колонки `NULL` или `0`;
  - две живые папки с одним именем после миграции вставляются;
  - схема совпадает с экспортом.

### 4.3 Firestore и синк

**Папки.**
- `RemoteFolder` получает `color: String? = null` и `icon: String? = null`.
- Маппинг в обе стороны — там же, где сейчас.
- Пуш и слияние не меняются: побеждает более поздний `updatedAt`.

**Теги — коллекция `users/{uid}/tags/{docId}`.**
- `docId` — `Base64.encodeToString(name.toByteArray(UTF_8), URL_SAFE or NO_WRAP or NO_PADDING)`: в имени может быть «/».
- Документ — `RemoteTag(name: String = "", color: String? = null, icon: String? = null, updatedAt: Long = 0L)`.
- Сброс оформления — это запись с `color` и `icon` = null, не удаление.
- `FirestoreTagsClient` / `RemoteTagsDataSource` по образцу `FirestoreFoldersClient` / `RemoteFoldersDataSource`: `fetchAll(uid)`, `upsertAll(uid, tags)`, `observeTags(uid)`.

**`SyncRepositoryImpl`:**
- **`syncNow`.** Блок тегов идёт **после** заметок и общих заметок и обёрнут в **свой** `runCatching` с `Log.w`: `PERMISSION_DENIED` до деплоя правил не останавливает синк заметок и папок. Шаги:
  1. `fetchAll`;
  2. слияние;
  3. пуш `getPendingSync()` → `upsertAll` → `markSynced(name, now, updatedAt)`.
- **Слияние** (`mergeRemoteTags`) — под тем же `mergeMutex` и в `transactor.transaction`, как `mergeRemoteFolders`. Удалённый тег применяется через `TagDao.applyRemote(…, syncedAt = now)`, если строки нет или удалённый `updatedAt` больше локального. Локальное несинканное изменение с большим `updatedAt` остаётся и уйдёт пушем.
- **`observeRemote`** получает четвёртый независимый поток `tagsFlow` со своим `catch`, как `foldersFlow`.
- **`pushLocalChanges`** (сброс перед изоляционной очисткой в `SyncManager.onSignedIn`) пушит и ожидающие теги, иначе их оформление теряется при смене аккаунта. Пуш тегов и там в своём `runCatching` с `Log.w`: до деплоя правил он не должен проваливать весь сброс.
- **`TagRepositoryImpl.setAppearance`** вызывает `syncScheduler.requestSync()`, как `FolderRepositoryImpl`.

**`firestore.rules`** — новый блок:

```
match /users/{uid}/tags/{tagId} {
  allow read, delete: if request.auth != null && request.auth.uid == uid;
  allow create, update: if request.auth != null && request.auth.uid == uid
    && request.resource.data.keys().hasOnly(['name', 'color', 'icon', 'updatedAt'])
    && request.resource.data.name is string
    && (request.resource.data.get('color', null) == null || request.resource.data.color is string)
    && (request.resource.data.get('icon', null) == null || request.resource.data.icon is string)
    && request.resource.data.updatedAt is int;
}
```

Шапка-комментарий «Модель» дополняется строкой про `users/{uid}/tags`.

**Тесты правил** (`firestore-rules-tests/rules.test.js`):
- владелец пишет и читает тег;
- владелец пишет сброс (`color` и `icon` = null);
- чужой пользователь не читает и не пишет;
- лишний ключ отклоняется;
- `color` числом отклоняется;
- владелец пишет папку с `color` и `icon`.

**Совместимость:** старая версия приложения на другом устройстве пушит папку целиком без новых полей и стирает её цвет и иконку. Принимается: пользователь один, приложение обновляется.

## 5. Интерфейс

### 5.1 Попап оформления — `ui/notes/components/AppearancePopup.kt`

Новый файл, заменяет `FolderNamePopup` (тот удаляется). Строится на `TesseraPopup`. Сверху вниз:

1. **Заголовок** `headlineSmall` / SemiBold:
   - «Новая папка» при создании;
   - «Папка» при редактировании;
   - `#имя` для тега.
2. **Поле имени** — только для папки (`name: String?`, `null` прячет поле). `FloatingCapsule` + `TesseraTextField`, как в `FolderNamePopup`. Под полем — ошибка `folders_name_taken` («Папка с таким именем уже есть», `bodySmall` / `error`): показывается после `NameTaken`, пропадает при правке имени.
3. **«Цвет».**
   - Подпись `labelLarge` / `onSurfaceVariant`.
   - Ряд кружков 32 dp, горизонтальная прокрутка, шаг `TesseraSpacing.sm`.
   - Первый кружок — «без цвета»: для тега «Авто» (по хешу), для папки «Нет». Рисуется контуром `outline` с диагональю.
   - Дальше 10 кружков `container` с обводкой `content`.
   - Выбранный — галочка `content`.
   - У каждого `contentDescription` с названием цвета.
4. **«Иконка».**
   - Подпись.
   - `FlowRow` ячеек 44 dp (не ленивая сетка: внутри прокручиваемой колонки `TesseraPopup` она дала бы вложенный скролл против свайпа закрытия):
     - первая ячейка — «Без иконки»;
     - дальше 30 Lucide, иконка 22 dp цветом `content` выбранного цвета, без цвета — `onSurfaceVariant`;
     - выбранная ячейка — фон `secondaryContainer`, скругление 12 dp.
   - Под ячейками — поле «Эмодзи» (`TesseraTextField`, одна строка):
     - ввод, проходящий `isAcceptableEmoji` (§3.2), выбирает эмодзи;
     - выбор в сетке очищает поле;
     - при вводе нескольких графем остаётся последняя, если она проходит проверку;
     - непроходящий ввод не меняет выбор, а поле показывает прежнее значение.
5. **Предпросмотр.** Чип тега (для тега) или строка папки (для папки) с выбранными цветом и иконкой, обновляется сразу.
6. **Кнопки:**
   - «Отмена» (`OutlinedButton`) и «Сохранить» (`Button`, неактивна при пустом имени);
   - для тега и для редактирования папки — ещё текстовая «Сбросить». Она только ставит в попапе цвет и иконку в «нет»; записывает «Сохранить».

**Поведение.**
- Сохранение тега закрывает попап сразу.
- Сохранение папки ждёт результат:
  - `Saved` закрывает попап;
  - `NameTaken` показывает ошибку;
  - `Invalid` закрывает попап и пишет `Log.w` (из UI недостижимо).
- Свайп вниз, тап по фону и «назад» закрывают без сохранения.

**Новые строки** — в `values` и `values-ru`:
- заголовки;
- `appearance_color`, `appearance_icon`, `appearance_emoji`, `appearance_reset`;
- `appearance_color_auto`, `appearance_color_none`, `appearance_icon_none`;
- `folders_name_taken`;
- 10 названий цветов;
- пункты меню §5.3 (`folders_edit`; «Новая подпапка» и «Удалить» — существующие `folders_create_subfolder`, `folders_delete`).

### 5.2 Тег: долгое нажатие на главном экране

- `TesseraChipRow` получает необязательный `onChipLongClick: ((String) -> Unit)? = null`.
  - **`null`** — разметка прежняя (`FilterChip`), скриншоты прежние.
  - **Не `null`** — тег-чипы (`label.startsWith("#")`) рисуются **своим чипом**: `Surface` с `CircleShape` и цветами тега, `labelLarge`, высота 32 dp и отступы как у `FilterChip`, плюс `Modifier.combinedClickable(onClick, onLongClick)`.
    - `FilterChip` не годится: его внутренний `clickable` забирает DOWN, и обёртка не видит долгое нажатие.
    - `combinedClickable` после долгого нажатия не вызывает `onClick`, фильтр не переключается.
    - Долгое нажатие даёт хаптику `LongPress`.
  - Чипы-не-теги всегда остаются `FilterChip`.
- `NotesListScreen` всегда передаёт `onChipLongClick`, то есть на главном экране тег-чипы всегда идут по новой ветке. Пиксельная разница с прежним golden допустима только в пределах сглаживания. Golden перезаписывает оркестратор после просмотра `*_compare.png`.
- Долгое нажатие открывает попап §5.1 для тега.
- Сохранение → `NotesListViewModel.setTagAppearance(name, color, icon)` → юзкейс.
- Тап по чипу по-прежнему включает и выключает фильтр.

### 5.3 Папка: «⋮» в сайдбаре

**`DrawerFolderRow`.**
- Кнопки «+» и «удалить» заменяются одной «⋮» (`Icons.Rounded.MoreVert` — как соседние material-иконки сайдбара, 44 dp, как `DrawerFolderAction`).
- По ней — `DropdownMenu`:
  - «Изменить» → попап §5.1 с полем имени;
  - «Новая подпапка» (только у корневых) → попап §5.1 в режиме создания с `parentId`;
  - «Удалить» (цвет `error`) → текущий `onDeleteFolder`, поведение прежнее.
- Иконка папки — `EntityIconView`, если иконка задана, иначе `Folder`. Цвет иконки — `content` эффективного цвета папки (свой, иначе родителя, §5.4); без цвета — как сейчас (`contentColor` строки).

**Создание корневой папки** («+» в заголовке секции) — тоже попап §5.1.

**Подключение.**
- `folderEditorTarget` в `NotesListScreen` выставляется пунктом «Изменить».
- `onRenameFolder` → `onUpdateFolder(id, name, color, icon)`; `onCreateFolder` и `onCreateSubfolder` получают `color` и `icon`.
- Результат `FolderSaveResult` доходит до попапа — по образцу соседнего кода: `suspend`-функция VM, вызываемая из `rememberCoroutineScope`, или одноразовое событие в state.

### 5.4 Подкраска заметок

**Правило:** карточки **не** подкрашиваются, когда список — это заметки одной папки: открыта папка (`selectedFolderId != null`) и действует ветка `notesFlow`, фильтрующая по ней. Во всех остальных списках подкрашиваются: «Все», «Закреплённые», «Архив», «Корзина», «Общие», а также результаты поиска, которые `notesFlow` при непустом запросе берёт из всех папок.

**VM** вычисляет признак `tinted` из той же ветки `notesFlow`, что выбирает источник заметок (порядок: Корзина, Архив, Общие, Закреплённые, непустой `effectiveQuery`, папка, «Все»), по `effectiveQuery` (с задержкой ввода), а не по сырому `query` и не по одному `selectedFolderId`.

**Эффективный цвет заметки:**
- у её папки есть `color` — он;
- иначе, если папка — подпапка и у родителя есть `color` — цвет родителя;
- иначе цвета нет.

**Расчёт** — чистая функция в `ui/notes`: `noteTints(notes, assignments, folders): Map<NoteId, String>`, с unit-тестом.

**Поток.**
- `NotesListViewModel` комбинирует `observeFolderAssignments()` и папки.
- `NotesListState.Content` получает `noteTints: Map<NoteId, String> = emptyMap()`, пустую, когда `tinted == false`.

**Карточка.**
- `TesseraNoteCard` получает необязательный `tint: Color? = null`. Не выбрана — фон `tint ?: surfaceContainerLow`; выбрана — `secondaryContainer`, как сейчас (выделение важнее подкраски).
- `tint` = `AccentColor.cardTint()` текущей темы. Текст, превью и дата цвет не меняют (контраст — §3.1).
- Оба вызова передают `tint`: сетка — напрямую, список — через `TesseraSwipeableNoteCard`, который получает такой же необязательный параметр.

### 5.5 Теги: цвет и иконка везде

- `core/ui/tessera/TagColors.kt`:
  - `val LocalTagAppearances = compositionLocalOf<Map<String, TagStyle>> { emptyMap() }`;
  - `data class TagStyle(val color: AccentColor?, val icon: EntityIcon?)` — UI-модель.
- **Цвет.** `rememberTagChipColors(tag)` берёт `LocalTagAppearances.current[tag]?.color`: цвет есть — его `chipColors()`, иначе хеш, как сейчас. Ключи `remember` — `(tag, dark, color)`. Сигнатура не меняется, все четыре вызова получают цвет без правок.
- **Иконка.** `@Composable fun rememberTagIcon(tag: String): EntityIcon?` из того же local. Если иконка есть, вместо «#» перед именем рисуется `EntityIconView` (16 dp, `tint = content`):
  - `TesseraChipRow` делает это **сам**: для тег-чипа (определяется по `label.startsWith("#")`, вызывающий лейбл не меняет) при наличии иконки показывает её ведущей и выводит лейбл без «#»;
  - `TesseraNoteCard`, `TagChipsExpandable`, `SearchScreen.TagPill` — так же, в месте, где сейчас пишется «#». Размер иконки — по тексту чипа: 16 dp в строке фильтров и в `TagPill` (`labelLarge`), 12 dp в чипах карточки и редактора (`labelSmall`; уточнено 2026-11-12 по финальному ревью).
- **Кто предоставляет карту.** `NotesNavGraph` (корень навигации) через `@HiltViewModel TagAppearanceViewModel` (`ui/navigation`). Он мапит `ObserveTagAppearancesUseCase` в `Map<String, TagStyle>` (`AccentColor.fromKey`, `parseEntityIcon`); вокруг `NavHost` — `CompositionLocalProvider`.

## 6. Тесты

### 6.1 Unit (`app/src/test`)

- `Migration7To8Test` (§4.2).
- `FolderRepositoryImplTest`:
  - `updateFolder` и `createFolder` с именем живой папки (в другом регистре тоже) дают `NameTaken` и ничего не меняют;
  - имя удалённой (tombstone) папки свободно;
  - своё имя сохраняется;
  - цвет и иконка пишутся, `syncedAt = null`.
- `TagRepositoryImplTest`:
  - `setAppearance` для тега без строки создаёт её, для существующего не трогает `id` и `note_tags`;
  - сброс пишет `null` с новым `updatedAt`, `observeAppearances` его не отдаёт;
  - `requestSync` вызван.
- `SyncRepositoryImplTest`:
  - удалённый тег новее — применён через `applyRemote`, связи заметок целы;
  - удалённый старее — нет;
  - ожидающий тег уходит и помечается только своей версией;
  - ошибка `fetchAll` тегов не мешает синку заметок;
  - папка с цветом и иконкой ходит в обе стороны;
  - удалённое изменение существующей папки оставляет её `note_folders` и `parentId` её подпапок на месте (тест на реальном Room in-memory, не на фейке DAO, — фейк не воспроизводит FK);
  - `pushLocalChanges` пушит теги, ошибка тегов его не проваливает.
- Палитра — контраст §3.1.
- `EntityIcon` — разбор `lucide:`, `emoji:`, неизвестного ключа, `null`; `isAcceptableEmoji` для «🏠», «1️⃣», «👨‍👩‍👧», «a», «é» (e + U+0301), «1», « », «🏠🏠».
- `noteTints`:
  - свой цвет;
  - цвет родителя;
  - без цвета;
  - заметка без папки.
- `NotesListViewModel` — `noteTints` пустая в папке и непустая в поиске из папки.

### 6.2 Правила Firestore

`firestore-rules-tests` — кейсы §4.3, `npm test` на эмуляторе.

### 6.3 Скриншоты (Roborazzi, light + dark)

- Попап оформления: тег; папка с ошибкой `NameTaken`.
- Сайдбар: папка с цветом и иконкой, подпапка с цветом родителя, открытое меню «⋮».
- Список: «Все» с подкрашенными карточками (одна выбрана, на одной чип тега того же цвета), та же папка открыта — без подкраски.
- Строка фильтров: тег с цветом и иконкой.
- Существующие golden без кастомизации не меняются, кроме §3.3.

### 6.4 На эмуляторе (instrumented / adb)

- Долгое нажатие на чип тега открывает попап, фильтр при этом не переключился; тап по чипу фильтрует.
- Цвет и иконка тега видны в строке фильтров, на карточке, в редакторе и в поиске.
- «⋮» → «Изменить»: новое имя, цвет и иконка видны в сайдбаре. Имя живой папки — ошибка, приложение не падает. Имя только что удалённой папки — сохраняется.
- Подкраска в «Всех» есть, в папке нет.

## 7. Вне рамок

- Переименование тегов.
- Иконки и цвета в `FolderPickerPopup` (перенос заметки в папку).
- Цвет и иконка системных разделов сайдбара.
- Ручная сортировка папок.
- Произвольный цвет (HSV-пикер).
- Дубликаты имён от синка с двух устройств остаются двумя папками с одним именем: после §4.2 это не теряет данные, их слияние — отдельная задача.
