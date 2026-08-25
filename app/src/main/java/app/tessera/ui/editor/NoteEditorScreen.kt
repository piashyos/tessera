package app.tessera.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import app.tessera.core.ui.tessera.MembersSheet
import app.tessera.core.ui.tessera.TesseraTopBar
import app.tessera.core.ui.tessera.TesseraTopBarIconButton
import app.tessera.core.ui.theme.TesseraMotion
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.ui.editor.components.EditorFormatBar
import app.tessera.ui.editor.components.EditorMeta
import app.tessera.ui.editor.components.RichBodyActions
import app.tessera.ui.editor.components.RichNoteBody
import app.tessera.ui.editor.components.TableBlockEditor
import app.tessera.ui.editor.components.TagChipsExpandable
import app.tessera.ui.editor.components.TagPickerPopup
import app.tessera.ui.notes.noteDateLabel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.wordpress.aztec.AztecTextFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin

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
    onMakeShared: () -> Unit = {},
    onManageAccess: () -> Unit = {},
    onUnshare: () -> Unit = {},
    onLeave: () -> Unit = {},
    onDeleteForAll: () -> Unit = {},
    onInvite: (String, app.tessera.domain.model.ShareRole) -> Unit = { _, _ -> },
    onChangeRole: (app.tessera.domain.model.UserId, app.tessera.domain.model.ShareRole) -> Unit = { _, _ -> },
    onRemoveMember: (app.tessera.domain.model.UserId) -> Unit = {},
    onInviteInputChanged: () -> Unit = {},
    onDismissMembers: () -> Unit = {},
    onClearShareError: () -> Unit = {},
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
    var overflowVisible by remember { mutableStateOf(false) }
    var leaveConfirmVisible by remember { mutableStateOf(false) }
    var deleteForAllConfirmVisible by remember { mutableStateOf(false) }
    var unshareConfirmVisible by remember { mutableStateOf(false) }
    // Live formats at the editor's caret, pushed from RichNoteBody on selection/text/toggle
    // changes. Drives the toolbar highlight so buttons no longer stay lit after the caret
    // leaves the formatted span.
    var activeFormats by remember { mutableStateOf<Set<AztecTextFormat>>(emptySet()) }
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
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    actions = {
                        // Each action is wrapped in a stable `key` so that when one button toggles
                        // (e.g. share state resolves and the delete button appears/disappears) Compose
                        // keeps every other button's node identity instead of positionally reusing a
                        // removed slot — which otherwise briefly morphs the overflow icon into the
                        // trash icon on each recomposition while typing.
                        // Pinning is a content edit — viewers can't pin.
                        key("editor-action-pin") {
                            if (state.canEditContent) {
                                TesseraTopBarIconButton(
                                    onClick = { haptics.tick(); onTogglePin() },
                                    contentDescription = stringResource(
                                        if (state.isPinned) R.string.editor_unpin else R.string.editor_pin,
                                    ),
                                    modifier = Modifier.testTag(UiTestTags.EditorPin),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PushPin,
                                        contentDescription = null,
                                        tint = if (state.isPinned) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                        // Trash deletes a private note directly. For shared notes, deletion is
                        // "delete for everyone" and lives in the overflow menu (owner/manager only).
                        key("editor-action-delete") {
                            if (!state.isShared) {
                                TesseraTopBarIconButton(
                                    onClick = { haptics.tick(); onDelete() },
                                    contentDescription = stringResource(R.string.editor_delete),
                                    modifier = Modifier.testTag(UiTestTags.EditorDelete),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                        val hasOverflow = state.canMakeShared || state.canManageAccess ||
                            state.canUnshare || state.canLeave || state.canDeleteForAll
                        key("editor-action-overflow") {
                            if (hasOverflow) {
                                Box {
                                TesseraTopBarIconButton(
                                    onClick = { haptics.tick(); overflowVisible = true },
                                    contentDescription = stringResource(R.string.editor_more),
                                    modifier = Modifier.testTag(UiTestTags.EditorOverflow),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = overflowVisible,
                                    onDismissRequest = { overflowVisible = false },
                                ) {
                                    if (state.canMakeShared) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.editor_make_shared)) },
                                            leadingIcon = { Icon(Icons.Rounded.Group, contentDescription = null) },
                                            onClick = { overflowVisible = false; onMakeShared() },
                                        )
                                    }
                                    if (state.canManageAccess) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.editor_manage_access)) },
                                            leadingIcon = { Icon(Icons.Rounded.Group, contentDescription = null) },
                                            onClick = { overflowVisible = false; onManageAccess() },
                                        )
                                    }
                                    if (state.canUnshare) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.editor_make_private)) },
                                            leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                                            onClick = {
                                                overflowVisible = false
                                                unshareConfirmVisible = true
                                            },
                                        )
                                    }
                                    if (state.canLeave) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.editor_leave)) },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
                                            onClick = { overflowVisible = false; leaveConfirmVisible = true },
                                        )
                                    }
                                    if (state.canDeleteForAll) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(R.string.editor_delete_for_all),
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Rounded.Delete,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            },
                                            onClick = {
                                                overflowVisible = false
                                                deleteForAllConfirmVisible = true
                                            },
                                        )
                                    }
                                }
                            }
                            }
                        }
                    },
                )
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = imeVisible && !state.isReadOnly,
                    enter = slideInVertically(
                        animationSpec = tween(TesseraMotion.DurShort4Ms, easing = TesseraMotion.EaseDecelerate),
                    ) { it },
                    exit = slideOutVertically(
                        animationSpec = tween(150, easing = TesseraMotion.EaseStandard),
                    ) { it },
                ) {
                    // Floating pill toolbar above the keyboard (M3 expressive
                    // HorizontalFloatingToolbar shape language) instead of a full-width bar.
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                            shadowElevation = 3.dp,
                        ) {
                            EditorFormatBar(
                                actions = actions,
                                activeFormats = activeFormats,
                            )
                        }
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
                    folderBreadcrumb = state.folderBreadcrumb,
                    relativeDate = if (state.updatedAt > 0L) noteDateLabel(state.updatedAt) else "",
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

                // Read-only notes keep the outer Compose scroll: a non-editable AztecText doesn't
                // scroll its own content, and viewers can't select text so the selection-overlap
                // issue the internal-scroll path fixes doesn't apply to them.
                if (hasTable || state.isReadOnly) {
                    // BLOCK-STACK / READ-ONLY PATH — text blocks interleaved with tables are multiple
                    // Views, so they share one outer Compose scroll (each editor stays wrap-content).
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        // The tap target below the text must cover ALL visible empty space so a tap
                        // anywhere in the body — even far below the last line — focuses the editor
                        // and opens the keyboard. Size it to the viewport height.
                        val viewportHeight = maxHeight
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
                                onActiveFormatsChanged = { activeFormats = it },
                                editable = !state.isReadOnly,
                            )

                            // Tap target below the content forwards focus to the editor body and
                            // surfaces the IME, so users can resume typing past the visible text.
                            // Read-only viewers can't edit, so it must NOT exist for them — otherwise
                            // tapping the empty area would call focusBody() and force the keyboard open.
                            if (!state.isReadOnly) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(viewportHeight)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { actions?.focusBody() },
                                        ),
                                )
                            }
                            Box(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                        }
                    }
                } else {
                    // FAST PATH — one editor bounded to the remaining space (between the divider and
                    // the format bar / keyboard) that scrolls internally. weight(1f) takes exactly
                    // the leftover height so the editor's bottom aligns with the bar/keyboard and
                    // the OS keeps text-selection UI inside this region. Empty space below the text
                    // is part of the editor View, so tapping it already focuses the editor.
                    EditorBody(
                        hasTable = false,
                        blocks = blocks,
                        initialHtml = initialHtml,
                        content = state.content,
                        converter = converter,
                        onContentChange = onContentChange,
                        onActionsReady = { actions = wrapActions(it) },
                        onBlockFocused = { lastFocusedBlock = it },
                        onActiveFormatsChanged = { activeFormats = it },
                        editable = !state.isReadOnly,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(
                                // Keyboard hidden: keep the editor above the nav bar. Keyboard up:
                                // the format bar already sits above the IME, so no extra inset.
                                if (!imeVisible) {
                                    Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 20.dp),
                    )
                }
            }
        }

        // Tag picker popup — drawn on top of everything.
        TagPickerPopup(
            visible = tagPickerVisible,
            availableTags = state.availableTags,
            onAddTag = { onAddTag(it); tagPickerVisible = false },
            onDismiss = { tagPickerVisible = false },
            hazeState = hazeState,
        )

        // Members management sheet (shared notes).
        if (state.membersSheetVisible && state.myRole != null) {
            MembersSheet(
                members = state.members,
                myRole = state.myRole,
                currentUserId = state.currentUserId?.let { app.tessera.domain.model.UserId(it) },
                inviteState = state.inviteState,
                onChangeRole = onChangeRole,
                onRemoveMember = onRemoveMember,
                onInvite = onInvite,
                onInviteInputChanged = onInviteInputChanged,
                onDismiss = onDismissMembers,
            )
        }

        if (leaveConfirmVisible) {
            ConfirmDialog(
                title = stringResource(R.string.leave_confirm_title),
                message = stringResource(R.string.leave_confirm_message),
                confirmLabel = stringResource(R.string.leave_confirm_action),
                destructive = true,
                onConfirm = { leaveConfirmVisible = false; onLeave() },
                onDismiss = { leaveConfirmVisible = false },
            )
        }

        if (deleteForAllConfirmVisible) {
            ConfirmDialog(
                title = stringResource(R.string.delete_all_confirm_title),
                message = stringResource(R.string.delete_all_confirm_message),
                confirmLabel = stringResource(R.string.delete_all_confirm_action),
                destructive = true,
                onConfirm = { deleteForAllConfirmVisible = false; onDeleteForAll() },
                onDismiss = { deleteForAllConfirmVisible = false },
            )
        }

        if (unshareConfirmVisible) {
            ConfirmDialog(
                title = stringResource(R.string.unshare_confirm_title),
                message = stringResource(R.string.unshare_confirm_message),
                confirmLabel = stringResource(R.string.unshare_confirm_action),
                destructive = true,
                onConfirm = { unshareConfirmVisible = false; onUnshare() },
                onDismiss = { unshareConfirmVisible = false },
            )
        }

        // Surfaces a failed share action (e.g. denied by security rules, or offline) so the
        // remote-first share doesn't silently do nothing.
        val shareError = state.shareError
        if (shareError != null) {
            AlertDialog(
                onDismissRequest = onClearShareError,
                title = { Text(stringResource(R.string.share_failed_title)) },
                text = {
                    Text(
                        shareError.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.share_failed_message),
                    )
                },
                confirmButton = {
                    TextButton(onClick = onClearShareError) {
                        Text(stringResource(R.string.share_failed_ok))
                    }
                },
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.share_cancel)) }
        },
    )
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
    onActiveFormatsChanged: (Set<AztecTextFormat>) -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = true,
) {
    if (!hasTable) {
        // FAST PATH — single editor that fills the viewport and scrolls internally. Keeping the
        // editor bounded (rather than one giant wrap-content View inside the outer scroll) keeps
        // the OS text-selection toolbar/handles within the visible editor instead of spilling
        // over the top bar or the keyboard.
        RichNoteBody(
            html = initialHtml,
            markdownKey = content,
            htmlToMarkdown = converter::htmlToMarkdown,
            onMarkdownChange = { markdown -> if (markdown != content) onContentChange(markdown) },
            onActionsReady = onActionsReady,
            onFocusChanged = { if (it) onBlockFocused(0) },
            onActiveFormatsChanged = onActiveFormatsChanged,
            editable = editable,
            scrollable = true,
            modifier = modifier.fillMaxWidth(),
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
                        onActiveFormatsChanged = onActiveFormatsChanged,
                        editable = editable,
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
