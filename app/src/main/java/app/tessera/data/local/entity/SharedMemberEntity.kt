package app.tessera.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Local mirror of one entry in `sharedNotes/{noteId}.members`. Cached so member avatars render
 * offline and without extra remote reads. Rebuilt wholesale from the remote `members` map on sync.
 */
@Entity(
    tableName = "shared_members",
    primaryKeys = ["noteId", "uid"],
    indices = [Index("noteId")],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SharedMemberEntity(
    val noteId: String,
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String?,
    val role: String,
    val joinedAt: Long,
)
