package app.tessera.core.ui.tessera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tessera.R
import app.tessera.core.ui.components.TesseraInputField
import app.tessera.core.ui.theme.TesseraSpacing
import app.tessera.core.ui.theme.colorForUser
import app.tessera.domain.model.ShareMember
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.UserId
import app.tessera.domain.model.canManageAccess
import app.tessera.ui.editor.InviteUiState
import coil.compose.AsyncImage

object MembersSheetTags {
    const val Root = "members_sheet"
    const val InviteField = "members_invite_field"
    const val InviteButton = "members_invite_button"
    const val InviteFeedback = "members_invite_feedback"
}

/** Roles that can be assigned to a member from the sheet (owner is implicit and not assignable). */
private val AssignableRoles = listOf(ShareRole.MANAGER, ShareRole.EDITOR, ShareRole.VIEWER)

/**
 * Member-management bottom sheet for a shared note (Phase 4, spec §6.3).
 *
 * Owners/managers see per-member role menus, a remove action, and an invite-by-email field;
 * viewers/editors see a read-only roster. Stateless — invite progress and outcome come in via
 * [inviteState] and all mutations are reported through the callbacks.
 */
@Composable
fun MembersSheet(
    members: List<ShareMember>,
    myRole: ShareRole,
    currentUserId: UserId?,
    inviteState: InviteUiState,
    onChangeRole: (UserId, ShareRole) -> Unit,
    onRemoveMember: (UserId) -> Unit,
    onInvite: (String, ShareRole) -> Unit,
    onInviteInputChanged: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TesseraBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        header = { Text(stringResource(R.string.members_title)) },
    ) {
        MembersSheetContent(
            members = members,
            myRole = myRole,
            currentUserId = currentUserId,
            inviteState = inviteState,
            onChangeRole = onChangeRole,
            onRemoveMember = onRemoveMember,
            onInvite = onInvite,
            onInviteInputChanged = onInviteInputChanged,
        )
    }
}

/**
 * Inner content of [MembersSheet], extracted so screenshot tests can render it without the
 * platform [androidx.compose.material3.ModalBottomSheet] (which doesn't lay out under Robolectric).
 */
@Composable
fun ColumnScope.MembersSheetContent(
    members: List<ShareMember>,
    myRole: ShareRole,
    currentUserId: UserId?,
    inviteState: InviteUiState,
    onChangeRole: (UserId, ShareRole) -> Unit,
    onRemoveMember: (UserId) -> Unit,
    onInvite: (String, ShareRole) -> Unit,
    onInviteInputChanged: () -> Unit,
) {
    val canManage = myRole.canManageAccess
    // Owner first, then by join time — a stable, readable roster order.
    val ordered = remember(members) {
        members.sortedWith(compareBy({ it.role != ShareRole.OWNER }, { it.joinedAt }))
    }

    Column(modifier = Modifier.fillMaxWidth().testTag(MembersSheetTags.Root)) {
        ordered.forEach { member ->
            MemberRow(
                member = member,
                canManage = canManage && member.role != ShareRole.OWNER && member.uid != currentUserId,
                onChangeRole = { onChangeRole(member.uid, it) },
                onRemove = { onRemoveMember(member.uid) },
            )
        }

        if (canManage) {
            InviteSection(
                inviteState = inviteState,
                onInvite = onInvite,
                onInputChanged = onInviteInputChanged,
            )
        }
    }
}

@Composable
private fun MemberRow(
    member: ShareMember,
    canManage: Boolean,
    onChangeRole: (ShareRole) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TesseraSpacing.xxl, vertical = TesseraSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.lg),
    ) {
        Avatar(member = member, size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.displayName.ifBlank { member.email },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = member.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (member.role == ShareRole.OWNER) {
            RoleBadge(text = stringResource(R.string.members_owner_badge))
        } else if (canManage) {
            MemberRoleMenu(role = member.role, onChangeRole = onChangeRole, onRemove = onRemove)
        } else {
            RoleBadge(text = stringResource(roleLabel(member.role)))
        }
    }
}

@Composable
private fun MemberRoleMenu(
    role: ShareRole,
    onChangeRole: (ShareRole) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        RoleBadge(
            text = stringResource(roleLabel(role)),
            modifier = Modifier.clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AssignableRoles.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(roleLabel(option))) },
                    onClick = {
                        expanded = false
                        if (option != role) onChangeRole(option)
                    },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.members_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun InviteSection(
    inviteState: InviteUiState,
    onInvite: (String, ShareRole) -> Unit,
    onInputChanged: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(ShareRole.EDITOR) }
    var roleMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TesseraSpacing.xxl, vertical = TesseraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(TesseraSpacing.sm),
    ) {
        Text(
            text = stringResource(R.string.members_invite_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TesseraSpacing.sm),
        ) {
            TesseraInputField(
                value = email,
                onValueChange = { email = it; onInputChanged() },
                placeholder = stringResource(R.string.members_invite_hint),
                singleLine = true,
                keyboardType = KeyboardType.Email,
                modifier = Modifier
                    .weight(1f)
                    .testTag(MembersSheetTags.InviteField),
            )
            Box {
                RoleBadge(
                    text = stringResource(roleLabel(role)),
                    modifier = Modifier.clickable { roleMenu = true },
                )
                DropdownMenu(expanded = roleMenu, onDismissRequest = { roleMenu = false }) {
                    AssignableRoles.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(roleLabel(option))) },
                            onClick = { role = option; roleMenu = false },
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            inviteFeedback(inviteState)?.let { (text, isError) ->
                Text(
                    text = stringResource(text),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(MembersSheetTags.InviteFeedback),
                )
            }
            TextButton(
                onClick = { onInvite(email, role) },
                enabled = email.isNotBlank() && inviteState != InviteUiState.Loading,
                modifier = Modifier.testTag(MembersSheetTags.InviteButton),
            ) {
                Text(stringResource(R.string.members_invite_action))
            }
        }
    }
}

@Composable
private fun RoleBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Avatar(member: ShareMember, size: Dp) {
    val bg = colorForUser(member.uid)
    val label = member.displayName.ifBlank { member.email }
    Box(
        modifier = Modifier.size(size).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
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
                    text = label.trim().firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (bg.luminance() > 0.5f) Color.Black else Color.White,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun roleLabel(role: ShareRole): Int = when (role) {
    ShareRole.OWNER -> R.string.members_role_owner
    ShareRole.MANAGER -> R.string.members_role_manager
    ShareRole.EDITOR -> R.string.members_role_editor
    ShareRole.VIEWER -> R.string.members_role_viewer
}

/** Maps an invite outcome to a (message, isError) pair, or null when there is nothing to show. */
private fun inviteFeedback(state: InviteUiState): Pair<Int, Boolean>? = when (state) {
    InviteUiState.Idle, InviteUiState.Loading -> null
    InviteUiState.Added -> R.string.members_invite_added to false
    InviteUiState.UserNotFound -> R.string.members_invite_not_found to true
    InviteUiState.AlreadyMember -> R.string.members_invite_already to true
    InviteUiState.Error -> R.string.members_invite_error to true
}
