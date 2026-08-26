package app.tessera.data.remote

/**
 * Firestore document shape for a shared note stored at top level `sharedNotes/{id}`.
 *
 * Unlike private notes (which live under each owner's `users/{uid}/notes` subtree), shared notes
 * live in one collection so any member can read them via a single `whereArrayContains` query and
 * the security rules can authorize by membership.
 *
 * No-arg defaults are required for Firestore's reflective deserialization.
 *
 * @property members map keyed by member uid, so a single member's role can be changed atomically
 *   and the current user's role is cheap to read in security rules.
 * @property memberIds duplicate of [members] keys, kept because Firestore cannot query by map keys
 *   — `whereArrayContains("memberIds", uid)` is how each device finds the notes shared with it.
 */
data class RemoteSharedNote(
    val id: String = "",
    val ownerId: String = "",
    val title: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    val members: Map<String, RemoteShareMember> = emptyMap(),
    val memberIds: List<String> = emptyList(),
)

/** One entry of [RemoteSharedNote.members]. No-arg defaults for Firestore deserialization. */
data class RemoteShareMember(
    val role: String = "VIEWER",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val joinedAt: Long = 0L,
)
