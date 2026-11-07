# Нативный редактор — этап 2 «Объекты и переключение»

- **Дата:** 2026-11-07.
- **Статус:** Approved, спека прошла ревью.
- **Основа:**
  - общая спека `2026-11-06-native-editor-design.md` (§2 этап 2, §11);
  - этап 1 закрыт на ветке `feature/native-editor`.
- **Цель:** картинки и таблицы работают в нативном редакторе не хуже, чем в Aztec. После этого нативный
  редактор становится единственным; Aztec, `MarkdownHtmlConverter` и флаг удаляются.
- **Апрув Gradle — ОЖИДАЕТСЯ.** Удаление Aztec меняет `app/build.gradle.kts`, `gradle/libs.versions.toml`
  и `settings.gradle.kts`. Кроме того, нативный редактор становится единственным для всех, и
  вступает в силу чек-лист релиза. Явного «да» пока нет: на «нужно ваше явное „да“» пользователь
  ответил «иди дальше по плану». P2-0…P2-3 идут сейчас. Перед P2-4 руководитель задаёт пользователю
  один вопрос: три файла плюс чек-лист релиза.

## 1. Что есть сейчас

| Что | Aztec-путь (по умолчанию) | Нативный путь (флаг) |
| --- | --- | --- |
| Картинка `![](tessera-img://id?w=&rot=)` | `AztecImageSpan` + `TesseraImageLoader` (Aztec `Html.ImageGetter`; `decode` уже «запекает» `w` и `rot` в битмап, ширина = `min(ширина после поворота, maxWidth) × w%`); выделение тапом + `ImageSelectionOverlay` (ресайз по углам, поворот на 90°); вставка из галереи/камеры (`InsertImageUseCase` → `imageInserts`), IME и буфера (`onImageCommitted`) | заглушка `ObjectSpan`, вставка скрыта |
| Таблица (GFM + `<!-- tessera:… -->`) | заметка режется на блоки: текст (Aztec) + `TableBlockEditor` (Compose) во внешнем скролле; панель `TableActionsBar` | заглушка `ObjectSpan`, вставка скрыта |
| Разделитель | Aztec hr | линия `ObjectSpan` (готово) |

## 2. Объекты: общие правила

- `EditRules.replaceObject(e: Editable, ps: Int, block: NoteBlock)` заменяет объект в абзаце `ps` новым
  блоком. Это шаг истории и выдача Markdown. Работает для картинки и таблицы.
- `EditRules.insertObject(e, caret, block)` вставляет объект отдельным абзацем после абзаца с кареткой.
  Если каретка в середине абзаца, абзац делится. Каретка ставится в начало абзаца после объекта.
- Объект выделен (выделение = его `U+FFFC`):
  - Backspace, `KEYCODE_FORWARD_DEL` и вырезание удаляют абзац объекта целиком;
  - набор поверх (IME `commitText`, вставка) заменяет объект **абзацем `Body`** с набранным текстом.
- **Только чтение** (зритель общей заметки):
  - картинки видны, но не выделяются, ресайза и поворота нет;
  - таблица видна и не редактируется: в варианте A оверлей неактивен, в варианте B лист не открывается.
- **Доступность.** Объект озвучивается подписью: «Изображение» или «Таблица, N строк, M столбцов».
  Реализация — виртуальный узел, как `CheckboxAccessibilityHelper`. Строки в en и ru.

## 3. Картинки

### 3.1 Источник и слои

- **Данные.** Чтение файлов и Room (`NoteImageDao`, `localPath`, кэш `cacheDir/images`, Drive-загрузка
  через `ImageDownloader`) переезжает из `ui/editor/components/TesseraImageLoader` в `data/images`.
  Наружу — интерфейс без Aztec и без Room. Сейчас UI ходит в DAO, это нарушение правил слоёв, и оно
  исправляется.
- **Движок** знает только свой интерфейс в `ui/editor/engine`:

  ```kotlin
  sealed interface ImageResult { data class Loaded(val drawable: Drawable) : ImageResult; data object Unavailable : ImageResult }
  interface ObjectImageSource {
      fun peek(src: String, maxWidthPx: Int): Drawable?                    // синхронно, только кэш в памяти
      fun load(src: String, maxWidthPx: Int, onResult: (ImageResult) -> Unit): () -> Unit  // возвращает отмену
  }
  ```

  - `onResult` всегда вызывается на главном потоке и не больше одного раза. После вызова отмены
    `onResult` не вызывается.
  - Реализация живёт в `ui/editor/components` (адаптер к `data/images`). Кэш в памяти — `LruCache` по
    ключу `src + maxWidthPx` с ограничением по байтам.
- **Размер — как сейчас в Aztec.**
  - Декод в `data/images` по-прежнему «запекает» `w` и `rot`: ширина равна `min(ширина после поворота,
    maxWidth) × w%`.
  - Спан рисует drawable в его собственном размере и сам `w`/`rot` не применяет.
  - Маленькие картинки не растягиваются; старые заметки выглядят как раньше.
  - `maxWidth` — ширина текста `EditText`.
- **Плейсхолдер и «недоступно»** рисует спан: тема из `EditorStyle`, `surfaceContainerHigh`, скругление
  `shapes.medium`, иконка из подписи. Высота плейсхолдера — фиксированная, `EditorStyle.imagePlaceholderHeightPx`
  (160dp). Цветных плашек загрузчика больше нет.

### 3.2 Жизненный цикл в редакторе

- **Загрузка.** При `bind`/`build` картинка сначала берётся из кэша (`peek`). Если её там нет, ставится
  плейсхолдер и запускается `load`.
- **Пересборка** (undo, redo, внешнее обновление, `reapplyTheme`) берёт картинку из кэша через `peek`:
  плейсхолдер не мигает, повторного декода нет. `ObjectSpan.restyled()` переносит drawable.
- **Пришёл результат.** Колбэк проверяет, что спан ещё в документе и src не изменился; иначе результат
  выбрасывается. Затем спан **тихо** переустанавливается на тот же диапазон, чтобы layout перемерился.
  - Тихо значит без шага истории, без выдачи Markdown, без смены `EngineEditable.version` и без сброса
    метки «объект выделен».
  - Переустановка идёт под guard хоста.
- **Отмена.** Загрузки отменяются при удалении объекта, при пересборке с другим src, при `dispose`.

### 3.3 Выделение, ресайз, поворот, вставка

- **Выделение.** Тап по картинке выделяет объект, клавиатура не открывается (как в `e78570d`).
- **Оверлей.** Поверх рисуется `ImageSelectionOverlay` по прямоугольнику спана: координаты layout
  минус scroll плюс padding. При скролле и перекладке прямоугольник пересчитывается.
- **Правка.**
  - Ресайз — `ImageSelectionGeometry.dragToWidthPercent` (база — текущая ширина при `w`=100).
  - Поворот — `withRotation`.
  - Применение — `replaceObject` с новым src. Новая картинка грузится через `load`; до её прихода
    спан показывает прежний drawable, масштабированный в новый размер.
- **Снятие выделения.** Тап мимо картинки.
- **Вставка:**
  - галерея и камера — существующий поток ViewModel (`insertedImageIds` → `actions.insertImage(id)`
    → `insertObject`);
  - IME rich content и вставка картинки из буфера — `ViewCompat.setOnReceiveContentListener` на
    `TesseraEditText`, затем `onImageCommitted(uri)` в ViewModel; байты копируются до того, как
    отпущен доступ к URI (`aa04903`).
- **Панель.** Кнопки «Картинка» и «Камера» на нативной панели возвращаются.

## 4. Таблицы

Этап начинается со **спайка** (Chunk P2-0): оба варианта прототипируются на эмуляторе, по критериям
выбирается один. Оба держат документ в одном `EditText`. Вариант B **заменяет** запасной вариант из
общей спеки §11 («текстовые сегменты + блок таблицы») — к сегментам не возвращаемся.

**A. Правка прямо в тексте (оверлей)**
- `ObjectSpan` таблицы резервирует высоту.
- Поверх `EditText` в общем `Box` позиционируется `TableBlockEditor` (Compose, переиспользуем).
- Высота спана синхронизируется с измеренной высотой оверлея.
- Фокус переходит из `EditText` в ячейку и обратно. Контекстная панель — `TableActionsBar`.
- Правка ячейки фиксируется через `replaceObject` по уходу фокуса из ячейки или по паузе 500 мс.
  Это один шаг истории на одну фиксацию, а не на каждую клавишу.

**B. Таблица рисуется в тексте, правка — в листе**
- `ObjectSpan` сам рисует сетку: `StaticLayout` на ячейку, ширины колонок из meta.
- Тап открывает нижний лист (`TesseraBottomSheet`) с `TableBlockEditor` и `TableActionsBar`.
- При закрытии листа, если таблица изменилась, — `replaceObject`.

**Критерии выбора.** Вариант A берём, если выполнены все пункты; иначе B.
1. IME в ячейке работает: набор, Backspace, Enter, автокоррекция Gboard.
2. При скролле редактора оверлей следует без рывков и без отставания на кадр (замер на эмуляторе).
3. Высота спана совпадает с высотой оверлея в пределах одного кадра после правки ячейки.
4. Каретка и ручки `EditText` не рисуются поверх таблицы; тап по таблице не ставит каретку в текст.
5. При открытой клавиатуре ячейка у нижнего края поднимается над IME.

Результат спайка — короткий отчёт с замерами. Выбор фиксирует руководитель в плане.

**Вставка таблицы.** Пустая 2×2 через `insertObject`. Кнопка «Таблица» на нативной панели возвращается.

**Сериализация** не меняется: `serializeGfmTable` + meta, каноничная форма (общая спека §4 п.8).

## 5. Переключение и удаление Aztec — последний срез, P2-4

Делается только после зелёных P2-1…P2-3 и QA.

**Флаг убирается:**
- `useNativeEditor` в `UserPreferences` и `UserPreferencesRepository`; `FakeUserPreferences`;
  `UserPreferencesRepositoryTest`;
- строка в Settings, `showDebugOptions` (`SettingsScreen`, `SettingsState`, `SettingsViewModel`,
  `NotesNavGraph`), строки `settings_native_editor` в en и ru;
- `NoteEditorState.useNativeEditor` и ветвление в экране. Тесты, которые ставят флаг: `NoteEditorNativeRacesTest`,
  `NoteEditorViewModelTest`, `SettingsViewModelTest`, androidTest `NativeAppTest`, `AppDriver.aztecOrNull`
  и его вызовы в `NativeEditorRacesTest`.

**Код Aztec удаляется:**

- **Компоненты.** `RichNoteBody`, `FlickerlessAztecText`, `TesseraBulletListSpan`, `TesseraOrderedListSpan`,
  `TesseraPreformatSpan`, путь блоков `EditorBody`.
- **Текстовый слой.**
  - `MarkdownHtmlConverter` удаляется.
  - `SerifH1Spanner.kt` удаляется целиком вместе с `SerifH1SpannerTest`: у `SerifHeadingTypefaceSpan`
    в main нет пользователей.
  - Из `markdownblocks/MarkdownBlocks.kt` уходят `Block`, `splitMarkdownIntoBlocks` и join.
    `SEPARATOR`, `TESSERA_META`, `applyMeta`, `metaComment` остаются для `NoteMarkdown`.
    `MarkdownBlocksTest` переписывается под оставшееся.
- **Чекбоксы.** Из `EditorCheckboxTheming` уходит Aztec-часть; `TesseraCheckboxDrawable` и
  `CheckboxPalette` остаются.
- **Интерфейс панели.**
  - `RichBodyActions` переезжает в `components/BodyActions.kt` без `activeFormats()`.
  - `NoteBody` больше не реализует `activeFormats()`.
  - `EditorFormatBar` и `MarkdownFormatMenu` теряют параметр `nativeEngine` и ветку Aztec.
- **ViewModel.** `NoteEditorViewModel.imageGetter: Html.ImageGetter` заменяется на `ObjectImageSource`.
- **Ресурсы.**
  - `res/layout/rich_note_body.xml` удаляется: в нём Aztec-атрибуты, и без библиотеки он не пройдёт
    линковку aapt2.
  - После него осиротеют `editor_text_cursor.xml`, цвет `aztec_bullet` (`values` и `values-night`) и
    переопределения Aztec `ic_checkbox_checked`, `ic_checkbox_unchecked`, `ic_baseline_check_24`.
    Удаляется только то, что больше нигде не используется; проверить по коду и ресурсам.
- **Тесты.**
  - Удаляются: `MarkdownHtmlConverterTest`, `AztecBlankLineRoundTripTest`, `HeadingToggleArtifactsTest`,
    `AztecImageParamSpikeTest`, `BlankLineAfterBlockTypingTest`, `ImageSpanRectTest`,
    `RichNoteBodyFormatFlushTest`, `AztecOracle.kt` + `AztecOracleTest`, androidTest `RichNoteBody*`
    и `AztecPathSmokeTest`.
  - Переписываются под код без Aztec: `CheckboxRenderColorTest`, `TesseraImageLoaderTest` (уезжает в
    `data/images` вместе с загрузчиком), `NoteEditorScreenshotTest` (код и golden), `EditorFormatBarRenderTest`,
    `EditorFormatBarNativeTest`, `MarkdownFormatMenuTest`.
  - Совместимость со старыми заметками держат `NoteDocFixtures.legacyCorpus` и `NativeEditorOpenCloseTest`.

**Gradle** (апрув — см. шапку):
- убираются `implementation(libs.aztec)` в `app/build.gradle.kts` и `aztec` в `libs.versions.toml`;
- в `settings.gradle.kts` есть репозитории `a8c-libs.s3.amazonaws.com/android` и `jitpack.io`. Каждый
  удаляется, только если без него резолвится `:app:dependencies --configuration debugRuntimeClasspath`
  (и `releaseRuntimeClasspath`) — проверка обязательна;
- `appcompat` остаётся.

**Чек-лист релиза.** До выпуска все устройства пользователя и участники общих заметок обновлены до
сборки с этапом 2. Старая сборка теряет `###`, `+ ` и вложенность (общая спека §2).

## 6. Срезы

| Срез | Роль | Содержание |
| --- | --- | --- |
| P2-0 | feature | спайк таблиц A/B на эмуляторе, отчёт по критериям §4 |
| P2-1 | data + feature | картинки: `data/images` без UI/DAO-утечки, `ObjectImageSource` + кэш, спан картинки, жизненный цикл §3.2, выделение/ресайз/поворот, вставка всеми путями, доступность объектов, read-only |
| P2-2 | feature | таблицы по выбранному варианту, вставка, read-only |
| P2-3 | qa | устройство: всё из §7 «Instrumented», старые заметки с картинками и таблицами, перф |
| P2-4 | feature + оркестратор | удаление флага и Aztec по §5, Gradle последним коммитом, полный прогон и сравнение с базой |

## 7. Тестирование

| Уровень | Что |
| --- | --- |
| JVM / Robolectric | <ul><li>спан картинки: плейсхолдер → картинка (перемер; «тихая» переустановка не даёт шага истории и выдачи); результат после удаления или смены src выбрасывается; отмена при dispose; пересборка берёт из кэша без повторной загрузки;</li><li>`replaceObject` и `insertObject` в начало, середину и конец абзаца, в пустой документ, рядом с объектом;</li><li>Delete, вырезание и набор поверх выделенного объекта;</li><li>read-only;</li><li>таблица по выбранному варианту;</li><li>фаззер Chunk 4c дополняется действиями «вставить картинку/таблицу», «ресайз», «поворот», «правка ячейки», «результат загрузки пришёл»</li></ul> |
| Instrumented | <ul><li>вставка из галереи (тестовый URI), из буфера и через IME `commitContent`;</li><li>тап → выделение без клавиатуры; ресайз и поворот жестом;</li><li>таблица: набор живым Gboard, структура, скролл;</li><li>открыть и закрыть старые заметки с картинками и таблицами — `content` и `updatedAt` не меняются;</li><li>перф: заметка с 20 картинками — первый layout ≤ 300 мс, догрузка без рывков, ввод не медленнее этапа 1;</li><li>TalkBack-узлы объектов</li></ul> |
| Roborazzi | `NoteBody` с картинкой (загружена / грузится / недоступна) и таблицей, light и dark; экран редактора после удаления Aztec |

## 8. Приёмка этапа 2

1. Старые заметки с картинками и таблицами открываются, выглядят как раньше и при открытии-закрытии не
   меняются.
2. Картинки: вставка всеми путями, ресайз и поворот, сохранение и переоткрытие. Формат ссылок прежний,
   Drive-синк не затронут.
3. Таблицы: вставка, правка ячеек, структура, сохранение и переоткрытие.
4. Сборка без Aztec компилируется. Все тесты зелёные, кроме известного старого долга вне редактора.
   По сравнению с базой новых красных нет.
5. Перф — по §7.

**Кандидаты на уборку, не делаем молча:** раздел «Редактор (Aztec) — грабли» и правило про round-trip
`MarkdownHtmlConverterTest` в `.claude/rules/tessera.md`, описание агента `android-senior`, memory про
Aztec. После удаления Aztec они устаревают. Это файлы пользователя: в отчёте предложить правку.
