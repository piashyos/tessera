package app.tessera.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
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

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("UPDATE folders SET deletedAt = :ts, updatedAt = :ts, syncedAt = NULL WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("UPDATE folders SET name = :name, color = :color, icon = :icon, updatedAt = :ts, syncedAt = NULL WHERE id = :id")
    suspend fun update(id: String, name: String, color: String?, icon: String?, ts: Long)

    @Query("UPDATE folders SET parentId = :parentId, updatedAt = :ts, syncedAt = NULL WHERE id = :id")
    suspend fun updateParent(id: String, parentId: String?, ts: Long)

    @Query("SELECT * FROM folders WHERE syncedAt IS NULL")
    suspend fun getPendingSync(): List<FolderEntity>

    @Query("UPDATE folders SET syncedAt = :ts WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, ts: Long, updatedAt: Long)

    @Query("DELETE FROM folders WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun purgeOldTombstones(threshold: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun assignNote(ref: NoteFolderCrossRef)

    @Query("SELECT * FROM note_folders")
    fun observeAllAssignments(): Flow<List<NoteFolderCrossRef>>

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
        WHERE note_folders.folderId = :folderId AND notes.deletedAt IS NULL AND notes.isArchived = 0
        ORDER BY notes.isPinned DESC, notes.updatedAt DESC
        """,
    )
    fun observeNotesInFolder(folderId: String): Flow<List<NoteEntity>>
}
