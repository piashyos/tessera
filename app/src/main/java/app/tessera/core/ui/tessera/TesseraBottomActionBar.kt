package app.tessera.core.ui.tessera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.theme.GlassBlurRadius
import app.tessera.core.ui.theme.GlassTintAlpha
import app.tessera.core.ui.theme.TesseraElevation
import app.tessera.core.ui.theme.TesseraMotion
import app.tessera.core.ui.theme.TesseraShape
import app.tessera.core.ui.theme.TesseraSpacing
import app.tessera.core.ui.theme.tesseraGlassShadow
import app.tessera.core.ui.theme.tesseraShadow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Bottom action bar of the notes list: [leadingAction] slot (folders/drawer trigger — thumb-zone
 * access to navigation), the search pill, and the create FAB. The clickable surfaces use
 * `Surface(onClick=)` so TalkBack gets a Button role and the 48dp minimum touch target applies.
 *
 * With a [hazeState] (the screen's `hazeSource` is the content behind the bar) each element turns into glass:
 * a blurred backdrop under a translucent tint of its own theme colour. Without one — the old opaque look.
 * Below API 31 Haze draws the translucent tint without the blur.
 */
@Composable
fun TesseraBottomActionBar(
  searchQuery: String,
  onSearchClick: () -> Unit,
  onFabClick: () -> Unit,
  fabIcon: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  searchPlaceholder: String = stringResource(R.string.search_hint),
  showFab: Boolean = true,
  leadingAction: (@Composable () -> Unit)? = null,
  hazeState: HazeState? = null,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = TesseraSpacing.lg, vertical = TesseraSpacing.xxl)
      .height(52.dp),
    horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.md),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (leadingAction != null) {
      val color = MaterialTheme.colorScheme.surfaceContainerHigh
      Surface(
        color = color.glassBase(hazeState),
        contentColor = contentColorFor(color),
        shape = TesseraShape.pill,
        modifier = Modifier
          .size(52.dp)
          .then(if (hazeState == null) Modifier.tesseraShadow(TesseraElevation.Level2, shape = TesseraShape.pill) else Modifier.tesseraGlassShadow(TesseraElevation.Level2, TesseraShape.pill)) // glass: the shadow is drawn only outside the shape
      ) {
        Box(modifier = Modifier.fillMaxSize().tesseraGlass(hazeState, color), contentAlignment = Alignment.Center) {
          leadingAction()
        }
      }
    }
    val searchColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
      onClick = onSearchClick,
      color = searchColor.glassBase(hazeState),
      contentColor = contentColorFor(searchColor),
      shape = TesseraShape.pill,
      modifier = Modifier
        .semantics { role = Role.Button }
        .weight(1f)
        .height(52.dp)
        .then(if (hazeState == null) Modifier.tesseraShadow(TesseraElevation.Level2, shape = TesseraShape.pill) else Modifier.tesseraGlassShadow(TesseraElevation.Level2, TesseraShape.pill)) // glass: the shadow is drawn only outside the shape
    ) {
      Box(
        modifier = Modifier.fillMaxSize().tesseraGlass(hazeState, searchColor).padding(horizontal = TesseraSpacing.xl),
        contentAlignment = Alignment.CenterStart,
      ) {
        val (text, color) = if (searchQuery.isBlank()) {
          searchPlaceholder to MaterialTheme.colorScheme.onSurfaceVariant
        } else {
          searchQuery to MaterialTheme.colorScheme.onSurface
        }
        Text(
          text = text,
          color = color,
          style = MaterialTheme.typography.bodyLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    AnimatedVisibility(
      visible = showFab,
      enter = fadeIn(TesseraMotion.snappySpring()) + scaleIn(TesseraMotion.snappySpring(), initialScale = 0.6f),
      exit = fadeOut(TesseraMotion.snappySpring()) + scaleOut(TesseraMotion.snappySpring(), targetScale = 0.6f),
    ) {
      val fabColor = MaterialTheme.colorScheme.primaryContainer
      Surface(
        onClick = onFabClick,
        color = fabColor.glassBase(hazeState),
        contentColor = contentColorFor(fabColor),
        shape = TesseraShape.fab,
        modifier = Modifier
          .semantics { role = Role.Button }
          .size(52.dp)
          .then(if (hazeState == null) Modifier.tesseraShadow(TesseraElevation.Level3, shape = TesseraShape.fab) else Modifier.tesseraGlassShadow(TesseraElevation.Level3, TesseraShape.fab)) // glass: the shadow is drawn only outside the shape
      ) {
        Box(modifier = Modifier.fillMaxSize().tesseraGlass(hazeState, fabColor), contentAlignment = Alignment.Center) {
          fabIcon()
        }
      }
    }
  }
}

/** The Surface's own fill: transparent when [hazeState] draws the glass inside it. */
private fun Color.glassBase(hazeState: HazeState?): Color = if (hazeState == null) this else Color.Transparent

/** Blurred backdrop plus the element's own colour at [GlassTintAlpha]; the Surface above clips it to its shape. */
@Composable
internal fun Modifier.tesseraGlass(hazeState: HazeState?, color: Color): Modifier {
  if (hazeState == null) return this
  val base = MaterialTheme.colorScheme.surface // Haze requires a colour behind the blur
  return hazeEffect(state = hazeState) {
    backgroundColor = base
    blurEnabled = true
    blurRadius = GlassBlurRadius // Haze's own default is unspecified: without a radius nothing is blurred
    val tint = HazeTint(color.copy(alpha = GlassTintAlpha))
    tints = listOf(tint)
    fallbackTint = tint
  }
}
