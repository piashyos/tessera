package app.tessera.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteImageEntity

@Dao
interface NoteImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(image: NoteImageEntity)

    @Query("SELECT * FROM note_images WHERE imageId = :imageId")
    suspend fun byId(imageId: String): NoteImageEntity?

    @Query("SELECT * FROM note_images WHERE noteId = :noteId")
    suspend fun byNote(noteId: String): List<NoteImageEntity>

    @Query("SELECT * FROM note_images WHERE ownerUid = :uid AND status = :status")
    suspend fun byOwnerAndStatus(uid: String, status: ImageStatus): List<NoteImageEntity>

    @Query("SELECT * FROM note_images WHERE ownerUid = :uid")
    suspend fun byOwner(uid: String): List<NoteImageEntity>

    @Query("UPDATE note_images SET ownerUid = :uid WHERE ownerUid IS NULL")
    suspend fun backfillOwner(uid: String)

    @Query("UPDATE note_images SET status = :to WHERE ownerUid = :uid AND status = :from")
    suspend fun transition(uid: String, from: ImageStatus, to: ImageStatus)

    @Query("DELETE FROM note_images WHERE imageId = :imageId")
    suspend fun delete(imageId: String)
}
