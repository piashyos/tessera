package app.tessera.domain.usecase

import app.tessera.domain.model.NoteId
import app.tessera.domain.repository.AuthRepository
import app.tessera.domain.repository.NotesRepository
import app.tessera.domain.repository.SyncRepository
import javax.inject.Inject

class PermanentlyDeleteNoteUseCase @Inject constructor(
    private val notesRepository: NotesRepository,
    private val syncRepository: SyncRepository,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(id: NoteId) {
        invoke(listOf(id))
    }

    /**
     * Permanently removes [ids]. When signed in the cloud documents are deleted first (via
     * [SyncRepository]) so a pull can't resurrect them; otherwise a local-only hard delete runs.
     */
    suspend operator fun invoke(ids: List<NoteId>) {
        if (ids.isEmpty()) return
        val uid = authRepository.currentUser.value
        if (uid != null) {
            syncRepository.permanentlyDelete(uid, ids)
        } else {
            ids.forEach { notesRepository.permanentlyDeleteNote(it) }
        }
    }
}
