package app.tessera.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.tessera.data.local.entity.SharedMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedMemberDao {
    @Query("SELECT * FROM shared_members WHERE noteId = :noteId ORDER BY joinedAt ASC")
    fun observeForNote(noteId: String): Flow<List<SharedMemberEntity>>

    @Query("SELECT * FROM shared_members WHERE noteId = :noteId ORDER BY joinedAt ASC")
    suspend fun getForNote(noteId: String): List<SharedMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(members: List<SharedMemberEntity>)

    @Query("DELETE FROM shared_members WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: String)

    /** Rebuilds a note's member set wholesale from the remote `members` map. */
    @Transaction
    suspend fun replaceForNote(noteId: String, members: List<SharedMemberEntity>) {
        deleteForNote(noteId)
        if (members.isNotEmpty()) upsertAll(members)
    }
}
