package app.tessera.domain.model

/**
 * A participant of a shared note. Mirrors one entry of `sharedNotes/{id}.members` and is cached
 * locally (table `shared_members`) so avatars render offline without extra reads.
 */
data class ShareMember(
    val uid: UserId,
    val displayName: String,
    val email: String,
    val photoUrl: String?,
    val role: ShareRole,
    val joinedAt: Long,
)
