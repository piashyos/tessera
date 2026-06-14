package app.tessera.domain.repository

import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.UserId
import kotlinx.coroutines.flow.Flow

interface NotesRepository {
    fun observeNotes(): Flow<List<Note>>

    fun observeNote(id: NoteId): Flow<Note?>

    fun searchNotes(query: String): Flow<List<Note>>

    suspend fun getNote(id: NoteId): Note?

    suspend fun createNote(ownerId: UserId? = null): Note

    suspend fun updateNote(note: Note): Note

    suspend fun deleteNote(id: NoteId)

    suspend fun restoreNote(id: NoteId)

    suspend fun togglePin(id: NoteId)
}
