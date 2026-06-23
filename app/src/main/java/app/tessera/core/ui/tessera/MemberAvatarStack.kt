package app.tessera.core.ui.tessera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tessera.core.ui.theme.colorForUser
import app.tessera.domain.model.ShareMember
import app.tessera.domain.model.UserId
import coil.compose.AsyncImage

/**
 * Overlapping stack of collaborator avatars for a shared note.
 *
 * Behavior (Phase 3 spec):
 * - The current user's own avatar is hidden — only *other* members are shown. A note shared with
 *   nobody but yourself therefore renders nothing.
 * - At most [maxVisible] avatars are drawn; any remainder collapses into a trailing `+N` chip.
 *
 * Each avatar falls back to a colored initial when no [ShareMember.photoUrl] is available; the
 * color is stable per user via [colorForUser] (shared with live-editing carets).
 */
@Composable
fun MemberAvatarStack(
  members: List<ShareMember>,
  currentUserId: UserId?,
  modifier: Modifier = Modifier,
  maxVisible: Int = 2,
  avatarSize: Dp = 24.dp,
) {
  val others = members.filterNot { it.uid == currentUserId }
  if (others.isEmpty()) return

  val visible = others.take(maxVisible)
  val overflow = others.size - visible.size
  val ringColor = MaterialTheme.colorScheme.surface

  // 30% overlap between adjacent avatars.
  val overlap = 0.30f

  Layout(
    modifier = modifier,
    content = {
      visible.forEach { member ->
        MemberAvatar(member = member, size = avatarSize, ringColor = ringColor)
      }
      if (overflow > 0) {
        OverflowChip(count = overflow, size = avatarSize, ringColor = ringColor)
      }
    },
  ) { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints) }
    if (placeables.isEmpty()) return@Layout layout(0, 0) {}
    val step = (placeables.first().width * (1f - overlap)).toInt()
    val width = step * (placeables.size - 1) + placeables.last().width
    val height = placeables.maxOf { it.height }
    layout(width, height) {
      var x = 0
      placeables.forEach { placeable ->
        placeable.placeRelative(x, (height - placeable.height) / 2)
        x += step
      }
    }
  }
}

@Composable
private fun MemberAvatar(member: ShareMember, size: Dp, ringColor: Color) {
  val bg = colorForUser(member.uid)
  val label = member.displayName.ifBlank { member.email }
  Box(
    modifier = Modifier
      .size(size)
      .clip(CircleShape)
      .border(1.5.dp, ringColor, CircleShape)
      .semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) {
    if (member.photoUrl != null) {
      AsyncImage(
        model = member.photoUrl,
        contentDescription = null,
        modifier = Modifier.size(size).clip(CircleShape),
      )
    } else {
      Box(
        modifier = Modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = initialOf(label),
          style = MaterialTheme.typography.labelSmall,
          color = if (bg.luminance() > 0.5f) Color.Black else Color.White,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
private fun OverflowChip(count: Int, size: Dp, ringColor: Color) {
  Box(
    modifier = Modifier
      .size(size)
      .clip(CircleShape)
      .border(1.5.dp, ringColor, CircleShape)
      .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "+$count",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private fun initialOf(label: String): String =
  label.trim().firstOrNull()?.uppercase() ?: "?"
