# Редактор: касания вне текста, ручка вставки, меню «⋯» как модалка — план

> Ведёт оркестратор по скиллу `feature-team`; срезы исполняют ролевые сессии (не subagent-driven). Спека:
> `docs/superpowers/specs/2026-11-11-editor-touch-and-format-sheet-design.md` (далее «спека», ссылки `§N`).
> Коммит — только по команде пользователя.

**Цель:** капли нет; долгое нажатие вне текста не выделяет слово, а ставит каретку и даёт «Вставить / Выбрать всё»; меню «⋯» — модалка с размытым фоном, нижний бар чёткий, «⋯» открывает и закрывает, «назад» не выходит из заметки.

**Архитектура:**
- **Движок.** `TesseraEditText` сам решает жесты вне текста: `performLongClick`, `ACTION_CANCEL` на отпускании, свой floating `ActionMode`. Курсор платформы скрыт на всех API (`isCursorVisible = false`).
- **Экран.** В `NoteEditorScreen` бар формата выносится из `Scaffold` в корневой `Box`, над модалкой `TesseraPopup`. У `TesseraPopup` два новых необязательных параметра.

**Стек:** Kotlin, Compose M3, Haze 1.2.2, Android `TextView`/`Editor`, Roborazzi, instrumented-тесты на AVD API 36.

---

## 1. Источники и окружение

- **Спека** — §2 ручка, §3 касания, §4 меню, §6 тесты.
- **Макета нет.** Эталон вида модалки — `SearchOptionPopup` на экране поиска (`ui/notes/components/SearchScreen.kt`, на `TesseraPopup`): фон, карточка, строки.
  - Эталонный кадр снимает `qa` с настоящего экрана поиска (фильтр «Тег» или «Период») в light и dark → `.claude/local/editor-touch-and-format-sheet-ref/ref/search-option-{light,dark}.png`.
- **Worktree и база.** Worktree `/Users/vorislove/.config/superpowers/worktrees/notes/editor-touch-and-format-sheet`, ветка `feature/editor-touch-and-format-sheet`, база `master` @ `6dd1d2b`.
- **codebase-memory:** `project = "Users-vorislove-.config-superpowers-worktrees-notes-editor-touch-and-format-sheet"`.
- **Устройства:**
  - `emulator-5554` — у пользователя, команде **не трогать**;
  - команда работает на AVD `Small_Phone`, порт 5556 → `emulator-5556`, инструментальные прогоны с `ANDROID_SERIAL=emulator-5556`;
  - на машине только образ `android-36`: API 26–28 не проверяются (в финальном отчёте — «не проверено»).
- **Рабочая папка:** `.claude/local/editor-touch-and-format-sheet-ref/` (`baseline/`, `probe/`, `ref/`, `qa/`).

| NN | Кадр | Экран | Состояние | Тема |
| --- | --- | --- | --- | --- |
| — | без макета | редактор | меню «⋯» открыто | light, dark |
| — | без макета | редактор | плавающее меню «Вставить / Выбрать всё» под текстом | light |
| ref | `ref/search-option-*.png` | поиск | `SearchOptionPopup` открыт (эталон модалки) | light, dark |

## 2. Спека против макета — как делаем

Макета нет. Вид модалки сверяется с эталоном `SearchOptionPopup`:
- те же фон, форма и тень карточки, отступы строк, цвет выделенной строки;
- отличия — заголовок «Стиль абзаца», иконки в строках, карточка над баром.

## 3. Решения

**D1. Решения пользователя (2026-11-11, дословно по ответам):**
- «капля под кареткой» → «Не показывать совсем»;
- «долгое нажатие в пустом месте под текстом» → «Каретка в конец + „Вставить“» (с «Выбрать всё»);
- «меню „⋯“» → «Только нижний бар» чёткий, всё остальное размыто, включая верхний бар;
- «отступ без клавиатуры» → «Оставить без отступа»: поведение `9b83ada`, `GlassBarGuardsTest.textRunsUnderTheNavigationBarWithoutTheKeyboard` переписывается на `bottomInset == 0` + текст в полосе навбара. Это решение пользователя, а не ослабление ассерта (для `review`).

**D2. Решения оркестратора** (вошли в спеку после одобрения дизайна, пользователю — в первом отчёте):
- платформенный пузырь «Вставить» по тапу на каретку пропадает вместе с ручкой (§2);
- долгое нажатие правее короткой строки и на пустой строке — тоже «каретка + меню» (§3);
- любая другая кнопка бара при открытом меню выполняет действие и закрывает меню (§4.4);
- ячейки таблиц (`TableOverlayHost`) вне рамок — если проба P4 покажет каплю в ячейке, вопрос пользователю **до** кикоффа Chunk 1.

**D3. Механизмы с запасными путями** (проба — Chunk 0b; провал — запасной путь без смены подхода посреди среза):

| Проба | Что проверяем на API 36 | Провал → запасной путь |
| --- | --- | --- |
| P1 | `editor.isCursorVisible = false` в рантайме: (a) после тапа нет окна ручки и пикселей капли; (b) долгое нажатие по слову даёт ручки выделения и панель; (c) каретка движка рисуется; (d) при наборе в конце длинной заметки view следует за кареткой | (a) или (b) провал → вопрос пользователю до Chunk 1. (c) или (d) провал → движок сам скроллит к каретке (`bringPointIntoView` после правки) — в срез Chunk 1 |
| P2 | `startActionMode(cb, TYPE_FLOATING)` из теста + затем `ACTION_CANCEL` в `onTouchEvent`: меню живо; «Вставить» через `onTextContextMenuItem(paste)` вставляет | Меню гибнет на CANCEL → меню стартует на `ACTION_UP` (после CANCEL), а не в `performLongClick` |
| P3 | `performAccessibilityAction(ACTION_SET_SELECTION, 0..len)` → видны ручки и «Копировать» | Пункт «Выбрать всё» убирается (спека §3.2) |
| P4 | Тап по ячейке таблицы: есть ли капля в ячейке | **Итог:** капля в ячейке есть. Решение пользователя 2026-11-11 — «Пока оставить»: ячейки вне рамок, кандидат на отдельную задачу |

**D4. Состав команды.**
- **Одна пишущая роль `feature`** (sonnet), два последовательных среза: движок (Chunk 1), потом экран и меню (Chunk 2).
  - Зона: `ui/**`, `res/**`, их тесты в `app/src/test`, плюс явно `core/ui/components/TesseraPopup.kt` (решение оркестратора: одна правка общего компонента двумя необязательными параметрами; отдельная роль `ui` ради неё не нужна).
- **`review`** — после каждого среза и финальный (новая сессия).
- **`qa`** — Chunk 0b (база на устройстве + пробы), затем Chunk 3 на принятом коде. Владеет `emulator-5556`.

**D5. Инвариант пикселей §4.2** проверяется в устоявшихся состояниях (клавиатура полностью поднята или полностью спрятана), не во время анимации IME.

## 4. Реестр компонентов (ресёрч до создания)

| Элемент | Запрос в codebase-memory и находка | Решение | Роль |
| --- | --- | --- | --- |
| Модалка: размытый фон + карточка | `search_graph` «Popup» → `core/ui/components/TesseraPopup` (используют `SearchOptionPopup`, `FolderNamePopup`, `FolderPickerPopup`, `TagPickerPopup`) | Берём `TesseraPopup`, расширяем `bottomInset: Dp = 0.dp`, `clearFocusOnDismiss: Boolean = true` (§4.3) | feature |
| Строка варианта с выделением | `SearchOptionPopup` (`ui/notes/components/SearchScreen.kt:367`) — строки внутри экранного composable, без иконки | Не выносим: строки в `MarkdownFormatMenu` с теми же токенами (`Surface`, 16 dp, `TesseraSpacing.md`, `secondaryContainer`). Кандидат на уборку — общий `TesseraOptionRow` при третьем использовании | feature |
| Плавающее меню «Вставить / Выбрать всё» | нет в Compose; платформенный `ActionMode.TYPE_FLOATING` | Платформа, без нового компонента | feature |
| Стеклянный бар формата | `NoteEditorScreen` (`tesseraGlass`, `tesseraGlassShadow`, `EditorFormatBar`, `TableActionsBar`) | Переносится без изменений вида | feature |

Новых компонентов `Tessera*` нет.

## 5. Срезы

### Chunk 0a: база — unit и Roborazzi (оркестратор)

- [x] `gw.sh :app:testDebugUnitTest :app:verifyRoborazziDebug --continue` на `6dd1d2b` (итог — раздел 8, «Унаследованный долг») → `baseline/unit-roborazzi.log`, числа `>> gw`.
- [ ] Красное — в раздел 8 «Унаследованный долг» с именами тестов; роли это не чинят.
- [ ] Копия `app/build/outputs/roborazzi/` и текущих golden редактора (`app/src/test/screenshots/**/editor*`, `NoteEditor*`) → `baseline/roborazzi/` — «до» для инварианта §4.2.

### Chunk 0b: база и пробы на устройстве (`qa`)

**Зона:** `app/src/androidTest/**`, `.claude/local/editor-touch-and-format-sheet-ref/{baseline,probe}/`.

- [ ] Поднять `Small_Phone` на 5556 (если не запущен): `~/Library/Android/sdk/emulator/emulator -avd Small_Phone -port 5556 -no-snapshot-save &`, дождаться `sys.boot_completed`. SystemUI ANR после загрузки — закрыть (memory `device-e2e-gotchas`).
- [ ] Базовый прогон классов на `6dd1d2b` (`ANDROID_SERIAL=emulator-5556`, по одному `-Pandroid.testInstrumentationRunnerArguments.class=…`):
  - `GlassBarGuardsTest`, `GlassBarShotsTest`, `GlassPopupShotsTest`;
  - `TesseraPopupTest`, `TesseraPopupSwipeTest`;
  - `NativeEditorFlingTest`, `NativeTableGestureTest`.

  Числа и красные — в `baseline/device.md`. Скрины `GlassBarShotsTest` → `baseline/device-shots/`.
- [ ] Пробы P1–P4 (D3) — тест `app/src/androidTest/java/app/tessera/ui/editor/screen/EditorTouchProbeTest.kt` на живом редакторе (`NativeAppTest`/`AppDriver`).
  - Касания — `adb shell input` / `UiAutomation`, не Compose-правило (memory `compose-test-rule-gesture-artifact`).
  - Окна ручки и меню — `UiAutomation.getWindows()` / `UiDevice`; пиксели — PixelCopy.
  - По каждой пробе — PASS/FAIL с доказательством (скрин, дамп окон) в `probe/report.md`.
- [x] **Готово, когда:** `baseline/device.md` и `probe/report.md` с числами и скринами; устройство в исходном состоянии.
  - **Принято 2026-11-11.** Пробы `EditorTouchProbeTest`: 11/11 PASS; P1a–d, P2 (оба порядка), P3 — запасные пути D3 не нужны.
  - Способ для §6.2-1: окно `Editor$InsertionHandleView` через `WindowInspector.getGlobalWindowViews()` + `uiAutomation.takeScreenshot()` (PixelCopy окна каплю не видит).
  - Решения по вопросам `qa`:
    - две настройки клавиатуры на время Chunk 3 ставить и возвращать;
    - `EditorTouchProbeTest` дожить до Chunk 3, хелперы перенести в `EditorTouchOutsideTest`, пробу удалить до коммита;
    - `NativeEditorFlingTest` — в долг.
  - Окружение: на AVD `Small_Phone` откатаны обновления Gboard (18.5.6 → 15.1.08), вернуть без Play-аккаунта нельзя.

### Chunk 1: движок — ручка и касания вне текста (`feature`, после 0a/0b и решений по пробам)

**Файлы:**
- Modify: `app/src/main/java/app/tessera/ui/editor/engine/TesseraEditText.kt` — `hidePlatformCaret` (~1091), `handlesHidden` (~1243), `handleTouch` (~950), новый `performLongClick`, меню.
- Create: `app/src/main/java/app/tessera/ui/editor/engine/OutsideText.kt` — чистая функция «вне текста».
- Test: `app/src/test/java/app/tessera/ui/editor/engine/OutsideTextTest.kt` (Robolectric).

**Шаги:**
- [ ] **1. Падающий тест** `OutsideTextTest` на чистой функции:

  ```kotlin
  sealed interface Outside { data object BelowText : Outside; data class OffLine(val offset: Int) : Outside }
  /** [x],[y] — координаты layout'а (уже за вычетом padding и со scroll). null — точка по тексту. */
  fun outsideTextAt(layout: android.text.Layout, x: Float, y: Float): Outside?
  ```

  Кейсы:
  - ниже последней строки → `BelowText`;
  - правее конца короткой строки → `OffLine(lineEnd)`;
  - пустая строка → `OffLine(lineStart)`;
  - по слову → `null`;
  - левее начала строки с отступом (цитата, список) → `OffLine(lineStart)`.

  `StaticLayout` строится в тесте.
- [ ] **2.** Прогон `gw.sh :app:testDebugUnitTest --tests 'app.tessera.ui.editor.engine.OutsideTextTest'` → красный (нет функции).
- [ ] **3.** Реализация `outsideTextAt`: `getLineForVertical`, `y > getLineBottom(lastLine)` → `BelowText`; иначе `x < getLineLeft(line) || x > getLineRight(line)` → `OffLine(getOffsetForHorizontal(line, x))`.
- [ ] **4.** Тест зелёный (число из `>> gw`).
- [ ] **5. §2.**
  - `hidePlatformCaret()` → `isCursorVisible = false` на всех API;
  - `NoCaret` удалить, если больше не используется;
  - сеттер `handlesHidden` — без строки про `isCursorVisible` (комментарий обновить: курсор платформы скрыт всегда);
  - запасной путь P1(c/d), если решено, — здесь же.
- [ ] **6. §3.2, `handleTouch`:**
  - `downOutside` вычисляется на `ACTION_DOWN` **до** проверки `ready && editable`, если нет цели (`tapTarget`/`imageTapTarget`/`tableTapTarget`);
  - перевод координат view → layout: `x - totalPaddingLeft + scrollX`, `y - totalPaddingTop + scrollY`;
  - сброс на `UP`/`CANCEL`;
  - любой `ACTION_DOWN` закрывает наше меню;
  - `caughtFling` → `downOutside = null`.
- [ ] **7. `override fun performLongClick(): Boolean` по §3.2:**
  - хаптика `LONG_PRESS`;
  - `requestFocus`, `setSelection` (§3.1: `BelowText` → конец заметки: последний абзац текстовый — его конец, объект — `caretNextToTable(ps, above = false)`);
  - `showSoftInput`, старт меню;
  - флаг «наше долгое нажатие» для `UP`;
  - у зрителя — `return true` без действий.
- [ ] **8. `ACTION_UP`:**
  - после нашего долгого нажатия — `ACTION_CANCEL` в `super`, `return true`;
  - тап с `BelowText` — `CANCEL` в `super` + каретка в конец + `showSoftInput`, `return true` (только у редактируемой).
- [ ] **9. Меню:**
  - `startActionMode(callback, ActionMode.TYPE_FLOATING)`, `Callback2.onGetContentRect` = прямоугольник каретки (`layout.getPrimaryHorizontal`, `getLineTop`/`getLineBottom`);
  - пункты `android.R.id.paste` (если в буфере текст) и `android.R.id.selectAll` (если текст не пуст), заголовки `android.R.string.paste` / `android.R.string.selectAll`;
  - «Вставить» → `onTextContextMenuItem(paste)` → `finish`;
  - «Выбрать всё» → `finish` → `performAccessibilityAction(ACTION_SET_SELECTION, Bundle(START=0, END=length))` (или удалён по P3);
  - `finish` на наборе, смене выделения, `ACTION_DOWN`;
  - запасной путь P2, если решено.
- [ ] **10.** `gw.sh :app:compileDebugKotlin :app:testDebugUnitTest --tests 'app.tessera.ui.editor.*'` — числа. Красное из «Унаследованного долга» не считается.
- [x] **Готово, когда:**
  - модуль компилируется;
  - `OutsideTextTest` зелёный, тесты `app.tessera.ui.editor.*` не хуже базы;
  - в отчёте — как закрыты P1–P4 и что не проверено (API 26–28).
  - **Принято 2026-11-11:**
    - сессия 5564615e → 286b8fce;
    - ревью-1: 1 блокер (дрейф > slop обнулял `downOutside`) и 5 «заметно» — исправлены;
    - новые `OutsideTextTest` (5), `TesseraEditTextOutsideTouchTest` (9);
    - прогон оркестратора `ui.editor.*` --rerun: 734/0, классов 65;
    - на устройстве — Chunk 3.

### Chunk 2: экран — бар над модалкой, меню «⋯» (`feature`, после приёмки Chunk 1)

**Файлы:**
- Modify: `app/src/main/java/app/tessera/core/ui/components/TesseraPopup.kt` (§4.3).
- Modify: `app/src/main/java/app/tessera/ui/editor/components/MarkdownFormatMenu.kt` (§4.1, §4.6).
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt` (§4.2, §4.4).
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ru/strings.xml` (`editor_style_menu_title`).
- Modify: `app/src/test/java/app/tessera/ui/editor/components/MarkdownFormatMenuTest.kt`.
- Create: `app/src/test/java/app/tessera/ui/editor/components/MarkdownFormatMenuScreenshotTest.kt` (light + dark, `captureScreenRoboImage`).

**Шаги:**
- [ ] **1. `TesseraPopup`:**
  - `bottomInset: Dp = 0.dp` и `clearFocusOnDismiss: Boolean = true`;
  - при `bottomInset > 0`: `padding(bottom = bottomInset + cardBottomGap)` над IME;
  - макс. высота `min(60 % экрана, max(144.dp, место до низа статус-бара))` — через `BoxWithConstraints` или `Layout` после `windowInsetsPadding`;
  - при `0.dp` — путь и формула прежние;
  - `dismiss` снимает фокус только при `clearFocusOnDismiss`.
- [ ] **2. `MarkdownFormatMenuTest` переписать (падающий):**
  - показывает заголовок и 6 строк;
  - активная строка по `formatState` (семантика или `testTag` + цвет);
  - тап по строке вызывает действие и `onDismiss`.

  Прогон → красный.
- [ ] **3. `MarkdownFormatMenu` на `TesseraPopup`:**
  - сигнатура `expanded`, `actions`, `onDismiss`, `formatState`, плюс `hazeState`, `bottomInset`;
  - `clearFocusOnDismiss = false`;
  - строки по §4.1;
  - удалить `Popup`, `AboveBarPositionProvider`, `ShadowRoom*`, `FromButton`, `roomAbovePx`, `MinListHeight`.

  Тест зелёный.
- [ ] **4. Строка** `editor_style_menu_title`: en «Paragraph style», ru «Стиль абзаца».
- [ ] **5. `NoteEditorScreen`:**
  - `Box(hazeSource)` вокруг `Scaffold`, `hazeSource` с `Column` снять;
  - бар (вся `AnimatedVisibility` с `Column`, `pointerInput`, пилюлей, `onSizeChanged`) — из `bottomBar` в корневой `Box`, `Alignment.BottomCenter`, после `MarkdownFormatMenu`;
  - отступ колонки снизу `if (imeVisible) ime.bottom else 0`;
  - `NoteBody.bottomInsetPx` как сейчас.
- [ ] **6. Состояние:**
  - `formatMenuExpanded` — на уровень экрана;
  - `LaunchedEffect(imeVisible, nativeTableBar, state.isReadOnly)` → закрыть при `!imeVisible || nativeTableBar != null || isReadOnly`;
  - обёртка действий бара (по образцу `wrapActions`): любое действие, кроме `onToggleMenu`, закрывает меню;
  - `MarkdownFormatMenu(bottomInset = barExtentPx в dp)`.
- [ ] **7. Скриншот-тест** `MarkdownFormatMenuScreenshotTest` (light, dark): экран редактора с открытым меню. Golden не пишет — отчёт.
- [ ] **8.** `gw.sh :app:compileDebugKotlin :app:testDebugUnitTest --tests 'app.tessera.ui.editor.*' --tests 'app.tessera.core.ui.*'` и `gw.sh :app:verifyRoborazziDebug --tests 'app.tessera.ui.editor.*' --tests 'app.tessera.core.ui.*'` — числа. Красный verify по чужим экранам — в отчёт с именами.
- [x] **Готово, когда:**
  - **Принято 2026-11-11:**
    - сессия a1e4e8f4 → 8c212ef3;
    - ревью-2: 0 блокеров, 1 заметно (тест на `sheetMaxHeight`) + 5 мелочей — исправлены; readOnly + IME → отступ колонки ime (решение оркестратора, формула спеки);
    - прогон оркестратора до правок: `ui.editor.*` + `core.ui.*` 794/0, verify редактора 10/3/5;
    - golden `editor_format_sheet_{light,dark}` записан оркестратором с фильтром, verify 2/0;
  - модуль компилируется;
  - `MarkdownFormatMenuTest` зелёный;
  - Roborazzi редактора и попапов не хуже базы (новые скриншоты — без golden, в отчёт);
  - в отчёте — как посчитан `bottomInset` и формула высоты.

### Chunk 3: QA на устройстве (`qa`)

- [x] **Принято 2026-11-11** (сессии dc292f14 → ebc04f66 → 7f077a0a → a6af9277 → cde1e86f; отчёт `docs/superpowers/qa/editor-touch-and-format-sheet.md`):
  - `EditorTouchOutsideTest` 12/12, включая 3 теста «меню вставки уступает модалке» после финального ревью;
  - `FormatSheetTest` 6/6; `GlassBarGuardsTest` 3/3, переписан по D1;
  - регресс 7 классов зелёный;
  - пиксели §4.2: 26 пар из 28 совпадают, 2 отличаются фазой каретки;
  - визуал совпадает с `SearchOptionPopup`; багов прод-кода нет;
  - не проверено: ландшафт с клавиатурой (extract UI, было на базе), API 26–28.

- **3a** — после приёмки Chunk 1, может идти параллельно с Chunk 2: §6.2 пп. 1–6.
- **3b** — после приёмки Chunk 2: §6.2 пп. 7–9 и визуальная сверка.

Подробно — раздел 7.

## 6. Строки

| Ключ | en | ru |
| --- | --- | --- |
| `editor_style_menu_title` | Paragraph style | Стиль абзаца |

Пункты плавающего меню — системные `android.R.string.paste` / `selectAll`.

## 7. Чанк QA (`qa`, `emulator-5556`)

1. **Тесты по инвариантам** в `app/src/androidTest/java/app/tessera/ui/editor/screen/`:
   - `EditorTouchOutsideTest` (§6.2-1…6);
   - `FormatSheetTest` (§6.2-7…8);
   - правка `GlassBarGuardsTest.textRunsUnderTheNavigationBarWithoutTheKeyboard` по D1 (только этот тест класса).

   Прогон: `ANDROID_SERIAL=emulator-5556 gw.sh :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<класс>`.
2. **Регресс §6.2-9** — те же классы, что в Chunk 0b. Сравнение с `baseline/device.md`.
3. **Визуал** (паритет: `adb shell wm size 1125x2436 && adb shell wm density 480`):
   - меню «⋯» в light и dark → склейка с `ref/search-option-*.png` (`ffmpeg hstack`) → `qa/sheet-vs-search-{light,dark}.png`;
   - плавающее меню под текстом;
   - пиксели §4.2 в устоявшихся состояниях против `baseline/device-shots/`.
4. **Нагрузка:**
   - все палитры × light/dark — меню и бар;
   - шрифт ×1.3 — карточка не заходит на бар, список прокручивается;
   - ландшафт с клавиатурой — карточка ≥ 144 dp.
5. **Отчёт** `docs/superpowers/qa/editor-touch-and-format-sheet.md`: таблица «пункт §6.2 · результат · доказательство».
6. **Вернуть устройство:** `wm size reset`, `wm density reset`, `font_scale 1.0`, тема.

## 8. Финал, вопросы, долг

**Финал (оркестратор):**
- [x] **2026-11-11:**
  - финальный `review` (890ed78a): 0 блокеров, 2 «заметно» — исправлены (17adad8d);
  - прогон оркестратора после правок: unit `ui.editor.*` + `core.ui.*` 800/0; verify редактора 10/3/5/2;
  - device: `EditorTouchOutsideTest` 12/0, `FormatSheetTest` 6/0, `NativeEditorFlingTest` 1 красный из 2 (`aTouchOnTheMoving…`, нестабилен и на базе) — сравнение ветка/база ×5 в `final2/fling-summary.txt`;
  - вручную через adb на 5556: капли нет; долгое нажатие под текстом → каретка в конце + «Paste / Select all»; «⋯» открывает модалку и прячет меню вставки; повторный «⋯» закрывает; «назад» закрывает модалку с клавиатурой, заметка остаётся;
  - полный unit-прогон до последней правки: 1337, упало 138 — те же флаки, что на базе (`final-failures.txt`).
- полный `gw.sh :app:compileDebugKotlin :app:testDebugUnitTest --rerun` и `gw.sh :app:verifyRoborazziDebug` — числа, сравнение с базой 0a;
- сам на устройстве — классы Chunk 3 и главный сценарий через adb;
- финальный `review` — новая сессия по `git diff master...HEAD` + незакоммиченное;
- advisor → отчёт пользователю.

**Унаследованный долг (база `6dd1d2b`, Chunk 0a, 2026-11-11):**
- **Полный unit-прогон:** 1315, упало 128. Почти всё — флак `RenderNodeNatives`/`Typeface` (memory `robolectric_native_graphics_flake`). По одному классу (`baseline/isolated.txt`) реально красные только два:
  - `ShareReceiverViewModelTest` — «save into existing note appends text and merges tags», порядок текста;
  - `AccessibilityAuditTest.settingsCompactTargetsAreDocumented`.
- **Roborazzi verify по одному классу** (`baseline/isolated-verify.txt`):
  - редактор зелёный: `NoteEditorScreenshotTest` 10/10, `EditorFormatBarNativeScreenshotTest` 3/3, `NoteBody*Screenshot` 14/14;
  - красные golden чужих экранов, вероятно после WIP-коммитов `5a6f40a`…`4f5a0a4` (стеклянный бар, свайп, тени): `NotesListScreenshotTest` 12/18, `TesseraSwipeableNoteCardScreenshotTest` 4/4, `SettingsScreenshotTest` 6/8, `MembersSheetScreenshotTest` 3/5, `TesseraShowcaseScreenshotTest` 1/1.
  - Не перезаписываются: решение пользователя.
- **На устройстве** (Chunk 0b, `baseline/device.md`; AVD `Small_Phone` API 36, настоящая Gboard 15.1.08 при `show_ime_with_hard_keyboard 1` + `stylus_handwriting_enabled 0`): 18 тестов, 2 красных.
  - `GlassBarGuardsTest.textRunsUnderTheNavigationBarWithoutTheKeyboard` — ожидаемо (inset 0 после `9b83ada`), переписывается по D1.
  - `NativeEditorFlingTest.aFastDragKeepsScrollingAfterTheFingerLifts` — долг `63ba51b`: красный с клавиатурой 2/2, зелёный без неё 2/2.
  - `NativeEditorFlingTest.aTouchOnTheMoving…` нестабилен: 1 красный из 4. В Chunk 3 красный этого класса проверяется повтором и сравнивается с базой.

**Открытые вопросы:** по итогам проб (D3: P1 a/b, P4).

**Кандидаты на уборку:**
- общий `TesseraOptionRow` (строки `SearchOptionPopup` и меню «⋯»);
- `GlassPopupShotsTest` без ассертов.

**Не проверяется:** API 26–28 (нет образа).
