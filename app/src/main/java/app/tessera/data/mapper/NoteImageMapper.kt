package app.tessera.data.mapper

import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteImageEntity
import app.tessera.data.remote.RemoteImageRef
import javax.inject.Inject

/**
 * Maps between a local [NoteImageEntity] and its Firestore manifest entry ([RemoteImageRef]). Pure
 * and JVM-unit-testable — no Room, no Firebase.
 */
class NoteImageMapper @Inject constructor() {
    /** The manifest entry an uploader writes for a freshly-uploaded row (must be an `UPLOADED` row). */
    fun toRemoteRef(entity: NoteImageEntity): RemoteImageRef =
        RemoteImageRef(
            driveFileId = entity.driveFileId.orEmpty(),
            resourceKey = entity.resourceKey,
            ownerUid = entity.ownerUid.orEmpty(),
            width = entity.width,
            height = entity.height,
        )

    /**
     * Projects a pulled manifest entry into the local row created for it. The row is [ImageStatus.UPLOADED]
     * with `localPath = null`: it exists purely so the renderer/downloader can resolve the image; the
     * bytes are fetched lazily on demand and never written here. `ownerUid` is copied verbatim from the
     * ref (the real owner — NOT the pulling device), so [app.tessera.data.local.dao.NoteImageDao.backfillOwner]
     * (which only touches null owners) can never mis-claim another user's image.
     */
    fun toPulledEntity(noteId: String, imageId: String, ref: RemoteImageRef): NoteImageEntity =
        NoteImageEntity(
            imageId = imageId,
            noteId = noteId,
            ownerUid = ref.ownerUid,
            localPath = null,
            driveFileId = ref.driveFileId.ifBlank { null },
            resourceKey = ref.resourceKey,
            status = ImageStatus.UPLOADED,
            width = ref.width,
            height = ref.height,
        )

    /**
     * The rows to upsert from a pulled [manifest]: exactly the entries whose `imageId` has NO existing
     * local row ([existingImageIds]). A row that already exists locally is NEVER overwritten — its local
     * state (an in-flight upload, a cached file, a FAILED marker) is always authoritative over the pull.
     */
    fun rowsToUpsert(
        noteId: String,
        manifest: Map<String, RemoteImageRef>,
        existingImageIds: Set<String>,
    ): List<NoteImageEntity> =
        manifest
            .filterKeys { it !in existingImageIds }
            .map { (imageId, ref) -> toPulledEntity(noteId, imageId, ref) }
}
