# Нативный редактор — этап 1 «Ядро»: план

> **Для исполнителей:** план ведёт оркестратор по скиллу `feature-team`. Код пишут ролевые сессии
> (`data`, `feature`, `qa`), каждая — только свой Chunk и только в своей зоне. Чекбоксы `- [ ]`
> закрывает оркестратор при приёмке, дописывая итог с числами.

**Цель:** собственный редактор на `AppCompatEditText` (без Aztec) с моделью `NoteDoc` и Markdown-диалектом
спеки, включаемый debug-флагом; по умолчанию приложение работает как раньше.

**Архитектура:** `core/text/notedoc` (чистый Kotlin: модель, парсер, сериализатор) →
`ui/editor/engine` (View: спаны, биндер, нормализатор, правила, история) → `ui/editor/components/NoteBody`
(Compose-обёртка с контрактом `RichNoteBody`). ViewModel и данные — без изменений, кроме флага.

**Стек:** Kotlin, Android View (`AppCompatEditText`, `Spannable`), Compose M3 (`AndroidView`), DataStore,
JUnit5 + Robolectric, Roborazzi, instrumented (`HiltTestRunner`).

**Спека:** `docs/superpowers/specs/2026-11-06-native-editor-design.md` (далее — «спека», ссылки `§N`).

---

## 1. Источники

- Спека §1–§10 (этап 1). §11 — только ориентир, в этом плане не реализуется.
- Кадры макета (рендер `mockup-shot.sh`, 1125×2436 = 375×812 @3x), `.claude/local/native-editor-ref/mock/`:

| NN | Файл кадра | Экран | Состояние | Тема |
| --- | --- | --- | --- | --- |
| 01 | `editor-content-light.html` | редактор | заметка с Названием, текстом, Заголовками, чек-листом, цитатой, кодом | light |
| 02 | `editor-content-dark.html` | редактор | то же | dark |
| 03 | `editor-format-bar.html` | редактор | ввод, клавиатура, панель форматирования | light |
| 04 | `editor-empty.html` | редактор | пустая заметка | light |

- Видео/скрины пользователя — нет.
- Базовая линия тестов (worktree, до правок, 2026-11-06): `core.text.*` + `ui.editor.*` — 240 тестов,
  25 классов; общий прогон дал 52 падения `UnsatisfiedLinkError RenderNodeNatives` (флак Robolectric).
  По отдельности зелёные все 10 «упавших» классов: `AztecBlankLineRoundTripTest` 20/20,
  `HeadingToggleArtifactsTest` 12/12, `NoteEditorScreenshotTest` 10/10, `AztecImageParamSpikeTest` 3/3,
  `BlankLineAfterBlockTypingTest` 2/2, `CheckboxRenderColorTest` 1/1, `ImageSpanRectTest` 1/1,
  `MarkdownFormatMenuTest` 1/1, `RichNoteBodyFormatFlushTest` 1/1, `TagChipsExpandableTest` 1/1.
  Классы `core.text` в том же прогоне (все зелёные): `AutoTitleTest` 9, `MarkdownHtmlConverterTest` 71,
  `MarkdownPreviewTest` 25, `GfmTableParserTest` 6, `MarkdownBlocksTest` 5, `SerifH1SpannerTest` 3,
  `SmartSearchTest` 12. Прочие `ui.editor`: `EditorFormatBarRenderTest` 3, `EditorMetaRenderTest` 2,
  `ImageRefTest` 13, `ImageSelectionGeometryTest` 9, `NoteEditorImageInsertTest` 2,
  `NoteEditorViewModelTest` 23, `TableBlockEditorTest` 1, `TesseraImageLoaderTest` 4.
- Кадры в worktree: `.claude/local/native-editor-ref/mock/editor-*.png` (абсолютный путь —
  `/Users/vorislove/.config/superpowers/worktrees/notes/native-editor/.claude/local/native-editor-ref/mock/`).

## 2. Спека против макета — как делаем

| # | Что | Макет | Код сейчас | Спека | Делаем | Основание |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Заголовок (H2) | Inter 600, 18px | PlayfairDisplay ≈ 20sp (`SerifH1Spanner` красит H1 и H2) | §6.11: как код | как код | спека решила; код (2026-08-25) новее макета (2026-06-22) |
| 2 | Цитата | serif italic + терракотовая полоса | основной текст, полоса/текст/заливка из палитры (`replaceQuoteSpans`) | §6.11: как код | как код | спека решила |
| 3 | Отмеченный пункт чек-листа | текст зачёркнут и приглушён | Aztec не зачёркивает | молчит | **как макет**: зачёркивание + `onSurfaceVariant`, только отрисовка (в Markdown не попадает) | спека молчит → макет |
| 4 | Панель форматирования | B I U S \| H1 H2 H3 \| список | текущий `EditorFormatBar` (undo/redo, группы, ⋯) | §7: вёрстка как сейчас + новые пункты | как спека | спека решила |
| 5 | Подзаголовок, тире, отступы | нет в макете | нет | §5, §6, §7 | по спеке, скрин пользователю | без макета |
| 6 | Заглушки картинки/таблицы | нет в макете | — | §6.1 | по спеке (см. D9) | без макета |
| 7 | Вертикальные отступы | `.editor-p` margin-bottom 10 после каждого абзаца; заголовки, списки, цитата, код — свои margin | Aztec: без отступов между строками | молчала | отступы на краях блоков и серий по CSS; Body ↔ Body = 0 (спека §6.11 дополнена 2026-11-07) | в нашей модели абзац = строка; отступ после каждой строки разредил бы текст относительно Aztec и Apple Notes |

Строки 1–4 — в открытые вопросы финального отчёта (пользователь просил не останавливаться; этап 1 за флагом).

## 3. Решения

- **D1. Флаг.** В интерфейс `core/preferences/UserPreferences` — члены с реализацией по умолчанию:
  `val useNativeEditor: Flow<Boolean> get() = flowOf(false)` и
  `suspend fun setUseNativeEditor(enabled: Boolean) {}`. Так существующие приватные `FakeUserPreferences`
  в тестах (`SettingsViewModelTest`, `NotesListViewModelTest`, …) не трогаются. `UserPreferencesRepository`
  переопределяет оба через ключ DataStore `native_editor` (по умолчанию `false`). Для тестов Chunk 5
  `data` добавляет общий фейк `app/src/test/java/app/tessera/test/fakes/FakeUserPreferences.kt` (Chunk 1b):
  все члены интерфейса на `MutableStateFlow`, флаг управляемый, сеттер его меняет. Приватные фейки в
  существующих тестах не трогаются.
- **D2. ViewModel.** `NoteEditorViewModel` получает `UserPreferences` в конструктор и кладёт флаг в
  `NoteEditorState.useNativeEditor: Boolean = false` (сбор в `viewModelScope`). Конструктор в
  `NoteEditorViewModelTest` дополняется фейком.
- **D3. Settings.** `SettingsScreen` получает `showDebugOptions: Boolean = false`,
  `onSetUseNativeEditor: (Boolean) -> Unit = {}`; `SettingsState.useNativeEditor`; строка — существующий
  `SettingsRow` + `Switch`, как у соседних переключателей. В `NotesNavGraph` на месте вызова:
  `showDebugOptions = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0`.
  `SettingsScreenshotTest` параметр не передаёт → golden не меняются.
- **D4. Aztec-путь не трогаем.** Этап 1 **не меняет `MarkdownHtmlConverter`** и его тесты. Правило
  `tessera.md` «каждый новый тег — round-trip в `MarkdownHtmlConverterTest`» к этому этапу **не
  применяется**: тесты диалекта — в `NoteMarkdownTest` и соседях. Общие правки текстового слоя —
  только две:
  1. снятие `\` в `MarkdownPreview` + кейс в `MarkdownPreviewTest`;
  2. в `core/text/markdownblocks/MarkdownBlocks.kt` хелперы `applyMeta`, `metaComment`,
     `TESSERA_META`, `SEPARATOR` из `private` становятся `internal` — без изменения поведения
     (`MarkdownBlocksTest` с прежними числами).

  `splitMarkdownIntoBlocks` для `NoteDoc` **не используется**: он режет `\n` на краях блоков, а это
  ломает §5.1. `NoteMarkdown` сам идёт по строкам и для таблиц вызывает только парсер и сериализатор
  `GfmTableParser`/`TableGrid` и meta-хелперы. Регэксп картинки копируется из `IMAGE_LINE`
  `MarkdownHtmlConverter` в `notedoc`; сам конвертер не трогаем.
- **D5. Memory про Aztec** (`aztec_*`, `markup-goes-crazy-*`) — фон: какие баги бывали и почему. Их
  «правила» (NBSP-строки, удвоенные `\n`, end-of-buffer marker Aztec) к новому движку не относятся.
- **D6. Файлы.**

  `core/text/notedoc/` (feature):
  - `NoteDoc.kt` — модель §4 (`NoteDoc`, `NoteBlock`, `ParagraphKind`, `ListType`, `Inline`, `MarkRange`,
    `Mark`) + `normalize()` (правила 1–10 §4).
  - `InlineMarkdown.kt` — разбор инлайна (delimiter-run CommonMark для `*`/`**`/`~~`, code spans,
    теги `<u>/<b>/<i>/<s>`, `\`-экранирование) и запись с проверкой + запасная форма (§5.3).
  - `NoteMarkdown.kt` — `parse(md): NoteDoc`, `serialize(doc): String`: блоки §5.1–5.2, блочное
    экранирование, fence, нумерация, таблицы через `markdownblocks`, картинки через `IMAGE_LINE`-правило.

  `ui/editor/engine/` (feature):
  - `spans/BlockSpans.kt` — `BlockSpan` (sealed) + `HeadingSpan`, `ListItemSpan`, `QuoteSpan`, `MonoSpan`.
  - `spans/MarkSpans.kt` — наши `Bold/Italic/Underline/Strike/CodeMarkSpan`.
  - `spans/ObjectSpan.kt`, `spans/SentinelSpan.kt`.
  - `EditorStyle.kt` — ARGB-цвета и размеры (px) для спанов, собирается из `MaterialTheme` в `NoteBody`.
  - `DocBinder.kt` — `bind(doc, editable, style)`, `read(editable): NoteDoc`, `reapplyTheme`.
  - `Normalizer.kt` — инварианты §6.2 и вставка; вызывает `TypingAttributes` и `AutoReplace`.
  - `TypingAttributes.kt` — §6.6. `AutoReplace.kt` — §6.5.
  - `EditRules.kt` — Enter / Backspace / отступы / переключение вида и меток / вставка разделителя
    (§6.4–6.6), чистые функции над `Editable`.
  - `EditHistory.kt` — §6.7. `ClipboardBridge.kt` — §6.8. `FormatState.kt` — §7.
  - `TesseraEditText.kt` — хост: `onKeyDown`, `TextWatcher`, тап по чекбоксу, paste как plain, выдача и
    эхо (§6.9, D13). `TesseraInputConnection.kt` — обёртка `InputConnection`.
  - `CheckboxAccessibilityHelper.kt` — `ExploreByTouchHelper` (§6.10).

  `ui/editor/components/` (feature): `NoteBody.kt` (новый); `EditorFormatBar.kt`, `MarkdownFormatMenu.kt`,
  `RichNoteBody.kt` (только интерфейс `RichBodyActions`), `EditorCheckboxTheming.kt` (только открыть
  фабрику отрисовки для `ListItemSpan`, без смены поведения).
  `ui/editor/`: `NoteEditorScreen.kt`, `NoteEditorState.kt`, `NoteEditorViewModel.kt`.
  `ui/settings/`: `SettingsScreen.kt`, `SettingsState.kt`, `SettingsViewModel.kt`. `ui/navigation/NotesNavGraph.kt`.
  `res/values/strings.xml`, `res/values-ru/strings.xml`. `core/text/MarkdownPreview.kt`.
- **D7. Тесты по ролям.** `feature` — JVM и Robolectric (`app/src/test`), включая оракул и Roborazzi-тесты
  без golden. `qa` — всё instrumented (`app/src/androidTest`) из §8 и §9 спеки, сверка с макетом,
  замер производительности. Старые `RichNoteBody*` androidTest остаются: Aztec-путь жив.
- **D8. Коммиты.** Пользователь сказал «без подтверждения делай всё сразу». Поэтому принятый срез
  оркестратор коммитит **локально** в `feature/native-editor`, явными путями, Conventional Commits.
  Без push и merge. Роли не коммитят.
- **D9. Заглушки картинки и таблицы.** Подпись («Изображение» / «Таблица») в скруглённой рамке
  `outlineVariant`, без иконки: заглушка живёт только до этапа 2, новые drawable ради неё не нужны.
  Это сужение §6.1 («иконка + подпись»).
- **D10. Чекбокс.** `ListItemSpan` рисует чекбокс той же отрисовкой и `CheckboxPalette`, что
  `EditorCheckboxTheming`; отмеченный пункт — зачёркнут + `onSurfaceVariant` (строка 3 таблицы §2).
- **D11. Room / Firestore** — без изменений (формат `content` прежний: Markdown).
- **D12. `RichBodyActions`.** Новые методы с реализацией по умолчанию (§7 спеки);
  `formatState(): FormatState = FormatState.Empty`. `FormatState` живёт в `ui/editor/engine`.
  Адаптер `Set<AztecTextFormat> → FormatState` не нужен: при `nativeEngine = false` панель, как и
  раньше, читает `activeFormats`. `NoteBody` реализует абстрактный `activeFormats()` как `emptySet()`.
- **D13. Выдача и эхо (§6.9 спеки) живут в `TesseraEditText`**, а не в `NoteBody`: так их можно
  тестировать Robolectric-ом на уровне View. `NoteBody` только передаёт `markdown` и колбэк и логику
  не дублирует.
- **D14. Подписи заглушек** («Изображение» / «Таблица») передаются в движок через `EditorStyle`.
  `NoteBody` берёт их из `stringResource`, тесты движка — литералами. Строки появляются в Chunk 5.
- **D15. Куда новые пункты.**
  - Подзаголовок — в меню ⋯ вместе с Названием, Заголовком, Основным, Моно и Цитатой.
  - Тире, Отступ вправо и Отступ влево — на панель, в группу списков рядом с точкой, номером и
    чек-листом.
  - При `nativeEngine = true` кнопки undo/redo активны по `canUndo`/`canRedo`.
- **D16. Сессии `feature` — новая на каждый Chunk** (2, 3, 4a, 4b, 5), без `--resume`: контекст
  предыдущего среза новой сессии не нужен, решения лежат в плане.
- **D17. Строка Settings.** Строка ставится в группу с версией приложения; иконка —
  `Icons.Rounded.EditNote` (material-icons-extended уже используется на экране). В `NotesNavGraph`
  `context` берётся через `LocalContext.current`.

## 4. Реестр компонентов

| Элемент | Поиск (codebase-memory) и находка | Решение | Роль |
| --- | --- | --- | --- |
| Панель форматирования | `EditorFormatBar` (`ui/editor/components`), получает `Set<AztecTextFormat>` | расширить необязательными `nativeEngine = false`, `formatState: FormatState? = null` | feature |
| Меню стилей абзаца (⋯) | `MarkdownFormatMenu`, `formatMenuEntries()` | расширить тем же `nativeEngine = false`; при `true` — список §7 спеки | feature |
| Строка настройки с переключателем | `SettingsRow` (`ui/settings/components`) + `Switch`, как у строк зерна/AMOLED | берём как есть | feature |
| Отрисовка чекбокса | `EditorCheckboxTheming` (`CheckboxPalette`, приватный `TesseraCheckboxDrawable`) | открыть фабрику (`internal`), поведение прежнее | feature |
| Serif для Названия/Заголовка | `SerifHeadingTypefaceSpan` (`core/text/SerifH1Spanner.kt`) | использовать класс внутри `HeadingSpan` | feature |
| Фон моно, полоса цитаты, маркеры | `TesseraPreformatSpan`, `replaceQuoteSpans`, `TesseraBulletListSpan`, `TesseraOrderedListSpan` — наследники Aztec-спанов | не наследовать (Aztec уйдёт на этапе 2): `MonoSpan`/`QuoteSpan`/`ListItemSpan` повторяют геометрию и цвета | feature |
| Таблица в модели | `TableGrid`, `GfmTableParser`, `serializeGfmTable`, `splitMarkdownIntoBlocks` (`core/text/markdownblocks`) | переиспользовать | feature |
| Компоненты `core/ui/tessera`, `core/ui/components` | не требуются | — | — |

Новых `Tessera*` компонентов нет; роль `ui` не запускается.

## 5. Срезы

Порядок:
- Chunk 1 (`data`) → Chunk 2 → 3 → 4a → 4b → 5 (`feature`, последовательно, новая сессия на срез, D16).
- Chunk 1b (`data`, общий фейк) — параллельно с Chunk 2: это новый тестовый файл, с `core/text` не
  пересекается.
- Chunk 6 (`qa`): instrumented-тесты движка стартуют после приёмки Chunk 4b, сверка экрана — после
  Chunk 5.
- `review` — после каждого среза. Каждый срез сдаётся с компилирующимся модулем.

### Chunk 1b: общий фейк предпочтений (`data`)

- [x] `app/src/test/java/app/tessera/test/fakes/FakeUserPreferences.kt` — принято, компиляция тестов зелёная, коммит `dfeaa94`: реализует весь `UserPreferences`
  на `MutableStateFlow` (по образцу приватного фейка в `SettingsViewModelTest`); `useNativeEditor`
  управляемый, `setUseNativeEditor` меняет его. Существующие приватные фейки не трогать.

**Готово, когда:** `gw.sh :app:compileDebugUnitTestKotlin` зелёный.

### Chunk 1: флаг (`data`)

**Файлы:** `core/preferences/UserPreferences.kt`, `core/preferences/UserPreferencesRepository.kt`,
`app/src/test/.../core/preferences/UserPreferencesRepositoryTest.kt`.

- [x] Тест: по умолчанию `useNativeEditor` = `false`; после `setUseNativeEditor(true)` → `true`; после
  `false` → `false` (стиль существующего `UserPreferencesRepositoryTest`). — `UserPreferencesRepositoryTest` 5/5 (`--rerun`).
- [x] D1: члены интерфейса с реализацией по умолчанию, переопределение в репозитории, ключ `native_editor`. — коммит `0a50d77`.
- [x] Ни один существующий `FakeUserPreferences` не изменён. — `compileDebugUnitTestKotlin` зелёный.

**Готово, когда:** `gw.sh :app:testDebugUnitTest --tests 'app.tessera.core.preferences.*'` — зелено, числа
в отчёте; `compileDebugUnitTestKotlin` зелёный (фейки компилируются без правок).

### Chunk 2: модель и диалект (`feature`, `core/text/notedoc`)

**Файлы:** `NoteDoc.kt`, `InlineMarkdown.kt`, `NoteMarkdown.kt`, `core/text/MarkdownPreview.kt`,
`core/text/markdownblocks/MarkdownBlocks.kt` (только `private` → `internal`, D4);
тесты `app/src/test/.../core/text/notedoc/`: `NoteMarkdownTest.kt`, `InlineMarkdownTest.kt`,
`NoteMarkdownPropertyTest.kt`, `NoteMarkdownCorpusTest.kt`, **`NoteDocFixtures.kt`** (общий для Chunk 3:
`legacyCorpus: List<String>` — хранимые строки из `MarkdownHtmlConverterTest`; `sampleDocs: List<NoteDoc>` —
все виды, уровни 0–4, пустые абзацы в начале/середине/конце, пустой последний пункт, объекты, таблица
сразу после таблицы; генератор случайных `NoteDoc` для property-теста); `MarkdownPreviewTest.kt` (+1 кейс).

TDD: сначала падающие кейсы из таблицы §8 спеки (строка «JVM»), потом код.

- [x] Модель §4 + `normalize` (правила 1–10).
- [x] Блоки §5.1–5.2 этапа 1: заголовки (H4–H6 → Subheading), fence (длина, незакрытый, язык
  отбрасывается, `~~~` — литерал), списки и чек-листы (чтение `* `, `N)`, `* [ ]`, `[X]`; запись `- `,
  `+ `, `N. `, `- [x] `, `- [x] `), вложенность (4 пробела, таб, `ceil`), цитата `> `, разделитель,
  картинка (`IMAGE_LINE`), таблица (+ meta-комментарий), правило пробелов §5.2.
- [x] Инлайн §5.3: delimiter-run, code spans (чтение и запись), теги `<u>/<b>/<i>/<s>`
  (`<strong>`/`<em>`/`<del>`/`<strike>` — литерал), `\`-экранирование (не в коде и не в fence),
  запись с проверкой и запасной формой.
- [x] Блочное экранирование — таблица §5.3 (в т.ч. `2024\. год`, `\|` всегда, `\[ ]` в пункте).
- [x] Нумерация §5.3.
- [x] `NoteMarkdownPropertyTest`: ≥ 2000 случайных документов, фиксированный seed, генератор §5.3;
  проверяет `parse(serialize(doc)) == normalize(doc)` и `serialize(parse(serialize(doc))) == serialize(doc)`.
- [x] `NoteMarkdownCorpusTest`: хранимые Markdown-строки из `MarkdownHtmlConverterTest` (аргументы
  `roundTrip(...)`, входы `markdownToHtml`-тестов, ожидаемые значения `htmlToMarkdown`-тестов) —
  выписать в тест списком; каждая парсится, повторная запись не меняет видимый текст строк.
- [x] `MarkdownPreview`: снятие `\` перед ASCII-пунктуацией + кейс в `MarkdownPreviewTest`.

**Итог:** принято, коммит `dfeaa94`; `core.text.*` 253/253 (`--rerun`, оркестратор): InlineMarkdownTest 31, NoteMarkdownTest 63, NoteDocNormalizeTest 17, NoteMarkdownCorpusTest 6, NoteMarkdownPropertyTest 3 (6000 док.), MarkdownPreviewTest 27, базовые классы с прежними числами. Ревью: 0 блокеров; 2 «заметно» (legacy-эмфаза, `\` в коде превью) и 3 мелочи исправлены (спека §4 п.11–12, §5.3, §8 дополнены).

**Готово, когда:** `gw.sh :app:testDebugUnitTest --tests 'app.tessera.core.text.*'` — все новые зелёные,
старые классы `core.text` с числами базовой линии раздела 1 (`MarkdownBlocksTest` 5, `GfmTableParserTest` 6,
`MarkdownPreviewTest` 25 + новый кейс, `MarkdownHtmlConverterTest` 71, …); property-тест — число
документов в отчёте.

### Chunk 3: спаны, биндер, нормализатор, оракул (`feature`, `ui/editor/engine`)

**Файлы:** `spans/*`, `EditorStyle.kt`, `DocBinder.kt`, `Normalizer.kt` (инварианты §6.2, без typing
attributes и автозамен — они в Chunk 4); `EditorCheckboxTheming.kt` (открыть фабрику).
Тесты (Robolectric): `DocBinderTest`, `NormalizerTest`, `AztecOracleTest`.

- [x] Спаны §6.1: составной `BlockSpan` на абзац (`SPAN_PARAGRAPH`), абзац без спана = `Body`;
  `MarkSpan` (`EXCLUSIVE_EXCLUSIVE`); `ObjectSpan` (`U+FFFC`, заглушки D9 с подписями из `EditorStyle` —
  D14, разделитель-линия);
  `SentinelSpan` для `U+200B` пустого последнего не-Body абзаца; Mono/Quote учитывают соседей;
  чекбокс D10; типографика §6.11; цвета только из `EditorStyle`.
- [x] `DocBinder`: `bind` / `read` / `reapplyTheme` (пересоздаёт спаны, текст не трогает).
- [x] `Normalizer` — инварианты 1–5 §6.2 (чужие оформляющие спаны, исключения `SuggestionSpan`,
  `SuggestionRangeSpan` по имени класса, `SPAN_COMPOSING`; служебные спаны фреймворка не трогать;
  `U+FFFC` без спана; `U+200B` только с `SentinelSpan`); новые абзацы от вставки берут вид абзаца
  начала вставки.
- [x] `DocBinderTest`: `read(bind(doc)) == normalize(doc)` на `NoteDocFixtures.sampleDocs` и на ≥ 300
  документах из генератора `NoteDocFixtures` (фиксированный seed).
- [x] `NormalizerTest`: вставка `Spanned` с чужими `StyleSpan`/`ForegroundColorSpan` → удалены;
  `SuggestionSpan` и composing — живы; `U+FFFC` без спана удалён; правка внутри абзаца не меняет его
  `BlockSpan`.
- [x] `AztecOracleTest` — по §8 спеки: вход (`NoteDocFixtures.legacyCorpus` без таблиц), сравнение
  (видимый текст + вид строки), нормализация, явный список допустимых расхождений отдельными кейсами с
  комментарием. Функция сравнения принимает список строк Markdown — её же `qa` прогонит на дампе
  реальных заметок (Chunk 6).

**Итог:** принято, коммит см. ниже; `engine.*` + `CheckboxRenderColorTest` — 65 тестов, 0 упало, 1 skip (`realNotesDump` без дампа): AztecOracleTest 16, BlockSpanDrawTest 8, DocBinderTest 13, NeighbourLayoutTest 4, NormalizerTest 23. Оракул: 74 документа, 6 расходятся, 0 необъяснённых. Ревью: 0 блокеров, 3 «заметно» + мелочи исправлены; добавлен механизм соседей (`Normalizer.affectedRange/refreshNeighbours`, `Numbering`) — обязателен для 4a/4b.

**Готово, когда:** `gw.sh :app:testDebugUnitTest --tests 'app.tessera.ui.editor.engine.*'` зелено (классы
по отдельности при флаке `RenderNodeNatives`), список допустимых расхождений оракула — в отчёте.

### Chunk 4a: правила, typing attributes, автозамены, FormatState (`feature`, `ui/editor/engine`)

Чистые операции над `Editable` — без View, IME и истории.

**Файлы:** `EditRules.kt`, `TypingAttributes.kt`, `AutoReplace.kt`, `FormatState.kt` (`Normalizer.kt` —
только точки вызова). Тесты (Robolectric): `EditRulesTest`, `TypingAttributesTest`, `AutoReplaceTest`,
`FormatStateTest`.

- [x] `EditRules`:
  - Enter и Backspace по таблицам §6.4, каждая клетка — параметризованный кейс;
  - Backspace с кареткой сразу после `SentinelSpan` считается Backspace в начале абзаца;
  - удаление выделения через абзацы;
  - отступы §6.6 (0..4);
  - переключение вида и меток §6.6;
  - вставка `Divider` после абзаца с кареткой (`insertHorizontalRule`, §7 спеки).
- [x] `TypingAttributes` §6.6:
  - умолчание без `Code`;
  - применяются только к реально вставленной середине, префикс и суффикс получают прежние метки из
    снимка `beforeTextChanged`;
  - сброс к умолчанию при перемещении каретки.

  Кейсы:
  - набор в конце «**Т**екст»: Т остаётся жирной, даже когда composing-регион заменяется целиком;
  - выключить жирный в конце жирного слова;
  - набор после legacy-кода — не код;
  - переключил метку, сдвинул каретку — набор без неё.
- [x] `AutoReplace` §6.5 — вся таблица; при вставке не срабатывает. Возврат по undo проверяется в 4b.
- [x] `FormatState` §7 спеки: вид у каретки, `Mixed`, метки по всему выделению, typing attributes без
  выделения, `canIndent`/`canOutdent`. `canUndo`/`canRedo` заполняются в 4b.

**Итог:** принято, коммит `a418056`; 277 тестов (graphics-классы по одному зелёные). Ревью 4a: 0 блокеров, 3 «заметно» — внесены в 4b.

**Готово, когда:** `gw.sh :app:testDebugUnitTest --tests 'app.tessera.ui.editor.engine.*'` зелено. В отчёте:
число кейсов `EditRulesTest` и подтверждение, что покрыта каждая клетка таблиц §6.4–6.5.

### Chunk 4b: хост, IME, история, буфер, доступность, выдача (`feature`, `ui/editor/engine`)

**Файлы:** `TesseraEditText.kt`, `TesseraInputConnection.kt`, `EditHistory.kt`, `ClipboardBridge.kt`,
`CheckboxAccessibilityHelper.kt`. Тесты (Robolectric): `EditHistoryTest`, `ClipboardBridgeTest`,
`TesseraEditTextTest`, `TesseraEditTextEmitTest`.

- [x] `EditHistory` §6.7:
  - склейка по ~500 мс, команды — отдельные шаги, лимит 100;
  - undo/redo — пользовательская правка, выдаётся наружу;
  - очистка при внешней пересборке;
  - undo сразу после автозамены возвращает набранные символы.
- [x] `ClipboardBridge` §6.8: внутри Tessera формат сохраняется, вставка извне — plain text.
- [x] `TesseraInputConnection`: `commitText("\n")`, `sendKeyEvent(ENTER/DEL)`,
  `deleteSurroundingText(1,0)`, `getCursorCapsMode` с учётом `SentinelSpan`.
- [x] `TesseraEditText`:
  - `onKeyDown`: Enter, Del, Tab/Shift+Tab, Ctrl+B/I/U/Z/Shift+Z;
  - тап по чекбоксу, зона ≥ 48dp;
  - read-only (§6.10);
  - каретка встаёт после `U+200B`;
  - выдача и эхо §6.9 (D13): `lastLoadedMarkdown`, `hasPendingUserEdit`, дебаунс 300 мс, dispose;
  - `formatState()` с `canUndo`/`canRedo`.
- [x] `CheckboxAccessibilityHelper`: виртуальный узел на чекбокс, `isCheckable`/`isChecked`, действие клика.
- [x] `TesseraEditTextEmitTest`:
  - загрузка, `reapplyTheme`, смена `editable`, dispose без правки → ничего не выдано;
  - правка → одна выдача после дебаунса;
  - undo → выдача;
  - внешний `markdown` при `hasPendingUserEdit` пропущен, без неё — применён, история очищена;
  - legacy `* x` открывается и принимает внешнее обновление.

**Итог:** сдан, коммит `a418056`; `engine.*` + `CheckboxRenderColorTest` — 365 тестов, 0 упало, 1 skip (оркестратор, `--rerun`). Отступление от D6: загрузка через `setText(DocBinder.build(..))`. Ревью 4b — идёт.

**Готово, когда:** `gw.sh :app:testDebugUnitTest --tests 'app.tessera.ui.editor.engine.*'` зелено.

### Chunk 4c: максимальное покрытие текстов и гонок — фаззер движка (`feature`, `ui/editor/engine`)

Указание пользователя (2026-11-07, передано через сессию `notes-7d`): «нужно протестировать все
кейсы в самом максимальном количестве вариантов чтобы избежать различных багов нужны все различные
вариции текстовок и рейсов». Стартует после приёмки 4b-fix. Сессия владеет `engine/**` и сама чинит
найденное, на каждый баг — регрессионный тест с зафиксированным seed.

- [ ] **Корпус текстов** (`EngineTextCorpus` в тестах). В него входят:
  - пусто, один символ, очень длинный абзац (≥ 10 000 символов), ≥ 2000 абзацев;
  - кириллица, латиница, смешанный текст;
  - эмодзи: тон кожи, ZWJ-семьи, флаги, keycap;
  - комбинируемые символы (e + U+0301);
  - RTL (иврит, арабский) и смешанный RTL/LTR;
  - спецсимволы Markdown внутри текста (`* _ \ # - + > [ ] | ` ~ < & !`), строки, которые выглядят как маркеры (`1.`, `- [ ]`, `---`, ```` ``` ````, `<!-- -->`);
  - пробельные символы: NBSP, табы, ведущие и хвостовые пробелы; набранные пользователем U+200B, U+FFFC, U+FEFF;
  - legacy-формы из `NoteDocFixtures.legacyCorpus`;
  - каждый вид блока рядом с каждым другим (матрица пар).
- [ ] **Фаззер сессий правок** (Robolectric, `TesseraEditText`). Случайная последовательность действий из набора:
  - ввод по символу через InputConnection (`commitText`);
  - composing (`setComposingText` и замена региона целиком, как у Gboard);
  - Enter и Backspace по всем путям;
  - выделение и удаление;
  - команды панели (вид, метка, отступ, разделитель);
  - автозамены;
  - undo и redo;
  - вставка внутренняя, внешняя plain и внешняя стилизованная;
  - внешний `setMarkdown`: с правкой в ожидании и без неё, повтор устаревшего значения;
  - `reapplyTheme`, переключение `editable`, `dispose`.

  Seeds фиксированные, ≥ 500 сессий × ≥ 200 шагов. После КАЖДОГО шага проверяется:
  - инварианты §6.2 1–5;
  - `read(editable) == normalize(read(editable))`;
  - неподвижная точка `serialize(parse(serialize(doc)))`;
  - отсутствие исключений.

  Каждые N шагов дополнительно:
  - живой layout совпадает со свежим (высоты строк, номера, отступы);
  - после `flushPendingEdit` выдано ровно `markdown()`;
  - undo после действия возвращает предыдущий снимок байт в байт.

  Упавший seed печатается вместе с минимизированной последовательностью шагов.
- [ ] **Гонки — детерминированные тесты** (Robolectric, с ручным управлением главным `Looper`):
  - дебаунс выдачи против внешнего `setMarkdown`;
  - устаревший `markdown` при рекомпозиции;
  - undo и redo во время ожидающего дебаунса;
  - вставка во время composing;
  - `dispose` посреди дебаунса;
  - правка после `dispose`;
  - внешнее обновление во время composing;
  - автозамена во время composing.

**Готово, когда:** фаззер и тесты гонок зелёные. В отчёте: число сессий и шагов, найденные и
исправленные баги (каждый с регрессионным тестом), время прогона.

### Chunk 5: интеграция (`feature`)

**Файлы:** `NoteBody.kt`; `RichNoteBody.kt` (только `RichBodyActions`); `EditorFormatBar.kt`,
`MarkdownFormatMenu.kt`; `NoteEditorScreen.kt`, `NoteEditorState.kt`, `NoteEditorViewModel.kt`;
`SettingsScreen.kt`, `SettingsState.kt`, `SettingsViewModel.kt`; `NotesNavGraph.kt`; строки (раздел 6).
Тесты:
- `NoteEditorViewModelTest`: конструктор + флаг в state, через общий `FakeUserPreferences` из Chunk 1b.
- `SettingsViewModelTest`: + переключатель, через общий фейк в новом кейсе; приватный фейк файла не трогать.
- `NoteBodyActionsTest` (Robolectric): маппинг `RichBodyActions` → движок —
  - `toggleHeading1/2/3` → Название/Заголовок/Подзаголовок;
  - `toggleHeading4–6` → Подзаголовок;
  - `toggleCode` → `Mono`;
  - `toggleParagraph` → `Body`;
  - `insertHorizontalRule` → `Divider`;
  - `toggleDashList`, `indent`, `outdent`.
- `EditorFormatBarNativeTest` (Compose UI, Robolectric):
  - при `nativeEngine = true` нет «Таблица», «Картинка», «Камера»;
  - есть «Тире» и «Отступ вправо/влево»;
  - активность кнопок — из `FormatState`;
  - undo/redo неактивны при `canUndo/canRedo = false`;
  - меню ⋯ — Название / Заголовок / Подзаголовок / Основной / Моно / Цитата;
  - при `nativeEngine = false` набор прежний.
- Roborazzi: `NoteBodyScreenshotTest` (все виды и метки, light + dark), `EditorFormatBarNativeScreenshotTest`.

- [ ] `NoteBody` по §7 спеки (без параметра `scrollable`; всегда заполняет область и скроллится сам;
  подъём каретки как `caretRectOf`).
- [ ] `RichBodyActions`: новые методы и маппинг §7 спеки (D12).
- [ ] `EditorFormatBar` / `MarkdownFormatMenu`: `nativeEngine` по §7 спеки, реестру и D15; при `false` —
  поведение прежнее.
- [ ] Экран: при `state.useNativeEditor` — быстрый путь с `NoteBody` для редактирования и чтения, вставка
  картинок/таблицы скрыта; при `false` — код без изменений (D2).
- [ ] Settings: D3, D17.
- [ ] Строки раздела 6 в `values` и `values-ru`.

**Готово, когда:**
- `gw.sh :app:testDebugUnitTest --tests 'app.tessera.ui.editor.*' --tests 'app.tessera.ui.settings.*'`
  зелено; классы базовой линии раздела 1 — с прежними числами (по отдельности при флаке);
- `verifyRoborazziDebug` по `NoteEditorScreenshotTest`, `SettingsScreenshotTest` и `EditorFormatBarRenderTest`
  зелёный без перезаписи golden;
- новые скриншот-тесты без golden указаны в отчёте (golden пишет оркестратор).

### Chunk 6: QA на устройстве (`qa`)

Устройство `emulator-5554`, флаг включён через Settings (debug-сборка). Тесты — в
`app/src/androidTest/java/app/tessera/ui/editor/engine/`.

- [ ] `NativeEditorImeTest` — §8 «Instrumented»: Enter и Backspace через `KEYCODE_DEL` и через
  `deleteSurroundingText`; Enter на пустом пункте (уровень −1 / выход); Enter в конце чек-листа (маркер
  виден на пустой последней строке, `U+200B`); автозамены + undo; тап по чекбоксу; выделение через
  абзацы + удаление; копирование и вставка внутри Tessera (формат сохранён); вставка стилизованного
  текста извне → plain; узел чекбокса в дереве доступности с `checkable`/`checked`.
- [ ] `NativeEditorAutocorrectReproTest` — перенос обоих классов из `b5c80c4`
  (`RichNoteBodyExactNoteMarkupTest`, `RichNoteBodyParagraphAutocorrectHeadingTest`) на `TesseraEditText`:
  та же заметка, тот же сценарий автокоррекции, ассерты на `BlockSpan` и сериализацию.
- [ ] `NativeEditorOpenCloseTest` — открыть и закрыть legacy-заметки корпуса (включая неканоничные
  `* x`, `1. 1.`) без правок → `content` и `updatedAt` не изменились (§9 п.2).
- [ ] `NativeEditorPerfTest` — заметка на 1000 абзацев: время от загрузки до первого layout, медиана
  5 прогонов; число в отчёт; > 300 мс — находка.
- [ ] `NativeEditorPersistTest` (§9 п.3) — для каждой конструкции этапа 1 (вид абзаца, метка, уровень,
  чек ±, разделитель): правка → закрыть редактор → открыть снова → вид и Markdown те же.
- [ ] Оракул на реальных заметках (§9 п.1). Если в базе на устройстве есть заметки:
  - выгрузить `content` через `adb shell run-as app.tessera` в `.claude/local/native-editor-ref/qa/notes-dump/`
    (не коммитить);
  - прогнать функцию сравнения из `AztecOracleTest` (Chunk 3) на этом дампе;
  - расхождения вне допустимого списка — находки.

  Если заметок нет — так и написать в отчёте.
- [ ] Сверка с макетом при паритете `wm size 1125x2436` / `density 480`: кадры 01–04 против экрана с
  флагом (заметка набрана тем же содержимым, что в кадре), склейки `ffmpeg hstack` →
  `.claude/local/native-editor-ref/qa/`; таблица в `docs/superpowers/qa/native-editor-design-diff.md`,
  в начале — «Уже принято, не баг» (строки 1, 2, 4 таблицы §2 плана).
- [ ] Без макета: Подзаголовок, тире, вложенность (уровни 0–4), заглушки картинки/таблицы,
  read-only (зритель общей заметки) — скрины.
- [ ] Нагрузка: light/dark × все палитры; `font_scale 1.3`; убийство процесса сразу после правки
  (правка не потеряна после перезапуска); флаг выключен — Aztec-путь как прежде (smoke).
- [ ] **Гонки и варианты на устройстве** (указание пользователя, см. Chunk 4c):
  - поворот экрана посреди composing и посреди дебаунса — текст, каретка и выдача целы;
  - смерть процесса (`am kill`) посреди правки;
  - переключение флага при открытой заметке — правка не теряется;
  - синк или внешнее обновление контента при открытом редакторе: с правкой в ожидании и без неё, через фейковый удалённый источник или второй экземпляр;
  - быстрый ввод через `adb shell input text` с одновременным undo;
  - вставка во время composing;
  - реальная клавиатура (Gboard на эмуляторе): автокоррекция, подсказки, свайп-ввод, если доступен;
  - корпус текстов из Chunk 4c (эмодзи, RTL, комбинируемые символы, Markdown-спецсимволы, очень длинный абзац): набор, Backspace по всему тексту, копирование и вставка, сохранение и переоткрытие.
- [ ] Вернуть устройство: `wm size reset`, `wm density reset`, `font_scale 1.0`, тема.

**Готово, когда:** все классы выше зелёные (числа из `>> gw`), склейки 01–04 и `design-diff.md` готовы,
находки разнесены по зонам.

## 6. Строки (en / ru)

| Ключ | en | ru |
| --- | --- | --- |
| `editor_style_title` | Title | Название |
| `editor_style_heading` | Heading | Заголовок |
| `editor_style_subheading` | Subheading | Подзаголовок |
| `editor_style_body` | Body | Основной текст |
| `editor_style_mono` | Monospaced | Моноширинный |
| `editor_list_dash` | Dashed list | Список с тире |
| `editor_indent` | Indent | Отступ вправо |
| `editor_outdent` | Outdent | Отступ влево |
| `editor_object_image` | Image | Изображение |
| `editor_object_table` | Table | Таблица |
| `settings_native_editor` | New editor (beta) | Новый редактор (beta) |

Перед добавлением — проверить, нет ли уже ключа с тем же текстом (цитата, маркированный список,
нумерованный список, чек-лист — существующие ключи меню переиспользуются).

## 7. Финал (оркестратор)

- [ ] `gw.sh :app:compileDebugKotlin :app:testDebugUnitTest --rerun` — полный прогон, числа; флаки — повтор
  класса отдельно 3 раза.
- [ ] `gw.sh :app:verifyRoborazziDebug` — весь набор; красные и на базе — отдельно как старый долг.
- [ ] Сам на устройстве: тесты Chunk 6 + главный сценарий руками через adb (включить флаг, открыть
  legacy-заметку, набрать список через `- `, Enter/Backspace, чек-лист, undo, закрыть-открыть).
- [ ] Финальное ревью — новая сессия `review` по `git diff 8e57004...HEAD` + незакоммиченное.
- [ ] advisor → отчёт пользователю.

## 8. Открытые вопросы и кандидаты на уборку

**Открытые вопросы** (в финальный отчёт, этап 1 не блокируют):
1. Заголовок — serif (как код) или Inter 600 (как макет)?
2. Цитата — как сейчас или serif italic (как макет)?
3. Отмеченный пункт — зачёркивание + приглушение (как макет, сделано) или как в Apple, без зачёркивания?
4. Панель — текущая или инлайн B I U S | H1 H2 H3 (как макет)?

**Кандидаты на уборку** (не делаем):
- Этап 2 удаляет Aztec-путь целиком (`RichNoteBody`, `FlickerlessAztecText`, `Tessera*ListSpan`,
  `TesseraPreformatSpan`, `MarkdownHtmlConverter`, `replace*Spans`, `SerifH1Spanner.applySerifHeadingSpans`).
