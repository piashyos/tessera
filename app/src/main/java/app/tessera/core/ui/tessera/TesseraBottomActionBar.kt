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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.theme.TesseraElevation
import app.tessera.core.ui.theme.TesseraShape
import app.tessera.core.ui.theme.TesseraSpacing
import app.tessera.core.ui.theme.tesseraShadow

/**
 * Bottom action bar of the notes list: [leadingAction] slot (folders/drawer trigger — thumb-zone
 * access to navigation), the search pill, and the create FAB. The clickable surfaces use
 * `Surface(onClick=)` so TalkBack gets a Button role and the 48dp minimum touch target applies.
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
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = TesseraShape.pill,
        modifier = Modifier
          .size(52.dp)
          .tesseraShadow(TesseraElevation.Level2, shape = TesseraShape.pill),
      ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          leadingAction()
        }
      }
    }
    Surface(
      onClick = onSearchClick,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      shape = TesseraShape.pill,
      modifier = Modifier
        .semantics { role = Role.Button }
        .weight(1f)
        .height(52.dp)
        .tesseraShadow(TesseraElevation.Level2, shape = TesseraShape.pill),
    ) {
      Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = TesseraSpacing.xl),
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
      enter = fadeIn() + scaleIn(),
      exit = fadeOut() + scaleOut(),
    ) {
      Surface(
        onClick = onFabClick,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = TesseraShape.fab,
        modifier = Modifier
          .semantics { role = Role.Button }
          .size(52.dp)
          .tesseraShadow(TesseraElevation.Level3, shape = TesseraShape.fab),
      ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          fabIcon()
        }
      }
    }
  }
}
