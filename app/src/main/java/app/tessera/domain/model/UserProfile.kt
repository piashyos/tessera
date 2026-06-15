package app.tessera.domain.model

/**
 * Public profile of the signed-in user, sourced from the auth provider (Google → Firebase).
 */
data class UserProfile(
    val id: UserId,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
)
