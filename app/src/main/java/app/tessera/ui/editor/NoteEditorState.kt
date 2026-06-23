package app.tessera.ui.editor

import app.tessera.domain.model.ShareMember
import app.tessera.domain.model.ShareRole
import app.tessera.domain.model.canDeleteForAll
import app.tessera.domain.model.canEditContent
import app.tessera.domain.model.canLeave
import app.tessera.domain.model.canManageAccess

data class NoteEditorState(
    val noteId: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val availableTags: List<String> = emptyList(),
    /** Note's last-modified epoch millis, shown in the editor meta row. 0 = unknown/new. */
    val updatedAt: Long = 0L,
    val isLoading: Boolean = true,
    // --- Sharing (Phase 4) ---
    /** The signed-in user's id, used to hide their own avatar and resolve [myRole]. */
    val currentUserId: String? = null,
    /** True when this note lives in `sharedNotes` (has at least its owner as a member). */
    val isShared: Boolean = false,
    /** True when the current user owns this private note and may therefore share it. */
    val isOwner: Boolean = false,
    /** The current user's role on this note, or null when private / unknown. */
    val myRole: ShareRole? = null,
    /** Live member list of the shared note (includes the owner); empty when private. */
    val members: List<ShareMember> = emptyList(),
    val membersSheetVisible: Boolean = false,
    val inviteState: InviteUiState = InviteUiState.Idle,
    /** One-shot message for a share action that failed; null when there's nothing to surface. */
    val shareError: String? = null,
) {
    /** Viewers see the note but cannot type — the editor body is rendered read-only. */
    val isReadOnly: Boolean get() = isShared && myRole == ShareRole.VIEWER

    /** May edit title/body/tags/pin. Private notes are always editable by their single user. */
    val canEditContent: Boolean get() = !isShared || (myRole?.canEditContent == true)

    /** Owner of a private note → can promote it to shared. */
    val canMakeShared: Boolean get() = !isShared && isOwner

    /** Owner/Manager of a shared note → can open the members sheet and manage access. */
    val canManageAccess: Boolean get() = isShared && (myRole?.canManageAccess == true)

    /** Non-owner member → can leave the shared note. */
    val canLeave: Boolean get() = isShared && (myRole?.canLeave == true)

    /** Owner/Manager → can delete the note for every member. */
    val canDeleteForAll: Boolean get() = isShared && (myRole?.canDeleteForAll == true)

    /** Only the owner can pull the note back out of sharing (make it private again). */
    val canUnshare: Boolean get() = isShared && myRole == ShareRole.OWNER
}
