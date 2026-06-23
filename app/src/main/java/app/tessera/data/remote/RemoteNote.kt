package app.tessera.data.remote

/**
 * Firestore document shape for a note stored under `users/{uid}/notes/{id}`.
 *
 * No-arg defaults are required for Firestore's reflective deserialization. There is no
 * `syncedAt` field on purpose — that flag is local-only bookkeeping and never leaves the device.
 */
data class RemoteNote(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
    /** Folder this note belongs to, null when assigned to "All Notes". */
    val folderId: String? = null,
    val isArchived: Boolean = false,
)
