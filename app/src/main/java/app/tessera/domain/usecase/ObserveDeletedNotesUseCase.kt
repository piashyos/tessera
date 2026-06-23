package app.tessera.domain.usecase

import app.tessera.domain.model.Note
import app.tessera.domain.repository.NotesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveDeletedNotesUseCase @Inject constructor(
    private val notesRepository: NotesRepository,
) {
    operator fun invoke(): Flow<List<Note>> = notesRepository.observeDeletedNotes()
}
