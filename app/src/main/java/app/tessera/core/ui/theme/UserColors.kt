package app.tessera.core.ui.theme

import androidx.compose.ui.graphics.Color
import app.tessera.domain.model.UserId

/**
 * Deterministic per-user accent colors for member avatars (Phase 3) and, later, live-editing
 * carets (Phase 9). The mapping is stable across app launches and themes so the same collaborator
 * always wears the same color everywhere in the UI.
 *
 * Colors are picked to be legible with white foreground content (initials / caret labels) in both
 * light and dark themes.
 */
object UserColors {
  /** Fixed palette; index chosen by a stable hash of the user id. */
  val palette: List<Color> = listOf(
    Color(0xFFD97757), // warm terracotta (brand-adjacent)
    Color(0xFF4C8DBF), // blue
    Color(0xFF5F9E6E), // green
    Color(0xFFB5713F), // amber
    Color(0xFF8E6FC2), // violet
    Color(0xFFC85F8E), // magenta
    Color(0xFF4FA3A1), // teal
    Color(0xFFB05656), // brick
  )
}

/**
 * Returns the stable avatar/caret color for [uid]. Uses [String.hashCode], which is specified and
 * stable for a given string, mapped into [UserColors.palette] with a non-negative index.
 */
fun colorForUser(uid: UserId): Color {
  val index = uid.value.hashCode().mod(UserColors.palette.size)
  return UserColors.palette[index]
}
