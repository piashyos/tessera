package app.tessera.data.remote

/**
 * Firestore document shape for a folder stored under `users/{uid}/folders/{id}`.
 *
 * No-arg defaults are required for Firestore's reflective deserialization. Like
 * [RemoteNote], `syncedAt` is intentionally absent — it is purely local bookkeeping.
 * `parentId = null` means root; old documents without the field deserialize as root.
 */
data class RemoteFolder(
    val id: String = "",
    val name: String = "",
    val parentId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)
