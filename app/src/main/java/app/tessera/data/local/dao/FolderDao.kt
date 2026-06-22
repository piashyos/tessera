package app.tessera.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.tessera.data.local.entity.FolderEntity
import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.local.entity.NoteFolderCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderEntity)

    @Query("UPDATE folders SET deletedAt = :ts, updatedAt = :ts, syncedAt = NULL WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("UPDATE folders SET name = :name, updatedAt = :ts, syncedAt = NULL WHERE id = :id")
    suspend fun rename(id: String, name: String, ts: Long)

    @Query("UPDATE folders SET parentId = :parentId, updatedAt = :ts, syncedAt = NULL WHERE id = :id")
    suspend fun updateParent(id: String, parentId: String?, ts: Long)

    @Query("SELECT * FROM folders WHERE syncedAt IS NULL")
    suspend fun getPendingSync(): List<FolderEntity>

    @Query("UPDATE folders SET syncedAt = :ts WHERE id = :id")
    suspend fun markSynced(id: String, ts: Long)

    @Query("DELETE FROM folders WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun purgeOldTombstones(threshold: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun assignNote(ref: NoteFolderCrossRef)

    @Query("DELETE FROM note_folders WHERE noteId = :noteId")
    suspend fun clearAssignment(noteId: String)

    @Query("SELECT folderId FROM note_folders WHERE noteId = :noteId LIMIT 1")
    fun observeFolderForNote(noteId: String): Flow<String?>

    @Query("SELECT folderId FROM note_folders WHERE noteId = :noteId LIMIT 1")
    suspend fun folderIdForNote(noteId: String): String?

    @Query(
        """
        SELECT notes.* FROM notes
        JOIN note_folders ON notes.id = note_folders.noteId
        WHERE note_folders.folderId = :folderId AND notes.deletedAt IS NULL
        ORDER BY notes.isPinned DESC, notes.updatedAt DESC
        """,
    )
    fun observeNotesInFolder(folderId: String): Flow<List<NoteEntity>>
}
