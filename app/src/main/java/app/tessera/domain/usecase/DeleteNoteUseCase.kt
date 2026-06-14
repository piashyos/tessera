package app.tessera.domain.usecase

import app.tessera.domain.model.NoteId
import app.tessera.domain.repository.NotesRepository
import javax.inject.Inject

class DeleteNoteUseCase @Inject constructor(
    private val notesRepository: NotesRepository,
) {
    suspend operator fun invoke(id: NoteId) {
        notesRepository.deleteNote(id)
    }
}
