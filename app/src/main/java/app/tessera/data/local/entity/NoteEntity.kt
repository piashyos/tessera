package app.tessera.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        Index("ownerId", "updatedAt"),
        Index("isPinned", "updatedAt"),
        Index("deletedAt"),
    ],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncedAt: Long?,
    val ownerId: String?,
    val isArchived: Boolean = false,
    /** "PRIVATE" | "SHARED" — whether the note lives in the owner's private subtree or `sharedNotes`. */
    val shareState: String = SHARE_STATE_PRIVATE,
    /** Current user's role (OWNER/MANAGER/EDITOR/VIEWER) when shared; null while private. */
    val myRole: String? = null,
) {
    companion object {
        const val SHARE_STATE_PRIVATE = "PRIVATE"
        const val SHARE_STATE_SHARED = "SHARED"
    }
}
