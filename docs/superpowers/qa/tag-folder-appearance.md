# Оформление тегов и папок — QA на устройстве (Chunk 5)

**Устройство:** `emulator-5556` (AVD `Small_Phone`), API 36, 720×1280, 320 dpi (360 dp в ширину). D6 — ещё в геометрии Pixel 9 Pro: `wm size 1280x2856`, `wm density 480`.

**Код.** Ветка `feature/tag-folder-appearance`: база `15b3171` плюс незакоммиченные Chunk 1–4, все приняты. Прод-код во время прогонов не менялся.

**Клавиатура.** Настоящая экранная, Gboard 15.1.08.
- На время прогонов: `show_ime_with_hard_keyboard 1`, `stylus_handwriting_enabled 0`.
- Обе настройки возвращены: `0` и `settings delete`.

**Как тесты трогают экран.** Compose-правила в тестах нет — так велит кикофф и артефакт из memory `compose-test-rule-gesture-artifact`. По memory, под правилом долгое нажатие в поле ввода не срабатывало; причина там названа гипотезой. В этой сессии под правилом не проверялось. Без правила приложение живёт на своих часах, и под инструментацией долгое нажатие на чип (`combinedClickable`) срабатывает. Как устроено:
- что на экране — несклеенная семантика всех Compose-корней процесса через `ViewRootForTest`: активити, `DropdownMenu`, диалог, с границами на экране;
- касания — события, инжектированные в окно через `UiAutomation`;
- клавиши и текст — `input keyevent` / `input text`;
- цвета — пиксели скриншота против тонов `AccentColor`.

**Сырьё** — `.claude/local/tag-folder-appearance-ref/qa/`, далее `qa/`:
- `xml/<прогон>/` — JUnit XML, `xml/<прогон>.log` — вывод Gradle, `xml/<прогон>-logcat.txt` — замеры тестов (тег `TagFolderAppearance`);
- `test-shots/` — кадры `TagFolderAppearanceTest` (прогон run7, финальный код);
- корень `qa/` — визуал и D6.

## Итог

- **§6.4 целиком зелёный.** `TagFolderAppearanceTest` (новый): всего 5, упало 0 — run4, run5 и run7; run7 на финальном коде, с проверкой глифа иконки папки. Красные run1–run3 и run6 — обвязка, см. «Прогоны».
- **D6:** заголовок, поле имени и «Сохранить» видны при поднятой клавиатуре во всех трёх случаях: 720×1280, Pixel 9 Pro 1280×2856/480, шрифт ×1.3. Вопроса пользователю нет.
- **D7:** сайдбар не закрывается ни под попапом, ни после «Сохранить». Новые имя, цвет и иконка видны в открытом сайдбаре.
- **Регресс:** `FormatSheetTest` 6/0, `EditorTouchOutsideTest` 12/0, `GlassPopupShotsTest` 1/0, `NativeEditorShotsTest` 1/0.
- **Визуал:** light и dark, 4 палитры (одна склейка), Material You, шрифт ×1.3 — снято.
- **Баги прод-кода:**
  - функциональных нет;
  - одно визуальное — сетка иконок на 360 dp. Это 6 колонок, а добивка последнего ряда считается под 7.

## Таблица по §6.4

| § | Тест или шаг | Результат | Доказательство |
| --- | --- | --- | --- |
| 1 | `TagFolderAppearanceTest.aLongPressOnATagChipOpensItsPopupAndLeavesTheFilterAndATapFilters` — долгое нажатие на «#alpha» в строке фильтров. Нажатие инжектировано, 1 с без движения | PASS: открыт попап «#alpha». Чип не выбран, обе заметки в списке. «Назад» закрывает без записи (`color` в Room = null) | `test-shots/tag-long-press-popup.png` |
| 1 | там же, тап по чипу и повторный тап | PASS: первый тап — чип выбран, в списке только заметка с #alpha, попапа нет. Второй тап снимает фильтр | `test-shots/tag-tap-filters.png` |
| 2 | `…aTagsColourAndIconShowInTheFilterRowOnTheCardInTheEditorAndInSearch` — в попапе: «Red» + Lucide «Star» → «Save». Затем «Blue» + эмодзи 🏠 в поле «Emoji» (буфер + `KEYCODE_PASTE`) → «Save». Поле ниже края экрана — попап прокручен пальцем | PASS: в Room `alpha = red / lucide:star`, `beta = blue / emoji:🏠` | `test-shots/tag-popup-red-star.png`, `tag-popup-blue-emoji.png` |
| 2, строка фильтров | там же. Вместо «#» иконка, лейбл без «#», фон под лейблом — `container` цвета, слева от лейбла — штрихи `content` или эмодзи | PASS: фон `#EECBC9` = red container, `#C9DAEE` = blue container (точно). Штрихи `#772822` (red content) — 192 px. Эмодзи — текстовый узел 🏠, 823 px своих цветов | `test-shots/tags-filter-row-and-card.png`; `xml/run7-logcat.txt` (те же числа в run5) |
| 2, карточка | там же, внутри карточки заметки | PASS: те же цвета; штрихи 113 px, эмодзи 435 px | `tags-filter-row-and-card.png` |
| 2, редактор (`TagChipsExpandable`) | там же, тап по карточке | PASS: те же цвета; штрихи 113 px, эмодзи 436 px | `test-shots/tags-editor.png` |
| 2, поиск (популярные теги) | там же, «Search notes» без запроса | PASS: те же цвета; штрихи 199 px, эмодзи 823 px | `test-shots/tags-search-popular.png` |
| 3, D7 | `…editFromTheFolderMenuShowsTheNewNameColourAndIconInTheDrawerThatStaysOpen` — «⋮» у «Errands» → «Edit»: имя «Projects», «Green», «Work» (портфель) → «Save» | PASS: сайдбар открыт под попапом и после «Save». В строке «Projects», «Errands» нет. Иконка:<br>• цвет — 312 px green content `#1E6730`;<br>• глиф — середина иконки (квадрат 3 dp) до правки закрашена на 1.0 (залитая папка по умолчанию), после пуста, 0.0 (контурный портфель Lucide).<br>Room: `Projects / green / lucide:briefcase` | `test-shots/folder-edit-popup.png`, `folder-edit-drawer-after.png` |
| 3, имя занято | `…aLiveFoldersNameIsTakenAndAJustDeletedFoldersNameIsFree` — «Birch» → «alpine» (живая «Alpine» в другом регистре) → «Save» | PASS: под полем «A folder with this name already exists», попап открыт, приложение живо, в Room «Birch». Правка имени убирает ошибку, «Cancel» ничего не пишет | `test-shots/folder-name-taken.png` |
| 3, имя удалённой | там же: «⋮» у «Alpine» → «Delete» → подтверждение, затем «Birch» → «Alpine» → «Save» | PASS: «Alpine» — tombstone (`deletedAt` ≠ null). Переименование сохранилось сразу, попап закрыт, ошибки нет, в сайдбаре «Alpine» | `test-shots/folder-deleted-name-reused.png` |
| 4, «Все» | `…cardsOfAColouredFolderAreTintedInAllNotesAndInSearchFromTheFolderButNotInTheFolder` — красная «Garden», её подпапка «Seeds» без цвета, «Plain» без цвета, заметка без папки | PASS: карточки «Garden» и «Seeds» — red cardTint `#F7EAE9` (±6). «Plain» и заметка без папки — обычный фон `#F6F4ED` | `test-shots/tint-all-notes.png` |
| 4, в папке | там же, сайдбар → «Garden» | PASS: карточка «Garden» без подкраски (`#F6F4ED` ±3) | `test-shots/tint-in-folder.png` |
| 4, поиск из папки | там же, поиск «tintword» из открытой «Garden» | PASS: найдены заметки всех папок. «Garden» и «Seeds» снова красные, заметка без папки — нет | `test-shots/tint-search-from-folder.png` |

## D6 — попап папки с поднятой клавиатурой

Фокус в поле имени, клавиатура поднята и стоит. Замеры — `TagFolderAppearanceShotsTest`: `xml/shots2-logcat.txt`, повтор на финальном коде `xml/shots5-logcat.txt` — числа те же.

| Геометрия | Попап | Статус-бар, низ | Карточка | Заголовок | Поле имени | «Save» | Клавиатура, верх | Снимок |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 720×1280, 320 dpi | «Folder» (правка) | 48 | 48–658 | 112–161 | 240–288 | 552–588 | 682 | `qa/d6-device-folder-keyboard.png` |
| 720×1280, 320 dpi | «New folder» | 48 | 48–658 | 88–137 | 193–241 | 552–588 | 682 | `qa/d6-device-new-folder-keyboard.png` |
| 1280×2856, 480 dpi | «Folder» | 72 | 72–1716 | 168–240 | 360–432 | 1557–1611 | 1751 | `qa/d6-pixel9pro-folder-keyboard.png` |
| 1280×2856, 480 dpi | «New folder» | 72 | 72–1716 | 132–204 | 288–360 | 1557–1611 | 1751 | `qa/d6-pixel9pro-new-folder-keyboard.png` |
| 720×1280, шрифт ×1.3 | «Folder» | 48 | 48–658 | 108–165 | 240–288 | 546–594 | 682 | `qa/font130-popup-folder-keyboard.png` |

Во всех строках заголовок и поле ниже статус-бара и выше клавиатуры, «Save» над клавиатурой. Карточка встаёт ровно под статус-бар, цвет и иконки уходят в прокрутку.

## Визуал для пользователя (макета нет)

| Что | Light | Dark |
| --- | --- | --- |
| «Все» с подкраской и строкой фильтров. Теги: 🌱 garden, 💡 ideas (Lucide), #todo (по умолчанию), ✈ travel (Lucide) | `qa/all-notes-tinted-tag-row-light.png` | `…-dark.png` |
| Попап тега (верх / низ с эмодзи и предпросмотром) | `qa/popup-tag-light.png`, `popup-tag-scrolled-light.png` | `…-dark.png` |
| Попап папки (верх / с клавиатурой / низ с предпросмотром) | `qa/popup-folder-light.png`, `popup-folder-keyboard-light.png`, `popup-folder-scrolled-light.png` | `…-dark.png` |
| Сайдбар: зелёная «Garden» с листом, подпапка «Seeds» цветом родителя, «Trips» с ✈️ | `qa/drawer-light.png` | `drawer-dark.png` |
| Сайдбар с открытым «⋮» (Edit / Create subfolder / Delete) | `qa/drawer-menu-light.png` | `drawer-menu-dark.png` |
| 4 палитры × light/dark — склейка | `qa/palettes-collage.png` (кадры — `qa/palettes/`) | |
| Material You (динамический цвет, глазами — спека §3.1) | `qa/dynamic-list-light.png`, `dynamic-drawer-light.png` | `…-dark.png` |
| Шрифт ×1.3: список, попап тега, сайдбар, меню «⋮», попап папки | `qa/font130-*.png` | |

## Регресс (по одному классу)

| Класс | Итог | Доказательство |
| --- | --- | --- |
| `FormatSheetTest` | 6/0 (прогнан первым — проверка устройства и клавиатуры) | `xml/regress-FormatSheetTest` |
| `EditorTouchOutsideTest` | 12/0 | `xml/regress-EditorTouchOutsideTest` |
| `GlassPopupShotsTest` (его вызов `createFolder` правился в Chunk 1) | 1/0. «New folder» — уже новый попап, в сайдбаре «⋮» | `xml/regress-GlassPopupShotsTest`, `qa/regress-shots/glass-popup-*` |
| `NativeEditorShotsTest` (хелпер `note()` правился в Chunk 1) | 1/0 | `xml/regress-NativeEditorShotsTest`, `qa/regress-shots/6b-*` |

## Баги прод-кода

**Визуальное: сетка иконок на 360 dp — 6 колонок, последний ряд со сдвигом.**
- **Шаги:** телефон 720×1280 при 320 dpi. Долгое нажатие на тег или «⋮» → «Edit» у папки, затем прокрутить сетку иконок до конца.
- **Ожидание** (отчёт feature-2: «сетка 7 в ряд ровно по ширине»): ровные колонки до последнего ряда.
- **Факт:**
  - Ширина содержимого — 360 − 2×12 − 2×20 = 296 dp, а 7 ячеек по 44 dp — 308 dp, поэтому `FlowRow(maxItemsInEachRow = 7)` кладёт по 6.
  - Добивка `repeat((7 − 31 % 7) % 7)` считается под 7: последний ряд — «Подарок» и 4 пустые ячейки с `weight(1f)`, то есть 5 долей.
  - Центр «Подарка» сдвинут вправо от первой колонки на ~5 dp.
  - На Pixel 9 Pro (427 dp) — ровно 7 колонок.
- **Доказательство:** `qa/probe-scroll/probe-3-drag-grid.png`, `qa/popup-tag-scrolled-light.png` (без сдвига на `qa/d6-pixel9pro-new-folder-keyboard.png`), `AppearancePopup.kt` — `IconColumns = 7`, `FlowRow`, `repeat`.

Функциональных багов не найдено.

## Наблюдения — не баги

- **На 720×1280 без клавиатуры** поле «Emoji» и предпросмотр лежат ниже видимой части попапа (карточка — 60 % экрана), до них нужно прокрутить. Так задумано вариантом (а) из отчёта feature-2. Пальцем прокручивается: проба `qa/probe-scroll`, `input swipe` из shell.
- **Подкраска в light мягкая.** Red cardTint `#F7EAE9` против обычной карточки `#F6F4ED` — разница в основном по зелёному каналу, 10 из 255. На снимках розоватость видна (`test-shots/tint-all-notes.png`), это решение палитры D5. Оценка — глазами пользователя.
- **Выбранный тег-чип** выглядит так же, как невыбранный: о фильтре говорит только сам список. На базе так же — у `FilterChip` для тега `selectedContainerColor = container`, рамки нет. Это не регрессия.

## Прогоны

| Прогон | Что | Итог | Причина красного |
| --- | --- | --- | --- |
| run1 | `TagFolderAppearanceTest` | 4/1 | обвязка: тап по полю «Emoji» ниже края экрана (y 1422 при высоте 1280) — нужно прокрутить попап |
| run2 | то же | 4/1 | обвязка: перетаскивание начиналось в 2 px над подвалом, прокрутки не было. Проба `probe-scroll`: из середины карточки скроллят и `input swipe`, и инжект → старт перенесён в середину |
| run3 | то же | 4/1 | обвязка: заголовок «Popular tags» рисуется капсом («POPULAR TAGS») — сравнение без регистра |
| run4, run5 | то же | 5/0, 5/0 | — |
| run6 | то же, добавлена проверка глифа иконки папки | 4/1 | обвязка: признак «общее число чернил в зоне» не разделяет фигуры (папка 668 px, портфель 588 px) → заменён на «закрашена ли середина иконки» (1.0 против 0.0 на готовых кадрах) |
| run7 | то же, финальный код | 5/0 | — |
| shots1 | `TagFolderAppearanceShotsTest` | 2/3 | обвязка: вложенный `runOnMainSync` в замере статус-бара; при ×1.3 чип «travel» за краем строки |
| shots2–4 | шрифт, D6, light/dark (пересъёмка), Material You | 3/0, 1/0, 1/0 | — |
| shots5 | `TagFolderAppearanceShotsTest` целиком, финальный код | 6/0 | — (кадры в корне `qa/` — из этого прогона) |

## Не проверено

- **Хаптика** долгого нажатия (`LongPress`) — не замерялась.
- **Эмодзи с клавиатуры Gboard** не набирались: эмодзи вставлен из буфера через `KEYCODE_PASTE`. Путь в `onValueChange` поля тот же, что у ввода с клавиатуры.
- **Пиксельные проверки** — только в light. Dark, палитры и Material You — снимки, глазами.
- **Русская локаль** на устройстве; синк через Firestore — вне Chunk 5.
- **Ландшафт** не снимался.

## Окружение после работы

- Клавиатура: `show_ime_with_hard_keyboard 0`, `stylus_handwriting_enabled` удалён (было null).
- Экран: `wm size/density reset` (720×1280, 320), `font_scale 1.0`.
- Ночной режим системы не трогался (`Night mode: no`).
- Приложение и тест-APK удалены — так делает `connectedDebugAndroidTest`, проверено `pm list packages`.
- `/data/local/tmp/tag-folder-appearance-qa` удалена.
- Регресс-классы пишут кадры в `Download/native-editor-qa` устройства: там добавились файлы с суффиксом « (1)», старые не трогал.
