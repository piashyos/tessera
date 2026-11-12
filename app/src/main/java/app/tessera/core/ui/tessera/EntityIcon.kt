package app.tessera.core.ui.tessera

import android.icu.text.BreakIterator
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.composables.icons.lucide.Baby
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Briefcase
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Car
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Code
import com.composables.icons.lucide.Coffee
import com.composables.icons.lucide.Dumbbell
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Gamepad2
import com.composables.icons.lucide.Gift
import com.composables.icons.lucide.GraduationCap
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Leaf
import com.composables.icons.lucide.Lightbulb
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.PawPrint
import com.composables.icons.lucide.Plane
import com.composables.icons.lucide.ShoppingCart
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.Utensils
import com.composables.icons.lucide.Wallet

/** Icon of a tag or folder. Stored as a string: `lucide:<key>`, `emoji:<text>` or null (none). */
@Immutable
sealed interface EntityIcon {
  data class Vector(val key: String, val image: ImageVector) : EntityIcon
  data class Emoji(val text: String) : EntityIcon
}

private const val LucidePrefix = "lucide:"
private const val EmojiPrefix = "emoji:"

/** The icon set offered in the appearance popup, in display order. */
val LucideIconSet: List<EntityIcon.Vector> = listOf(
  EntityIcon.Vector("folder", Lucide.Folder),
  EntityIcon.Vector("house", Lucide.House),
  EntityIcon.Vector("briefcase", Lucide.Briefcase),
  EntityIcon.Vector("book-open", Lucide.BookOpen),
  EntityIcon.Vector("lightbulb", Lucide.Lightbulb),
  EntityIcon.Vector("star", Lucide.Star),
  EntityIcon.Vector("heart", Lucide.Heart),
  EntityIcon.Vector("shopping-cart", Lucide.ShoppingCart),
  EntityIcon.Vector("plane", Lucide.Plane),
  EntityIcon.Vector("car", Lucide.Car),
  EntityIcon.Vector("dumbbell", Lucide.Dumbbell),
  EntityIcon.Vector("utensils", Lucide.Utensils),
  EntityIcon.Vector("coffee", Lucide.Coffee),
  EntityIcon.Vector("music", Lucide.Music),
  EntityIcon.Vector("camera", Lucide.Camera),
  EntityIcon.Vector("gamepad-2", Lucide.Gamepad2),
  EntityIcon.Vector("graduation-cap", Lucide.GraduationCap),
  EntityIcon.Vector("code", Lucide.Code),
  EntityIcon.Vector("palette", Lucide.Palette),
  EntityIcon.Vector("wallet", Lucide.Wallet),
  EntityIcon.Vector("calendar", Lucide.Calendar),
  EntityIcon.Vector("clock", Lucide.Clock),
  EntityIcon.Vector("flag", Lucide.Flag),
  EntityIcon.Vector("bookmark", Lucide.Bookmark),
  EntityIcon.Vector("tag", Lucide.Tag),
  EntityIcon.Vector("users", Lucide.Users),
  EntityIcon.Vector("baby", Lucide.Baby),
  EntityIcon.Vector("paw-print", Lucide.PawPrint),
  EntityIcon.Vector("leaf", Lucide.Leaf),
  EntityIcon.Vector("gift", Lucide.Gift),
)

/** Unknown Lucide key (newer app version), bad emoji or malformed string give `null`. */
fun parseEntityIcon(raw: String?): EntityIcon? = when {
  raw == null -> null
  raw.startsWith(LucidePrefix) -> raw.removePrefix(LucidePrefix).let { key -> LucideIconSet.firstOrNull { it.key == key } }
  raw.startsWith(EmojiPrefix) -> raw.removePrefix(EmojiPrefix).takeIf(::isAcceptableEmoji)?.let(EntityIcon::Emoji)
  else -> null
}

/** The string stored in the data layer for [this]; inverse of [parseEntityIcon]. */
fun EntityIcon.toRaw(): String = when (this) {
  is EntityIcon.Vector -> LucidePrefix + key
  is EntityIcon.Emoji -> EmojiPrefix + text
}

/**
 * Exactly one grapheme whose first code point is neither whitespace nor a letter, and — when the
 * grapheme is a single code point — not a digit. Keycaps ("1️⃣"), ZWJ sequences and flags pass;
 * "é" (e + U+0301), "1", " " do not.
 */
fun isAcceptableEmoji(text: String): Boolean {
  if (text.isEmpty()) return false
  val it = BreakIterator.getCharacterInstance()
  it.setText(text)
  if (it.next() != text.length) return false // more (or less) than one grapheme
  val first = text.codePointAt(0)
  if (Character.isWhitespace(first) || Character.isSpaceChar(first) || Character.isLetter(first)) return false
  // Invisible or non-standalone first code points: they would render as blank space.
  // UNASSIGNED is deliberately allowed: emoji newer than the device's ICU must still pass.
  if (Character.getType(first).toByte() in InvisibleTypes) return false
  val single = Character.charCount(first) == text.length
  return !(single && Character.isDigit(first))
}

private val InvisibleTypes = setOf(
  Character.CONTROL, Character.FORMAT, Character.SURROGATE,
  Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.PRIVATE_USE,
)

@Composable
fun EntityIconView(icon: EntityIcon, tint: Color, size: Dp, modifier: Modifier = Modifier) {
  when (icon) {
    is EntityIcon.Vector -> Icon(icon.image, contentDescription = null, tint = tint, modifier = modifier.size(size))
    is EntityIcon.Emoji -> {
      // A fixed size x size box: font size and line height both equal `size`, so the glyph never
      // inflates the host (chip) beyond what a vector icon would. `color` only affects text-style
      // symbols (★, ✓); coloured emoji ignore it.
      val fs = with(LocalDensity.current) { size.toSp() }
      Text(icon.text, color = tint, fontSize = fs, lineHeight = fs, modifier = modifier.size(size).wrapContentSize(unbounded = true))
    }
  }
}
