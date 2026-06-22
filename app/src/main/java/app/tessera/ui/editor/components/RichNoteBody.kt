package app.tessera.ui.editor.components

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.tessera.R
import org.wordpress.aztec.Aztec
import org.wordpress.aztec.AztecText
import org.wordpress.aztec.AztecTextFormat
import org.wordpress.aztec.ITextFormat
import org.wordpress.aztec.plugins.IToolbarButton
import org.wordpress.aztec.source.SourceViewEditText
import org.wordpress.aztec.spans.AztecTaskListSpan
import org.wordpress.aztec.spans.AztecUnorderedListSpan
import org.wordpress.aztec.toolbar.IAztecToolbar
import org.wordpress.aztec.toolbar.IAztecToolbarClickListener

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
    /** Programmatically focus the editor and surface the IME, placing the cursor at end of text. */
    fun focusBody()

    /**
     * Insert a new empty 2x2 table after the currently-focused text block.
     * Implemented at the screen level — this default is a no-op so previews / tests
     * that mock RichBodyActions without a screen still compile.
     */
    fun insertTable() {}

    /**
     * Formats currently applied at the editor's selection / cursor position.
     * The toolbar dropdown reads this to highlight active entries.
     */
    fun activeFormats(): Set<AztecTextFormat>
}

private const val DebounceMs = 300L

/**
 * Call `refresh()` on every `AztecTaskListSpan` currently attached to the editor's
 * editable. Aztec's stock `fromHtml` installs the task-list span but the leading
 * `verticalPadding` from `AztecListSpan.chooseHeight` is only applied *after* the
 * span gets replaced via `refresh()` — which normally only happens on the first
 * checkbox tap. Triggering it once at load time stabilises line metrics so toggling
 * later doesn't visibly resize the line.
 */
private fun primeTaskListSpans(editor: AztecText) {
    val editable = editor.editableText ?: return
    val spans = editable.getSpans(0, editable.length, AztecTaskListSpan::class.java)
    spans.forEach { it.refresh() }
}

/**
 * Replace Aztec's default unordered-list spans (which draw the bullet as a hardcoded
 * "•" glyph at the editor's font size) with our [TesseraBulletListSpan] that paints a
 * filled circle of the requested radius — lets us size the dot independently of the
 * surrounding text.
 */
private fun replaceBulletSpans(editor: AztecText, bulletRadiusPx: Float) {
    val editable = editor.editableText ?: return
    val spans = editable.getSpans(0, editable.length, AztecUnorderedListSpan::class.java)
    spans.forEach { span ->
        if (span is TesseraBulletListSpan) return@forEach
        val start = editable.getSpanStart(span)
        val end = editable.getSpanEnd(span)
        val flags = editable.getSpanFlags(span)
        if (start < 0 || end < 0) return@forEach
        editable.removeSpan(span)
        val replacement = TesseraBulletListSpan(
            nestingLevel = span.nestingLevel,
            attributes = span.attributes,
            listStyle = span.listStyle,
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

@Composable
fun RichNoteBody(
    html: String,
    markdownKey: String,
    htmlToMarkdown: (String) -> String,
    onMarkdownChange: (String) -> Unit,
    onActionsReady: (RichBodyActions) -> Unit,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
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
    val textSizeSp = MaterialTheme.typography.bodyLarge.fontSize.takeIf { it != 0.sp } ?: 16.sp
    val density = LocalDensity.current
    val bulletRadiusPx = with(density) { 5.dp.toPx() }

    // Handler lifted to the composable so DisposableEffect can clear pending debounce
    // runnables when the editor leaves composition (e.g. back-nav during typing).
    val handler = remember { Handler(Looper.getMainLooper()) }
    val editorRef = remember { mutableStateOf<AztecText?>(null) }
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
        modifier = modifier,
        factory = { context ->
            var debounceRunnable: Runnable? = null

            val inflater = LayoutInflater.from(context)
            val editor = inflater.inflate(R.layout.rich_note_body, null, false) as AztecText
            editor.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )

            // Apply theme-aware styling
            editor.setTextColor(textColor.toArgb())
            editor.textSize = textSizeSp.value
            editor.setPadding(0, 8, 0, 8)
            editor.includeFontPadding = false
            // Pull wrapped lines closer to the previous line. Aztec inherits Android's
            // default line spacing which leaves a noticeable gap when text wraps; trim
            // it by reducing the multiplier and zeroing extra.
            editor.setLineSpacing(0f, 1.05f)

            // Wire History + IME callbacks via the Aztec factory (no-op toolbar/listener — the Compose
            // MarkdownToolbar drives formatting through RichBodyActions). Without this call the view
            // renders fromHtml content but ignores key events / cursor input.
            Aztec.Factory.with(
                editor,
                NoOpAztecToolbar,
                NoOpAztecToolbarClickListener,
            )

            // Load initial HTML AFTER Aztec.with so plugins/history see it as the starting state.
            editor.fromHtml(html)
            // After load, force-refresh every task-list span so its line metrics
            // (`verticalPadding` from AztecListSpan.chooseHeight) are applied identically
            // to the post-tap state — otherwise the first checkbox toggle causes a visible
            // one-time line-height shift when Aztec's `refresh()` reinstalls the span.
            primeTaskListSpans(editor)
            replaceBulletSpans(editor, bulletRadiusPx)
            editorRef.value = editor

            editor.isFocusable = true
            editor.isFocusableInTouchMode = true
            // Report focus so the screen knows which text block the cursor is in — the
            // "insert table" action uses this to place the table at the cursor, not the end.
            editor.setOnFocusChangeListener { _, hasFocus -> currentOnFocusChanged(hasFocus) }

            fun scheduleHtmlFlush() {
                debounceRunnable?.let { handler.removeCallbacks(it) }
                val runnable = Runnable {
                    val md = currentHtmlToMarkdown(editor.toHtml())
                    // Mark the just-flushed markdown as already loaded so the next
                    // recomposition (markdownKey will now equal this) doesn't trigger
                    // a self-inflicted fromHtml round-trip. Non-state write — no extra
                    // recomposition is triggered, which keeps lastLoaded and
                    // markdownKey (via state.content) updating in the same Compose frame.
                    lastLoadedMarkdownRef[0] = md
                    currentOnMarkdownChange(md)
                }
                debounceRunnable = runnable
                handler.postDelayed(runnable, DebounceMs)
            }

            editor.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = scheduleHtmlFlush()
            })

            // Task-list checkbox toggles bypass TextWatcher (Aztec only mutates the leading
            // `checked` attribute, no text change). FlickerlessAztecText skips the stock
            // `setText(editable)` relayout — we hook its lightweight-refresh callback to
            // persist the new state without triggering the relayout flicker.
            (editor as? FlickerlessAztecText)?.onLightweightRefresh = { scheduleHtmlFlush() }

            // Expose formatting actions to the toolbar
            onActionsReady(object : RichBodyActions {
                private inline fun withPreservedSelection(block: () -> Unit) {
                    val start = editor.selectionStart
                    val end = editor.selectionEnd
                    block()
                    if (start in 0..editor.editableText.length &&
                        end in 0..editor.editableText.length &&
                        start != end
                    ) {
                        editor.setSelection(start, end)
                    }
                }

                override fun toggleBold() = withPreservedSelection { editor.toggleFormatting(AztecTextFormat.FORMAT_BOLD) }
                override fun toggleItalic() = withPreservedSelection { editor.toggleFormatting(AztecTextFormat.FORMAT_ITALIC) }
                override fun toggleStrikethrough() = withPreservedSelection { editor.toggleFormatting(AztecTextFormat.FORMAT_STRIKETHROUGH) }
                override fun toggleParagraph() = withPreservedSelection { editor.toggleFormatting(AztecTextFormat.FORMAT_PARAGRAPH) }
                override fun toggleHeading1() = withPreservedSelection { editor.toggleFormatting(AztecTextFormat.FORMAT_HEADING_1) }
                override fun toggleHeading2() = withPreservedSelection { editor.toggleFormatting(AztecTextFormat.FORMAT_HEADING_2) }
                override fun toggleHeading3() = withPreservedSelection { editor.toggleFormatting(AztecTextFormat.FORMAT_HEADING_3) }
                override fun toggleHeading4() = withPreservedSelection { editor.toggleFormatting(AztecTextFormat.FORMAT_HEADING_4) }
                override fun toggleHeading5() = withPreservedSelection { editor.toggleFormatting(AztecTextFormat.FORMAT_HEADING_5) }
                override fun toggleHeading6() = withPreservedSelection { editor.toggleFormatting(AztecTextFormat.FORMAT_HEADING_6) }
                override fun toggleBulletList() = withPreservedSelection {
                    editor.toggleFormatting(AztecTextFormat.FORMAT_UNORDERED_LIST)
                    // Aztec creates a fresh AztecUnorderedListSpan when toggling — swap it
                    // for our custom span so the larger circle bullet keeps rendering.
                    replaceBulletSpans(editor, bulletRadiusPx)
                }
                override fun toggleTaskList() = withPreservedSelection { editor.toggleFormatting(AztecTextFormat.FORMAT_TASK_LIST) }

                override fun focusBody() {
                    editor.requestFocus()
                    editor.setSelection(editor.length())
                    val imm = editor.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }

                private val queryFormats = listOf(
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
                )

                override fun activeFormats(): Set<AztecTextFormat> =
                    queryFormats.filterTo(mutableSetOf()) {
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
                editor.fromHtml(html)
                primeTaskListSpans(editor)
                replaceBulletSpans(editor, bulletRadiusPx)
                lastLoadedMarkdownRef[0] = markdownKey
            }
        },
    )
}
