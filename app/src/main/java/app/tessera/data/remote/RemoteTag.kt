package app.tessera.data.remote

/**
 * Firestore document for a tag's appearance under `users/{uid}/tags/{docId}` (docId is the
 * Base64url of the name, see [FirestoreTagsClient]). Resetting the appearance is a write with
 * `color` and `icon` = null, not a delete. `syncedAt` is local bookkeeping and absent here.
 */
data class RemoteTag(
    val name: String = "",
    val color: String? = null,
    val icon: String? = null,
    val updatedAt: Long = 0L,
)
