package app.tessera.data.repository

import app.tessera.data.local.NotesDatabase
import app.tessera.data.local.dao.NoteDao
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val database: NotesDatabase,
) : AccountRepository {
    override suspend fun currentOwnerId(): UserId? =
        noteDao.getCurrentOwnerId()?.let(::UserId)

    override suspend fun hasForeignData(uid: UserId): Boolean =
        noteDao.hasForeignNotes(uid.value)

    override suspend fun clearLocalData() {
        // clearAllTables() runs a raw delete on every table; keep it off the main thread.
        withContext(Dispatchers.IO) { database.clearAllTables() }
    }

    override suspend fun adoptLocalNotes(uid: UserId) {
        noteDao.adoptLocalNotes(uid.value)
    }

    override suspend fun moveAllNotesTo(uid: UserId) {
        noteDao.reassignAllNotes(uid.value)
    }

    override suspend fun deleteNotesOwnedBy(uid: UserId) {
        noteDao.deleteByOwner(uid.value)
    }
}
