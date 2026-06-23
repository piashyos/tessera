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
)
