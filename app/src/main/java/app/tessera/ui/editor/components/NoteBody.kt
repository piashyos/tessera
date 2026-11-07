package app.tessera.ui.editor.components

import android.content.Context
import android.net.Uri
import android.graphics.Typeface
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.tessera.R
import app.tessera.core.text.notedoc.ListType
import app.tessera.core.text.notedoc.Mark
import app.tessera.core.text.notedoc.ParagraphKind
import app.tessera.core.ui.theme.TesseraSpacing
import app.tessera.domain.model.parseImageRef
import app.tessera.ui.editor.engine.ImageSelection
import app.tessera.ui.editor.engine.ObjectImageSource
import app.tessera.ui.editor.engine.EditorStyle
import app.tessera.ui.editor.engine.FormatState
import app.tessera.ui.editor.engine.TesseraEditText
import kotlinx.coroutines.launch
import org.wordpress.aztec.AztecTextFormat

/**
 * Compose host of the native editor ([TesseraEditText], spec §7): the same contract as [RichNoteBody] on its fast path. It always
 * fills the area it is given and scrolls itself; the caret is lifted above the keyboard like `RichNoteBody` does ([caretRectOf]).
 *
 * `markdown` is the note as the screen knows it: every new value goes to `setMarkdown`, which drops the echo of our own emit and
 * skips an update while a user edit is still pending (§6.9). The engine's output (`onMarkdownChange`) is debounced there. The
 * `editable = false` mode is a viewer of a shared note.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteBody(
    markdown: String,
    onMarkdownChange: (String) -> Unit,
    onActionsReady: (RichBodyActions) -> Unit,
    onFormatStateChanged: (FormatState) -> Unit,
    editable: Boolean,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    /** Called on ON_STOP right after a pending edit was handed out, so the host can save at once (the app may be killed in the background). */
    onStopFlushed: () -> Unit = {},
    /** Where the pictures of the images come from (the ViewModel's); without it an image is a tile. */
    imageSource: ObjectImageSource? = null,
    /** An image from the keyboard or the clipboard (a file in the app's cache); `true` when the screen takes it. */
    onImageCommitted: (Uri) -> Boolean = { false },
    /** While a cell of a table has the focus: what the contextual table bar shows and does; `null` when none has it. */
    onTableBarChanged: (TableBarState?) -> Unit = {},
) {
    if (LocalInspectionMode.current) return // the View cannot be instantiated in a Compose preview

    val style = rememberEditorStyle()
    val inter = rememberTypeface(R.font.inter_variable)
    val selectionHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f).toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val accentArgb = MaterialTheme.colorScheme.primary.toArgb()
    val density = LocalDensity.current
    val lineSpacingExtraPx = with(density) { 4.dp.toPx() } // as RichNoteBody: extra leading on top of the font's natural height
    val padTop = with(density) { TesseraSpacing.sm.roundToPx() }
    val padBottom = with(density) { TesseraSpacing.lg.roundToPx() }
    val hint = stringResource(R.string.editor_body_hint)

    val currentOnMarkdownChange by rememberUpdatedState(onMarkdownChange)
    val currentOnFormatStateChanged by rememberUpdatedState(onFormatStateChanged)
    val currentOnFocusChanged by rememberUpdatedState(onFocusChanged)
    val currentOnImageCommitted by rememberUpdatedState(onImageCommitted)
    val currentOnTableBarChanged by rememberUpdatedState(onTableBarChanged)
    val overlayTheme = OverlayTheme(MaterialTheme.colorScheme, MaterialTheme.typography, MaterialTheme.shapes)
    val hostRef = remember { mutableStateOf<TableOverlayHost?>(null) }
    val imageSelection = remember { mutableStateOf<ImageSelection?>(null) }
    val editorRef = remember { mutableStateOf<TesseraEditText?>(null) }

    // The caret and the focus survive a re-creation of the host (rotation, process death): read from the live editor at the
    // moment the state is saved, put back into the new one below.
    val caret = rememberSaveable(
        saver = Saver<CaretSnapshot, List<Any>>(
            save = { old -> editorRef.value?.let { listOf(it.selectionStart, it.selectionEnd, it.isFocused) } ?: listOf(old.start, old.end, old.focused) },
            restore = { CaretSnapshot(it[0] as Int, it[1] as Int, it[2] as Boolean) },
        ),
    ) { CaretSnapshot(-1, -1, false) }

    // Keeps the caret line above the keyboard: the IME inset is read here so only this subtree recomposes while it animates.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val caretScope = rememberCoroutineScope()
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    LaunchedEffect(imeBottomPx) {
        if (imeBottomPx <= 0) return@LaunchedEffect
        val editor = editorRef.value ?: return@LaunchedEffect
        if (!editor.isFocused) return@LaunchedEffect
        val rect = caretRectOf(editor) ?: return@LaunchedEffect
        runCatching { bringIntoViewRequester.bringIntoView(rect) }
    }

    val currentOnStopFlushed by rememberUpdatedState(onStopFlushed)
    OnStop {
        hostRef.value?.commitCells() // a cell edit that waits for its pause is part of the note
        editorRef.value?.flushPendingEdit() // the debounced emit would not survive a kill
        currentOnStopFlushed()
    }

    DisposableEffect(Unit) {
        onDispose { hostRef.value?.commitCells(); editorRef.value?.dispose() } // emits a pending user edit, nothing else
    }

    Box(modifier) {
    AndroidView(
        modifier = Modifier.fillMaxSize().bringIntoViewRequester(bringIntoViewRequester),
        factory = { context ->
            // The Material3 XML theme is applied explicitly: the host context is not guaranteed to carry it
            // (Compose test rules use a bare ComponentActivity).
            val themed = ContextThemeWrapper(context, R.style.Theme_Tessera)
            val editor = TesseraEditText(themed, style)
            val host = TableOverlayHost(themed, editor)
            host.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            host.theme = overlayTheme
            host.onTableBar = { bar -> currentOnTableBarChanged(bar) }
            hostRef.value = host
            editor.isVerticalScrollBarEnabled = true
            editor.isVerticalFadingEdgeEnabled = false
            // The host's explicit padding (the editTextStyle one stays after `background = null`); the screen pads the sides.
            editor.setPadding(0, padTop, 0, padBottom)
            editor.includeFontPadding = false
            editor.setLineSpacing(lineSpacingExtraPx, 1.0f)
            inter?.let { editor.typeface = it }
            editor.highlightColor = selectionHighlight
            editor.hint = hint
            editor.setHintTextColor(hintColor)
            applyCursorAndHandleTint(editor, accentArgb)
            // Gaining the focus moves the caret to the end (TextView), also the focus that is given back after a re-creation:
            // the saved selection is put over that once, right after it.
            var restoreCaret = caret.start >= 0 && caret.focused
            fun putCaretBack() {
                val len = editor.length()
                editor.setSelection(caret.start.coerceIn(0, len), caret.end.coerceIn(0, len))
            }
            editor.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus && restoreCaret) {
                    restoreCaret = false
                    editor.post { putCaretBack() }
                }
                currentOnFocusChanged(hasFocus)
            }
            editor.onMarkdownChange = { md -> currentOnMarkdownChange(md) }
            editor.onFormatStateChanged = { fs -> currentOnFormatStateChanged(fs) }
            editor.onImageCommitted = { uri -> currentOnImageCommitted(uri) }
            editor.onImageSelectionChanged = { imageSelection.value = it }
            editor.imageSource = imageSource
            editor.editable = editable
            editor.setMarkdown(markdown)
            if (caret.start >= 0) {
                putCaretBack()
                if (caret.focused && editable) {
                    editor.requestFocus()
                    editor.post {
                        val imm = editor.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }
            // Typing moves the caret: pull its line above the keyboard (posted: the layout must reflect the edit first).
            editor.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    editor.post {
                        if (!editor.isFocused) return@post
                        val rect = caretRectOf(editor) ?: return@post
                        caretScope.launch { runCatching { bringIntoViewRequester.bringIntoView(rect) } }
                    }
                }
            })
            editorRef.value = editor
            onActionsReady(NoteBodyActions(editor, host::commitCells))
            host
        },
        update = { host ->
            val editor = host.editor
            host.theme = overlayTheme
            if (editor.editorStyle != style) editor.editorStyle = style
            if (editor.editable != editable) editor.editable = editable
            if (editor.imageSource !== imageSource) editor.imageSource = imageSource
            editor.setMarkdown(markdown)
        },
    )
    imageSelection.value?.let { sel ->
        if (editable) ImageHandles(sel, editorRef.value)
    }
    }
}

/**
 * The handles of the selected image over the editor (spec §3.3): a drag on a corner shows the image at the dragged width at once (the editor
 * scales its picture) and one history step is made when the finger leaves; the rotate button turns it by 90° at once. The handles follow the
 * image: the editor tells where it is after every draw.
 */
@Composable
internal fun ImageHandles(sel: ImageSelection, editor: TesseraEditText?) {
    val ref = parseImageRef(sel.src) ?: return
    // what the drag began with: the percent, and the width the image has at 100% (its own width follows the preview)
    val drag = remember(sel.ps, sel.src) { IntArray(3) { -1 } } // start percent, width at 100%, last percent
    ImageSelectionOverlay(
        rect = Rect(sel.rect.left.toFloat(), sel.rect.top.toFloat(), sel.rect.right.toFloat(), sel.rect.bottom.toFloat()),
        onResizeDelta = { delta ->
            if (drag[0] < 0) { drag[0] = ref.wPercent; drag[1] = (sel.rect.width() * 100f / ref.wPercent).toInt().coerceAtLeast(1) }
            drag[2] = dragToWidthPercent(drag[0], delta, drag[1])
            editor?.previewImageWidth(sel.ps, drag[2])
        },
        onResizeCommit = {
            val percent = drag[2]
            val start = drag[0]
            drag.fill(-1)
            if (editor != null && start >= 0) {
                if (percent in 10..100 && percent != start) editor.replaceImageSrc(sel.ps, withWidthPercent(sel.src, percent), expected = sel.src)
                else editor.previewImageWidth(sel.ps, null)
            }
        },
        onRotate = { editor?.replaceImageSrc(sel.ps, withRotation(sel.src, (ref.rotDegrees + 90) % 360), expected = sel.src) },
        resizeContentDescription = stringResource(R.string.image_resize_handle_description),
        rotateContentDescription = stringResource(R.string.image_rotate_button_description),
    )
}

/** Where the caret was and whether the editor had the focus when the host was saved. */
private class CaretSnapshot(val start: Int, val end: Int, val focused: Boolean)

/** The toolbar's commands over the native engine (spec §7): the `RichBodyActions` mapping. */
internal class NoteBodyActions(private val editor: TesseraEditText, private val commitCells: () -> Unit = {}) : RichBodyActions {
    override fun toggleBold() = editor.toggleMark(Mark.Bold)
    override fun toggleItalic() = editor.toggleMark(Mark.Italic)
    override fun toggleUnderline() = editor.toggleMark(Mark.Underline)
    override fun toggleStrikethrough() = editor.toggleMark(Mark.Strike)

    override fun toggleParagraph() = editor.toggleKind(ParagraphKind.Body)
    override fun toggleHeading1() = editor.toggleKind(ParagraphKind.Title)
    override fun toggleHeading2() = editor.toggleKind(ParagraphKind.Heading)
    override fun toggleHeading3() = editor.toggleKind(ParagraphKind.Subheading)
    override fun toggleHeading4() = editor.toggleKind(ParagraphKind.Subheading)
    override fun toggleHeading5() = editor.toggleKind(ParagraphKind.Subheading)
    override fun toggleHeading6() = editor.toggleKind(ParagraphKind.Subheading)
    override fun toggleSubheading() = editor.toggleKind(ParagraphKind.Subheading)

    override fun toggleBulletList() = editor.toggleKind(ParagraphKind.ListItem(ListType.Bullet, 0))
    override fun toggleDashList() = editor.toggleKind(ParagraphKind.ListItem(ListType.Dash, 0))
    override fun toggleOrderedList() = editor.toggleKind(ParagraphKind.ListItem(ListType.Number, 0))
    override fun toggleTaskList() = editor.toggleKind(ParagraphKind.ListItem(ListType.Check, 0))
    override fun toggleQuote() = editor.toggleKind(ParagraphKind.Quote)
    override fun toggleCode() = editor.toggleKind(ParagraphKind.Mono)

    override fun indent() = editor.indent()
    override fun outdent() = editor.outdent()
    override fun insertHorizontalRule() = editor.insertHorizontalRule()
    override fun insertTable() = editor.insertTable()
    override fun insertImage(imageId: String) = editor.insertImage(imageId)
    override fun undo() { commitCells(); editor.undo() }
    override fun redo() { commitCells(); editor.redo() }

    override fun focusBody() {
        // A viewer's editor: never grab focus or surface the keyboard for it.
        if (!editor.editable) return
        editor.requestFocus()
        editor.setSelection(editor.length())
        val imm = editor.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
    }

    /** Aztec formats do not exist here; the bar reads [formatState] when `nativeEngine = true`. */
    override fun activeFormats(): Set<AztecTextFormat> = emptySet()

    override fun formatState(): FormatState = editor.formatState()

    override fun flushPendingEdit() { commitCells(); editor.flushPendingEdit() }
}

/**
 * The caret line in the editor's own coordinates (what the AndroidView node shows), padding and scroll included, for the Compose
 * bring-into-view machinery. `RichNoteBody` measures the same way; it never has a scrolled editor, this one does.
 */
private fun caretRectOf(editor: TesseraEditText): Rect? {
    val layout = editor.layout ?: return null
    val offset = editor.selectionStart.coerceIn(0, editor.length())
    val line = layout.getLineForOffset(offset)
    val x = layout.getPrimaryHorizontal(offset) + editor.totalPaddingLeft - editor.scrollX
    val top = (layout.getLineTop(line) + editor.totalPaddingTop - editor.scrollY).toFloat()
    val bottom = (layout.getLineBottom(line) + editor.totalPaddingTop - editor.scrollY).toFloat()
    return Rect(x, top, x + 1f, bottom)
}

@Composable
private fun rememberTypeface(fontRes: Int): Typeface? {
    val context = LocalContext.current
    return remember(context, fontRes) { ResourcesCompat.getFont(context, fontRes) }
}

/**
 * [EditorStyle] from the live theme: colors are `MaterialTheme.colorScheme` roles (the same ones `RichNoteBody` gives the Aztec
 * spans), text sizes the typography (§6.11), geometry the mockup `editor-content-*` and `TesseraSpacing`.
 */
@Composable
internal fun rememberEditorStyle(): EditorStyle {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val serif = rememberTypeface(R.font.playfair_display_regular)
    val imageLabel = stringResource(R.string.editor_object_image)
    val tableLabel = stringResource(R.string.editor_object_table)
    val imageUnavailableLabel = stringResource(R.string.editor_object_image_unavailable)
    return with(density) {
        val corner = MaterialTheme.shapes.medium.topStart.toPx(Size.Zero, density)
        EditorStyle(
            textColor = colors.onSurface.toArgb(),
            mutedTextColor = colors.onSurfaceVariant.toArgb(),
            bodyTextSizePx = MaterialTheme.typography.bodyLarge.fontSize.toPx(),
            titleTextSizePx = 26.sp.toPx(), // mockup .editor-h1 26px; Aztec H1 at the 15sp base
            headingTextSizePx = MaterialTheme.typography.headlineSmall.fontSize.toPx(), // §6.11, as the Aztec H2 (20sp)
            subheadingTextSizePx = MaterialTheme.typography.titleLarge.fontSize.toPx(),
            serifTypeface = serif,
            accentColor = colors.primary.toArgb(),
            checkboxTickColor = colors.onPrimary.toArgb(),
            bulletRadiusPx = 5.dp.toPx(), // as RichNoteBody
            checkboxSizePx = 18.dp.toPx(), // mockup .cb
            checkboxCornerPx = 4.dp.toPx(), // mockup .cb radius, as EditorCheckboxTheming
            checkboxStrokePx = 2.dp.toPx(), // mockup .cb border
            listMarkerAreaPx = (18.dp + 10.dp).toPx(), // mockup: .cb 18 + gap 10
            listLevelStepPx = TesseraSpacing.xxl.toPx(), // not in the mockup
            quoteBarColor = colors.primary.toArgb(),
            quoteTextColor = colors.onSurface.toArgb(),
            quoteFillColor = colors.primary.copy(alpha = 0.08f).toArgb(), // Aztec quoteBackgroundAlpha 8%
            quoteBarWidthPx = 3.dp.toPx(), // mockup .editor-quote border-left
            quoteGapPx = TesseraSpacing.md.toPx(), // mockup .editor-quote padding-left 12
            monoBackgroundColor = colors.surfaceContainerHigh.toArgb(),
            monoTextColor = colors.onSurface.toArgb(),
            monoCornerPx = corner, // mockup .editor-code radius-md 12 = shapes.medium
            monoPaddingHorizontalPx = 14.dp.toPx(), // mockup .editor-code padding 12 14
            monoPaddingVerticalPx = TesseraSpacing.md.toPx(),
            codeBackgroundColor = colors.surfaceContainerHigh.toArgb(), // Aztec codeBackgroundColor
            dividerColor = colors.outlineVariant.toArgb(),
            dividerThicknessPx = 1.dp.toPx(),
            placeholderBorderColor = colors.outlineVariant.toArgb(), // D9
            placeholderTextColor = colors.onSurfaceVariant.toArgb(),
            placeholderCornerPx = corner,
            placeholderPaddingPx = TesseraSpacing.lg.toPx(),
            imageLabel = imageLabel,
            tableLabel = tableLabel,
            imageFillColor = colors.surfaceContainerHigh.toArgb(), // spec §3.1
            imageUnavailableLabel = imageUnavailableLabel,
            imagePlaceholderHeightPx = 160.dp.toPx(), // spec §3.1: the size of a placeholder whose image size is not known
            // Block spacing, spec §6.11 (mockup shared.css .editor-*). 6/10/3 have no TesseraSpacing token.
            titleSpaceTopPx = TesseraSpacing.md.toPx(), // .editor-h1 margin-top 12
            titleSpaceBottomPx = TesseraSpacing.sm.toPx(), // .editor-h1 margin-bottom 8
            headingSpaceTopPx = TesseraSpacing.md.toPx(), // .editor-h2 margin-top 12
            headingSpaceBottomPx = 6.dp.toPx(), // .editor-h2 margin-bottom 6
            blockEdgePx = 10.dp.toPx(), // .editor-checklist/.editor-quote/.editor-code margin 10
            listItemPaddingPx = 3.dp.toPx(), // .editor-checklist li padding 3px 0
            quotePaddingVerticalPx = TesseraSpacing.xs.toPx(), // .editor-quote padding 4
        )
    }
}

/** Runs [block] when the host lifecycle stops (Home, task switch, a screen on top): the last moment a process is sure to be alive. */
@Composable
internal fun OnStop(block: () -> Unit) {
    val current by rememberUpdatedState(block)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) current() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
