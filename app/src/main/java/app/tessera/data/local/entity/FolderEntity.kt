package app.tessera.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    indices = [
        Index("name"),
        Index("parentId"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.SET_NULL,
            deferred = true,
        ),
    ],
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String? = null,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
    val syncedAt: Long? = null,
    val color: String? = null,
    val icon: String? = null,
)
