package app.tessera.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** See spec: LOCAL_ONLY → UPLOADING → UPLOADED; FAILED / ACCESS_LOST = "no automatic retry", both recoverable. */
enum class ImageStatus { LOCAL_ONLY, UPLOADING, UPLOADED, FAILED, ACCESS_LOST }

/**
 * A Drive-backed image attached to a note. Deliberately has NO foreign key to `notes` — an image
 * row must survive its note's local disappearance (e.g. the ACCESS_LOST recovery flow depends on
 * the row outliving the note that referenced it).
 */
@Entity(
    tableName = "note_images",
    indices = [Index("noteId"), Index("status")],
)
data class NoteImageEntity(
    @PrimaryKey val imageId: String,
    val noteId: String,
    val ownerUid: String?,
    val localPath: String?,
    val driveFileId: String? = null,
    val resourceKey: String? = null,
    val status: ImageStatus = ImageStatus.LOCAL_ONLY,
    val width: Int,
    val height: Int,
    val permissionsSyncedAt: Long? = null,
    val orphanedAt: Long? = null,
)
