package app.tessera.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.TesseraTheme

@Composable
fun GlassFab(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val semanticsModifier =
        if (contentDescription == null) {
            Modifier
        } else {
            Modifier.semantics { this.contentDescription = contentDescription }
        }
    FloatingCapsule(
        modifier = if (onClick == null) {
            modifier.then(semanticsModifier)
        } else {
            modifier.then(semanticsModifier).clickable(onClick = onClick)
        },
        shape = CircleShape,
        blurRadius = 10.dp,
        shadowElevation = 3.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFBF4)
@Composable
private fun GlassFabLightPreview() {
    TesseraTheme(darkTheme = false, dynamicColor = false) {
        GlassFab(onClick = {}, modifier = Modifier) {
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF101116,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun GlassFabDarkPreview() {
    TesseraTheme(darkTheme = true, dynamicColor = false) {
        GlassFab(onClick = {}, modifier = Modifier) {
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}
