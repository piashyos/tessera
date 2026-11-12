package app.tessera.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.tessera.data.local.entity.NoteTagCrossRef
import app.tessera.data.local.entity.TagEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Query("SELECT * FROM tags WHERE color IS NOT NULL OR icon IS NOT NULL")
    fun observeAppearances(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE syncedAt IS NULL AND updatedAt > 0")
    suspend fun getPendingSync(): List<TagEntity>

    @Query("UPDATE tags SET syncedAt = :ts WHERE name = :name AND updatedAt = :updatedAt")
    suspend fun markSynced(name: String, ts: Long, updatedAt: Long)

    @Query("UPDATE tags SET color = :color, icon = :icon, updatedAt = :updatedAt, syncedAt = :syncedAt WHERE name = :name")
    suspend fun updateAppearance(name: String, color: String?, icon: String?, updatedAt: Long, syncedAt: Long?)

    /** Creates the row if the name is new, then writes the appearance; never replaces (note_tags has no FK). */
    @Transaction
    suspend fun setAppearance(name: String, color: String?, icon: String?, ts: Long) {
        insertTags(listOf(TagEntity(id = UUID.randomUUID().toString(), name = name)))
        updateAppearance(name, color, icon, ts, null)
    }

    /** Same shape as [setAppearance] but keeps the remote version and marks it synced. */
    @Transaction
    suspend fun applyRemote(name: String, color: String?, icon: String?, updatedAt: Long, syncedAt: Long) {
        insertTags(listOf(TagEntity(id = UUID.randomUUID().toString(), name = name)))
        updateAppearance(name, color, icon, updatedAt, syncedAt)
    }

    @Query("SELECT * FROM tags WHERE name IN (:names)")
    suspend fun findByNames(names: List<String>): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkTagsToNote(refs: List<NoteTagCrossRef>)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun clearTagsForNote(noteId: String)

    @Query("SELECT * FROM note_tags")
    fun observeAllCrossRefs(): Flow<List<NoteTagCrossRef>>

    @Query(
        """
        SELECT tags.name FROM tags
        JOIN note_tags ON tags.id = note_tags.tagId
        WHERE note_tags.noteId = :noteId
        """,
    )
    suspend fun tagsForNote(noteId: String): List<String>

    @Query(
        """
        SELECT tags.name FROM tags
        JOIN note_tags ON tags.id = note_tags.tagId
        WHERE note_tags.noteId = :noteId
        """,
    )
    fun observeTagsForNote(noteId: String): Flow<List<String>>
}
