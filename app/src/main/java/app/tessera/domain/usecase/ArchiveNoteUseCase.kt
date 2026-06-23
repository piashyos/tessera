package app.tessera.domain.usecase

import app.tessera.domain.model.NoteId
import app.tessera.domain.repository.NotesRepository
import javax.inject.Inject

/** Archives or unarchives a note. Archived notes drop out of the main list and live in the
 * drawer's Archive view until restored. */
class ArchiveNoteUseCase @Inject constructor(
    private val notesRepository: NotesRepository,
) {
    suspend operator fun invoke(id: NoteId, archived: Boolean) {
        notesRepository.setArchived(id, archived)
    }
}
