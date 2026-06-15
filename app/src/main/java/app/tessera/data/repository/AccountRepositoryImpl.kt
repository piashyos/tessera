package app.tessera.data.repository

import app.tessera.data.local.dao.NoteDao
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.AccountRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
) : AccountRepository {
    override suspend fun currentOwnerId(): UserId? =
        noteDao.getCurrentOwnerId()?.let(::UserId)

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
