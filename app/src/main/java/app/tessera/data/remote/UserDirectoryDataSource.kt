package app.tessera.data.remote

import app.tessera.domain.model.UserId

/**
 * Looks up registered users by email for the invite flow. Backed by the `usersByEmail/{emailKey}`
 * index, which [app.tessera.data.repository.AuthRepositoryImpl] upserts on every sign-in.
 */
interface UserDirectoryDataSource {
    /**
     * Resolves [email] to a registered user (id + public profile), or null when no user owns that
     * email. Invites are only allowed for already-registered users, so null surfaces as "user not
     * found" in the UI. The profile fields populate the new member's avatar/name without waiting
     * for that user's device to sync.
     */
    suspend fun lookupByEmail(email: String): DirectoryUser?
}

/** A registered user resolved from the directory, carrying enough profile to render a member. */
data class DirectoryUser(
    val uid: UserId,
    val displayName: String?,
    val email: String,
    val photoUrl: String?,
)

/** Normalizes an email into the `usersByEmail` document key (lower-cased, trimmed). */
fun emailKey(email: String): String = email.trim().lowercase()
