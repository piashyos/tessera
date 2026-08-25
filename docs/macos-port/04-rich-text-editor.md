# 04 — Rich-text редактор и Markdown

> Самая нетривиальная часть порта. Android использует **AztecText** (WYSIWYG поверх EditText) + конвертер Markdown↔HTML. На macOS Aztec недоступен — реализуем эквивалент на **`NSTextView`** (через `NSViewRepresentable`) с конвертером **Markdown↔`NSAttributedString`**. Канонический формат хранения — **Markdown того же диалекта**, что в Android (это контракт совместимости с Android-клиентом).

---

## 4.1. Принципиальная схема

```
Storage (Markdown)  ──markdownToAttributed──►  NSTextView (NSAttributedString, WYSIWYG)
                    ◄──attributedToMarkdown──
```

- **На диск и в сеть уходит только Markdown.** AttributedString — эфемерное представление в движке (как HTML в Aztec).
- Редактор показывает форматированный текст (жирный, заголовки, списки, чек-боксы, цитаты, код, таблицы), а не сырой Markdown.
- Таблицы выносятся в отдельные блок-редакторы (как в Android block-stack path).

> **Важно про диалект.** Конвертер обязан читать/писать ровно тот Markdown, что и Android `MarkdownHtmlConverter` + `MarkdownBlocks`/`GfmTableParser`. Точные правила — §4.6–4.8. Любое расхождение приведёт к «дрожанию» контента при кросс-платформенном редактировании.

---

## 4.2. Архитектура редактора

Компоненты (Features/Editor/):
- `NoteEditorView` — корневой layout (toolbar, meta, tags, body, members sheet).
- `NoteEditorViewModel` (`@MainActor @Observable`) — состояние + автосейв + live-sync.
- `NoteEditorState` — структура состояния.
- `RichTextView` (`NSViewRepresentable` поверх `NSTextView`) — один текстовый блок.
- `MarkdownToolbar` — панель форматирования.
- `TableBlockEditor` — редактор таблицы (SwiftUI Grid).
- `TagChips` / `TagPickerPopover`.
- `MembersSheet`.

### Два пути верстки тела (как в Android)
1. **Fast path** (нет таблиц): один `RichTextView` на всю высоту, внутренний скролл.
2. **Block-stack path** (есть таблица ИЛИ read-only): `ScrollView` с чередованием `RichTextView` (текстовые блоки) и `TableBlockEditor` (таблицы).

Разбиение: `splitMarkdownIntoBlocks(markdown) -> [Block]`, `Block = .text(String) | .table(TableGrid)` (§4.8).

---

## 4.3. NoteEditorState

```swift
struct NoteEditorState {
    var noteId: String = ""
    var content: String = ""                 // канонический Markdown
    var tags: [String] = []
    var isPinned: Bool = false
    var availableTags: [String] = []          // теги других заметок, отфильтрованы
    var folderBreadcrumb: [String] = []       // root → leaf
    var updatedAt: Int64 = 0
    var isLoading: Bool = true
    // sharing
    var currentUserId: String? = nil
    var isShared: Bool = false
    var isOwner: Bool = false
    var myRole: ShareRole? = nil
    var members: [ShareMember] = []
    var membersSheetVisible: Bool = false
    var inviteState: InviteUiState = .idle
    var shareError: String? = nil

    var isReadOnly: Bool { myRole == .VIEWER }
    var canEditContent: Bool { !isShared || (myRole?.canEditContent ?? true) }
}
```

---

## 4.4. ViewModel: автосейв, удаление, live-sync

### Константы тайминга (перенести точно)
- Автосейв debounce: **500 ms**.
- Flush текста из движка (TextView→Markdown): **300 ms** debounce.
- (Serif reapply 50ms — Android-специфика Aztec; на Mac не требуется, см. §4.10.)

### scheduleAutosave / persist
```swift
func onContentChange(_ md: String) { state.content = md; scheduleAutosave() }
func onAddTag(_ t: String) { ...; scheduleAutosave() }
func onTogglePin() { state.isPinned.toggle(); scheduleAutosave() }

private func scheduleAutosave() {
    autosaveTask?.cancel()
    autosaveTask = Task { [weak self] in
        try? await Task.sleep(for: .milliseconds(500))
        guard !Task.isCancelled else { return }
        await self?.persist()
    }
}

private func persist() async {
    guard contentChanged || pinChanged || tagsChanged else { return }   // no-op guard
    if state.content.isBlank {                                          // пустая → удалить везде
        await permanentlyDeleteNoteUseCase(noteId); baseline = nil; return
    }
    let updated = baseline.copy(content: state.content, tags: state.tags, isPinned: state.isPinned)
    baseline = await updateNoteUseCase(updated)                          // новый baseline
}
```

### flush при закрытии
`func flush()` — отменить debounce, выполнить `persist()` синхронно (на закрытии окна/вкладки редактора — см. doc 07 lifecycle).

### Live-sync коллабораторов (applyRemoteIfIdle)
```swift
func applyRemoteIfIdle(_ remote: Note) {
    guard !hasLocalEdits else { return }              // не затирать ввод
    guard remote.updatedAt > baseline.updatedAt else { return }  // только строго новее
    state.content = remote.content; ...
    baseline = remote
}
```
Подписки в `init`/`onAppear`: загрузка заметки; `observeNotes` → availableTags + applyRemoteIfIdle; breadcrumb (combine folderForNote + folders); `observeMembers` → isShared/myRole/members.

---

## 4.5. RichTextView (`NSViewRepresentable`)

Оборачивает `NSTextView` в `NSScrollView`. Отвечает за:
- загрузку `NSAttributedString` из Markdown,
- применение/снятие форматирования по командам toolbar,
- debounced-flush обратно в Markdown,
- запрос «активных форматов» в позиции каретки (для подсветки toolbar),
- read-only режим.

```swift
struct RichTextView: NSViewRepresentable {
    @Binding var markdown: String
    let editable: Bool
    let onActiveFormatsChanged: (Set<TextFormat>) -> Void
    let onFocusChanged: (Bool) -> Void
    var actionsBinding: (RichBodyActions) -> Void   // прокинуть действия в toolbar

    func makeNSView(context: Context) -> NSScrollView { ... }   // конфиг NSTextView
    func updateNSView(_ v: NSScrollView, context: Context) { ... }
    func makeCoordinator() -> Coordinator { ... }               // NSTextViewDelegate
}
```

**Coordinator (NSTextViewDelegate):**
- `textDidChange` → debounce 300ms → `attributedToMarkdown(textView.textStorage)` → если изменилось, `markdown = md`.
- `textViewDidChangeSelection` → пересчитать активные форматы → `onActiveFormatsChanged`.
- `becomeFirstResponder`/`resignFirstResponder` → `onFocusChanged`.
- read-only: `textView.isEditable = false; isSelectable = false` (но рендер форматированный).

**Хранение форматирования:** используем `NSAttributedString` атрибуты:
- bold/italic → `NSFont` traits.
- underline → `.underlineStyle`.
- strikethrough → `.strikethroughStyle`.
- inline code → моноширинный шрифт + фоновый цвет (custom attribute `.tesseraInlineCode`).
- headings (H1/H2) → размер/шрифт + custom attr `.tesseraBlock = .h1/.h2`.
- lists/quote/code-block/hr → реализуем как блочные атрибуты абзаца (`NSParagraphStyle` + custom block attribute), рендер маркеров/отступов/линий через `NSTextList`, `firstLineHeadIndent`, кастомный `NSTextAttachment` для `hr`/чекбоксов.

> **Сложность чек-листов:** чекбокс рендерим как интерактивный `NSTextAttachment` (картинка ☐/☑) + обработка клика по attachment → переключение `[ ]`↔`[x]` в Markdown без полного reflow (как Android lightweight refresh). Альтернатива на macOS 15 — `NSTextList` с маркером + отдельный glyph; но интерактивный attachment надёжнее для toggle.

---

## 4.6. Поддерживаемые форматы (паритет с Android)

### Инлайн
| Действие | Markdown | Атрибут NSAttributedString |
|----------|----------|----------------------------|
| Bold | `**text**` | bold trait |
| Italic | `*text*` | italic trait |
| Strikethrough | `~~text~~` | strikethrough |
| Underline | `<u>text</u>` (литеральный тег!) | underline |
| Inline code | `` `text` `` | mono + bg |

### Блочные
| Действие | Markdown | Рендер |
|----------|----------|--------|
| H1 | `# ...` | крупный serif (Instrument Serif), §4.10 |
| H2 | `## ...` | крупный sans |
| Bullet list | `- item` | • маркер, отступ |
| Ordered list | `1. item` | нумерация |
| Task list | `- [ ] ` / `- [x] ` | чекбокс attachment |
| Quote | `> line` (multiline) | левая полоса + отступ |
| Code block | ```` ``` ```` fenced | mono, фон, скругление |
| Horizontal rule | `---` | линия (attachment/divider) |
| Table | GFM | отдельный блок (§4.8) |

> Toolbar показывает H1–H6, но **конвертер обрабатывает только H1/H2** как блочный синтаксис; H3–H6 трактуются как параграф (точно как Android). Сохранить это поведение.

### Действия (RichBodyActions) — что вызывает toolbar
`toggleBold/Italic/Strikethrough/Underline/Code`, `toggleH1..H6` (toggle↔paragraph), `toggleBulletList/OrderedList/TaskList`, `toggleQuote`, `toggleCodeBlock`, `insertHorizontalRule`, `insertTable`, `focusBody`, `activeFormats()`.

**Логика toggle заголовка (Aztec-quirk, сохранить):** если текущий блок уже этот заголовок → перевести в параграф; иначе применить заголовок.

**Сохранение выделения:** обернуть изменение формата так, чтобы восстановить `selectedRange` после (как `withPreservedSelection`), затем пересчитать активные форматы и сделать синхронный flush.

---

## 4.7. Конвертер Markdown ↔ AttributedString

> Android-конвертер двунаправленный Markdown↔HTML. На macOS целевой формат — `NSAttributedString`, но **правила парсинга Markdown идентичны**. Реализуем `MarkdownConverter` с двумя направлениями. Ниже — точные правила из Android `MarkdownHtmlConverter` (перенести буквально).

### markdownToAttributed (Markdown → отображение)
Блочный разбор по строкам (greedy):
1. ```` ``` ```` → код-блок (внутри — без инлайн-парсинга, маркеры литеральны).
2. `# ` → H1, `## ` → H2.
3. `- [ ] ` / `- [x] ` → task-list item (checked если `[x]`).
4. `---` → горизонтальная линия.
5. `1. ` (и т.п.) → ordered list.
6. `> ` → blockquote (несколько подряд строк объединяются).
7. `- ` → bullet list.
8. иначе → параграф.

**Инлайн-парсинг** (в порядке): экранирование спец-символов → защитить inline-code `` ` `` → `**bold**` → `*italic*` → `~~strike~~` → восстановить inline-code → литеральный `<u>`.

**Пустые строки:** Android вставляет zero-width placeholder (`​`), чтобы движок не схлопывал пустые строки. На macOS `NSTextView` пустые абзацы сохраняет сам — placeholder не нужен **при рендере**, но при обратной конвертации нужно корректно восстанавливать пустые строки (см. ниже «учёт переводов строк»). Тестами зафиксировать round-trip.

### attributedToMarkdown (отображение → Markdown)
Обход `NSAttributedString` по абзацам:
- по блочному атрибуту абзаца определяем префикс: H1→`# `, H2→`## `, bullet→`- `, ordered→`1. ` (нумеровать), task→`- [ ] `/`- [x] `, quote→`> ` на каждую строку, code-block→обернуть в ```` ``` ````, hr→`---`.
- инлайн: по атрибутам run'ов восстановить `**`,`*`,`~~`,`` ` ``, `<u>`.
- **учёт переводов строк:** несколько пустых абзацев между блоками сохраняются 1:1 (N пустых строк ↔ N пустых строк). Это критично для совместимости — Android аккуратно считает newline-раны.
- декодировать сущности обратно (`&amp;`→`&` и т.п. — на стороне HTML; на NSAttributedString-стороне просто литералы).

> **Реализация:** перенести Android-алгоритм как чистые строковые функции `MarkdownConverter`, протестировать round-trip на корпусе примеров. Отдельно — слой `attributed↔internalModel`, где internalModel — список блоков с инлайн-разметкой. Промежуточная модель упрощает обе стороны и делает их тестируемыми без `NSTextView`.

**Рекомендуемая декомпозиция:**
```
Markdown ⇄ [InlineDoc]  (чистые функции, 100% тестируемо, ZERO зависимостей от AppKit)
[InlineDoc] ⇄ NSAttributedString  (AppKit-слой, рендер атрибутов)
```
`InlineDoc` = массив блоков (`heading(level)`, `paragraph`, `bullet`, `ordered`, `task(checked)`, `quote`, `codeBlock`, `hr`, `table`) с инлайн-спанами (bold/italic/strike/underline/code). Именно `Markdown ⇄ [InlineDoc]` повторяет Android-диалект и закрывается юнит-тестами (doc 08).

---

## 4.8. Таблицы (GFM)

Файлы-аналоги: `MarkdownBlocks`, `GfmTableParser`, `TableGrid`, `TableBlockEditor`.

### Разбиение на блоки
`splitMarkdownIntoBlocks(md) -> [Block]`:
- детект GFM-таблицы: строка `| h | h |`, разделитель `|---|---|`, ≥0 строк данных.
- опциональный метаданные-комментарий **перед** таблицей:
  ```
  <!-- tessera:cols=140,80;rows=44,30,30 -->
  ```
  кодирует ширины колонок и высоты строк (в pt). Внешние рендереры игнорируют коммент.
- `joinBlocksIntoMarkdown(blocks)` — обратно, с `\n\n` между блоками, таблицы сериализуются + метаданные-коммент.

### TableGrid (модель)
```swift
struct TableGrid: Equatable {
    var headers: [String]
    var rows: [[String]]
    var colWidths: [CGFloat]?      // pt, опц.
    var rowHeights: [CGFloat]?     // pt, индекс 0 = header
    static let DEFAULT_COL_WIDTH: CGFloat = 140
    static let MIN_COL_WIDTH: CGFloat = 56
    static let DEFAULT_ROW_HEIGHT: CGFloat = 44
    static let MIN_ROW_HEIGHT: CGFloat = 36
    // setCell, setHeader, addRow(at:), removeRow(at:), addColumn(at:), removeColumn(at:),
    // withColWidth, withRowHeight, empty(columns:2, rows:2)
}
```
- `parseGfmTable(lines) -> TableGrid?` — обрабатывает экранированные `\|`, добивает короткие строки пустыми ячейками, возвращает nil для невалидных.
- `serializeGfmTable(grid) -> String`.

### TableBlockEditor (UI, SwiftUI)
- горизонтальный скролл (колонки не сжимаются).
- ячейка = `TextField`/`TextEditor`, редактируема если note editable.
- ресайз: drag-хэндлы на границах колонок (ширина) и нижней границе строки (высота).
- операции: добавить строку выше/ниже, удалить (с guard на минимум), добавить/удалить колонку.
- кнопки перемещения ↑/↓ — поменять таблицу местами с соседним текстовым блоком.
- стиль: фон `surfaceContainer`, скругление, паддинг 8pt.

---

## 4.9. Панель форматирования (MarkdownToolbar)

Android: горизонтальный bar над клавиатурой. На macOS клавиатура отсутствует — toolbar размещаем **в верхней панели окна редактора** (или как floating-бар над телом). Группы и иконки (SF Symbols, см. doc 06):

| Группа | Кнопки |
|--------|--------|
| Инлайн | B, I, U, S |
| Заголовки | H1, H2 (меню H1–H6) |
| Списки | bullet, ordered, task |
| Блоки | quote, code, hr |
| Вставка | table |
| Overflow «…» | меню: Paragraph, H1–H6 |

- Активное состояние подсвечивается (как `secondaryContainer` pill) — в SwiftUI: `.background` + tint при `active`.
- На macOS добавить пункты в **меню Format** меню-бара с горячими клавишами (⌘B/⌘I/⌘U и т.д.) — см. doc 07.
- Overflow-меню заголовков — `Menu`/`Picker`.

---

## 4.10. Типографика редактора

- Тело: Inter 15pt, межстрочный + небольшой leading (см. doc 06).
- **H1 первой строки — Instrument Serif** (display). На Mac реализуем через атрибут шрифта для H1-абзацев (бандлим Instrument Serif). H2 и прочее — Inter.
- Inline code / code block — моноширинный (SF Mono / бандл) с фоном.
- bullet — увеличенный круглый маркер (как Android `TesseraBulletListSpan`), code block — скруглённый фон (`TesseraPreformatSpan`).

---

## 4.11. Каретка, фокус, read-only

- **Каретка в зоне видимости:** `NSTextView.scrollRangeToVisible(selectedRange)` при изменении выделения/вводе. На Mac клавиатура не перекрывает контент, поэтому проще, чем Android IME-логика, но при block-stack нужно скроллить внешний `ScrollView` к активному блоку.
- **Фокус:** клик по пустой зоне под текстом фокусирует редактор и ставит каретку в конец (`focusBody`).
- **Read-only (VIEWER):** рендер форматированный, но `isEditable=false`, `isSelectable=false` (или selectable-only для копирования — на Mac логичнее разрешить выделение для копирования; решение: selectable=true, editable=false), toolbar скрыт, чекбоксы не кликабельны.

---

## 4.12. Теги (TagChips / TagPicker)

- Ряд чипов `#tag` с кнопкой удаления (×).
- Кнопка «+» открывает `TagPickerPopover` со списком `availableTags` (исключая уже применённые).
- Выбор → `onAddTag`; удаление → `onRemoveTag`; оба триггерят автосейв.
- На macOS: чипы — горизонтальный `ScrollView`/`WrappingHStack`; picker — `Popover` (не bottom sheet).

---

## 4.13. EditorMeta (хлебные крошки + дата)

- Слева: иконка папки + путь `Root › Child › …` (соединять « › »), эллипсис при переполнении; скрыт если папки нет.
- Справа: относительная дата (`noteDateLabel`, см. doc 05), видна если `updatedAt > 0`.

---

## 4.14. Известные ограничения (by design, сохранить)

1. Нет CRDT — last-write-wins по `updatedAt` (синк-слой).
2. Только H1/H2 в Markdown-блоках (H3–H6 → параграф).
3. Таблицы только GFM (без выравнивания; метаданные ширин/высот в комменте).
4. Нет UI для ссылок (URL остаются как есть в тексте).
5. Нет изображений.
6. Пустая заметка = удаление везде.

---

## 4.15. Контрольный список редактора

- [ ] `Markdown ⇄ [InlineDoc]` чистые функции, диалект идентичен Android (round-trip тесты).
- [ ] `[InlineDoc] ⇄ NSAttributedString` рендер всех блочных/инлайн форматов.
- [ ] `RichTextView` (NSViewRepresentable): load, flush 300ms, активные форматы, focus, read-only.
- [ ] RichBodyActions: все toggles + insertTable + heading-toggle-quirk + сохранение выделения.
- [ ] Интерактивные чек-боксы (attachment) с toggle `[ ]`↔`[x]`.
- [ ] TableGrid + GFM parse/serialize + метаданные-коммент + TableBlockEditor с ресайзом.
- [ ] split/join блоков; block-stack path при наличии таблицы/read-only.
- [ ] MarkdownToolbar + пункты меню Format (⌘B/I/U…).
- [ ] Автосейв 500ms, flush при закрытии, no-op guard, удаление пустой.
- [ ] applyRemoteIfIdle (live-sync без затирания ввода).
- [ ] Serif H1, mono code, bullet/quote/code стили.
- [ ] Теги (chips + popover), EditorMeta (крошки + дата).
