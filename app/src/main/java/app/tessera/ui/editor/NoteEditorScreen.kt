package app.tessera.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.UiTestTags
import app.tessera.core.ui.components.EdgeFadeScrim
import app.tessera.core.ui.components.FloatingCapsule
import app.tessera.core.ui.components.ScrimEdge
import app.tessera.core.ui.theme.FloatingShadowAmbient
import app.tessera.core.ui.theme.FloatingShadowSpot
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun NoteEditorScreen(
    noteId: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val pageColor =
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
            MaterialTheme.colorScheme.surface
        } else {
            Color.White
        }
    val hazeState = remember { HazeState() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageColor)
            .testTag(UiTestTags.EditorPlaceholderRoot),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            color = pageColor,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))
                Spacer(modifier = Modifier.height(86.dp))
                Text(
                    text = "Editor route placeholder",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Opened note id: $noteId",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "This area is intentionally one continuous page now, so the future editor content can scroll under the header fade instead of being boxed into separate cards.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                repeat(10) { index ->
                    Text(
                        text = "Placeholder paragraph ${index + 1}. Markdown content, checklist items, and longer note text will live directly on this canvas and scroll under the top gradient.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.height(120.dp))
            }
        }

        EdgeFadeScrim(
            hazeState = hazeState,
            scrimColor = pageColor,
            edge = ScrimEdge.Top,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .height(150.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))
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
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.editor_back),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onBack)
                            .testTag("editor_back"),
                    )
                    Text(
                        text = "Chunk 6 editor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
