# Редактор: касания вне текста, ручка вставки, меню «⋯» — QA на устройстве (Chunk 3)

**Устройство:** `emulator-5556` (AVD `Small_Phone`), API 36, 720×1280, 320 dpi; визуал — в паритетной геометрии 1125×2436, 480 dpi.

**Код.** Ветка `feature/editor-touch-and-format-sheet`: база `6dd1d2b` плюс незакоммиченные Chunk 1 и Chunk 2 (приняты). Прод-код во время прогонов не менялся.

**Клавиатура.** Настоящая экранная, Gboard 15.1.08.
- У AVD `hw.keyboard = yes`, а тачскрин объявлен ещё и как стилус. Без настроек Gboard показывает стилусную мини-панель — см. «Окружение».
- На время прогонов: `show_ime_with_hard_keyboard 1`, `stylus_handwriting_enabled 0`. Оба возвращены.

**Сырьё** — `.claude/local/editor-touch-and-format-sheet-ref/qa/` (далее `qa/`):
- `xml/<прогон>/` — JUnit XML, logcat каждого теста, `gradle.log`;
- `shots-*`, снимки.

База для сравнения — `.claude/local/editor-touch-and-format-sheet-ref/baseline/device.md`.

## Уже принято, не баг

- **Капля в ячейке таблицы.** Тап по ячейке показывает ручку вставки Compose (`PopupLayout` у каретки ячейки). Ячейки `TableOverlayHost` вне рамок, решение пользователя 2026-11-11 «пока оставить» (проба P4, `probe/report.md`).
- **Карточка меню «⋯» шире пилюли бара на 4 dp с каждой стороны.** Поле карточки `TesseraPopup` — 12 dp, поле бара — 16 dp. Видно на всех `qa/shots-parity/qa-*`.

## Итог

- **§6.2-1…6** — `EditorTouchOutsideTest` (новый): всего 9, упало 0, дважды — второй прогон после перевода его `open()` на одиночный тап, как у остальных классов.
- **После финального ревью** (меню «Paste / Select all» уступает экрану): `EditorTouchOutsideTest` дополнен тремя тестами → всего 12, упало 0; `FormatSheetTest` 6/6, `GlassBarGuardsTest` 3/3 на том же коде.
- **§6.2-7, 8** — `FormatSheetTest` (новый): всего 6, упало 0. Ранние красные — обвязка под нагрузкой, см. «Прогоны».
- **§6.2-9:**
  - все 7 классов зелёные, `GlassBarGuardsTest.textRunsUnderTheNavigationBarWithoutTheKeyboard` переписан по D1;
  - `NativeTableGestureTest` — зелёный на повторе: первый прогон упал в обвязке, до таблиц не дошёл.
- **Инвариант пикселей §4.2** держится в обеих геометриях: 28 пар снимков совпадают, кроме фазы мигания каретки.
- **Визуал меню «⋯»** совпадает с эталоном `SearchOptionPopup` в light и dark, во всех 4 палитрах.
- **Нагрузка:**
  - шрифт ×1.3 — карточка над баром, последняя строка достижима;
  - ландшафт с клавиатурой на этом телефоне недостижим: Gboard уходит в полноэкранный ввод, так было и до фичи.
- **Багов прод-кода не найдено.**

## Таблица по §6.2

| § | Тест или шаг | Результат | Доказательство |
| --- | --- | --- | --- |
| 1, контроль детектора | `EditorTouchOutsideTest.controlTheDropIsSeenWhenThePlatformCursorIsOn` — курсор платформы включён обратно, тап по тексту | PASS: окно `Editor$InsertionHandleView` 80×80, 1281 px акцента под кареткой — детектор капли не слепой | `qa/shots-touch/touch-control-drop.png`, logcat `xml/EditorTouchOutsideTest-run1` |
| 1 | `…noInsertionHandleOnOpeningOnATapOnTheTextOrUnderItAndATapUnderTheCaretMovesIt` | PASS: после открытия — 0 окон капли (каретки ещё нет, поэтому без пиксельной проверки); после тапа по тексту, тапа на 20 dp под кареткой и тапа под словом последней строки — 0 окон капли и 0 px акцента. Тап под кареткой переносит её на строку ниже (42 → 70), без меню «Paste/Select all» при непустом буфере. `isCursorVisible` всё время false | `touch-tap-on-text.png`, `touch-tap-under-the-caret.png`, `touch-tap-under-the-text.png` |
| 2 | `…aLongPressUnderTheTextPutsTheCaretAtTheEndWithTheKeyboardAndPasteThenSelectAllGivesHandlesAndCopy` | PASS: выделения нет, каретка в конце (83..83), клавиатура поднята, в меню «Paste» и «Select all», ручек нет; «Paste» вставляет буфер в конец | `touch-long-below-menu.png` |
| 2, вибрация | там же | PASS: новая запись `app.tessera … performHapticFeedback(constant=0)` (= `LONG_PRESS`) в `dumpsys vibrator_manager` | logcat `EditorTouchOutside: long press under the text: new LONG_PRESS haptics` |
| 2b | там же, повтор → «Select all» | PASS: выделено 0..len, 2 окна `SelectionHandleView`, «Copy» | `touch-long-below-select-all.png` |
| 3 | `…aLongPressRightOfAShortLinePutsTheCaretAtItsEndWithTheMenu` | PASS: каретка в конце «Short», выделения нет, меню есть | `touch-long-right-of-short-line.png` |
| 4 | `…aLongPressOnAWordSelectsIt` | PASS: «bravo» (средняя строка) и «delta» (последняя) выделяются, 2 ручки, «Copy» | `touch-long-word-bravo.png`, `touch-long-word-delta.png` |
| 5 | `…aTapUnderTheTextPutsTheCaretAtTheEndOfTheNote` (тап под словом последней строки: на `6dd1d2b` каретка встала бы под палец) | PASS: каретка в конце, фокус | logcat |
| 5, таблица в конце | `…aTapUnderANoteThatEndsWithATablePutsTheCaretNextToTheTable` | PASS: каретка по правилу `caretNextToTable(…, above = false)`, ячейка фокус не взяла | `touch-tap-below-table.png` |
| 6 | `…aViewersLongPressUnderTheTextDoesNothing` (стенд `NoteBody(editable = false)`: на устройстве нет вошедшего зрителя) | PASS: нет выделения, «Paste/Select all/Copy», ручек; долгое нажатие по слову у зрителя по-прежнему выделяет «charlie» с «Copy» | `touch-viewer-long-below.png`, `touch-viewer-long-word.png` |
| холодный сценарий (ревью) | `…coldALongPressUnderTheTextKeepsTheMenuAtTheCaretWhenTheKeyboardScrollsTheNote` | PASS: 14 строк, текст до 0,71 экрана; клавиатура прокрутила заметку на 386 px; пункт меню (374–470) стоит над кареткой (486–522), бар/клавиатура с 554 | `touch-cold-menu-after-keyboard.png` |
| меню уступает экрану (финальное ревью) | `…theMenuGoesWhenTheParagraphStyleSheetOpens` — долгое нажатие под текстом, меню есть, тап по «⋯» | PASS: «Paragraph style» открыта, «Paste»/«Select all» на экране нет, окна плавающей панели ActionMode нет | `qa/shots-c4/touch-menu-under-sheet.png` |
| меню уступает экрану | `…theMenuGoesWhenTheTagPickerOpens` — то же, тап по «+» тега («Add tag») | PASS: попап тегов открыт, «Paste» нет | `qa/shots-c4/touch-menu-under-tag-picker.png` |
| меню уступает экрану | `…theMenuGoesWithAButtonOfTheBar` — то же, «жирный» на баре | PASS: «Paste» нет | `qa/shots-c4/touch-menu-after-bold.png` |
| 7 | `FormatSheetTest.moreOpensTheSheetAndMoreAgainClosesIt` | PASS: «⋯» открывает (карточка «Paragraph style»), повторный «⋯» закрывает, третий снова открывает; клавиатура на месте | `qa/shots-sheet/sheet-open.png` |
| 7 | `…aTapOnTheBackdropClosesTheSheetAndKeepsTheKeyboardAndTheCaret` | PASS: тап по фону закрывает, клавиатура и каретка на месте, фокус у редактора. Фон на 720×1280 — боковые поля 12 dp (см. «Наблюдения», 2) | logcat `backdrop: … tap (12, 289) in its side margin` |
| 7 | `…boldOnTheBarWithTheSheetOpenMakesTheSelectionBoldAndClosesTheSheet` | PASS: «bravo» стало `**bravo**`, меню закрыто | logcat `bold: Alpha **bravo** charlie delta 1` |
| 7 | `…headingFromTheSheetMakesTheCaretParagraphAHeadingAndKeepsTheKeyboardAndTheCaret` | PASS: абзацы `[Body, Heading, Body]`, меню закрыто, клавиатура видна, каретка на месте (43..43) | `sheet-after-heading.png` |
| 8 | `…theCardIsAboveTheBarAndTheBarAboveTheKeyboardAndTheSheetIsNoWindow` | PASS: карточка 48–530 ≤ пилюля 570; низ пилюли 666 ≤ клавиатура 682; у приложения одно окно (`DecorView`), Popup над IME нет | `sheet-geometry.png` |
| 8 | `…backWithTheSheetOpenKeepsTheNoteAndTheNextBackLeavesIt` («назад» — `KEYCODE_BACK` из shell) | PASS: первый «назад» прячет клавиатуру и закрывает меню, заметка открыта; второй выходит из заметки | `sheet-after-back.png` |
| 8, жест | вручную, `installDebug` + `adb shell input swipe` от левого края (навигация жестами) при открытом меню и поднятой клавиатуре | PASS: первый свайп прячет клавиатуру и закрывает меню, заметка на месте; второй — выход в список, заметка сохранена | `qa/gesture-back.png` (до · после первого · после второго) |
| 9 | `GlassBarGuardsTest` (3) | PASS 3/3. `textRunsUnderTheNavigationBarWithoutTheKeyboard` переписан по D1 (2026-11-11, KDoc со ссылкой): `bottomInset == 0` и текст в полосе навбара над нижним паддингом. На базе был красным | `qa/xml/regress-GlassBarGuardsTest` |
| 9 | `GlassBarShotsTest` (3) | PASS 3/3 в 720×1280 и 3/3 в 1125×2436 | `qa/regress-shots/720x1280-320`, `qa/pixels-new` |
| 9 | `GlassPopupShotsTest` (1), `TesseraPopupTest` (3), `TesseraPopupSwipeTest` (1) | PASS | `qa/xml/regress-*` |
| 9 | `NativeEditorFlingTest` (2) | PASS 2/2 и ещё раз 2/2. На базе с клавиатурой `aFastDrag…` был красным 2 из 2, а `aTouchOnTheMoving…` нестабилен: класс в долге плана, зелёный сейчас не означает починку | `qa/xml/regress-NativeEditorFlingTest` |
| 9 | `NativeTableGestureTest` (5) | 1/5, все 4 красных в `AppDriver.open` до таблиц (обвязка под нагрузкой, см. «Прогоны») → повтор PASS 5/5, как на базе | `qa/xml/regress-NativeTableGestureTest{,-2}` |

## Визуал (паритет 1125×2436, 480 dpi)

- **Меню «⋯» против эталона.** Эталон — экран поиска → «Period» → `SearchOptionPopup`.
  - Совпадают:
    - фон;
    - форма, цвет и тень карточки;
    - заголовок `headlineSmall` SemiBold;
    - строки `bodyLarge` с полями 16 dp;
    - активная строка в `secondaryContainer`.
  - Отличаются ожидаемо: иконки строк, заголовок, карточка над баром, бар чёткий поверх фона.
  - Склейки: `qa/sheet-vs-search-light.png`, `qa/sheet-vs-search-dark.png`; эталоны `ref/search-option-{light,dark}.png`.
- **Плавающее меню «Paste / Select all»** под текстом — над кареткой, клавиатура поднята: `qa/paste-menu.png`.
- **Инвариант пикселей §4.2.** Сравнение с `baseline/device-shots/`, без статус-бара, порог 24 на канал. Скрипт `qa/pixdiff.py`, вывод по парам — `qa/pixels-720x1280.txt` и `qa/pixels-1125x2436.txt`, диффы отличий — `qa/pixels-*/`. Снимки: `editor-top` (клавиатура спрятана); `editor-end`, `typed`, `scrolled`, `table-behind`, `image-behind` (поднята); `list` — light и dark.
  - **720×1280:** 13 из 14 пар совпадают до пикселя. В `editor-end-dark` 180 px в боксе 5×36 — каретка в фазе «вкл» против «выкл» на базе.
  - **1125×2436:** 13 из 14 совпадают. В `image-behind-dark` 168 px в боксе 7×24 — тоже каретка.
  - Вынос бара из `Scaffold` колонку не сдвинул.

## Нагрузка

- **Все палитры × light/dark** (WARM, NEUTRAL, SLATE, SAGE): `FormatSheetShotsTest.everyPaletteLightAndDark`, 8 снимков, листом — `qa/palettes.png`.
  - Карточка над чётким баром, активная строка в `secondaryContainer` палитры, «⋯» подсвечена.
  - Тёмные темы читаются.
- **Шрифт ×1.3** (720×1280): `theCardFitsAboveTheBarAndItsLastRowIsReachable` PASS.
  - Карточка 241 dp, 48–530 ≤ пилюля 570.
  - «Quote» достигнут одной прокруткой внутри карточки, меню не закрылось.
  - Снимки `qa/shots-fit/qa-fit-fs1.3-port-{open,scrolled}.png`.
  - Тот же тест при 1.0 — PASS: в 720×1280 карточка 241 dp, «Quote» после одной прокрутки; в 1125×2436 409 dp, «Quote» виден без прокрутки.
- **Ландшафт с клавиатурой — не проверено на этом AVD.**
  - На телефоне в ландшафте Gboard уходит в полноэкранный ввод (extract UI, `inFullscreenMode=true`): экран редактора, бар и «⋯» закрыты, у окна приложения нет инсета IME. Тест падает на «no on-screen keyboard».
  - Редактор не задаёт `imeOptions`/`IME_FLAG_NO_EXTRACT_UI` ни сейчас, ни на `6dd1d2b`, так что это было и до фичи.
  - Минимум карточки 144 dp покрыт unit-тестом `SheetMaxHeightTest`.
  - Снимок полноэкранного ввода: `qa/landscape-extract-ui.png`.

## Наблюдения (не баги, на решение)

1. **Ландшафт на телефоне** — см. выше. Чтобы «ландшафт с клавиатурой» стал сценарием, нужен `IME_FLAG_NO_EXTRACT_UI` у `TesseraEditText`. Вне рамок этой спеки — кандидат.
2. **Маленький телефон с клавиатурой** (720×1280): карточка по формуле §4.3 занимает всё место от статус-бара до бара (241 dp).
   - Верхний бар заметки закрыт целиком, размытого фона над карточкой нет.
   - Закрыть тапом по фону можно только по боковым полям 12 dp или в щели 12 dp над баром; «⋯» и «назад» закрывают как обычно.
   - В 1125×2436 то же: карточка от статус-бара.
   - Это следствие формулы, а не регрессия.

## Прогоны и окружение

- **Плашка буфера обмена Android 13+.** После `setPrimaryClip` система несколько секунд показывает в левом нижнем углу плашку `ClipboardOverlay`, и касание туда достаётся ей. Один прогон `EditorTouchOutsideTest` покраснел из-за этого: долгое нажатие под первым словом последней строки ушло в плашку (`InputDispatcher: Touched window was removed: ClipboardOverlay`). Отдельно тот же тест зелёный 3 из 3. `setClip` теста теперь ждёт, пока плашка уйдёт; после этого — 12/12.
- **Обвязка под нагрузкой хоста** (load average 20–50, чужие сессии):
  - `AppDriver.open` жмёт карточку, ждёт редактор 5 с и жмёт снова. Если редактор открывался дольше, второй тап ждёт уже исчезнувшую карточку → `ComposeTimeoutException` в `awaitCard`.
  - Так упали: первый `FormatSheetTest` (2 из 6), повтор полного `FormatSheetTest` (1 из 6), `NativeTableGestureTest` (4 из 5).
  - В `FormatSheetTest` и `FormatSheetShotsTest` свой `open()`: один тап и ожидание до 60 с.
  - После этого: `FormatSheetTest` 6/6, `NativeTableGestureTest` 5/5 на повторе.
  - Общий `AppDriver` не менял — кандидат на уборку.
- **Эмулятор завис** (`adb shell` не отвечал) и был перезапущен. Quick boot из снапшота вернул Gboard 18.5.6 и ANR чужого `ru.winscore`.
  - ANR закрыт.
  - Gboard снова откатан до заводского 15.1.08 (`pm uninstall-system-updates`, без Play): на 18.5.6 с этими настройками клавиатура не появляется.
  - Следующий перезапуск эмулятора вернёт 18.5.6.
- **Не проверено:**
  - API 26–28 — нет образа;
  - ландшафт с клавиатурой — см. выше;
  - вошедший зритель общей заметки — нет на устройстве, вместо него стенд `NoteBody(editable = false)`.
