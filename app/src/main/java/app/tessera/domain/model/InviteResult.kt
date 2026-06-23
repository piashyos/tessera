package app.tessera.domain.model

/**
 * Outcome of inviting a user to a shared note by email. Invites only target already-registered
 * users, so a missing email is a first-class result rather than an error.
 */
sealed interface InviteResult {
    /** The user was found and added (or their pending invite was created). */
    data object Added : InviteResult

    /** No registered user owns that email — surfaced as "user not found" in the UI. */
    data object UserNotFound : InviteResult

    /** The user is already a member of this note — a no-op with a friendly message. */
    data object AlreadyMember : InviteResult
}
