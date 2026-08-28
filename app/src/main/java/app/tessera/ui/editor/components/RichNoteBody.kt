package app.tessera.ui.editor.components

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.Spanned
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import app.tessera.R
import app.tessera.core.text.applySerifHeadingSpans
import org.wordpress.aztec.Aztec
import org.wordpress.aztec.AztecAttributes
import org.wordpress.aztec.AztecText
import org.wordpress.aztec.AztecTextFormat
import org.wordpress.aztec.Html
import org.wordpress.aztec.ITextFormat
import org.wordpress.aztec.plugins.IToolbarButton
import org.wordpress.aztec.source.SourceViewEditText
import org.wordpress.aztec.spans.AztecImageSpan
import org.wordpress.aztec.spans.AztecPreformatSpan
import org.wordpress.aztec.spans.AztecTaskListSpan
import org.wordpress.aztec.spans.AztecUnorderedListSpan
import org.wordpress.aztec.toolbar.IAztecToolbar
import org.wordpress.aztec.toolbar.IAztecToolbarClickListener
import kotlinx.coroutines.launch

/** Toolbar-facing actions applied to the AztecText current selection. */
interface RichBodyActions {
    fun toggleBold()
    fun toggleItalic()
    fun toggleStrikethrough()
    fun toggleParagraph()
    fun toggleHeading1()
    fun toggleHeading2()
    fun toggleHeading3()
    fun toggleHeading4()
    fun toggleHeading5()
    fun toggleHeading6()
    fun toggleBulletList()
    fun toggleTaskList()

    // --- Light Aztec formats (Plan 05). Default no-ops so previews / test mocks that
    // implement RichBodyActions without a live editor keep compiling. ---
    /** Toggle underline on the current selection. */
    fun toggleUnderline() {}

    /** Toggle an ordered (numbered) list on the current block(s). */
    fun toggleOrderedList() {}

    /** Toggle a block quote on the current block(s). */
    fun toggleQuote() {}

    /** Toggle a monospace code block (preformatted) on the current block(s). */
    fun toggleCode() {}

    /** Insert a horizontal rule (divider) at the cursor. */
    fun insertHorizontalRule() {}

    /** Undo the last edit via Aztec's history stack. No-op when the stack is empty. */
    fun undo() {}

    /** Redo the last undone edit. No-op when nothing was undone. */
    fun redo() {}

    /** Programmatically focus the editor and surface the IME, placing the cursor at end of text. */
    fun focusBody()

    /**
     * Insert a new empty 2x2 table after the currently-focused text block.
     * Implemented at the screen level — this default is a no-op so previews / tests
     * that mock RichBodyActions without a screen still compile.
     */
    fun insertTable() {}

    /**
     * User intent to attach an image from the gallery. Overridden at the screen level to launch
     * the photo picker; the editor object itself leaves it a no-op.
     */
    fun onInsertImage() {}

    /**
     * User intent to capture an image with the camera. Overridden at the screen level to request
     * the CAMERA permission (when needed) and launch capture; a no-op on the editor object.
     */
    fun onCaptureImage() {}

    /**
     * Splice a Drive-backed image span (`tessera-img://<imageId>`) into the editor AT THE CURRENT
     * CARET, resolving the just-stored local file to a drawable. Called by the screen after
     * [InsertImageUseCase] has stored the image and produced its [imageId]. Default no-op for
     * previews / mocks without a live editor.
     */
    fun insertImage(imageId: String) {}

    /**
     * Formats currently applied at the editor's selection / cursor position.
     * The toolbar dropdown reads this to highlight active entries.
     */
    fun activeFormats(): Set<AztecTextFormat>
}

private const val DebounceMs = 300L
private const val SerifReapplyMs = 50L

/** Fallback display width for an inserted image before the editor has been measured. */
private const val DefaultImageWidthPx = 1080

/**
 * A currently-selected image span the resize/rotate overlay tracks. [spanStart] is the offset of
 * the span in the editor's editable; [source] is its `tessera-img://…?w=..&rot=..` src (raw `&`);
 * [rect] is its on-screen rectangle in the AndroidView's visible coordinate space; [wPercent] and
 * [rotDegrees] are the parsed display params.
 */
data class SelectedImage(
    val spanStart: Int,
    val source: String,
    val rect: Rect,
    val wPercent: Int,
    val rotDegrees: Int,
)

/**
 * The on-screen rectangle of the [AztecImageSpan] starting at [spanStart], in the AztecText's
 * VISIBLE coordinate space (scroll subtracted), or null if the editor isn't laid out yet or no
 * image span/drawable is present there. Width/height come from the span drawable's (adjusted)
 * bounds — the size Aztec actually renders.
 */
fun imageSpanRect(editor: AztecText, spanStart: Int): Rect? {
    val layout = editor.layout ?: return null
    val editable = editor.editableText ?: return null
    val span = editable.getSpans(spanStart, spanStart + 1, AztecImageSpan::class.java).firstOrNull()
        ?: return null
    val bounds = span.drawable?.bounds ?: return null
    if (bounds.width() <= 0 || bounds.height() <= 0) return null
    val line = layout.getLineForOffset(spanStart)
    val top = (layout.getLineTop(line) + editor.totalPaddingTop - editor.scrollY).toFloat()
    val left = layout.getPrimaryHorizontal(spanStart) + editor.totalPaddingLeft - editor.scrollX
    return Rect(left, top, left + bounds.width(), top + bounds.height())
}

/**
 * Resolve a tap at ([x], [y]) (view coordinates) to the [SelectedImage] it lands on, or null when
 * the tap isn't on an image. `getOffsetForPosition` snaps to the NEAREST offset even for a tap in
 * the whitespace beside/below an image, so the tap is re-validated against the span's actual rect
 * before selecting — a near-miss returns null (clears the selection).
 */
fun selectImageAt(editor: AztecText, x: Float, y: Float): SelectedImage? {
    val editable = editor.editableText ?: return null
    val offset = editor.getOffsetForPosition(x, y)
    if (offset < 0) return null
    val span = editable.getSpans(offset, offset + 1, AztecImageSpan::class.java).firstOrNull()
        ?: editable.getSpans((offset - 1).coerceAtLeast(0), offset, AztecImageSpan::class.java).firstOrNull()
        ?: return null
    val spanStart = editable.getSpanStart(span)
    val rect = imageSpanRect(editor, spanStart) ?: return null
    if (x < rect.left || x > rect.right || y < rect.top || y > rect.bottom) return null
    val src = (span.attributes.getValue("src") ?: "").replace("&amp;", "&")
    val ref = parseImageRef(src)
    return SelectedImage(
        spanStart = spanStart,
        source = src,
        rect = rect,
        wPercent = ref?.wPercent ?: 100,
        rotDegrees = ref?.rotDegrees ?: 0,
    )
}

/** Formats the toolbar highlights as "active"; queried against the editor's selection. */
private val EditorQueryFormats = listOf(
    AztecTextFormat.FORMAT_BOLD,
    AztecTextFormat.FORMAT_ITALIC,
    AztecTextFormat.FORMAT_STRIKETHROUGH,
    AztecTextFormat.FORMAT_PARAGRAPH,
    AztecTextFormat.FORMAT_HEADING_1,
    AztecTextFormat.FORMAT_HEADING_2,
    AztecTextFormat.FORMAT_HEADING_3,
    AztecTextFormat.FORMAT_HEADING_4,
    AztecTextFormat.FORMAT_HEADING_5,
    AztecTextFormat.FORMAT_HEADING_6,
    AztecTextFormat.FORMAT_UNORDERED_LIST,
    AztecTextFormat.FORMAT_TASK_LIST,
    AztecTextFormat.FORMAT_UNDERLINE,
    AztecTextFormat.FORMAT_ORDERED_LIST,
    AztecTextFormat.FORMAT_QUOTE,
    AztecTextFormat.FORMAT_CODE,
    AztecTextFormat.FORMAT_PREFORMAT,
)

/**
 * Call `refresh()` on every `AztecTaskListSpan` currently attached to the editor's
 * editable. Aztec's stock `fromHtml` installs the task-list span but the leading
 * `verticalPadding` from `AztecListSpan.chooseHeight` is only applied *after* the
 * span gets replaced via `refresh()` — which normally only happens on the first
 * checkbox tap. Triggering it once at load time stabilises line metrics so toggling
 * later doesn't visibly resize the line.
 */
/**
 * Caret line rectangle in the editor's local (and therefore the AndroidView node's) coordinate
 * space, padding-corrected to where the line is actually drawn. Returned to the Compose
 * bring-into-view machinery so the host verticalScroll can pull the line above the keyboard.
 */
private fun caretRectOf(editor: AztecText): Rect? {
    val layout = editor.layout ?: return null
    val offset = editor.selectionStart.coerceIn(0, editor.length())
    val line = layout.getLineForOffset(offset)
    val x = layout.getPrimaryHorizontal(offset)
    val top = (layout.getLineTop(line) + editor.paddingTop).toFloat()
    val bottom = (layout.getLineBottom(line) + editor.paddingTop).toFloat()
    return Rect(x, top, x + 1f, bottom)
}

/**
 * Swap Aztec's stock [AztecPreformatSpan] (which rounds only the first line's top corners,
 * leaving single-line code blocks square at the bottom) for [TesseraPreformatSpan], which
 * paints a fully rounded background. Run after every (re)load and after toggling code so a
 * freshly-created block renders correctly.
 */
private fun replacePreformatSpans(editor: AztecText, bgArgb: Int, textArgb: Int) {
    val editable = editor.editableText ?: return
    val spans = editable.getSpans(0, editable.length, AztecPreformatSpan::class.java)
    spans.forEach { span ->
        val start = editable.getSpanStart(span)
        val end = editable.getSpanEnd(span)
        val flags = editable.getSpanFlags(span)
        if (start < 0 || end < 0) return@forEach
        // Re-style with the live theme palette (the XML attrs bake in the static brand, which the
        // AndroidView's Activity-themed context can't override with the Compose palette).
        val themedStyle = span.preformatStyle.copy(
            preformatBackground = bgArgb,
            preformatColor = textArgb,
        )
        editable.removeSpan(span)
        val replacement = TesseraPreformatSpan(
            nestingLevel = span.nestingLevel,
            attributes = span.attributes,
            preformatStyle = themedStyle,
        )
        editable.setSpan(replacement, start, end, flags)
    }
}

/**
 * Re-style block quotes with the active theme palette (bar/text/fill). Like the preformat and
 * bullet re-styling, this overrides the static brand colors baked into the layout XML so the
 * editor follows the chosen color palette.
 */
private fun replaceQuoteSpans(editor: AztecText, barArgb: Int, textArgb: Int, fillArgb: Int) {
    val editable = editor.editableText ?: return
    val spans = editable.getSpans(0, editable.length, org.wordpress.aztec.spans.AztecQuoteSpan::class.java)
    spans.forEach { span ->
        val start = editable.getSpanStart(span)
        val end = editable.getSpanEnd(span)
        val flags = editable.getSpanFlags(span)
        if (start < 0 || end < 0) return@forEach
        val themedStyle = span.quoteStyle.copy(
            quoteColor = barArgb,
            quoteTextColor = textArgb,
            quoteBackground = fillArgb,
        )
        editable.removeSpan(span)
        val replacement = org.wordpress.aztec.spans.AztecQuoteSpan(
            nestingLevel = span.nestingLevel,
            attributes = span.attributes,
            quoteStyle = themedStyle,
        )
        editable.setSpan(replacement, start, end, flags)
    }
}

/**
 * True while the IME holds an active composing region (the underlined, not-yet-committed word).
 * Android marks that region with a span carrying the [Spanned.SPAN_COMPOSING] flag. We use this to
 * avoid reflowing the serif heading overlay mid-composition, which can surface a half-laid-out
 * frame on the fast release render pipeline.
 */
private fun hasComposingRegion(spannable: Spannable): Boolean =
    spannable.getSpans(0, spannable.length, Any::class.java)
        .any { (spannable.getSpanFlags(it) and Spanned.SPAN_COMPOSING) != 0 }

private fun primeTaskListSpans(editor: AztecText) {
    val editable = editor.editableText ?: return
    val spans = editable.getSpans(0, editable.length, AztecTaskListSpan::class.java)
    // Prime line metrics only. The checkbox glyph is a drawable resource
    // (ic_checkbox_checked/_unchecked, brand-colored + day/night) — it can't follow the Compose
    // palette because Aztec loads it with a null theme and recreates the span on every tap.
    spans.forEach { it.refresh() }
}

/**
 * Swap Aztec's ordered-list spans for [TesseraOrderedListSpan] (numbers in Inter Medium) and
 * re-style the marker color from the live palette (the "1." markers paint with
 * listStyle.indicatorColor, which the XML bakes to the static brand).
 */
private fun replaceOrderedListSpans(editor: AztecText, indicatorArgb: Int) {
    val editable = editor.editableText ?: return
    val spans = editable.getSpans(0, editable.length, org.wordpress.aztec.spans.AztecOrderedListSpan::class.java)
    spans.forEach { span ->
        val start = editable.getSpanStart(span)
        val end = editable.getSpanEnd(span)
        val flags = editable.getSpanFlags(span)
        if (start < 0 || end < 0) return@forEach
        val themedStyle = span.listStyle.copy(indicatorColor = indicatorArgb)
        editable.removeSpan(span)
        editable.setSpan(
            TesseraOrderedListSpan(span.nestingLevel, span.attributes, themedStyle),
            start,
            end,
            flags,
        )
    }
}

/**
 * Replace Aztec's default unordered-list spans (which draw the bullet as a hardcoded
 * "•" glyph at the editor's font size) with our [TesseraBulletListSpan] that paints a
 * filled circle of the requested radius — lets us size the dot independently of the
 * surrounding text.
 */
/**
 * Tint the caret and the three selection handles with the live theme accent. These drawables
 * resolve from the STATIC XML theme (Theme.Tessera maps colorPrimary to the fixed terracotta
 * brand), so without this they ignore dynamic color / palette switches. API 29+ exposes the
 * drawables directly; on 26–28 the platform offers no public hook, so the static tint stays.
 */
private fun applyCursorAndHandleTint(editor: AztecText, accentArgb: Int) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
    // The getters may return fresh instances loaded from theme attrs — mutate, tint, and hand
    // the drawable BACK through the setter so the Editor actually swaps what it renders.
    editor.textCursorDrawable = editor.textCursorDrawable?.mutate()?.apply { setTint(accentArgb) }
    editor.textSelectHandle?.let { editor.setTextSelectHandle(it.mutate().apply { setTint(accentArgb) }) }
    editor.textSelectHandleLeft?.let { editor.setTextSelectHandleLeft(it.mutate().apply { setTint(accentArgb) }) }
    editor.textSelectHandleRight?.let { editor.setTextSelectHandleRight(it.mutate().apply { setTint(accentArgb) }) }
}

private fun replaceBulletSpans(editor: AztecText, bulletRadiusPx: Float, bulletArgb: Int) {
    val editable = editor.editableText ?: return
    val spans = editable.getSpans(0, editable.length, AztecUnorderedListSpan::class.java)
    spans.forEach { span ->
        val start = editable.getSpanStart(span)
        val end = editable.getSpanEnd(span)
        val flags = editable.getSpanFlags(span)
        if (start < 0 || end < 0) return@forEach
        // Re-style the bullet with the live palette color (XML bakes in the static brand).
        val themedStyle = span.listStyle.copy(indicatorColor = bulletArgb)
        editable.removeSpan(span)
        val replacement = TesseraBulletListSpan(
            nestingLevel = span.nestingLevel,
            attributes = span.attributes,
            listStyle = themedStyle,
            bulletRadiusPx = bulletRadiusPx,
        )
        editable.setSpan(replacement, start, end, flags)
    }
}

/**
 * No-op IAztecToolbar — Aztec.Factory.with requires a toolbar reference to install the
 * History plugin and wire IME callbacks, but we drive formatting from the Compose
 * MarkdownToolbar via RichBodyActions, so the native toolbar must not surface any UI.
 */
private object NoOpAztecToolbar : IAztecToolbar {
    override fun onKeyUp(keyCode: Int, keyEvent: KeyEvent): Boolean = false
    override fun addButton(buttonPlugin: IToolbarButton) = Unit
    override fun setEditor(editor: AztecText?, sourceEditor: SourceViewEditText?) = Unit
    override fun setToolbarListener(listener: IAztecToolbarClickListener) = Unit
    override fun toggleMediaToolbar() = Unit
    override fun toggleEditorMode() = Unit
}

private object NoOpAztecToolbarClickListener : IAztecToolbarClickListener {
    override fun onToolbarCollapseButtonClicked() = Unit
    override fun onToolbarExpandButtonClicked() = Unit
    override fun onToolbarFormatButtonClicked(format: ITextFormat, isKeyboardShortcut: Boolean) = Unit
    override fun onToolbarHeadingButtonClicked() = Unit
    override fun onToolbarHtmlButtonClicked() = Unit
    override fun onToolbarListButtonClicked() = Unit
    override fun onToolbarMediaButtonClicked(): Boolean = false
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RichNoteBody(
    html: String,
    markdownKey: String,
    htmlToMarkdown: (String) -> String,
    onMarkdownChange: (String) -> Unit,
    onActionsReady: (RichBodyActions) -> Unit,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    onActiveFormatsChanged: (Set<AztecTextFormat>) -> Unit = {},
    editable: Boolean = true,
    /**
     * Renders `tessera-img://<id>` image spans (from `fromHtml` and the insert-at-caret path).
     * Null in previews / tests without a live loader — image sources then fail to load, which is
     * fine for a text-only editor.
     */
    imageGetter: Html.ImageGetter? = null,
    /**
     * Routes an image that arrives via the IME rich-content path or an image clipboard paste
     * to the same insert pipeline the picker/camera use. Returns true when it will be handled.
     */
    onImageCommitted: (Uri) -> Boolean = { false },
    /**
     * When true the editor fills its (bounded) height and scrolls internally instead of growing to
     * wrap its content. Used by the single-editor fast path so the text-selection toolbar/handles —
     * which Android positions relative to the editor View's bounds — stay within the visible editor
     * region and no longer spill over the top bar or the keyboard. The table path keeps this false
     * because its blocks are laid out inside an outer Compose scroll.
     */
    scrollable: Boolean = false,
) {
    if (LocalInspectionMode.current) {
        // Aztec cannot instantiate in Compose preview — show a placeholder
        Text(
            text = "(Rich editor — preview not available)",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    // Capture theme values before entering the AndroidView lambda (Compose context required)
    val textColor = MaterialTheme.colorScheme.onSurface
    // Themed selection highlight — without it the platform paints the stock teal, which
    // clashes with Tessera's terracotta primary (especially visible because format toggles
    // keep the range selected via withPreservedSelection).
    val selectionHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val density = LocalDensity.current
    val bulletRadiusPx = with(density) { 5.dp.toPx() }
    // Block-element accent colors pulled from the active Material3 palette (the layout XML bakes in
    // the static brand, which the Activity-themed AndroidView can't override with the live palette).
    val codeBgArgb = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
    val codeTextArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val bulletArgb = MaterialTheme.colorScheme.primary.toArgb()
    val checkboxTickArgb = MaterialTheme.colorScheme.onPrimary.toArgb()
    // Task-list checkbox is a drawable Aztec loads with a null theme (static colors only) and its
    // span is recreated on every tap — so it can't be re-styled like the other blocks. Instead we
    // intercept the drawable via a wrapped context and repaint it from this live-updated holder.
    val checkboxPalette = remember { CheckboxPalette() }
    checkboxPalette.boxColor = bulletArgb
    checkboxPalette.tickColor = checkboxTickArgb
    val checkboxCornerPx = with(density) { 4.dp.toPx() }
    val checkboxStrokePx = with(density) { 2.dp.toPx() }
    val quoteBarArgb = MaterialTheme.colorScheme.primary.toArgb()
    val quoteTextArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val quoteFillArgb = MaterialTheme.colorScheme.primary.toArgb()
    val accentArgb = MaterialTheme.colorScheme.primary.toArgb()
    // Editor body size follows the Material theme (bodyLarge) instead of a magic number. The
    // extra leading stays a hand-tuned 4dp: TextView's lineSpacingExtra adds to the font's
    // NATURAL line height (unlike Compose's absolute lineHeight), so deriving it from
    // (lineHeight - fontSize) would double-count the built-in leading.
    val bodyTextSizeSp = MaterialTheme.typography.bodyLarge.fontSize.value
    val lineSpacingExtraPx = with(density) { 4.dp.toPx() }

    // Handler lifted to the composable so DisposableEffect can clear pending debounce
    // runnables when the editor leaves composition (e.g. back-nav during typing).
    val handler = remember { Handler(Looper.getMainLooper()) }
    val editorRef = remember { mutableStateOf<AztecText?>(null) }
    // The image the user tapped, driving the resize/rotate overlay. Editable notes only — a
    // read-only (Viewer) editor never installs the tap detector, so this stays null there.
    val selectedImage = remember { mutableStateOf<SelectedImage?>(null) }
    // Re-apply block-element theming whenever the palette colors change (e.g. the user switches
    // palette in Settings). The factory captures colors once and `update` only re-styles on a
    // content change, so without this a live palette switch would leave the old accent colors.
    LaunchedEffect(
        editorRef.value, bulletArgb, checkboxTickArgb, codeBgArgb, codeTextArgb, quoteBarArgb, quoteTextArgb, quoteFillArgb, accentArgb,
    ) {
        val editor = editorRef.value ?: return@LaunchedEffect
        replaceBulletSpans(editor, bulletRadiusPx, bulletArgb)
        replacePreformatSpans(editor, codeBgArgb, codeTextArgb)
        replaceQuoteSpans(editor, quoteBarArgb, quoteTextArgb, quoteFillArgb)
        replaceOrderedListSpans(editor, bulletArgb)
        applyCursorAndHandleTint(editor, accentArgb)
        // The checkbox drawable reads checkboxPalette live; just force a repaint to pick up changes.
        editor.invalidate()
    }
    // Tracks the markdown most recently *loaded into* the editor (initial load + each
    // post-debounce flush). MUST NOT be a Compose State — writing to it would trigger an
    // additional recomposition that races the slower collectAsState→state.content path,
    // letting `update` see (new lastLoaded, old markdownKey) and clobber the editor.
    // Plain array container = mutable storage without snapshot tracking.
    val lastLoadedMarkdownRef = remember { arrayOf(markdownKey) }
    // Always read the latest callbacks — the factory closure captures only the first
    // value, so the dispose-time flush would otherwise call a stale callback.
    val currentOnMarkdownChange by rememberUpdatedState(onMarkdownChange)
    val currentHtmlToMarkdown by rememberUpdatedState(htmlToMarkdown)
    val currentOnFocusChanged by rememberUpdatedState(onFocusChanged)
    val currentOnActiveFormatsChanged by rememberUpdatedState(onActiveFormatsChanged)
    val currentOnImageCommitted by rememberUpdatedState(onImageCommitted)

    // Keeps the caret line riding above the soft keyboard. The IME inset is read *here* (not in
    // the screen) so only this editor subtree recomposes as the keyboard animates — the top bar
    // stays out of that recomposition path. On each inset change we ask the host verticalScroll
    // to bring the current caret line into view via the Compose-native bring-into-view machinery.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val caretScope = rememberCoroutineScope()
    val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottomPx) {
        if (imeBottomPx <= 0) return@LaunchedEffect
        val editor = editorRef.value ?: return@LaunchedEffect
        if (!editor.isFocused) return@LaunchedEffect
        val rect = caretRectOf(editor) ?: return@LaunchedEffect
        runCatching { bringIntoViewRequester.bringIntoView(rect) }
    }

    DisposableEffect(Unit) {
        onDispose {
            handler.removeCallbacksAndMessages(null)
            // Flush latest content synchronously so checkbox toggles or sub-debounce edits
            // made right before back-nav are not lost when pending runnables are cancelled.
            //
            // Guard: only flush when the editor actually holds unsaved edits (md differs
            // from what we last loaded). Without this, switching layouts — e.g. inserting a
            // table flips the editor from the single fast-path body to the block stack —
            // disposes this editor while it still holds the *pre-insert* HTML, and an
            // unconditional flush would write that stale markdown back over the new
            // content, silently reverting the inserted table.
            editorRef.value?.let { editor ->
                val md = currentHtmlToMarkdown(editor.toHtml())
                if (md != lastLoadedMarkdownRef[0]) {
                    lastLoadedMarkdownRef[0] = md
                    currentOnMarkdownChange(md)
                }
            }
        }
    }

    AndroidView(
        modifier = modifier.bringIntoViewRequester(bringIntoViewRequester),
        factory = { context ->
            var debounceRunnable: Runnable? = null
            var serifRunnable: Runnable? = null

            // Inflate from a context whose Resources swap Aztec's static checkbox drawable for our
            // palette-driven one. Aztec builds task-list spans from this context, so their checkbox
            // resolves to ours; checkboxPalette is read live at draw time.
            // The Material3 XML theme is applied explicitly: the layout resolves ?attr/color* and
            // text-appearance attrs, and the host context is not guaranteed to carry them (Compose
            // test rules use a bare ComponentActivity with the framework default theme).
            val m3Context = android.view.ContextThemeWrapper(context, R.style.Theme_Tessera)
            val themedContext = checkboxThemedContext(m3Context, checkboxPalette, checkboxCornerPx, checkboxStrokePx)
            // cloneInContext binds the inflater (and every view it inflates, incl. the AztecText's
            // own context) to themedContext. LayoutInflater.from(wrapper) alone would delegate
            // getSystemService to the base context and yield a base-bound inflater.
            val inflater = LayoutInflater.from(context).cloneInContext(themedContext)
            val editor = inflater.inflate(R.layout.rich_note_body, null, false) as AztecText
            editor.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                // Fast path fills the viewport and scrolls internally so the OS keeps the
                // selection toolbar/handles inside the visible editor; the block-stack (table)
                // path wraps its content and is scrolled by the outer Compose scroll.
                if (scrollable) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            if (scrollable) {
                // A height-bounded multiline EditText scrolls its own content and auto-keeps the
                // caret visible (incl. when the height shrinks for the keyboard), so the outer
                // BringIntoView machinery isn't needed here.
                editor.isVerticalScrollBarEnabled = true
                editor.setVerticalFadingEdgeEnabled(false)
            }

            // Apply theme-aware styling
            editor.setTextColor(textColor.toArgb())
            editor.highlightColor = selectionHighlight.toArgb()
            editor.textSize = bodyTextSizeSp
            // Inter is the Tessera body face; the variable font ships as inter_variable.
            ResourcesCompat.getFont(context, R.font.inter_variable)?.let { editor.typeface = it }
            editor.setPadding(0, 8, 0, 8)
            editor.includeFontPadding = false
            // 4dp extra leading per spec (≈15sp/24). Keeps wrapped lines from drifting too
            // far apart while giving paragraphs room to breathe.
            editor.setLineSpacing(lineSpacingExtraPx, 1.0f)

            // Wire History + IME callbacks via the Aztec factory (no-op toolbar/listener — the Compose
            // MarkdownToolbar drives formatting through RichBodyActions). Without this call the view
            // renders fromHtml content but ignores key events / cursor input.
            Aztec.Factory.with(
                editor,
                NoOpAztecToolbar,
                NoOpAztecToolbarClickListener,
            )

            // Re-apply the Playfair serif overlay to every H1/H2. Aztec's fromHtml round-trip
            // rebuilds spans, so this must run after every (re)load and after edits that may
            // shift a heading (debounced via the TextWatcher).
            //
            // [skipWhileComposing] guards the typing-driven path: while the IME holds an active
            // composing region (mid-word suggestion on a heading line), re-applying the serif
            // overlay forces a layout reflow that, on the fast release render pipeline, can
            // surface a half-reflowed frame (lines briefly drawn at the wrong heading size). We
            // defer until the composition commits — the afterTextChanged that fires on commit
            // schedules another reapply, which then runs with no composing region. Load and
            // toolbar-toggle callers pass false: there's no composition then, and the overlay
            // must land synchronously.
            fun reapplySerifHeadings(skipWhileComposing: Boolean = false) {
                val spannable = editor.text as? Spannable ?: return
                if (skipWhileComposing && hasComposingRegion(spannable)) return
                applySerifHeadingSpans(spannable, context)
            }

            // Attach the image getter BEFORE fromHtml so `tessera-img://` sources resolve during
            // the initial load (and every subsequent reload / insert).
            imageGetter?.let { editor.imageGetter = it }

            // Load initial HTML AFTER Aztec.with so plugins/history see it as the starting state.
            editor.fromHtml(html)
            // After load, force-refresh every task-list span so its line metrics
            // (`verticalPadding` from AztecListSpan.chooseHeight) are applied identically
            // to the post-tap state — otherwise the first checkbox toggle causes a visible
            // one-time line-height shift when Aztec's `refresh()` reinstalls the span.
            primeTaskListSpans(editor)
            replaceBulletSpans(editor, bulletRadiusPx, bulletArgb)
            replacePreformatSpans(editor, codeBgArgb, codeTextArgb)
            replaceQuoteSpans(editor, quoteBarArgb, quoteTextArgb, quoteFillArgb)
            replaceOrderedListSpans(editor, bulletArgb)
            reapplySerifHeadings()
            editorRef.value = editor

            editor.isFocusable = editable
            editor.isFocusableInTouchMode = editable
            if (!editable) {
                // Read-only (Viewer role): keep the styled content fully rendered and scrollable,
                // but reject all input so the IME never opens and the text can't be mutated.
                editor.keyListener = null
                editor.setTextIsSelectable(false)
                editor.isCursorVisible = false
            }
            // Report focus so the screen knows which text block the cursor is in — the
            // "insert table" action uses this to place the table at the cursor, not the end.
            editor.setOnFocusChangeListener { _, hasFocus -> currentOnFocusChanged(hasFocus) }

            // Tap-to-select an image (editable notes only — a read-only Viewer never gets handles).
            // A GestureDetector observing single-tap-up: it never consumes the event (the touch
            // listener returns false) so caret placement / text selection keep working normally.
            if (editable) {
                val tapDetector = GestureDetector(
                    editor.context,
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapUp(e: MotionEvent): Boolean {
                            selectImageAt(editor, e.x, e.y)?.let { selectedImage.value = it }
                                ?: run { selectedImage.value = null }
                            return false
                        }
                    },
                )
                editor.setOnTouchListener { _, event ->
                    tapDetector.onTouchEvent(event)
                    false
                }
            }

            // Convert the editor's current HTML to markdown and push it upward. Marks the
            // just-flushed markdown as already loaded so the next recomposition (markdownKey
            // will now equal this) doesn't trigger a self-inflicted fromHtml round-trip.
            // Non-state write — no extra recomposition is triggered, which keeps lastLoaded
            // and markdownKey (via state.content) updating in the same Compose frame.
            fun flushHtmlNow() {
                val md = currentHtmlToMarkdown(editor.toHtml())
                lastLoadedMarkdownRef[0] = md
                currentOnMarkdownChange(md)
            }

            fun scheduleHtmlFlush() {
                debounceRunnable?.let { handler.removeCallbacks(it) }
                val runnable = Runnable { flushHtmlNow() }
                debounceRunnable = runnable
                handler.postDelayed(runnable, DebounceMs)
            }

            // Cheap span-only re-apply (no HTML round-trip) so the serif headings follow the
            // heading as the user edits / moves it. 50ms keeps it off the typing hot path.
            fun scheduleSerifReapply() {
                serifRunnable?.let { handler.removeCallbacks(it) }
                val runnable = Runnable { reapplySerifHeadings(skipWhileComposing = true) }
                serifRunnable = runnable
                handler.postDelayed(runnable, SerifReapplyMs)
            }

            // Recompute the formats active at the current selection and push them to the
            // toolbar. Called on selection moves, text changes, and after each toggle so the
            // active-format highlight never goes stale (e.g. an H2 button staying lit after
            // the caret moved to a plain paragraph).
            fun pushActiveFormats() {
                val active = EditorQueryFormats.filterTo(mutableSetOf()) {
                    editor.contains(it, editor.selectionStart, editor.selectionEnd)
                }
                currentOnActiveFormatsChanged(active)
            }

            // Pull the caret line above the keyboard as the user types or moves the cursor. Posted
            // so the text layout reflects the just-applied edit before we measure the caret rect.
            fun bringCaretIntoView() {
                editor.post {
                    if (!editor.isFocused) return@post
                    val rect = caretRectOf(editor) ?: return@post
                    caretScope.launch { runCatching { bringIntoViewRequester.bringIntoView(rect) } }
                }
            }

            editor.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    scheduleHtmlFlush()
                    scheduleSerifReapply()
                    pushActiveFormats()
                    bringCaretIntoView()
                }
            })

            // Task-list checkbox toggles bypass TextWatcher (Aztec only mutates the leading
            // `checked` attribute, no text change). FlickerlessAztecText skips the stock
            // `setText(editable)` relayout — we hook its lightweight-refresh callback to
            // persist the new state without triggering the relayout flicker.
            (editor as? FlickerlessAztecText)?.onLightweightRefresh = { scheduleHtmlFlush() }

            // Keep the toolbar's active-format highlight in sync as the caret moves. The
            // initial fromHtml above fires before this listener is attached, so seed the
            // toolbar once here too.
            (editor as? FlickerlessAztecText)?.onCaretSelectionChanged = { _, _ ->
                pushActiveFormats()
                bringCaretIntoView()
            }
            // Images arriving from the keyboard's rich-content commit or an image/* clipboard
            // paste flow through the same VM.insertImage pipeline as the picker/camera.
            (editor as? FlickerlessAztecText)?.onImageCommitted = { uri -> currentOnImageCommitted(uri) }
            pushActiveFormats()

            // Expose formatting actions to the toolbar
            onActionsReady(object : RichBodyActions {
                private inline fun withPreservedSelection(rebuildLayout: Boolean = false, block: () -> Unit) {
                    val start = editor.selectionStart
                    val end = editor.selectionEnd
                    block()
                    // Block-level toggles (heading/list/quote/code/paragraph) change line height.
                    // The lightweight refresh Aztec runs on toggle leaves the lines below at stale
                    // Y positions (they render overlapping), so force a full layout rebuild here.
                    // Inline toggles don't change metrics and skip this to stay flicker-free.
                    if (rebuildLayout) (editor as? FlickerlessAztecText)?.rebuildLayout()
                    if (start in 0..editor.editableText.length &&
                        end in 0..editor.editableText.length &&
                        start != end
                    ) {
                        editor.setSelection(start, end)
                    }
                    // Format toggles change spans without moving the caret or changing text,
                    // so neither the selection nor the TextWatcher fires. That also means
                    // scheduleSerifReapply() (driven by afterTextChanged) never runs, so a
                    // heading toggled via the toolbar would keep Aztec's native heading metrics
                    // instead of the clamped serif H1 — and, worse, a re-toggle would stack the
                    // multiplicative AztecHeadingSpan on a stale absolute size span, blowing the
                    // line up far past 26sp. Re-sync the serif overlay synchronously here so the
                    // first H1 always renders at the intended size/typeface and removing a heading
                    // strips its size span. Cheap span-only op (no HTML round-trip).
                    reapplySerifHeadings()
                    pushActiveFormats()
                    flushHtmlNow()
                }

                // Aztec's BlockFormatter.toggleHeading is a no-op when the line already has
                // the requested heading (it only acts when !containsHeadingOnly and has no
                // "remove" branch). So tapping H1/H2 a second time would never revert the
                // heading. Make it a real toggle: when the heading is already active at the
                // selection, switch the block back to a paragraph (the only path Aztec
                // exposes for removing a heading span).
                private fun toggleHeadingFormat(format: AztecTextFormat) = withPreservedSelection(rebuildLayout = true) {
                    if (editor.contains(format, editor.selectionStart, editor.selectionEnd)) {
                        editor.toggleFormatting(AztecTextFormat.FORMAT_PARAGRAPH)
                    } else {
                        editor.toggleFormatting(format)
                    }
                }

                override fun undo() {
                    if (!editor.history.undoValid()) return
                    editor.history.undo(editor)
                    // History restores raw text; re-sync the serif overlay + toolbar and persist,
                    // same as a toolbar toggle (the TextWatcher's debounce alone would lag).
                    reapplySerifHeadings()
                    pushActiveFormats()
                    flushHtmlNow()
                }

                override fun redo() {
                    if (!editor.history.redoValid()) return
                    editor.history.redo(editor)
                    reapplySerifHeadings()
                    pushActiveFormats()
                    flushHtmlNow()
                }

                override fun toggleBold() = withPreservedSelection { editor.toggleFormatting(AztecTextFormat.FORMAT_BOLD) }
                override fun toggleItalic() = withPreservedSelection { editor.toggleFormatting(AztecTextFormat.FORMAT_ITALIC) }
                override fun toggleStrikethrough() = withPreservedSelection { editor.toggleFormatting(AztecTextFormat.FORMAT_STRIKETHROUGH) }
                override fun toggleParagraph() = withPreservedSelection(rebuildLayout = true) { editor.toggleFormatting(AztecTextFormat.FORMAT_PARAGRAPH) }
                override fun toggleHeading1() = toggleHeadingFormat(AztecTextFormat.FORMAT_HEADING_1)
                override fun toggleHeading2() = toggleHeadingFormat(AztecTextFormat.FORMAT_HEADING_2)
                override fun toggleHeading3() = toggleHeadingFormat(AztecTextFormat.FORMAT_HEADING_3)
                override fun toggleHeading4() = toggleHeadingFormat(AztecTextFormat.FORMAT_HEADING_4)
                override fun toggleHeading5() = toggleHeadingFormat(AztecTextFormat.FORMAT_HEADING_5)
                override fun toggleHeading6() = toggleHeadingFormat(AztecTextFormat.FORMAT_HEADING_6)
                override fun toggleBulletList() = withPreservedSelection(rebuildLayout = true) {
                    editor.toggleFormatting(AztecTextFormat.FORMAT_UNORDERED_LIST)
                    // Aztec creates a fresh AztecUnorderedListSpan when toggling — swap it
                    // for our custom span so the larger circle bullet keeps rendering.
                    replaceBulletSpans(editor, bulletRadiusPx, bulletArgb)
                }
                override fun toggleTaskList() = withPreservedSelection(rebuildLayout = true) {
                    editor.toggleFormatting(AztecTextFormat.FORMAT_TASK_LIST)
                    // Prime the freshly-created task-list span so its leading verticalPadding
                    // is applied immediately — otherwise the first checkbox tap triggers
                    // Aztec's refresh() and the line height jumps once.
                    primeTaskListSpans(editor)
                }
                override fun toggleUnderline() = withPreservedSelection { editor.toggleFormatting(AztecTextFormat.FORMAT_UNDERLINE) }
                override fun toggleOrderedList() = withPreservedSelection(rebuildLayout = true) {
                    editor.toggleFormatting(AztecTextFormat.FORMAT_ORDERED_LIST)
                    replaceOrderedListSpans(editor, bulletArgb)
                }
                override fun toggleQuote() = withPreservedSelection(rebuildLayout = true) {
                    editor.toggleFormatting(AztecTextFormat.FORMAT_QUOTE)
                    // Re-style the freshly-created quote span with the live palette colors.
                    replaceQuoteSpans(editor, quoteBarArgb, quoteTextArgb, quoteFillArgb)
                }
                override fun toggleCode() = withPreservedSelection(rebuildLayout = true) {
                    editor.toggleFormatting(AztecTextFormat.FORMAT_PREFORMAT)
                    // Aztec creates a stock AztecPreformatSpan on toggle — swap it for our span
                    // so the freshly-created block rounds all corners (incl. single-line).
                    replacePreformatSpans(editor, codeBgArgb, codeTextArgb)
                }
                override fun insertHorizontalRule() {
                    editor.toggleFormatting(AztecTextFormat.FORMAT_HORIZONTAL_RULE)
                }

                override fun insertImage(imageId: String) {
                    // Splice `<img src="tessera-img://id">` at the caret. Aztec serializes the media
                    // span back to that <img> tag, which htmlToMarkdown turns into the
                    // `![](tessera-img://id)` line the store keeps — so this rides the SAME markdown
                    // flush path as every other edit (no parallel content channel).
                    val attrs = AztecAttributes().apply { setValue("src", "tessera-img://$imageId") }
                    val width = editor.maxImagesWidth.takeIf { it > 0 }
                        ?: editor.width.takeIf { it > 0 }
                        ?: DefaultImageWidthPx
                    val loader = imageGetter as? TesseraImageLoader
                    caretScope.launch {
                        // Resolve the just-stored local file to a drawable off the main thread; fall
                        // back to a loading tile if it isn't decodable yet (the ImageGetter will
                        // re-resolve on the next reload).
                        val drawable = loader?.resolve("tessera-img://$imageId", width)
                            ?: loader?.loadingPlaceholder(width, null)
                            ?: android.graphics.drawable.ColorDrawable(android.graphics.Color.LTGRAY)
                                .apply { setBounds(0, 0, width, width * 9 / 16) }
                        editor.insertImage(drawable, attrs)
                        reapplySerifHeadings()
                        pushActiveFormats()
                        flushHtmlNow()
                    }
                }

                override fun focusBody() {
                    // Read-only (Viewer) editors are non-focusable; never grab focus or surface the
                    // IME for them, even if a caller invokes this (e.g. a stray tap target).
                    if (!editor.isFocusable) return
                    editor.requestFocus()
                    editor.setSelection(editor.length())
                    val imm = editor.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }

                override fun activeFormats(): Set<AztecTextFormat> =
                    EditorQueryFormats.filterTo(mutableSetOf()) {
                        editor.contains(it, editor.selectionStart, editor.selectionEnd)
                    }
            })

            editor
        },
        update = { editor ->
            // Reload only when the markdown source actually changed since we last loaded
            // it. Comparing against `lastLoadedMarkdown` (updated on initial load + every
            // flush) — NOT against `editor.toHtml()` — means a mid-typing recomposition
            // can't race the unflushed Aztec state and wipe the user's keystrokes.
            if (markdownKey != lastLoadedMarkdownRef[0]) {
                // Guard against clobbering unflushed local edits. The flush is debounced
                // (300ms), so within that window `state.content` (the source of `markdownKey`)
                // still holds the pre-edit text. If an external snapshot — e.g. the editor
                // ViewModel's applyRemoteIfIdle, which can fire while `hasLocalEdits` is still
                // false — pushes a different `markdownKey` during this window, reloading would
                // wipe the in-progress keystroke (a just-deleted character reappearing). When
                // the editor's live content no longer matches what we last loaded, a flush is
                // still pending and will reconcile state, so skip the reload entirely.
                val live = currentHtmlToMarkdown(editor.toHtml())
                if (live == lastLoadedMarkdownRef[0]) {
                    editor.fromHtml(html)
                    primeTaskListSpans(editor)
                    replaceBulletSpans(editor, bulletRadiusPx, bulletArgb)
                    replacePreformatSpans(editor, codeBgArgb, codeTextArgb)
                    replaceQuoteSpans(editor, quoteBarArgb, quoteTextArgb, quoteFillArgb)
                    replaceOrderedListSpans(editor, bulletArgb)
                    (editor.text as? Spannable)?.let { applySerifHeadingSpans(it, editor.context) }
                    lastLoadedMarkdownRef[0] = markdownKey
                }
            }
        },
    )
}
