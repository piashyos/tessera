package app.tessera.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.text.MarkdownHtmlConverter
import app.tessera.core.text.markdownblocks.Block
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.core.text.markdownblocks.joinBlocksIntoMarkdown
import app.tessera.core.text.markdownblocks.splitMarkdownIntoBlocks
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.rememberHaptics
import app.tessera.core.ui.tessera.TesseraTopBar
import app.tessera.core.ui.tessera.TesseraTopBarIconButton
import app.tessera.core.ui.theme.TesseraMotion
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.components.EditorFormatBar
import app.tessera.ui.editor.components.EditorMeta
import app.tessera.ui.editor.components.MarkdownFormatMenu
import app.tessera.ui.editor.components.RichBodyActions
import app.tessera.ui.editor.components.RichNoteBody
import app.tessera.ui.editor.components.TableBlockEditor
import app.tessera.ui.editor.components.TagChipsExpandable
import app.tessera.ui.editor.components.TagPickerPopup
import app.tessera.ui.notes.formatNoteDate
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.Trash2
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
    state: NoteEditorState,
    isDraftEntry: Boolean = false,
    onContentChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val converter = remember { MarkdownHtmlConverter() }
    var actions by remember { mutableStateOf<RichBodyActions?>(null) }
    // Index of the text block whose Aztec editor last held focus. Drives where the
    // "insert table" action places the new table (after the focused block, not at the end).
    var lastFocusedBlock by remember { mutableStateOf(0) }
    val currentFocusedBlock by rememberUpdatedState(lastFocusedBlock)
    val initialHtml = remember(state.content) { converter.markdownToHtml(state.content) }
    val blocks = remember(state.content) { splitMarkdownIntoBlocks(state.content) }
    val hasTable = blocks.any { it is Block.Table }

    // Keep insertTable's closure pointing at the latest content/callback so that
    // the action captured into `actions` (set once when the editor reports ready)
    // doesn't observe stale state across recompositions.
    val currentContent by rememberUpdatedState(state.content)
    val currentOnContentChange by rememberUpdatedState(onContentChange)
    val wrapActions: (RichBodyActions) -> RichBodyActions = { base ->
        object : RichBodyActions by base {
            override fun insertTable() {
                val existing = splitMarkdownIntoBlocks(currentContent).toMutableList()
                // Insert the table right after the text block that currently holds the
                // cursor, followed by an empty text block so the user can keep typing
                // prose past the grid. Falls back to the end when nothing is focused.
                val insertAt = (currentFocusedBlock + 1).coerceIn(0, existing.size)
                existing.add(insertAt, Block.Table(TableGrid.empty(columns = 2, rows = 2)))
                existing.add(insertAt + 1, Block.Text(""))
                val joined = joinBlocksIntoMarkdown(existing)
                if (joined != currentContent) currentOnContentChange(joined)
            }
        }
    }

    val hazeState = remember { HazeState() }
    var tagPickerVisible by remember { mutableStateOf(false) }
    var formatMenuVisible by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible

    Box(modifier = modifier.fillMaxSize().testTag(UiTestTags.EditorRoot)) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TesseraTopBar(
                    title = {},
                    navigationIcon = {
                        TesseraTopBarIconButton(
                            onClick = { haptics.tick(); onBack() },
                            contentDescription = stringResource(R.string.editor_back),
                            modifier = Modifier.testTag(UiTestTags.EditorBack),
                        ) {
                            Icon(
                                imageVector = Lucide.ArrowLeft,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    actions = {
                        TesseraTopBarIconButton(
                            onClick = { haptics.tick(); onTogglePin() },
                            contentDescription = stringResource(
                                if (state.isPinned) R.string.editor_unpin else R.string.editor_pin,
                            ),
                            modifier = Modifier.testTag(UiTestTags.EditorPin),
                        ) {
                            Icon(
                                imageVector = Lucide.Pin,
                                contentDescription = null,
                                tint = if (state.isPinned) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        TesseraTopBarIconButton(
                            onClick = { haptics.tick(); onDelete() },
                            contentDescription = stringResource(R.string.editor_delete),
                            modifier = Modifier.testTag(UiTestTags.EditorDelete),
                        ) {
                            Icon(
                                imageVector = Lucide.Trash2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = imeVisible,
                    enter = slideInVertically(
                        animationSpec = tween(TesseraMotion.DurShort4Ms, easing = TesseraMotion.EaseDecelerate),
                    ) { it },
                    exit = slideOutVertically(
                        animationSpec = tween(150, easing = TesseraMotion.EaseStandard),
                    ) { it },
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding(),
                    ) {
                        EditorFormatBar(
                            actions = actions,
                            activeFormats = actions?.activeFormats() ?: emptySet(),
                            onOpenOverflow = { formatMenuVisible = true },
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .hazeSource(hazeState),
            ) {
                EditorMeta(
                    folderBreadcrumb = emptyList(),
                    relativeDate = if (state.updatedAt > 0L) formatNoteDate(state.updatedAt) else "",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
                TagChipsExpandable(
                    expanded = true,
                    tags = state.tags,
                    onRemoveTag = onRemoveTag,
                    onAddTagClick = { tagPickerVisible = true },
                    modifier = Modifier
                        .testTag(UiTestTags.EditorTagToggle)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    EditorBody(
                        hasTable = hasTable,
                        blocks = blocks,
                        initialHtml = initialHtml,
                        content = state.content,
                        converter = converter,
                        onContentChange = onContentChange,
                        onActionsReady = { actions = wrapActions(it) },
                        onBlockFocused = { lastFocusedBlock = it },
                    )

                    // Tap target below the content forwards focus to the editor body and
                    // surfaces the IME, so users can resume typing past the visible text.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { actions?.focusBody() },
                            ),
                    )
                    Box(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                }
            }
        }

        // Overflow format menu (headings 3–6 / paragraph) — anchored as its own popup.
        val currentActions = actions
        if (currentActions != null) {
            MarkdownFormatMenu(
                visible = formatMenuVisible,
                activeFormats = remember(formatMenuVisible) {
                    if (formatMenuVisible) currentActions.activeFormats() else emptySet()
                },
                actions = currentActions,
                onDismiss = { formatMenuVisible = false },
                hazeState = hazeState,
            )
        }

        // Tag picker popup — drawn on top of everything.
        TagPickerPopup(
            visible = tagPickerVisible,
            availableTags = state.availableTags,
            onAddTag = { onAddTag(it); tagPickerVisible = false },
            onDismiss = { tagPickerVisible = false },
            hazeState = hazeState,
        )
    }
}

/**
 * Editor body — the fast single-editor path for note without tables, or the ordered
 * block stack (text ↔ table) when a table is present. Logic preserved verbatim from the
 * pre-redesign screen; only extracted into a helper to keep [NoteEditorScreen] readable.
 */
@Composable
private fun EditorBody(
    hasTable: Boolean,
    blocks: List<Block>,
    initialHtml: String,
    content: String,
    converter: MarkdownHtmlConverter,
    onContentChange: (String) -> Unit,
    onActionsReady: (RichBodyActions) -> Unit,
    onBlockFocused: (Int) -> Unit,
) {
    if (!hasTable) {
        // FAST PATH — preserves behavior for notes without tables (the majority).
        RichNoteBody(
            html = initialHtml,
            markdownKey = content,
            htmlToMarkdown = converter::htmlToMarkdown,
            onMarkdownChange = { markdown -> if (markdown != content) onContentChange(markdown) },
            onActionsReady = onActionsReady,
            onFocusChanged = { if (it) onBlockFocused(0) },
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        blocks.forEachIndexed { idx, block ->
            when (block) {
                is Block.Text -> {
                    val blockHtml = remember(block.markdown) { converter.markdownToHtml(block.markdown) }
                    RichNoteBody(
                        html = blockHtml,
                        // The block's local markdown is the echo-loop key — it matches what
                        // RichNoteBody's internal flush writes back, so a self-originated
                        // change doesn't fire a self-inflicted reload.
                        markdownKey = block.markdown,
                        htmlToMarkdown = converter::htmlToMarkdown,
                        onMarkdownChange = { newMd ->
                            val updated = blocks.toMutableList()
                            updated[idx] = Block.Text(newMd)
                            val joined = joinBlocksIntoMarkdown(updated)
                            if (joined != content) onContentChange(joined)
                        },
                        onActionsReady = onActionsReady,
                        onFocusChanged = { if (it) onBlockFocused(idx) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is Block.Table -> {
                    val swapBlocks: (Int, Int) -> Unit = { a, b ->
                        val updated = blocks.toMutableList()
                        val tmp = updated[a]
                        updated[a] = updated[b]
                        updated[b] = tmp
                        val joined = joinBlocksIntoMarkdown(updated)
                        if (joined != content) onContentChange(joined)
                    }
                    TableBlockEditor(
                        grid = block.grid,
                        onGridChange = { newGrid ->
                            val updated = blocks.toMutableList()
                            updated[idx] = Block.Table(newGrid)
                            val joined = joinBlocksIntoMarkdown(updated)
                            if (joined != content) onContentChange(joined)
                        },
                        onMoveUp = if (idx > 0) { { swapBlocks(idx, idx - 1) } } else null,
                        onMoveDown = if (idx < blocks.lastIndex) { { swapBlocks(idx, idx + 1) } } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFBF4)
@Composable
private fun NoteEditorScreenLightPreview() {
    TesseraTheme(darkTheme = false, dynamicColor = false) {
        NoteEditorScreen(
            state = NoteEditorState(
                content = "My first note\n\n- [ ] Buy groceries\n- [x] Call the dentist",
                tags = listOf("work", "personal"),
                isPinned = false,
                availableTags = listOf("ideas", "kotlin"),
                isLoading = false,
            ),
            onContentChange = {},
            onAddTag = {},
            onRemoveTag = {},
            onTogglePin = {},
            onBack = {},
            onDelete = {},
        )
    }
}
