package app.tessera.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * User-pickable accent for tags and folders. Tones follow one rule (plan D5), not 60 hand-picked
 * hexes: a hue per color, saturation/lightness per tone and theme. Hex/HSL lives here only.
 *
 * - [container] — chip / palette-dot fill; [content] — text and icon on it;
 * - [cardTint] — soft note-card fill, much quieter than [container] so a same-colour tag chip
 *   stays readable on the card (lighter in light, darker in dark).
 */
enum class AccentColor(
  val key: String,
  private val hue: Float,
  private val saturation: Float = 1f,
  /** Light-theme `content` lightness correction found by the contrast test (§3.1, within ±0.06). */
  private val contentLightShift: Float = 0f,
) {
  Red("red", 4f),
  Orange("orange", 26f),
  Yellow("yellow", 46f),
  Green("green", 135f, contentLightShift = -0.04f),
  Teal("teal", 175f, contentLightShift = -0.04f),
  Blue("blue", 212f),
  Indigo("indigo", 236f),
  Purple("purple", 276f),
  Pink("pink", 330f),
  Brown("brown", 24f, saturation = 0.5f);

  fun container(dark: Boolean): Color = if (dark) tone(.30f, .30f) else tone(.52f, .86f)

  fun content(dark: Boolean): Color = if (dark) tone(.45f, .82f) else tone(.55f, .30f + contentLightShift)

  fun cardTint(dark: Boolean): Color = if (dark) tone(.20f, .19f) else tone(.45f, .94f)

  private fun tone(s: Float, l: Float) = Color.hsl(hue, s * saturation, l)

  companion object {
    /** Unknown key (written by a newer app version) gives `null`. */
    fun fromKey(key: String?): AccentColor? = entries.firstOrNull { it.key == key }
  }
}
