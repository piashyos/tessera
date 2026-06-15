package app.tessera.ui.editor.components

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
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
import org.wordpress.aztec.AztecText
import org.wordpress.aztec.AztecTextFormat

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

            val editor = AztecText(context)

            // Apply theme-aware styling
            editor.setTextColor(textColor.toArgb())
            editor.textSize = textSizeSp.value
            editor.setPadding(0, 8, 0, 8)

            // Load initial HTML
            editor.fromHtml(html)
            lastLoadedMarkdown.value = markdownKey

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
