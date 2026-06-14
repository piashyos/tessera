package app.tessera.core.ui.components

import android.content.res.Configuration
import android.graphics.Color.TRANSPARENT
import android.util.TypedValue
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.tessera.core.ui.theme.TesseraTheme
import io.noties.markwon.Markwon

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val markwon = Markwon.create(context)
            FrameLayout(context).apply {
                setBackgroundColor(TRANSPARENT)
                addView(
                    TextView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                        setTextColor(textColor)
                        setLineSpacing(0f, 1.15f)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        setBackgroundColor(TRANSPARENT)
                        setPadding(0, 0, 0, 0)
                        tag = markwon
                    },
                )
            }
        },
        update = { container ->
            container.setBackgroundColor(TRANSPARENT)
            val textView = container.getChildAt(0) as TextView
            textView.setTextColor(textColor)
            textView.setBackgroundColor(TRANSPARENT)
            val markwon = textView.tag as Markwon
            markwon.setMarkdown(textView, markdown)
        },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFBF4)
@Composable
private fun MarkdownTextLightPreview() {
    TesseraTheme(darkTheme = false, dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            FloatingCapsule {
                MarkdownText(
                    markdown = "## Tessera\nA *glass-style* note shell with:\n- floating controls\n- markdown rendering\n- soft borders",
                )
            }
        }
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF101116,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun MarkdownTextDarkPreview() {
    TesseraTheme(darkTheme = true, dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            FloatingCapsule {
                MarkdownText(
                    markdown = "### Preview\nUse `Markwon` for **rich text** while staying inside Compose.",
                )
            }
        }
    }
}
