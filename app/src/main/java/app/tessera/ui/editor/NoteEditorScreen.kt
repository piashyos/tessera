package app.tessera.ui.editor

import androidx.compose.foundation.background
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
import app.tessera.core.ui.components.EdgeFadeScrim
import app.tessera.core.ui.components.FloatingCapsule
import app.tessera.core.ui.components.ScrimEdge
import app.tessera.core.ui.theme.FloatingShadowAmbient
import app.tessera.core.ui.theme.FloatingShadowSpot
import app.tessera.core.ui.theme.TesseraTheme
import app.tessera.core.text.MarkdownHtmlConverter
import app.tessera.ui.editor.components.MarkdownFormatMenu
import app.tessera.ui.editor.components.MarkdownToolbar
import app.tessera.ui.editor.components.RichBodyActions
import app.tessera.ui.editor.components.RichNoteBody
import app.tessera.ui.editor.components.TagChipsExpandable
import app.tessera.ui.editor.components.TagPickerPopup
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
    state: NoteEditorState,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val converter = remember { MarkdownHtmlConverter() }
    var actions by remember { mutableStateOf<RichBodyActions?>(null) }
    val initialHtml = remember(state.content) { converter.markdownToHtml(state.content) }

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

                    // Title field
                    BasicTextField(
                        value = state.title,
                        onValueChange = onTitleChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.EditorTitleField),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (state.title.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.editor_title_hint),
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )

                    // Body — Aztec WYSIWYG editor
                    RichNoteBody(
                        html = initialHtml,
                        markdownKey = state.content,
                        htmlToMarkdown = converter::htmlToMarkdown,
                        onMarkdownChange = { markdown ->
                            if (markdown != state.content) onContentChange(markdown)
                        },
                        onActionsReady = { actions = it },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Bottom spacing for the floating bottom capsule
                    Spacer(modifier = Modifier.height(160.dp))
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
                            onClick = onBack,
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
                                onClick = onTogglePin,
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
                                onClick = onDelete,
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
                    hazeState = null,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MarkdownToolbar(
                            onOpenMenu = { formatMenuVisible = true },
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            onClick = { tagsExpanded = !tagsExpanded },
                            shape = CircleShape,
                            color = if (tagsExpanded) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag(UiTestTags.EditorTagToggle),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Lucide.Tag,
                                    contentDescription = stringResource(R.string.editor_tags_toggle),
                                    tint = if (tagsExpanded) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))

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
                title = "My first note",
                content = "- [ ] Buy groceries\n- [x] Call the dentist\n\nSome extra thoughts here.",
                tags = listOf("work", "personal"),
                isPinned = false,
                availableTags = listOf("ideas", "kotlin"),
                isLoading = false,
            ),
            onTitleChange = {},
            onContentChange = {},
            onAddTag = {},
            onRemoveTag = {},
            onTogglePin = {},
            onBack = {},
            onDelete = {},
        )
    }
}
