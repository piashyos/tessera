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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.tessera.R
import org.wordpress.aztec.Aztec
import org.wordpress.aztec.AztecText
import org.wordpress.aztec.AztecTextFormat
import org.wordpress.aztec.ITextFormat
import org.wordpress.aztec.plugins.IToolbarButton
import org.wordpress.aztec.source.SourceViewEditText
import org.wordpress.aztec.toolbar.IAztecToolbar
import org.wordpress.aztec.toolbar.IAztecToolbarClickListener

/** Toolbar-facing actions applied to the AztecText current selection. */
interface RichBodyActions {
    fun toggleBold()
    fun toggleItalic()
    fun toggleStrikethrough()
    fun toggleHeading1()
    fun toggleHeading2()
    fun toggleBulletList()
    fun toggleTaskList()
}

private const val DebounceMs = 300L

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
    onHtmlChange: (String) -> Unit,
    onActionsReady: (RichBodyActions) -> Unit,
    modifier: Modifier = Modifier,
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

    // Echo-loop guard: tracks which markdownKey was last loaded into the editor
    val lastLoadedMarkdown = remember { mutableStateOf<String?>(null) }
    // Handler lifted to the composable so DisposableEffect can clear pending debounce
    // runnables when the editor leaves composition (e.g. back-nav during typing).
    val handler = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(Unit) {
        onDispose { handler.removeCallbacksAndMessages(null) }
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
            lastLoadedMarkdown.value = markdownKey

            editor.isFocusable = true
            editor.isFocusableInTouchMode = true

            // Debounced text change listener
            editor.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    debounceRunnable?.let { handler.removeCallbacks(it) }
                    val runnable = Runnable {
                        onHtmlChange(editor.toHtml())
                    }
                    debounceRunnable = runnable
                    handler.postDelayed(runnable, DebounceMs)
                }
            })

            // Expose formatting actions to the toolbar
            onActionsReady(object : RichBodyActions {
                override fun toggleBold() = editor.toggleFormatting(AztecTextFormat.FORMAT_BOLD)
                override fun toggleItalic() = editor.toggleFormatting(AztecTextFormat.FORMAT_ITALIC)
                override fun toggleStrikethrough() = editor.toggleFormatting(AztecTextFormat.FORMAT_STRIKETHROUGH)
                override fun toggleHeading1() = editor.toggleFormatting(AztecTextFormat.FORMAT_HEADING_1)
                override fun toggleHeading2() = editor.toggleFormatting(AztecTextFormat.FORMAT_HEADING_2)
                override fun toggleBulletList() = editor.toggleFormatting(AztecTextFormat.FORMAT_UNORDERED_LIST)
                override fun toggleTaskList() = editor.toggleFormatting(AztecTextFormat.FORMAT_TASK_LIST)
            })

            editor
        },
        update = { editor ->
            // Only reload HTML when the markdown source changed externally (e.g. first load
            // or a programmatic mutation), NOT on self-originated keystrokes.
            if (markdownKey != lastLoadedMarkdown.value) {
                editor.fromHtml(html)
                lastLoadedMarkdown.value = markdownKey
            }
        },
    )
}
