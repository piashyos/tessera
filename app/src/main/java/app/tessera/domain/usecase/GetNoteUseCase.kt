package app.tessera.domain.usecase

import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.repository.NotesRepository
import javax.inject.Inject

class GetNoteUseCase @Inject constructor(
    private val notesRepository: NotesRepository,
) {
    suspend operator fun invoke(id: NoteId): Note? = notesRepository.getNote(id)
}
