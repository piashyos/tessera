package app.tessera.ui.editor

/**
 * Transient outcome of an invite-by-email attempt in the members sheet. Drives the inline feedback
 * under the email field (spinner / "user not found" / "already a member" / success) and resets to
 * [Idle] when the user edits the email again.
 */
sealed interface InviteUiState {
    data object Idle : InviteUiState

    data object Loading : InviteUiState

    /** A new member was added — the sheet shows a brief success hint; the chip arrives via the members flow. */
    data object Added : InviteUiState

    /** No registered user owns that email. */
    data object UserNotFound : InviteUiState

    /** The email already belongs to a member of this note. */
    data object AlreadyMember : InviteUiState

    /** The invite failed for another reason (e.g. offline — the lookup needs the network). */
    data object Error : InviteUiState
}
