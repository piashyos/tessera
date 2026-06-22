package app.tessera.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "note_folders",
    primaryKeys = ["noteId", "folderId"],
    indices = [
        Index("noteId", unique = true),
        Index("folderId"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class NoteFolderCrossRef(
    val noteId: String,
    val folderId: String,
)
