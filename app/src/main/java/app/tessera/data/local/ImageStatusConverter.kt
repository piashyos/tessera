package app.tessera.data.local

import androidx.room.TypeConverter
import app.tessera.data.local.entity.ImageStatus

object ImageStatusConverter {
    @TypeConverter
    fun fromStatus(status: ImageStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): ImageStatus = ImageStatus.valueOf(value)
}
