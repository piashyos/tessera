# Шапка редактора по-эппловски и плавный свайп в модалках

- **Дата:** 2026-11-12.
- **Статус:** дизайн утверждён пользователем в сессии 2026-11-12 (вариант «панель + мета»).
- **Slug:** `editor-header-and-popup-gestures`.
- **Основа:**
  - `core/ui/tessera/TesseraTopBar` — общая верхняя панель (список, настройки, редактор);
  - `ui/editor/NoteEditorScreen` + `ui/editor/components/EditorMeta`, `TagChipsExpandable`;
  - `core/ui/components/TesseraPopup` — общая модалка-карточка. Её используют
    `SearchOptionPopup` (параметры поиска), `AppearancePopup` (папка/тег: название, цвет, иконка),
    `TagPickerPopup`, `FolderPickerPopup`, `MarkdownFormatMenu` (меню «⋯» формата в редакторе).
- **Слои:** только UI (`core/ui`, `ui/editor`). Domain, data, Room, Firestore не меняются.
- **Вне рамок:**
  - `TesseraBottomSheet` (M3 `ModalBottomSheet`: действия с заметкой, участники, тема, язык) — жест системный, жалоб нет;
  - чипы тегов — остаются как есть, с ✕;
  - цвет/иконка папки в заголовке (нужны новые поля в `NoteEditorState`) — кандидат на потом;
  - текст под полупрозрачной панелью при прокрутке (iOS scroll edge) — редактор прокручивает себя сам, отдельная работа.

## 1. Жалобы и причины

| # | Жалоба | Причина (по коду) |
| --- | --- | --- |
| 1 | Шапка редактора перегружена | Три строки: панель `← 📌 🗑 ⋮`, строка `EditorMeta` (иконка папки, путь, дата), строка тегов. Набор кнопок меняется с ролью/общим доступом: корзина есть только у личной заметки, `⋮` — только если есть пункты общего доступа (отсюда `key(...)`-обход в `NoteEditorScreen`) |
| 2 | Свайп вниз закрывает модалку дёргано | `TesseraPopup`: `LaunchedEffect(visible) { dragOffset.snapTo(0f) }` срабатывает и при `visible = false` — карточка, оттянутая за порог, прыгает обратно вверх и только потом уезжает анимацией выхода, стартующей с нулевой скорости |
| 3 | Быстрый короткий взмах не закрывает | `detectVerticalDragGestures.onDragEnd` не знает скорости; решение только по расстоянию 120dp |
| 4 | Карточка «зависает» сдвинутой | `NestedScrollConnection.onPostScroll` двигает карточку на любом источнике, в т.ч. на инерции скролла контента — после `onPreFling` смещение растёт, и его никто не возвращает. `onPreFling` при сдвинутой карточке возвращает `Velocity.Zero` — контент под ней ещё и прокручивается по инерции |
| 5 | Закрытие с открытой клавиатурой (поле названия папки) рывками | `dismiss` снимает фокус → IME уезжает → посреди закрытия меняются `windowInsetsPadding(ime)` и `maxCardHeight` (переход `sheetMaxHeight` ↔ 60 %) |
| 6 | Лаг пальца на кадр | `dragScope.launch { snapTo }` на каждое событие: смещение применяется в следующем кадре, гонка со `animateTo` |

## 2. Шапка редактора

### 2.1 Вид

```
‹        Работа › Проекты        📌  ⋯
          изменено 12 июн, 16:42
#срочно ✕  #встреча ✕  (+)

Q3 roadmap — приоритеты
```

Две строки вместо трёх: панель (кнопки + заголовок по центру) и теги. Строки `EditorMeta` между панелью и тегами больше нет.

### 2.2 `TesseraTopBar` — необязательный `centerTitle`

- Новый параметр `centerTitle: Boolean = false`. При `false` всё как сейчас (список, настройки не меняются, их golden не краснеют).
- При `true`:
  - заголовок центрирован по **всей ширине панели**, а не по промежутку между кнопками;
  - ширина заголовка — не больше `ширина панели − 2 × max(ширина слота навигации, ширина слота действий)`; заголовок не налезает ни на одну кнопку;
  - раскладка — `Layout` по измеренным слотам (без зашитых отступов под число кнопок);
  - высота панели, отступы, `windowInsetsPadding(statusBars)` — как сейчас.
- `ProvideTextStyle(titleLarge)` остаётся провайдером по умолчанию; содержимое заголовка задаёт свои стили.

### 2.3 Заголовок: `EditorMeta` становится блоком заголовка

`EditorMeta(folderBreadcrumb, relativeDate)` переписывается в центрированный блок (тот же файл, та же сигнатура + `modifier`):

| Данные | Строка 1 | Строка 2 |
| --- | --- | --- |
| Есть папка и дата | путь `folderBreadcrumb.joinToString(" › ")`, `typography.labelLarge` (W500 13/18, как `.center-title .title` макета), `colorScheme.onSurface`, 1 строка, `TextOverflow.StartEllipsis` (видна последняя папка: «… › Проекты») | дата `relativeDate`, `typography.bodySmall` (W400 11/15), `colorScheme.onSurfaceVariant`, 1 строка |
| Есть папка, нет даты (`updatedAt == 0`) | путь, как выше | — |
| Папки нет | дата, `typography.bodySmall`, `onSurfaceVariant` | — |
| Нет ни папки, ни даты (`updatedAt == 0`) | пусто | — |

- Иконка папки убирается.
- Обе строки по центру по горизонтали, блок по центру панели по вертикали.
- Дата — тот же `noteDateLabel(state.updatedAt)`, новых строк ресурсов нет.

### 2.4 Кнопки панели

| Слот | Кнопка | Иконка | Когда видна |
| --- | --- | --- | --- |
| навигация | Назад | `Lucide.ChevronLeft` (центрированный шеврон, как в макете; Lucide уже в редакторе — `EditorFormatBar`), 20dp, `colorScheme.primary`. RTL приложение не поддерживает (en/ru) | всегда; `onClick` как сейчас: `haptics.tick(); handOverEdit(); onBack()`; тег `EditorBack` |
| действие 1 | Закрепить | `Icons.Rounded.PushPin`, 20dp; закреплена — `primary`, нет — `onSurfaceVariant` (как сейчас) | `state.canEditContent` (как сейчас); тег `EditorPin` |
| действие 2 | Ещё | `Icons.Rounded.MoreHoriz` (горизонтальные точки вместо `MoreVert`), 20dp, `onSurfaceVariant` | когда в меню есть хоть один пункт (§2.5) — у личной заметки всегда; тег `EditorOverflow`; описание `editor_more` |

- Корзины в панели больше нет. `key("editor-action-delete")` удаляется вместе с ней; `key` у закрепления и «⋯» остаются.
- Кнопки — `TesseraTopBarIconButton` (48dp), как сейчас.

### 2.5 Меню «⋯»

`DropdownMenu` у кнопки «⋯», как сейчас. Порядок пунктов (каждый — только при своём флаге, как сейчас):

1. «Сделать общей» — `canMakeShared` → `onMakeShared()`.
2. «Управление доступом» — `canManageAccess` → `onManageAccess()`.
3. «Сделать приватной» — `canUnshare` → подтверждение `unshareConfirmVisible` (как сейчас).
4. «Выйти из заметки» — `canLeave` → подтверждение `leaveConfirmVisible`.
5. `HorizontalDivider(color = colorScheme.outlineVariant)` — только если выше есть хотя бы один пункт и ниже есть пункт удаления.
6. Удаление, текст и иконка `colorScheme.error`:
   - личная заметка (`!state.isShared`): «Удалить заметку» (`editor_delete`), `Icons.Rounded.Delete`, тег `EditorDelete` → `overflowVisible = false; haptics.tick(); handOverEdit(); onDelete()` — **сначала** передача правки, потом удаление, без подтверждения (как у прежней корзины);
   - общая (`state.canDeleteForAll`): «Удалить для всех» → подтверждение `deleteForAllConfirmVisible` (как сейчас).

Меню никогда не пустое:

| Заметка / роль | Пункты |
| --- | --- |
| Личная, вход выполнен | Сделать общей · — · Удалить заметку |
| Личная, без входа (`canMakeShared = false`) | Удалить заметку |
| Общая, владелец | Управление доступом · Сделать приватной · — · Удалить для всех |
| Общая, редактор/зритель | Выйти из заметки (+ пункты, на которые у роли есть флаг) |

`hasOverflow = !state.isShared || canMakeShared || canManageAccess || canUnshare || canLeave || canDeleteForAll`. Пустой набор возможен только у общей заметки, пока роль не загрузилась (`myRole == null`) — тогда «⋯» не показывается, как сейчас.

### 2.6 Остальное в `NoteEditorScreen`

- `EditorMeta` переезжает из `Column` в `title` панели; `TesseraTopBar(centerTitle = true, title = { EditorMeta(...) }, ...)`.
- `TagChipsExpandable` и `Spacer(8dp)` под ним — без изменений.
- Новых строк ресурсов нет (используются `editor_delete`, `editor_more`, `editor_*` существующие).

### 2.7 Мокапы

`design/mockups/screens/editor-content-light.html`, `editor-content-dark.html`, `editor-empty.html`, `editor-format-bar.html`, фон `overlay-folder-picker.html`: верхняя панель `.top-bar.centered` — шеврон, заголовок по центру (`.center-title`: путь `--title-small` 500, дата `--body-small` 400), пин, «⋯»; блок и CSS `.editor-meta` удалены. Токены — из `tokens.css`, новых цветов нет. Сделано оркестратором до раздачи срезов. Иконка пина в макете — звезда (старое расхождение, в приложении `PushPin` — уже принято).

## 3. Свайп в `TesseraPopup`

Правка — только в `TesseraPopup.kt`; меняется поведение всех пяти модалок на нём. Публичная сигнатура не меняется.

### 3.1 Состояние

- `offset` — смещение карточки вниз, px, `≥ 0`. Обычный `mutableFloatStateOf`, пишется **синхронно** из жеста (без `launch` на событие).
- `cardHeight` — высота карточки, px, из `onSizeChanged`.
- `closing` — идёт ли уход после свайпа.
- `settleJob: Job?` — **единственная** доводка (уход или возврат). Новая доводка отменяет прежнюю. Отменяется также:
  в `onDragStarted`, в `onPreScroll`/`onPostScroll` от `UserInput`, при открытии. Только она пишет `offset` вне жеста.
- Сброс `settleJob?.cancel()`, `offset = 0`, `closing = false` — **только при открытии** (`LaunchedEffect(visible) { if (visible) … }`).
  При `visible = false` смещение не трогается — ни прыжка вверх. Отмена при открытии гарантирует, что уход,
  начатый до повторного открытия (два тапа «⋯» бара формата подряд), не вызовет `onDismiss` у новой карточки.
- **Контракт вызывающих** (в KDoc `TesseraPopup`): `onDismiss` обязан перевести `visible` в `false`. Так делают все
  пять вызывающих в проде. Хосты превью и тестов с постоянным `visible = true` после свайпа остаются с карточкой
  за экраном — это допустимо только в них.

### 3.2 Палец

- `Modifier.draggable(orientation = Vertical, state = rememberDraggableState { delta -> offset = (offset + delta).coerceAtLeast(0f) }, onDragStopped = { v -> settle(v) })` вместо `pointerInput { detectVerticalDragGestures }`.
- Пока `closing` — жест не принимается (`enabled = !closing`), вложенный скролл карточку не двигает, тап по фону и «Назад» ничего не делают (карточка и так уходит).
- Где какой путь работает: колонка контента с `verticalScroll` занимает всю карточку без футера — там жест идёт через вложенный скролл (§3.6); `draggable` срабатывает на зонах вне скролла — ряд футера (`AppearancePopup`). Оба пути решают одной `releaseDismisses` и одной `settle`.
- `.offset { IntOffset(0, offset.roundToInt()) }` читает состояние в фазе раскладки — без рекомпозиции на кадр.

### 3.3 Решение при отпускании — чистая функция

```kotlin
/** Закрыть ли карточку, отпущенную со смещением [offsetPx] и скоростью [velocityPxPerSec] (вниз > 0). */
internal fun releaseDismisses(offsetPx: Float, velocityPxPerSec: Float, thresholdPx: Float, flingPxPerSec: Float): Boolean =
    when {
        velocityPxPerSec >= flingPxPerSec -> true
        velocityPxPerSec <= -flingPxPerSec -> false
        else -> offsetPx >= thresholdPx
    }
```

- `thresholdPx = min(120.dp, cardHeight / 2)`; пока `cardHeight == 0` (не измерена) — `120.dp`.
- `flingPxPerSec = 500.dp` в секунду. Обе константы — рядом, приватные, подбираются на устройстве.
- Функция `internal` в `TesseraPopup.kt`, как `sheetMaxHeight`.

### 3.4 Доводка (`settle(velocity)`)

- **Закрыть:** `closing = true`; фокус **не** снимается в начале (изменение 2026-11-12 по QA на устройстве: снятие фокуса запускает уход IME и перестройку клавиатуры, главный поток стоит ~50–150 мс, и пружина по времени «проскакивает» — карточка замирала, потом исчезала за кадр; меню формата без снятия фокуса уходило плавно). Если `clearFocusOnDismiss` — фокус снимается в корутине ухода после анимации, рядом с `gone = true`, до `onDismiss`: клавиатура уходит **после** карточки, а не вместе с ней. При поднятой клавиатуре карточка считается ушедшей, когда её верх под верхом IME (IME — окно поверх приложения): `goneAtPx = высота окна − низ IME − верх карточки`; анимировать `offset` пружиной `TesseraMotion.smoothSpring()` с `initialVelocity = max(velocity, 0)` **к цели = высота окна** (`LocalWindowInfo.containerSize.height`). Порог ухода `goneAtPx = высота окна − верх карточки в окне при offset = 0` (верх — из `onGloballyPositioned { positionInWindow().y − offset }`) строго меньше цели, поэтому пружина пересекает его на полном ходу, а не в хвосте. **В первом кадре, где `offset ≥ goneAtPx`**, остановить анимацию и вызвать `onDismiss()`. Время от отпускания до `onDismiss` — не больше ~350 мс при любой скорости (тест §4.2). Дальше `visible = false` и обычный выход `AnimatedVisibility` — карточка уже за экраном, фон уже прозрачный (§3.5), ничего не мигает.
- **Вернуть:** анимировать `offset` до 0 пружиной `TesseraMotion.snappySpring()` с `initialVelocity = velocity`; значение в блоке `animate` ограничить `coerceAtLeast(0f)` (пружина с ζ=0.85 и взмахом вверх проскочила бы ниже нуля).
- Анимация — `androidx.compose.animation.core.animate(...)` в `settleJob` (`rememberCoroutineScope().launch`), каждое значение пишется в `offset`. Отмена — по правилам §3.1 (мьютекс `draggable` об этой корутине не знает, отменяет её только сам `TesseraPopup`).
- Тап по фону, «Назад», кнопки футера, выбор пункта — как сейчас: `dismiss()` / `onDismiss()` сразу и выход `AnimatedVisibility` из текущего положения.

### 3.5 Фон следует за пальцем

- `progress = 1 − (offset / cardHeight).coerceIn(0f, 1f)`.
- Затемнение: `scrim.copy(alpha = 0.25f * progress)`; размытие haze — `alpha = progress` в блоке `hazeEffect { }` (`HazeEffectScope.alpha` в haze 1.2.2 есть). `backgroundColor` и `blurRadius` остаются заданы (память `haze_explicit_background_and_radius`). Проверить на устройстве, что размытие гаснет плавно, без мигания.
- При `cardHeight == 0` (первый кадр) `progress = 1`.
- Сила размытия — `GlassBlurRadius` 28dp (было 16dp; просьба пользователя 2026-11-13). Константа общая: фон под
  попапами и стеклянные нижние панели редактора и списка.

### 3.6 Вложенный скролл

- `onPreScroll`: только `source == NestedScrollSource.UserInput`; при `available.y < 0` и `offset > 0` — сперва поднять карточку (как сейчас).
- `onPostScroll`: только `UserInput`; `available.y > 0` → `offset += available.y`.
- `onPreFling(available)`: если `offset > 0` — `settle(available.y)` и вернуть `available` (вся скорость съедена, контент под карточкой не крутится); иначе `Velocity.Zero`.
- Инерция скролла контента карточку больше не двигает.

### 3.7 Клавиатура при закрытии

- Пока карточка уходит (`closing` или `!visible` во время выхода) её нижний отступ (IME ∪ nav bar), `keyboardVisible` (от него зависят и зазор 12/16dp `cardBottomGap`, и `maxCardHeight`) **заморожены** на значениях последнего кадра до начала закрытия. Клавиатура уезжает сама, карточка не меняет ни размер, ни отступ. После свайпа клавиатура уходит уже после карточки (§3.4) — заморозка работает на путях «тап по фону» и Back, где фокус снимается сразу (`dismiss`). Кнопки футера и выбор пункта — содержимое вызывающего, фокус они не трогают (как и в базе): клавиатура уходит, когда поле покидает композицию, то есть после карточки.
- Реализация: живой отступ считается в композиции из `WindowInsets.ime.union(WindowInsets.navigationBars).getBottom(density)` и применяется `padding(bottom = …)` вместо `windowInsetsPadding(...)`, за ним `consumeWindowInsets(WindowInsets.ime.union(WindowInsets.navigationBars))` — содержимое карточки (поле, футер) не должно отступать от тех же инсетов второй раз; пока попап открыт и не `closing` — запоминается вместе с `keyboardVisible`, при закрытии используются запомненные.
- Пока открыт — поведение как сейчас (карточка над клавиатурой, `bottomInset`, футер над клавиатурой).

### 3.8 Регрессии, которые проверить

- `MarkdownFormatMenu`: `bottomInset` над баром формата, `clearFocusOnDismiss = false` — фокус и клавиатура остаются после свайпа; кнопка «⋯» бара по-прежнему закрывает меню.
- `AppearancePopup`: горизонтальные ряды цветов/иконок прокручиваются по горизонтали, вертикальный свайп по ним тянет карточку; поле названия получает фокус по тапу.
- `TagPickerPopup`: поле ввода тега и список.
- Тесты `TesseraPopupFooterTest`, `SheetMaxHeightTest`, `TesseraPopupTest`, `TesseraPopupSwipeTest` — зелёные без ослабления.

## 4. Тесты

Под инварианты фичи, не под код.

### 4.1 Unit (`app/src/test`)

- `PopupReleaseTest` для `releaseDismisses`: медленно за порог → закрыть; медленно не дотянул → вернуть; короткий быстрый взмах вниз → закрыть; дотянул за порог, но взмах вверх → вернуть; ровно на пороге → закрыть.
- `TesseraTopBarTest` (Robolectric): при `centerTitle = true`, одной кнопке слева и двух справа центр заголовка совпадает с центром панели (±1px); длинный заголовок не заходит на кнопки (правый край заголовка ≤ левого края первой кнопки действия, левый ≥ правого края навигации); при `centerTitle = false` раскладка прежняя (существующие тесты).
- `EditorMeta` (Robolectric, рядом по пакету `ui/editor/components`): четыре строки таблицы §2.3 — путь + дата; путь без даты; только дата; пусто (ничего не рисуется).
- `NoteEditorScreen` / шапка (Robolectric или instrumented, как соседи): путь и дата в панели; без папки — только дата; набор пунктов «⋯» по таблице §2.5 для личной, владельца общей, зрителя общей; «⋯» есть во всех трёх случаях; корзины в панели нет.
- `NoteEditorNativeRacesTest.delete_right_after_a_key_writes_nothing` (`app/src/test/.../ui/editor/`, ~стр. 312): сейчас тапает `onNodeWithContentDescription("Delete note")`. Теперь — тап «⋯» (`EditorOverflow`), затем пункт «Удалить заметку» (по тексту или тегу `EditorDelete`). Открытие меню не должно прокрутить часы теста дальше дебаунса автосохранения: смысл теста — удаление через ~50 мс после клавиши; часы двигать не дольше, чем сейчас между клавишей и удалением.

### 4.2 Instrumented (`app/src/androidTest`)

`TesseraPopupSwipeTest` — хост с `var visible by mutableStateOf(true)` и `onDismiss = { visible = false }` (иначе путь выхода, где жил баг, не проверяется):
- существующий `swipeDownDismissesPopup` — остаётся;
- **верх карточки только опускается после отпускания за порогом**: `mainClock.autoAdvance = false`, медленный свайп за порог, затем покадрово (`advanceTimeByFrame`) снимать верх `PopupCard` до исчезновения — последовательность не убывает ни в одном кадре;
- **короткий быстрый взмах закрывает**: ~60dp за ~50 мс → `onDismiss` вызван;
- **`onDismiss` не ждёт хвоста пружины**: от отпускания за порогом (медленно и быстро) до `onDismiss` по часам теста ≤ 350 мс;
- **то же через `draggable`**: хост с футером (`footer = { … }`), свайпы начинаются на ряду футера — медленно за порог закрывает, короткий быстрый взмах закрывает, короткое медленное возвращает (без футера весь путь идёт через вложенный скролл, `onDragStopped` иначе не проверен);
- **повторное открытие во время ухода**: свайп за порог, до `onDismiss` выставить `visible = false → true` (как двойной тап «⋯» бара) — после покоя карточка открыта, в исходном положении, `onDismiss` не вызван повторно;
- **короткое медленное оттягивание возвращает**: ~60dp медленно → не закрыта, верх карточки вернулся в исходное положение;
- **инерция контента не оставляет карточку сдвинутой**: высокий контент прокручен до середины, быстрый свайп вниз по контенту, ожидание покоя → либо закрыта, либо верх карточки ровно в исходном положении;
- **клавиатура**: `AppearancePopup` с фокусом в поле названия, свайп за порог → покадрово высота карточки не меняется до её исчезновения. Если IME на тестовом эмуляторе не поднимается надёжно — проверка на устройстве через adb (запись экрана, покадрово).

Редактор (обновить поток, ассерты не ослаблять):
- `NoteEditorScreenTest.tappingDeleteControlInvokesOnDelete`: тап `EditorOverflow` → тап `EditorDelete` → `onDelete` вызван;
- `NativeEditorRacesTest.deletingTheNoteRightAfterAKeyDoesNotBringItBack` (~стр. 210): сначала открыть «⋯». Других тапов `EditorDelete` в `androidTest` нет (хелперы `AppDriver` его не трогают);
- `tappingPinControlInvokesOnTogglePin`, `tappingBackInvokesOnBack` — без изменений.

### 4.3 На устройстве (сам, adb)

- Все пять модалок: медленный свайп, быстрый взмах, оттянуть и вернуть, свайп из прокрученного контента; с клавиатурой — `AppearancePopup` (поле названия) и `TagPickerPopup`.
- Меню «⋯» формата в редакторе: свайп закрывает, клавиатура остаётся.
- Шапка в светлой и тёмной теме: личная заметка с папкой и без, общая (владелец / зритель); длинный путь папки.

### 4.4 Скриншоты Roborazzi

Golden редактора и, возможно, модалок покраснеют. Роли отчитываются красным verify, golden перезаписывает только оркестратор с фильтром по классу после просмотра `*_compare.png`.

## 5. Файлы

| Файл | Что |
| --- | --- |
| `core/ui/tessera/TesseraTopBar.kt` | `centerTitle: Boolean = false`, раскладка `Layout` |
| `ui/editor/components/EditorMeta.kt` | центрированный блок заголовка (§2.3), без иконки папки |
| `ui/editor/NoteEditorScreen.kt` | панель (§2.4), меню (§2.5), `EditorMeta` в `title`, без строки меты в `Column` |
| `core/ui/components/TesseraPopup.kt` | §3 целиком, `releaseDismisses` |
| `app/src/test/.../core/ui/components/PopupReleaseTest.kt` | новый |
| `app/src/test/.../core/ui/tessera/TesseraTopBarTest.kt` | + центрирование |
| `app/src/test/.../ui/editor/components/EditorMetaTest.kt` | новый, §4.1 |
| `app/src/test/.../ui/editor/NoteEditorNativeRacesTest.kt` | удаление через «⋯» |
| `app/src/androidTest/.../ui/components/TesseraPopupSwipeTest.kt` | + сценарии §4.2 |
| `app/src/androidTest/.../ui/editor/NoteEditorScreenTest.kt`, `.../screen/NativeEditorRacesTest.kt` | поток удаления через «⋯» |
| `design/mockups/screens/editor-*.html`, `overlay-folder-picker.html`, `shared.css` | §2.7 (сделано) |

Без новых зависимостей, без правок `libs.versions.toml`, `build.gradle.kts`, манифеста.


> **2026-11-12, по сверке QA:** `titleSmall`/`labelSmall` в `TesseraTypography` — W600 с разрядкой 0.8sp (токены капс-подписей), макет — 500/400 без разрядки. Видимое — по макету: путь `labelLarge`, дата `bodySmall`; макет `.subtitle` приведён к `--body-small`.
