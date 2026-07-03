package app.tessera.ui.notes.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One revealable swipe button. Built at the call site from the current view so the card stays
 * dumb about views. [key] is stable per logical action ("archive", "pin", "delete", "restore",
 * "unarchive", "delete_forever") and is used for the test tag.
 */
@Immutable
data class SwipeAction(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val container: Color,
    val content: Color,
    val onClick: () -> Unit,
)
