package app.tessera.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.tessera.data.local.entity.NoteTagCrossRef
import app.tessera.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TagEntity>)

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
