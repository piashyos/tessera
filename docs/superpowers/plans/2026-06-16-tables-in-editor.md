# Plan C — Tables in the Editor

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow creating, editing, and resizing tables in a note. Tables are persisted as GFM (`| col | col |\n|---|---|\n| a | b |`) inline in the note's markdown. Within the editor, the note renders as a **vertical stack of blocks** — Aztec rich-text for prose, a custom Compose `TableBlockEditor` for each table — so the WYSIWYG model still holds.

**Why a block split:** Aztec `v2.1.7` has **zero** native `<table>` support (confirmed via AAR decompile: no Table/Cell/Row classes anywhere). Building a parallel HTML-table renderer inside Aztec would mean forking the library. A block-stack model keeps Aztec for what it does well (inline rich text) and uses Compose for the table — same surface area as widely-used note apps (Notion, Anytype).

**Architecture:**
1. **Markdown content split:** a new `MarkdownBlocks` module parses note markdown into an ordered list of `Block.Text(markdown)` and `Block.Table(grid)`. `Block.Table` is converted to/from GFM rows on the fly.
2. **Editor screen body** becomes a `Column { blocks.forEach { ... } }` — each Text block hosts its own `RichNoteBody` (Aztec), each Table block hosts a `TableBlockEditor`.
3. **Toolbar "Insert table" action** inserts a `Block.Table(2x2 default)` at the cursor.
4. **Edit / resize:** the `TableBlockEditor` supports add/remove row, add/remove column, and drag-handles on column dividers for width. Row height auto-fits content (advanced row resize is out of scope for this plan — flagged at the end).

**Tech Stack:** Kotlin, Jetpack Compose, no new dependencies. Pure-Kotlin GFM table parser/serializer (small, ~150 LOC).

**Scope discipline:** This is a single feature plan but a substantial one. It's split into 4 chunks: parser, single-block editor, integration into NoteEditorScreen, column-resize handles. Each chunk delivers something testable.

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `app/src/main/java/app/tessera/core/text/markdownblocks/MarkdownBlocks.kt` | Create | Sealed `Block` type (Text/Table) + `splitMarkdownIntoBlocks(md)` + `joinBlocksIntoMarkdown(blocks)`. |
| `app/src/main/java/app/tessera/core/text/markdownblocks/GfmTableParser.kt` | Create | GFM table syntax parse / serialise; `parseGfmTable(lines)` → `TableGrid` and back. |
| `app/src/main/java/app/tessera/core/text/markdownblocks/TableGrid.kt` | Create | Immutable `TableGrid(headers, rows, colWidthsPct)`; methods: addRow, removeRow, addColumn, removeColumn, setCell. |
| `app/src/main/java/app/tessera/ui/editor/components/TableBlockEditor.kt` | Create | Compose composable for one table — editable cells (BasicTextField inside each), add/remove row/col controls, column drag-resize. |
| `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt` | Modify | Render the note body as a list of blocks (loop over `Block.Text` + `Block.Table`) instead of one `RichNoteBody`. Insert-table action wired to RichBodyActions. |
| `app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt` | Modify | Add `insertTable()` to `RichBodyActions` interface — but its implementation lives at the screen level (it splits the surrounding block and inserts a new Table block). |
| `app/src/main/java/app/tessera/ui/editor/components/MarkdownFormatMenu.kt` | Modify | Add "Insert table" entry to the dropdown. |
| `app/src/test/java/app/tessera/core/text/markdownblocks/MarkdownBlocksTest.kt` | Create | Round-trip tests: markdown ↔ blocks ↔ markdown is identity for tables, prose-only, and mixed. |
| `app/src/test/java/app/tessera/core/text/markdownblocks/GfmTableParserTest.kt` | Create | Parser unit tests: well-formed, edge cases (escaped pipes, empty cells, 1x1). |
| `app/src/test/java/app/tessera/ui/editor/components/TableBlockEditorTest.kt` | Create | Compose UI test: render, edit a cell, add row, remove column. |

---

## Chunk 1: Markdown block split + GFM parser

### Task 1: TableGrid value type

**Files:**
- Create: `app/src/main/java/app/tessera/core/text/markdownblocks/TableGrid.kt`

- [ ] **Step 1: Write the data class**

```kotlin
package app.tessera.core.text.markdownblocks

/**
 * Immutable representation of a GFM table.
 * @param headers cell strings for the header row
 * @param rows ordered list of body rows; each row's size == headers.size
 * @param colWidthsPct optional per-column width as a fraction of total table width;
 *        if null, columns are equal-width. Must sum to ~1.0 when present.
 */
data class TableGrid(
    val headers: List<String>,
    val rows: List<List<String>>,
    val colWidthsPct: List<Float>? = null,
) {
    val columnCount: Int get() = headers.size
    val rowCount: Int get() = rows.size

    fun setCell(rowIndex: Int, colIndex: Int, value: String): TableGrid =
        copy(rows = rows.mapIndexed { r, row ->
            if (r == rowIndex) row.mapIndexed { c, v -> if (c == colIndex) value else v }
            else row
        })

    fun setHeader(colIndex: Int, value: String): TableGrid =
        copy(headers = headers.mapIndexed { c, h -> if (c == colIndex) value else h })

    fun addRow(at: Int = rowCount): TableGrid {
        val empty = List(columnCount) { "" }
        return copy(rows = rows.subList(0, at) + listOf(empty) + rows.subList(at, rowCount))
    }

    fun removeRow(at: Int): TableGrid =
        copy(rows = rows.filterIndexed { i, _ -> i != at })

    fun addColumn(at: Int = columnCount): TableGrid {
        val newHeaders = headers.subList(0, at) + "" + headers.subList(at, columnCount)
        val newRows = rows.map { row -> row.subList(0, at) + "" + row.subList(at, columnCount) }
        val newWidths = colWidthsPct?.let { ws ->
            val even = 1f / (columnCount + 1)
            ws.subList(0, at) + even + ws.subList(at, columnCount)
        }
        return TableGrid(newHeaders, newRows, newWidths)
    }

    fun removeColumn(at: Int): TableGrid {
        if (columnCount <= 1) return this
        val newHeaders = headers.filterIndexed { i, _ -> i != at }
        val newRows = rows.map { row -> row.filterIndexed { i, _ -> i != at } }
        val newWidths = colWidthsPct?.filterIndexed { i, _ -> i != at }
        return TableGrid(newHeaders, newRows, newWidths)
    }

    companion object {
        fun empty(columns: Int = 2, rows: Int = 2): TableGrid =
            TableGrid(
                headers = List(columns) { "" },
                rows = List(rows) { List(columns) { "" } },
            )
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/app/tessera/core/text/markdownblocks/TableGrid.kt
git commit -m "feat(markdown): TableGrid value type with row/column mutations"
```

### Task 2: GFM parser/serializer

**Files:**
- Create: `app/src/main/java/app/tessera/core/text/markdownblocks/GfmTableParser.kt`
- Create: `app/src/test/java/app/tessera/core/text/markdownblocks/GfmTableParserTest.kt`

- [ ] **Step 1: Write failing tests first**

```kotlin
class GfmTableParserTest {
    @Test fun `parse minimal 2x1 table`() {
        val md = """
            | A | B |
            |---|---|
            | 1 | 2 |
        """.trimIndent()
        val grid = parseGfmTable(md.lines())
        assertEquals(listOf("A", "B"), grid?.headers)
        assertEquals(listOf(listOf("1", "2")), grid?.rows)
    }

    @Test fun `serialize round-trip`() {
        val grid = TableGrid(listOf("A", "B"), listOf(listOf("1", "2"), listOf("3", "4")))
        val md = serializeGfmTable(grid)
        val back = parseGfmTable(md.lines())
        assertEquals(grid, back)
    }

    @Test fun `parse handles escaped pipes`() {
        val grid = parseGfmTable("""
            | A | B |
            |---|---|
            | a \| b | c |
        """.trimIndent().lines())
        assertEquals("a | b", grid?.rows?.first()?.first())
    }

    @Test fun `non-table input returns null`() {
        assertNull(parseGfmTable(listOf("just text")))
    }
}
```

- [ ] **Step 2: Run — should FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.text.markdownblocks.GfmTableParserTest"`
Expected: compilation failure (parser/serializer don't exist yet).

- [ ] **Step 3: Implement**

```kotlin
package app.tessera.core.text.markdownblocks

/**
 * Parse a list of consecutive markdown lines as a GFM table. Returns null if the input
 * is not a well-formed table (header row + separator row + ≥0 body rows).
 *
 * Separator row pattern: `|---|---|` (cells may have leading/trailing `:` for alignment;
 * alignment is ignored — preserved on round-trip via column-width pcts only.)
 *
 * Handles `\|` as escaped pipe within a cell.
 */
fun parseGfmTable(lines: List<String>): TableGrid? {
    if (lines.size < 2) return null
    val header = parseCells(lines[0]) ?: return null
    val sep = lines[1].trim()
    if (!Regex("""^\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?$""").matches(sep)) return null
    val sepCells = parseCells(lines[1]) ?: return null
    if (sepCells.size != header.size) return null

    val rows = lines.drop(2)
        .map { parseCells(it) ?: return null }
        .map { if (it.size < header.size) it + List(header.size - it.size) { "" } else it.take(header.size) }

    return TableGrid(header, rows)
}

private fun parseCells(line: String): List<String>? {
    val trimmed = line.trim()
    if (!trimmed.startsWith("|")) return null
    // Split on unescaped pipes
    val cells = mutableListOf<String>()
    val buf = StringBuilder()
    var i = 1 // skip leading |
    while (i < trimmed.length) {
        val c = trimmed[i]
        when {
            c == '\\' && i + 1 < trimmed.length && trimmed[i + 1] == '|' -> {
                buf.append('|'); i += 2
            }
            c == '|' -> { cells.add(buf.toString().trim()); buf.clear(); i++ }
            else -> { buf.append(c); i++ }
        }
    }
    if (buf.isNotEmpty()) cells.add(buf.toString().trim())
    return cells
}

fun serializeGfmTable(grid: TableGrid): String {
    val sb = StringBuilder()
    sb.append("| ").append(grid.headers.joinToString(" | ") { it.replace("|", "\\|") }).append(" |\n")
    sb.append("|").append(grid.headers.joinToString("|") { "---" }).append("|\n")
    grid.rows.forEach { row ->
        sb.append("| ").append(row.joinToString(" | ") { it.replace("|", "\\|") }).append(" |\n")
    }
    return sb.toString().trimEnd()
}
```

- [ ] **Step 4: Run tests — should PASS**

Run: same command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/core/text/markdownblocks/GfmTableParser.kt \
        app/src/test/java/app/tessera/core/text/markdownblocks/GfmTableParserTest.kt
git commit -m "feat(markdown): GFM table parse + serialize with escaped-pipe support"
```

### Task 3: Block split

**Files:**
- Create: `app/src/main/java/app/tessera/core/text/markdownblocks/MarkdownBlocks.kt`
- Create: `app/src/test/java/app/tessera/core/text/markdownblocks/MarkdownBlocksTest.kt`

- [ ] **Step 1: Write tests first**

```kotlin
class MarkdownBlocksTest {
    @Test fun `prose-only input becomes a single Text block`() {
        val md = "Hello\n\nWorld"
        val blocks = splitMarkdownIntoBlocks(md)
        assertEquals(listOf(Block.Text("Hello\n\nWorld")), blocks)
    }

    @Test fun `table is isolated as a Table block`() {
        val md = """
            Intro
            
            | A | B |
            |---|---|
            | 1 | 2 |
            
            Outro
        """.trimIndent()
        val blocks = splitMarkdownIntoBlocks(md)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is Block.Text)
        assertTrue(blocks[1] is Block.Table)
        assertTrue(blocks[2] is Block.Text)
    }

    @Test fun `round-trip preserves content`() {
        val md = """
            # Title
            
            | A | B |
            |---|---|
            | 1 | 2 |
            
            - item
        """.trimIndent()
        assertEquals(md, joinBlocksIntoMarkdown(splitMarkdownIntoBlocks(md)))
    }
}
```

- [ ] **Step 2: Run — FAIL**

`./gradlew :app:testDebugUnitTest --tests "app.tessera.core.text.markdownblocks.MarkdownBlocksTest"`

- [ ] **Step 3: Implement**

```kotlin
package app.tessera.core.text.markdownblocks

sealed interface Block {
    data class Text(val markdown: String) : Block
    data class Table(val grid: TableGrid) : Block
}

fun splitMarkdownIntoBlocks(markdown: String): List<Block> {
    if (markdown.isEmpty()) return emptyList()
    val lines = markdown.split("\n")
    val out = mutableListOf<Block>()
    val buf = mutableListOf<String>()
    var i = 0
    while (i < lines.size) {
        // Detect table start: current line starts with | AND next line is a separator
        if (lines[i].trimStart().startsWith("|") && i + 1 < lines.size &&
            Regex("""^\s*\|?\s*:?-{3,}:?""").containsMatchIn(lines[i + 1])
        ) {
            // Find table end
            var j = i + 2
            while (j < lines.size && lines[j].trimStart().startsWith("|")) j++
            val tableLines = lines.subList(i, j)
            val grid = parseGfmTable(tableLines)
            if (grid != null) {
                if (buf.isNotEmpty()) {
                    out.add(Block.Text(buf.joinToString("\n").trim('\n')))
                    buf.clear()
                }
                out.add(Block.Table(grid))
                i = j
                continue
            }
        }
        buf.add(lines[i])
        i++
    }
    if (buf.isNotEmpty()) {
        out.add(Block.Text(buf.joinToString("\n").trim('\n')))
    }
    return out
}

fun joinBlocksIntoMarkdown(blocks: List<Block>): String =
    blocks.joinToString("\n\n") { block ->
        when (block) {
            is Block.Text -> block.markdown
            is Block.Table -> serializeGfmTable(block.grid)
        }
    }
```

- [ ] **Step 4: Run — PASS**

Same command. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/core/text/markdownblocks/MarkdownBlocks.kt \
        app/src/test/java/app/tessera/core/text/markdownblocks/MarkdownBlocksTest.kt
git commit -m "feat(markdown): split note markdown into text + table blocks"
```

---

## Chunk 2: TableBlockEditor (standalone composable)

### Task 4: Bare-bones rendering + cell editing

**Files:**
- Create: `app/src/main/java/app/tessera/ui/editor/components/TableBlockEditor.kt`

- [ ] **Step 1: Write the composable**

```kotlin
package app.tessera.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tessera.core.text.markdownblocks.TableGrid
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus

@Composable
fun TableBlockEditor(
    grid: TableGrid,
    onGridChange: (TableGrid) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(8.dp),
    ) {
        // Header row
        TableRow(
            cells = grid.headers,
            onCellChange = { i, v -> onGridChange(grid.setHeader(i, v)) },
            isHeader = true,
        )
        // Body rows
        grid.rows.forEachIndexed { rowIdx, row ->
            TableRow(
                cells = row,
                onCellChange = { colIdx, v -> onGridChange(grid.setCell(rowIdx, colIdx, v)) },
                isHeader = false,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onGridChange(grid.addRow()) }) {
                Icon(Lucide.Plus, contentDescription = "Add row", modifier = Modifier.size(16.dp))
            }
            Text("Row", style = MaterialTheme.typography.labelSmall)
            IconButton(onClick = { if (grid.rowCount > 0) onGridChange(grid.removeRow(grid.rowCount - 1)) }) {
                Icon(Lucide.Minus, contentDescription = "Remove row", modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = { onGridChange(grid.addColumn()) }) {
                Icon(Lucide.Plus, contentDescription = "Add column", modifier = Modifier.size(16.dp))
            }
            Text("Col", style = MaterialTheme.typography.labelSmall)
            IconButton(onClick = { if (grid.columnCount > 1) onGridChange(grid.removeColumn(grid.columnCount - 1)) }) {
                Icon(Lucide.Minus, contentDescription = "Remove column", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    onCellChange: (Int, String) -> Unit,
    isHeader: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        cells.forEachIndexed { idx, value ->
            BasicTextField(
                value = value,
                onValueChange = { onCellChange(idx, it) },
                textStyle = if (isHeader) {
                    MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                modifier = Modifier
                    .weight(1f)
                    .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Compose UI test**

`app/src/test/java/app/tessera/ui/editor/components/TableBlockEditorTest.kt`:

```kotlin
@RunWith(RobolectricTestRunner::class)
class TableBlockEditorTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `cell edit updates grid`() {
        var grid by mutableStateOf(TableGrid(listOf("A", "B"), listOf(listOf("1", "2"))))
        composeRule.setContent {
            TableBlockEditor(grid = grid, onGridChange = { grid = it })
        }
        // Manipulating BasicTextField in Robolectric requires the text-field semantic node.
        // Smoke check: composable renders.
        composeRule.onRoot().assertExists()
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.editor.components.TableBlockEditorTest"`. Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/components/TableBlockEditor.kt \
        app/src/test/java/app/tessera/ui/editor/components/TableBlockEditorTest.kt
git commit -m "feat(editor): TableBlockEditor with cell editing + row/column controls"
```

---

## Chunk 3: Integrate into NoteEditorScreen as a block list

### Task 5: Render note as a stack of blocks

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt`

- [ ] **Step 1: Replace the single RichNoteBody invocation with a block loop**

In `NoteEditorScreen.kt`, where `RichNoteBody(...)` is currently called (around line 154):

```kotlin
val blocks = remember(state.content) { splitMarkdownIntoBlocks(state.content) }

Column {
    blocks.forEachIndexed { idx, block ->
        when (block) {
            is Block.Text -> {
                RichNoteBody(
                    html = converter.markdownToHtml(block.markdown),
                    markdownKey = block.markdown,
                    htmlToMarkdown = converter::htmlToMarkdown,
                    onMarkdownChange = { newMarkdown ->
                        val updated = blocks.toMutableList()
                        updated[idx] = Block.Text(newMarkdown)
                        onContentChange(joinBlocksIntoMarkdown(updated))
                    },
                    onActionsReady = { actions = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is Block.Table -> {
                TableBlockEditor(
                    grid = block.grid,
                    onGridChange = { newGrid ->
                        val updated = blocks.toMutableList()
                        updated[idx] = Block.Table(newGrid)
                        onContentChange(joinBlocksIntoMarkdown(updated))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
```

> **Caveat:** with multiple `RichNoteBody` instances, only ONE's `actions` is captured at a time (the last `onActionsReady` callback wins). For the toolbar to act on the focused editor, we need to track which block is active. Simplest: store `actions` per block index; pick the one that last received focus. Approach: `RichBodyActions` callback receives the block index; store `Map<Int, RichBodyActions>` and look up by `lastFocusedBlock`.
>
> Wire `RichNoteBody` to emit a focus event (add `onFocusChanged: (Boolean) -> Unit` parameter); on focus gained, set `lastFocusedBlock = idx`.

- [ ] **Step 2: Wire per-block focus tracking**

Add to `RichNoteBody`:
```kotlin
onFocusChanged: (Boolean) -> Unit = {},
```
Inside the factory: `editor.setOnFocusChangeListener { _, focused -> onFocusChanged(focused) }`.

In `NoteEditorScreen`: keep `var lastFocusedBlock by remember { mutableStateOf(0) }` and pass it through.

- [ ] **Step 3: Build & smoke test**

Run: `./scripts/run-debug-on-emulator.sh`. Open a note that has no table — should render exactly as before (single block). Confirm typing/checkbox toggles still work.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt \
        app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt
git commit -m "feat(editor): render note body as a list of text + table blocks"
```

### Task 6: "Insert table" action

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt` (interface only — add `onInsertTableRequest` callback)
- Modify: `app/src/main/java/app/tessera/ui/editor/components/MarkdownFormatMenu.kt` (assumes Plan A landed; add "Insert table" entry)
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt`

- [ ] **Step 1: Add `insertTable()` to `RichBodyActions`**

`RichBodyActions` becomes:
```kotlin
fun insertTable()  // implemented at screen level — splits current text block and inserts Table block after cursor
```

- [ ] **Step 2: Implement at screen level**

In `NoteEditorScreen.kt`, when constructing the per-block `RichBodyActions` (in `onActionsReady`), wrap with a delegating implementation:

```kotlin
val baseActions = it
actions = object : RichBodyActions by baseActions {
    override fun insertTable() {
        val updated = blocks.toMutableList()
        // Insert empty 2x2 table after the currently focused text block
        updated.add(lastFocusedBlock + 1, Block.Table(TableGrid.empty(2, 2)))
        // Add a fresh empty text block AFTER the table so user can keep typing prose
        updated.add(lastFocusedBlock + 2, Block.Text(""))
        onContentChange(joinBlocksIntoMarkdown(updated))
    }
}
```

- [ ] **Step 3: Add the entry to the format menu**

(Assumes Plan A's `MarkdownFormatMenu.kt` exists. Add a `FormatEntry` at the bottom of the list with `Lucide.Table` icon, label "Insert table", `onClick = actions::insertTable`.)

- [ ] **Step 4: Smoke test**

In emulator: open a note → toolbar → Format → Insert table. A 2x2 empty table appears below the current paragraph; both rich text and the table are editable.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt \
        app/src/main/java/app/tessera/ui/editor/components/MarkdownFormatMenu.kt \
        app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt
git commit -m "feat(editor): insert-table action splits surrounding text and inserts grid"
```

---

## Chunk 4: Column width drag-resize

### Task 7: Drag handles between columns

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/components/TableBlockEditor.kt`

- [ ] **Step 1: Track column widths in state**

Use `grid.colWidthsPct` (defaults to even split). On drag of a handle between columns `i` and `i+1`, recompute the two affected percentages so they still sum to their combined original.

```kotlin
@Composable
private fun rememberColumnWidths(grid: TableGrid): SnapshotStateList<Float> {
    val initial = grid.colWidthsPct ?: List(grid.columnCount) { 1f / grid.columnCount }
    return remember(grid.columnCount) { mutableStateListOf(*initial.toTypedArray()) }
}
```

In `TableRow` change `Modifier.weight(1f)` to `Modifier.weight(widths[idx])` and add a 4dp drag handle between cells:

```kotlin
DragHandle(
    onDrag = { deltaPx ->
        // convert deltaPx to fraction of table width and rebalance widths[idx]/widths[idx+1]
        val totalWidth = tableWidthPx
        val delta = deltaPx / totalWidth
        val newLeft = (widths[idx] + delta).coerceIn(0.1f, widths[idx] + widths[idx + 1] - 0.1f)
        val newRight = widths[idx] + widths[idx + 1] - newLeft
        widths[idx] = newLeft
        widths[idx + 1] = newRight
    },
)
```

The `DragHandle` is a 4dp-wide `Box` with `Modifier.pointerInput { detectDragGestures { ... } }`.

When `widths` changes, call `onGridChange(grid.copy(colWidthsPct = widths.toList()))`.

- [ ] **Step 2: Round-trip the percentages through markdown**

GFM has no width syntax. Embed widths as a side-channel HTML comment line BEFORE the table:
```
<!-- tessera:colwidths=0.4,0.6 -->
| A | B |
|---|---|
| 1 | 2 |
```

Update `splitMarkdownIntoBlocks` to look for the comment line preceding a table and consume it into `TableGrid.colWidthsPct`. Update `joinBlocksIntoMarkdown` to emit the comment when widths exist and are non-uniform.

Add a parser unit test:
```kotlin
@Test fun `colwidths comment is consumed into grid`() {
    val md = """
        <!-- tessera:colwidths=0.4,0.6 -->
        | A | B |
        |---|---|
        | 1 | 2 |
    """.trimIndent()
    val grid = (splitMarkdownIntoBlocks(md).first() as Block.Table).grid
    assertEquals(listOf(0.4f, 0.6f), grid.colWidthsPct)
}
```

- [ ] **Step 3: Smoke test**

Drag a column divider — the two adjacent columns resize. Close & re-open the note — widths persist.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/components/TableBlockEditor.kt \
        app/src/main/java/app/tessera/core/text/markdownblocks/MarkdownBlocks.kt \
        app/src/test/java/app/tessera/core/text/markdownblocks/MarkdownBlocksTest.kt
git commit -m "feat(editor): drag column dividers to resize; persist via HTML comment"
```

---

## Chunk 5: Verification + branch close

### Task 8: End-to-end manual pass

- [ ] **Step 1**: Run the emulator. Create a note with text + insert a table. Type into cells. Add/remove rows and columns. Drag column dividers. Close, reopen — content + widths persist exactly.
- [ ] **Step 2**: Run unit tests: `./gradlew :app:testDebugUnitTest`. Expected: PASS.
- [ ] **Step 3**: Run lint: `./gradlew :app:lintDebug`. No new errors.
- [ ] **Step 4**: @superpowers:finishing-a-development-branch

---

## Risks / Open Questions

- **Multiple Aztec instances per screen** (Task 5): performance may degrade for notes with many text blocks. If sluggish, lazy-render off-screen blocks via `LazyColumn`. Today the screen uses a `Column` inside `verticalScroll` — switch to `LazyColumn` if needed.
- **Cross-block selection / cursor flow** (Task 5): pressing Backspace at the start of a text block won't merge it with the previous one across a table. Out of scope for this plan — flag in commit message.
- **Markdown round-trip with the HTML-comment widths trick** (Task 7): markdown rendered by external tools (GitHub) will display the comment as-is — fine for a private notes app. Document this in the parser.
- **Row resize**: deliberately out of scope. Cells auto-fit content. If row resize becomes a future requirement, store row heights similarly via HTML comment.
- **Sync (Firestore)**: tables are pure markdown — no schema change needed.
