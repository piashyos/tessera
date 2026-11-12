package app.tessera.core.ui.tessera

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.tessera.core.ui.theme.AccentColor

/** Container + on-container colors for a tag chip. */
@Immutable
data class TagChipColors(val container: Color, val content: Color)

/** UI model of a tag's chosen appearance; `null` fields fall back to the hash colour / "#". */
@Immutable
data class TagStyle(val color: AccentColor?, val icon: EntityIcon?)

/** Tag name -> chosen appearance. Provided once at the navigation root; empty means "all default". */
val LocalTagAppearances = compositionLocalOf<Map<String, TagStyle>> { emptyMap() }

@Composable
private fun isDarkTheme() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** `container`/`content` of this accent for the current theme. */
@Composable
fun AccentColor.chipColors(): TagChipColors {
    val dark = isDarkTheme()
    return remember(this, dark) { TagChipColors(container(dark), content(dark)) }
}

/** Soft note-card fill of this accent for the current theme. */
@Composable
fun AccentColor.cardTint(): Color {
    val dark = isDarkTheme()
    return remember(this, dark) { cardTint(dark) }
}

/** The tag's chosen icon, or `null` (then "#" is drawn as before). */
@Composable
fun rememberTagIcon(tag: String): EntityIcon? = LocalTagAppearances.current[tag]?.icon

/**
 * A stable per-tag color so every tag reads in its OWN hue everywhere it appears — the filter row,
 * the note-card chips, and the editor chips. Derived deterministically from the tag name (its
 * `hashCode`), so the same tag always maps to the same color across the app and across launches
 * (`String.hashCode` is a fixed algorithm). Saturation/lightness are tuned per theme so the chip
 * stays a soft tint with readable text in both light and dark. A colour the user picked
 * ([LocalTagAppearances]) wins over the hash.
 */
@Composable
fun rememberTagChipColors(tag: String): TagChipColors {
    val dark = isDarkTheme()
    val color = LocalTagAppearances.current[tag]?.color
    if (color != null) return color.chipColors()
    return remember(tag, dark) {
        val hue = (((tag.hashCode() % 360) + 360) % 360).toFloat()
        if (dark) {
            TagChipColors(
                container = Color.hsl(hue, saturation = 0.30f, lightness = 0.30f),
                content = Color.hsl(hue, saturation = 0.45f, lightness = 0.82f),
            )
        } else {
            TagChipColors(
                container = Color.hsl(hue, saturation = 0.52f, lightness = 0.86f),
                content = Color.hsl(hue, saturation = 0.55f, lightness = 0.30f),
            )
        }
    }
}
