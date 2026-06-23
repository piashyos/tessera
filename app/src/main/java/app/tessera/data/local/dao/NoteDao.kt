package app.tessera.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.tessera.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query(
        "SELECT * FROM notes " +
            "WHERE deletedAt IS NULL AND isArchived = 0 " +
            "ORDER BY isPinned DESC, updatedAt DESC",
    )
    fun observeActive(): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes " +
            "WHERE deletedAt IS NULL AND isArchived = 1 " +
            "ORDER BY isPinned DESC, updatedAt DESC",
    )
    fun observeArchived(): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes " +
            "WHERE deletedAt IS NOT NULL " +
            "ORDER BY deletedAt DESC",
    )
    fun observeDeleted(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Query(
        """
        SELECT notes.* FROM notes
        JOIN notes_fts ON notes.rowid = notes_fts.rowid
        WHERE notes_fts MATCH :query AND notes.deletedAt IS NULL AND notes.isArchived = 0
        ORDER BY notes.isPinned DESC, notes.updatedAt DESC
        """,
    )
    fun search(query: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Query("UPDATE notes SET deletedAt = :ts, updatedAt = :ts, syncedAt = NULL WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Query("UPDATE notes SET deletedAt = NULL, updatedAt = :ts, syncedAt = NULL WHERE id = :id")
    suspend fun restore(id: String, ts: Long)

    @Query("UPDATE notes SET isPinned = :pinned, updatedAt = :ts, syncedAt = NULL WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, ts: Long)

    @Query("UPDATE notes SET isArchived = :archived, updatedAt = :ts, syncedAt = NULL WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, ts: Long)

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun purgeOldTombstones(threshold: Long): Int

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT * FROM notes WHERE syncedAt IS NULL")
    suspend fun getPendingSync(): List<NoteEntity>

    @Query("UPDATE notes SET syncedAt = :ts WHERE id = :id")
    suspend fun markSynced(id: String, ts: Long)

    @Query("UPDATE notes SET syncedAt = NULL, updatedAt = :ts WHERE id = :id")
    suspend fun markPendingSync(id: String, ts: Long)

    @Query("SELECT DISTINCT ownerId FROM notes WHERE ownerId IS NOT NULL LIMIT 1")
    suspend fun getCurrentOwnerId(): String?

    @Query("UPDATE notes SET ownerId = :uid, syncedAt = NULL WHERE ownerId IS NULL")
    suspend fun adoptLocalNotes(uid: String)

    @Query("UPDATE notes SET ownerId = :uid, syncedAt = NULL")
    suspend fun reassignAllNotes(uid: String)

    @Query("DELETE FROM notes WHERE ownerId = :uid")
    suspend fun deleteByOwner(uid: String)
}
