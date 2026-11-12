package app.tessera.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index("name", unique = true)],
)
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String? = null,
    val icon: String? = null,
    val updatedAt: Long = 0,
    val syncedAt: Long? = null,
)
