package app.tessera.data.remote

/**
 * Firestore document shape for a folder stored under `users/{uid}/folders/{id}`.
 *
 * No-arg defaults are required for Firestore's reflective deserialization. Like
 * [RemoteNote], `syncedAt` is intentionally absent — it is purely local bookkeeping.
 */
data class RemoteFolder(
    val id: String = "",
    val name: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)
