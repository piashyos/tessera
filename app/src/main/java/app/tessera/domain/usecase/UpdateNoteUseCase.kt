package app.tessera.domain.usecase

import app.tessera.domain.model.Note
import app.tessera.domain.repository.NotesRepository
import javax.inject.Inject

class UpdateNoteUseCase @Inject constructor(
    private val notesRepository: NotesRepository,
) {
    suspend operator fun invoke(note: Note): Note = notesRepository.updateNote(note)
}
