package app.tessera.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pin
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.Trash2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.rememberHaptics
import app.tessera.core.ui.components.EdgeFadeScrim
import app.tessera.core.ui.components.FloatingCapsule
import app.tessera.core.ui.components.ScrimEdge
import app.tessera.core.ui.theme.FloatingShadowAmbient
import app.tessera.core.ui.theme.FloatingShadowSpot
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.core.text.MarkdownHtmlConverter
import app.tessera.core.text.markdownblocks.Block
import app.tessera.core.text.markdownblocks.TableGrid
import app.tessera.core.text.markdownblocks.joinBlocksIntoMarkdown
import app.tessera.core.text.markdownblocks.splitMarkdownIntoBlocks
import app.tessera.ui.editor.components.MarkdownFormatMenu
import app.tessera.ui.editor.components.MarkdownToolbar
import app.tessera.ui.editor.components.RichBodyActions
import app.tessera.ui.editor.components.RichNoteBody
import app.tessera.ui.editor.components.TableBlockEditor
import app.tessera.ui.editor.components.TagChipsExpandable
import app.tessera.ui.editor.components.TagPickerPopup
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

    val pageColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        MaterialTheme.colorScheme.surface
    } else {
        Color.White
    }
    val hazeState = remember { HazeState() }
    val popupHazeState = remember { HazeState() }
    var tagPickerVisible by remember { mutableStateOf(false) }
    var tagsExpanded by remember { mutableStateOf(false) }
    var formatMenuVisible by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageColor)
            .testTag(UiTestTags.EditorRoot),
    ) {
        // Inner wrapper — everything that the popup blurs over
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            // Scrollable content surface
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .hazeSource(popupHazeState),
                color = pageColor,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Space for top capsule + safe area
                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))
                    Spacer(modifier = Modifier.height(86.dp))

                    // Body — Aztec WYSIWYG editor (the first non-empty line becomes the
                    // note's title automatically — Apple Notes–style).
                    if (!hasTable) {
                        // FAST PATH — identical to today. Preserves behavior for notes
                        // without tables (the overwhelming majority).
                        RichNoteBody(
                            html = initialHtml,
                            markdownKey = state.content,
                            htmlToMarkdown = converter::htmlToMarkdown,
                            onMarkdownChange = { markdown ->
                                if (markdown != state.content) onContentChange(markdown)
                            },
                            onActionsReady = { actions = wrapActions(it) },
                            onFocusChanged = { if (it) lastFocusedBlock = 0 },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        // BLOCK STACK — render text and table blocks in order. Only
                        // engaged when the note actually contains a table.
                        Column(modifier = Modifier.fillMaxWidth()) {
                            blocks.forEachIndexed { idx, block ->
                                when (block) {
                                    is Block.Text -> {
                                        val blockHtml = remember(block.markdown) {
                                            converter.markdownToHtml(block.markdown)
                                        }
                                        RichNoteBody(
                                            html = blockHtml,
                                            // Use the block's local markdown directly as the
                                            // echo-loop key — it matches what RichNoteBody's
                                            // internal flush writes back, so a self-originated
                                            // change doesn't fire a self-inflicted reload.
                                            markdownKey = block.markdown,
                                            htmlToMarkdown = converter::htmlToMarkdown,
                                            onMarkdownChange = { newMd ->
                                                val updated = blocks.toMutableList()
                                                updated[idx] = Block.Text(newMd)
                                                val joined = joinBlocksIntoMarkdown(updated)
                                                if (joined != state.content) onContentChange(joined)
                                            },
                                            onActionsReady = { actions = wrapActions(it) },
                                            onFocusChanged = { if (it) lastFocusedBlock = idx },
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
                                            if (joined != state.content) onContentChange(joined)
                                        }
                                        TableBlockEditor(
                                            grid = block.grid,
                                            onGridChange = { newGrid ->
                                                val updated = blocks.toMutableList()
                                                updated[idx] = Block.Table(newGrid)
                                                val joined = joinBlocksIntoMarkdown(updated)
                                                if (joined != state.content) onContentChange(joined)
                                            },
                                            onMoveUp = if (idx > 0) {
                                                { swapBlocks(idx, idx - 1) }
                                            } else null,
                                            onMoveDown = if (idx < blocks.lastIndex) {
                                                { swapBlocks(idx, idx + 1) }
                                            } else null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom spacing for the floating bottom capsule. Tapping in this
                    // empty area forwards focus to the Aztec body and opens the IME so
                    // users can resume typing past the visible content.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { actions?.focusBody() },
                            ),
                    )
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
                }
            }

            // Top edge fade scrim — gradient fade-to-pageColor at the top edge. The haze blur
            // inside the scrim cannot blur the AztecText (AndroidView), but the scrim's own
            // color gradient still provides the visual fade.
            EdgeFadeScrim(
                hazeState = hazeState,
                scrimColor = pageColor,
                edge = ScrimEdge.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .height(150.dp),
            )

            // Top floating capsule — back + pin
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FloatingCapsule(
                        modifier = Modifier.shadow(
                            elevation = 12.dp,
                            shape = CircleShape,
                            ambientColor = FloatingShadowAmbient,
                            spotColor = FloatingShadowSpot,
                        ),
                        blurRadius = 24.dp,
                        shadowElevation = 3.dp,
                        // Solid tint scrim: haze cannot blur the AztecText (AndroidView pixels live
                        // outside the Compose graphics layer, see aztec-spike-findings.md §5).
                        hazeState = null,
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        IconButton(
                            onClick = { haptics.tick(); onBack() },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag(UiTestTags.EditorBack),
                        ) {
                            Icon(
                                imageVector = Lucide.ArrowLeft,
                                contentDescription = stringResource(R.string.editor_back),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    FloatingCapsule(
                        modifier = Modifier.shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(28.dp),
                            ambientColor = FloatingShadowAmbient,
                            spotColor = FloatingShadowSpot,
                        ),
                        blurRadius = 24.dp,
                        shadowElevation = 3.dp,
                        hazeState = null,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { haptics.tick(); onTogglePin() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag(UiTestTags.EditorPin),
                            ) {
                                Icon(
                                    imageVector = Lucide.Pin,
                                    contentDescription = stringResource(
                                        if (state.isPinned) R.string.editor_unpin else R.string.editor_pin,
                                    ),
                                    tint = if (state.isPinned) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            IconButton(
                                onClick = { haptics.tick(); onDelete() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag(UiTestTags.EditorDelete),
                            ) {
                                Icon(
                                    imageVector = Lucide.Trash2,
                                    contentDescription = stringResource(R.string.editor_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Bottom floating capsule — markdown toolbar + tag toggle, with chips above
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TagChipsExpandable(
                    expanded = tagsExpanded,
                    tags = state.tags,
                    onRemoveTag = onRemoveTag,
                    onAddTagClick = { tagPickerVisible = true },
                    modifier = Modifier.align(Alignment.End),
                )

                FloatingCapsule(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(28.dp),
                            ambientColor = FloatingShadowAmbient,
                            spotColor = FloatingShadowSpot,
                        ),
                    blurRadius = 20.dp,
                    shadowElevation = 4.dp,
                    // Real backdrop blur over the scrollable content registered via
                    // hazeSource(hazeState) above. Compose-drawn content (background,
                    // table blocks) blurs through; the toolbar reads as frosted glass.
                    hazeState = hazeState,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Box wraps toolbar + popup so the Popup composable shares the
                        // toolbar's layout slot — anchors the dropdown above the T
                        // trigger without participating in the Row's `spacedBy` gap.
                        Box(modifier = Modifier.weight(1f)) {
                            MarkdownToolbar(
                                onOpenMenu = { formatMenuVisible = true },
                                actions = actions,
                                activeFormats = actions?.activeFormats() ?: emptySet(),
                            )
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
                        }
                        Surface(
                            onClick = { haptics.tick(); tagsExpanded = !tagsExpanded },
                            shape = CircleShape,
                            color = if (tagsExpanded) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag(UiTestTags.EditorTagToggle),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Lucide.Tag,
                                    contentDescription = stringResource(R.string.editor_tags_toggle),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            }
        }

        // Tag picker popup — drawn on top of everything
        TagPickerPopup(
            visible = tagPickerVisible,
            availableTags = state.availableTags,
            onAddTag = { onAddTag(it); tagPickerVisible = false },
            onDismiss = { tagPickerVisible = false },
            hazeState = popupHazeState,
        )
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
