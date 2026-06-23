package app.tessera.domain.model

/**
 * Whether a note is private to its owner or shared with members.
 *
 * [Shared.myRole] is the current user's role on this note, which drives the permission helpers in
 * [ShareRole].
 */
sealed interface ShareState {
    data object Private : ShareState

    data class Shared(
        val members: List<ShareMember>,
        val myRole: ShareRole,
    ) : ShareState
}
