# Шапка редактора и плавный свайп в модалках — план

> Ведёт оркестратор по скиллу `feature-team`; срезы исполняют ролевые сессии. Спека:
> `docs/superpowers/specs/2026-11-12-editor-header-and-popup-gestures-design.md` (далее «спека», `§N`). Коммит — только по команде пользователя.

**Цель:** шапка редактора в две строки (панель с заголовком «путь папки / дата» по центру, `‹ · 📌 · ⋯`, удаление в «⋯») и свайп-закрытие `TesseraPopup` без рывка: с учётом скорости, без прыжка назад, с фоном, гаснущим за пальцем.
**Архитектура:** только UI. `core/ui` — `TesseraTopBar(centerTitle)` и `TesseraPopup` (жест, `releaseDismisses`); `ui/editor` — `NoteEditorScreen`, `EditorMeta`.
**Стек:** Kotlin, Compose BOM 2026.06 (ui 1.11.3), material3 1.5.0-alpha18, haze 1.2.2, Lucide 1.1.0, Roborazzi.

---

## 1. Источники и окружение

- Спека — §2 шапка, §3 свайп, §4 тесты.
- Worktree `/Users/vorislove/.config/superpowers/worktrees/notes/editor-header-and-popup-gestures`, ветка `feature/editor-header-and-popup-gestures` от `master` @ `7ec94b4`.
- codebase-memory: `project = "Users-vorislove-.config-superpowers-worktrees-notes-editor-header-and-popup-gestures"`.
- Устройство команды — `emulator-5556` (AVD `Small_Phone`, 720×1280), во всех adb/gradle-вызовах `ANDROID_SERIAL=emulator-5556`. `emulator-5554` (`Pixel_9_Pro`) — пользователя, не трогать. Настоящая клавиатура на 5556 — только с `show_ime_with_hard_keyboard 1` (вернуть в конце).
- Связь с ролями — ТОЛЬКО файлами (memory `feature-team-cross-mode-messaging`): отчёты `.claude/local/msg-to-orchestrator-<роль>-<N>.md`, команды — `--resume` с `msg-*.md`.
- Рабочая папка `.claude/local/editor-header-and-popup-gestures-ref/`.

### Кадры макета

Макеты обновлены оркестратором под утверждённый дизайн (спека §2.7) до раздачи срезов.

| NN | Файл кадра | Экран | Состояние | Тема |
| --- | --- | --- | --- | --- |
| 01 | `screens/editor-content-light.html` | редактор | заметка в папке «Работа › Проекты», 2 тега | light |
| 02 | `screens/editor-content-dark.html` | редактор | то же | dark |
| 03 | `screens/editor-empty.html` | редактор | пустая заметка в папке «Идеи», без тегов | light |
| 04 | `screens/editor-format-bar.html` | редактор | клавиатура + бар формата | light |

Рендеры — `.claude/local/editor-header-and-popup-gestures-ref/mock/NN-*.png` (1125×2436). Свайп модалок визуала не меняет — макета нет, проверка поведением (§4.2, §4.3).

## 2. Спека против макета, как делаем

| Расхождение | Как делаем |
| --- | --- |
| Шеврон «назад»: макет — центрированный шеврон | `Lucide.ChevronLeft` (как в макете), спека §2.4 приведена к макету |
| Пин: в макете звезда, в приложении `PushPin` | `PushPin` — старое расхождение, уже принято |
| Разделитель под тегами в макете (`.editor-divider`) | в приложении нет с `5466c61` — уже принято, не баг |
| Дата: макет «изменено 12 июн · 16:42» | текст даёт существующий `noteDateLabel` — формат не меняем (новых строк нет) |
| Размер иконок: макет 36px-кнопки | приложение — `TesseraTopBarIconButton` 48dp, иконка 20dp (как сейчас) |

## 3. Решения

- **D1. Решения пользователя (2026-11-12):** вариант шапки «Панель + мета» — путь папки и дата в заголовке панели по центру, `‹ · 📌 · ⋯`, удаление в «⋯» последним красным пунктом; теги остаются с ✕. Свайп — по iOS-модели (расстояние или скорость, уход с той же скоростью, фон гаснет за пальцем). «Делай всё дальше без меня» — план и раздача без подтверждений.
- **D2. Правка общих компонентов:** `TesseraTopBar` — только необязательный `centerTitle = false`. `TesseraPopup` — без смены сигнатуры, но поведение свайпа меняется у всех пяти модалок: это и есть запрос пользователя (параметры поиска, папки/теги), остальные три (`TagPickerPopup`, `FolderPickerPopup`, `MarkdownFormatMenu`) получают ту же модель — регрессии проверяет qa (спека §3.8).
- **D3. Сначала красный тест на базе.** Причина рывка (прыжок назад из-за `LaunchedEffect(visible) { snapTo(0f) }`) выведена из кода, на устройстве не воспроизведена. Chunk 1 начинает с покадрового теста «верх карточки после отпускания за порогом не поднимается» на хосте `visible`/`onDismiss = { visible = false }` — Robolectric в `app/src/test/.../core/ui/components/` (зона `ui`). Тест должен быть **красным на базе** (числа в отчёт), затем зелёным после правки. Не получилось сделать красным в Robolectric — в отчёт, оркестратор отдаёт instrumented-версию `qa`.
- **D4. `StartEllipsis`** проверен по jar: `TextOverflow.StartEllipsis` есть в ui-text 1.11.3. Иконки `Lucide.ChevronLeft`, `Icons.Rounded.MoreHoriz` есть. `HazeEffectScope.alpha` в haze 1.2.2 есть (ревью спеки, javap).
- **D5. Инсеты.** `windowInsetsPadding(ime ∪ nav)` заменяется на `padding(bottom = …)` + `consumeWindowInsets(ime ∪ nav)` — содержимое карточки не отступает второй раз (спека §3.7).
- **D6. Порог и скорость** (`120.dp`, `500.dp/с`) — константы рядом с `releaseDismisses`, подбираются на устройстве в Chunk 3; изменение — в отчёт с причиной.
- **D7. Тестовые теги** `EditorBack`, `EditorPin`, `EditorOverflow`, `EditorDelete` сохраняются; `EditorDelete` переезжает на пункт меню.
- **D8. Существующие тесты, чей поток меняет спека** (правка потока — не ослабление, для `review`):
  - `app/src/test/.../ui/editor/NoteEditorNativeRacesTest.delete_right_after_a_key_writes_nothing` — `feature`;
  - `app/src/androidTest/.../ui/editor/NoteEditorScreenTest.tappingDeleteControlInvokesOnDelete`, `.../screen/NativeEditorRacesTest.deletingTheNoteRightAfterAKeyDoesNotBringItBack` — `qa`;
  - golden `NoteEditorScreenshotTest` (шапка), возможно `MarkdownFormatMenuScreenshotTest`, `AppearanceScreenshotTest`, `NotesListScreenshotTest` (если попап в кадре) — перезапись только оркестратором после просмотра; красные на базе — раздел 8.

## 4. Реестр компонентов

| Элемент | Поиск (codebase-memory) и что нашёл | Решение | Роль |
| --- | --- | --- | --- |
| Верхняя панель с заголовком по центру | `TesseraTopBar` (`core/ui/tessera`), заголовок слева в `weight(1f)` | расширяем `TesseraTopBar` необязательным `centerTitle = false`, раскладка `Layout` | ui |
| Блок «путь / дата» | `EditorMeta` (`ui/editor/components`) — строка над тегами | переписываем `EditorMeta` в центрированный двухстрочный блок, кладём в `title` панели | feature |
| Кнопки панели | `TesseraTopBarIconButton` | берём как есть | feature |
| Меню «⋯» | `DropdownMenu` в `NoteEditorScreen`; `NoteActionsSheet` (`TesseraBottomSheet`, список) — другой паттерн | оставляем `DropdownMenu`, меняем состав и порядок; новый компонент не нужен | feature |
| Модалка-карточка со свайпом | `TesseraPopup` (`core/ui/components`), `TesseraBottomSheet` (M3, вне рамок) | правим `TesseraPopup` (жест), сигнатура прежняя | ui |
| Решение при отпускании | `sheetMaxHeight` — образец чистой функции рядом с попапом | новая `internal fun releaseDismisses` в `TesseraPopup.kt` | ui |

## 5. Срезы

### Chunk 1 — `ui`: свайп `TesseraPopup` и `TesseraTopBar(centerTitle)`

Зона: `app/src/main/java/app/tessera/core/ui/**`, `app/src/test/java/app/tessera/core/ui/**`.

- [x] 1.1 Покадровый Robolectric-тест «верх карточки не поднимается после отпускания за порогом» (D3) — **красный на базе**: отпускание на 3051px, в первом кадре верх подскочил до 2439px (+612px ≈ 204dp, исходное положение) — причина рывка подтверждена. Сейчас зелёный (`TesseraPopupSwipeMotionTest`).
- [x] 1.2 `releaseDismisses` + `PopupReleaseTest` (5).
- [x] 1.3 `TesseraPopup` по спеке §3.1–§3.7: `offset` синхронно, `draggable`, единственный `settleJob`, уход к высоте окна с `onDismiss` при пересечении `goneAtPx`, возврат с `coerceAtLeast(0f)`, фон по `progress`, вложенный скролл только `UserInput` + `onPreFling` съедает скорость, заморозка инсетов/`keyboardVisible` при закрытии, `consumeWindowInsets`, KDoc-контракт `onDismiss`.
- [x] 1.4 Robolectric-тесты (`TesseraPopupSwipeMotionTest`, 5): ≤ 350 мс до `onDismiss` медленно и быстро, возврат, повторное открытие. Не в Robolectric (→ qa): путь `draggable` с футера, инерция контента, клавиатура, haze. Где выполнимо: время от отпускания до `onDismiss` ≤ 350 мс; короткое медленное — возврат; повторное открытие во время ухода — без второго `onDismiss`.
- [x] 1.5 `TesseraTopBar(centerTitle)` (`Layout` по слотам, ветка `false` — прежний `Row`; `TesseraTopBarTest` 4) + `TesseraTopBarTest` (центр ±1px при 1 кнопке слева и 2 справа; длинный заголовок не заходит на кнопки).
- [x] 1.6 Приёмка оркестратором: `compileDebugKotlin` ок; `testDebugUnitTest app.tessera.core.ui.*` — 102/0 (25 классов); `verifyRoborazziDebug --rerun` core.ui.components + TopBar + MarkdownFormatMenu + Appearance + NotesList + Settings — 66/0 (10 классов), golden не менялись. Проверки: `compileDebugKotlin`; `testDebugUnitTest --tests 'app.tessera.core.ui.*'`; `verifyRoborazziDebug` классов `app.tessera.core.ui.*` и `MarkdownFormatMenuScreenshotTest`, `AppearanceScreenshotTest`.

**Готово, когда:** тест 1.1 был красным на базе и зелёный сейчас; `PopupReleaseTest`, `TesseraTopBarTest`, `TesseraPopupFooterTest`, `SheetMaxHeightTest` зелёные (числа `>> gw`); модуль компилируется; красные verify — перечислены, не перезаписаны.

### Chunk 1.1 — `ui`: правки ревью Chunk 1

- [x] Ревью Chunk 1 (`review`, 18:33): 0 блокеров, 2 заметно, 9 мелочей. Взяты п.1 (Back во время ухода поглощается), п.2 (флаг `gone`: после ухода свайпом выход без анимации — невидимый слой не глотает касания), п.3 (`onDismiss` один раз за открытие), п.4 (закрытие посреди возврата — без встречного движения), п.5 (`onPreFling` берёт скорость только своего жеста, горизонтальную не съедает), п.9 (цель пружины `max(высота окна, goneAt + высота карточки)`), п.10 (`placeRelative`), п.11 (тест ловит поздний второй `onDismiss`). Не взяты: п.6 (⋯ бара в окне ухода) и п.9 по устройству — qa; п.7, п.8 — приняты как есть.
- [x] Приёмка: `testDebugUnitTest app.tessera.core.ui.*` — 105/0 (25 классов; `TesseraPopupSwipeMotionTest` 8: тап под попапом доходит через 2 кадра после `onDismiss`, Back хоста во время ухода не срабатывает, закрытие посреди возврата — один кадр подъёма на 12px допущен и описан: анимация шага идёт раньше рекомпозиции, без п.4 подъём продолжается `1610 → 1594`).
- [x] Golden `NoteEditorScreenshotTest` перезаписаны оркестратором с фильтром после просмотра compare (content_light, image_dark): 8 кадров (content/empty/image/table × light/dark); verify — 10/0.

### Chunk 1.2 — `ui`: фокус снимается после ухода (эксперимент по QA)

- [x] QA 3a (19:36): при поднятой клавиатуре после отпускания карточка стоит ~50–150 мс и исчезает с фоном за кадр; А/Б — меню формата (фокус не снимается) уходит плавно. Решение оркестратора (отступление от утверждённого «клавиатура уходит вместе с карточкой», спека §3.4/§3.7 обновлены): `clearFocus` — в конце ухода (рядом с `gone = true`, вне `if (currentVisible)`), порог «ушла» при клавиатуре — верх IME.
- [x] Приёмка: `core.ui` 107/0 (роль, `--rerun`), components 23/0 (оркестратор); `TesseraPopupSwipeMotionTest` 10 (+фокус снят после `onDismiss`, при повторном открытии — остаётся).
- [x] Перепроверка на устройстве (qa-2): взмахи с клавиатурой 5/5 с промежуточными кадрами (в 1.1 — «стоит → исчезает за кадр»), клавиатура уходит после карточки 12/12; медленные 5/7 (2/7 — путь до верха IME ~0–2 кадра записи, как у эталона без снятия фокуса — принято). Новый артефакт: маркер курсора поля над клавиатурой во время ухода → Chunk 1.3.

### Chunk 1.3 — `ui`: маркер курсора скрыт во время ухода

- [x] Пока `closing`, внутри карточки `LocalTextSelectionColors.handleColor = Transparent` (фокус и клавиатура — как в 1.2). `core.ui` 108/0, verify 124/0.
- [x] QA-3: маркер над клавиатурой — 0 из 11 записей. **Остаток:** взмах папки с клавиатурой в первые ~4 с после тапа в поле (маркер виден) — на эмуляторе пауза 100–200 мс, затем уход за кадр (0/5 плавно); без маркера (после ввода символа, пустое поле TagPicker, без клавиатуры) — плавно. Гипотеза — перерисовка окна маркера при смене цвета; замеров главного потока нет; эмулятор перегружен (janky 81 % кадров без клавиатуры, load average 4–5). **Решение оркестратора:** оставить 1.3 (откат даёт детерминированный артефакт на любом устройстве — маркер поверх клавиатуры), остаток — «не проверено на реальном телефоне», альтернатива — прятать маркер на старте перетаскивания.

### Chunk 2 — `feature`: шапка редактора

Зона: `app/src/main/java/app/tessera/ui/**`, `app/src/test/java/app/tessera/ui/**`, `res/**` (новых строк не ожидается). Старт — после приёмки Chunk 1 (нужен `centerTitle`).

- [x] 2.1 `EditorMeta` (+`EditorMetaTest` 4) → центрированный блок (спека §2.3) + `EditorMetaTest` (4 строки таблицы).
- [x] 2.2 `NoteEditorScreen`: `TesseraTopBar(centerTitle = true, title = { EditorMeta(...) })`, кнопки §2.4 (`Lucide.ChevronLeft`, `PushPin`, `MoreHoriz`), меню §2.5 (порядок, разделитель, красное удаление, `handOverEdit()` до `onDelete()`, `hasOverflow`), строка `EditorMeta` из `Column` убрана.
- [x] 2.3 Robolectric (`NoteEditorHeaderTest` 7, вкл. общую с `myRole = null` — «⋯» нет): набор пунктов «⋯» по таблице §2.5 (личная / владелец общей / зритель общей); корзины в панели нет; путь и дата в панели.
- [x] 2.4 `NoteEditorNativeRacesTest.delete_right_after_a_key_writes_nothing` — удаление через «⋯» без лишнего хода часов (спека §4.1).
- [x] 2.5 Приёмка оркестратором: `compileDebugKotlin` ок; `testDebugUnitTest app.tessera.ui.editor.*` — 753/0 (69 классов). Кадры `NoteEditorScreenshotTest` дополнены папкой и датой (content — «Work › Projects / 12 Jun 2024», image — длинный путь со `StartEllipsis`); verify красный 8/10 — задумано, compare просмотрены (content_light, image_dark). Проверки: `compileDebugKotlin`; `testDebugUnitTest --tests 'app.tessera.ui.editor.*'`; `verifyRoborazziDebug --tests 'app.tessera.ui.editor.NoteEditorScreenshotTest'` (ожидаемо красный — в отчёт, golden не писать).

**Готово, когда:** тесты пакета `ui.editor` зелёные (числа), кроме ожидаемо красного verify шапки; новых `Color(0x…)` и строк без `values-ru` нет.

### Chunk 3 — `qa`: устройство и сверка

Зона: `app/src/androidTest/**`, `docs/superpowers/qa/editor-header-and-popup-gestures-*.md`, `.claude/local/editor-header-and-popup-gestures-ref/qa/`. Часть 3a стартует после приёмки Chunk 1 (параллельно с Chunk 2), 3b — после приёмки Chunk 2.

- [x] 3a.1 `TesseraPopupSwipeTest` — сценарии спеки §4.2 (хост `visible`/`onDismiss = { visible = false }`; покадрово «не поднимается»; быстрый взмах; медленный возврат; путь `draggable` на хосте с футером; инерция контента; повторное открытие; ≤ 350 мс до `onDismiss`; клавиатура в `AppearancePopup`).
- [x] 3a.2 На устройстве руками (adb, запись экрана, покадрово): все пять модалок — медленно, взмах, оттянуть и вернуть, из прокрученного контента; с клавиатурой — `AppearancePopup`, `TagPickerPopup`; меню «⋯» формата — клавиатура остаётся. Подбор D6.
- [x] 3b.1 `NoteEditorScreenTest.tappingDeleteControlInvokesOnDelete`, `NativeEditorRacesTest.deletingTheNoteRightAfterAKeyDoesNotBringItBack` — через «⋯»; on-device тест набора «⋯» по ролям, если Robolectric 2.3 чего-то не покрыл.
- [x] 3b.2 Сверка кадров 01–04 с настоящим экраном (паритет `wm size 1125x2436`, `density 480`), склейки, `docs/superpowers/qa/editor-header-and-popup-gestures-design-diff.md`; light/dark; длинный путь папки; без папки; общая заметка (владелец/зритель — если есть аккаунт; иначе в «не проверено»); шрифт ×1.3.
- [x] 3b.3 Вернуть устройство: `wm size reset`, `wm density reset`, `font_scale 1.0`, тема, `show_ime_with_hard_keyboard`.

**Готово, когда:** instrumented-классы фичи зелёные (числа `>> gw`), склейки 01–04 и таблица расхождений, записи экрана свайпов, устройство возвращено.

**Итог QA:** `TesseraPopupSwipeTest` 15/0 (на базовой копии попапа те же тесты — 13/7: прыжок 868 → 444 px в кадре 0, взмах не закрывает, инерция оставляет карточку на 1538 px, высота с клавиатурой 610 → 768); `NoteEditorScreenTest` 3/0, `NativeEditorRacesTest` 11/0 (удаление через «⋯» в 17–46 мс после клавиши, guard < 300 мс), `NoteEditorOverflowMenuTest` 3/0, `EditorHeaderShotsTest` 1/0 (fs 1.0 и 1.3). Отчёты: `docs/superpowers/qa/editor-header-and-popup-gestures-gestures.md`, `…-design-diff.md`. Начертание шапки приведено к макету (путь `labelLarge`, дата `bodySmall`; макет `.subtitle` → `--body-small`).

### Chunk 4 — оркестратор: размытие сильнее (просьба пользователя 2026-11-13)

- [x] `GlassBlurRadius` 16 → 28dp (`core/ui/theme/Color.kt`). Одна константа на фон под попапами и стеклянные нижние
  панели редактора и списка. Устройство (emulator-5556, 16dp против 28dp на одной сборке): фон под попапами
  размыт заметно сильнее, панель списка — слабо заметно. Под панелью редактора текста нет: область текста кончается
  у её верхнего края, поэтому меняется только мягкость теней иконок. В Robolectric на 28dp поверх размытия
  проступает бледный резкий текст, на устройстве его нет. Golden перезаписаны: 17 штук в 4 классах
  (`NotesListScreenshotTest`, `AppearanceScreenshotTest`, `MarkdownFormatMenuScreenshotTest`,
  `TesseraShowcaseScreenshotTest`), verify 37/0. Итого golden в ветке: 25 (8 шапка + 17 размытие).

### Chunk 5 — оркестратор: строки шторки ⋯ ниже (просьба пользователя 2026-11-13)

- [x] `MarkdownFormatMenu`: строки в своей `Column` без зазора попапа (16dp) — шаг строки 64 → 48dp, карточка ниже на ~80dp.
  Сама строка осталась 48dp (минимум касания, `AccessibilityAuditTest`). `MarkdownFormatMenuTest` 5/0; golden
  `editor_format_sheet_{light,dark}` перезаписаны, verify 2/0. On-device `FormatSheetTest` + `FormatSheetShotsTest`: 11, упал 1 (ниже).
  **Открыто:** `FormatSheetShotsTest.theCardFitsAboveTheBarAndItsLastRowIsReachable` КРАСНЫЙ с компактными строками
  (с прежней вёрсткой — зелёный, 1 перетаскивание; проверено подменой файла). Новый список прокручивается всего на
  ~260 px, и перетаскивание теста (289 px, старт теперь на строке) доходит до конца. После этого под тестом
  (UiAutomation подключён) `RenderThread` занят ~37 % CPU, кадры идут без остановки (18 за 4 с), `waitForIdle` не
  возвращается, тест висит до таймаута. Шторка при этом открыта, прокручена до «Quote», стиль не сменился
  (снимки каждые 2 с). Вручную тот же быстрый взмах до конца лишних кадров не даёт (9 за 4 с — мигание курсора).
  Причина не найдена; подозрение — конец прокрутки (overscroll) + nested scroll `TesseraPopup` при включённой
  доступности.

### Ревью

`review` (только чтение) — после Chunk 1 и после Chunk 2, гейты шага 6 скилла. Финал — новой сессией по всему диффу.

## 6. Строки

Новых ключей нет: `editor_back`, `editor_pin`/`editor_unpin`, `editor_more`, `editor_delete`, `editor_make_shared`, `editor_manage_access`, `editor_make_private`, `editor_leave`, `editor_delete_for_all` — существующие, en + ru.

## 7. Финал

Шаг 8 скилла: полный `testDebugUnitTest --rerun`, полный `verifyRoborazziDebug` против базы, сам на устройстве главный сценарий, новая сессия `review`, advisor, отчёт.

## 8. Открытые вопросы и кандидаты на уборку

- База @ `7ec94b4` (2026-11-12): `verifyRoborazziDebug` по `NoteEditorScreenshotTest`, `MarkdownFormatMenuScreenshotTest`, `AppearanceScreenshotTest`, `NotesListScreenshotTest`, `SettingsScreenshotTest`, `TesseraShowcaseScreenshotTest`, `TesseraTopBarTest`, `core.ui.components.*` — `>> gw тесты: всего 65, упало 0` (10 классов). Любой красный там после правок — наш.
- Кандидаты на уборку:
  - `EditorMetaRenderTest` (2) дублирует часть нового `EditorMetaTest` (feature);
  - `SearchOptionPopup`/`TagPickerPopup` с клавиатурой ограничены только 60 % — на 640 dp заголовок уходит под статус-бар (база, qa п.2);
  - Back при открытом сайдбаре списка выходит из приложения — нет `BackHandler` в `NotesListScreen.kt:256–257` (база, qa п.3);
  - красные на базе: `AccessibilityAuditTest.settingsCompactTargetsAreDocumented` (недокументированный 52×32dp — Switch в настройках), `ShareReceiverViewModelTest` «save into existing note appends text» (порядок склейки) — оба 1 упавший и на `7ec94b4`.
