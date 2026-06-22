package app.tessera.data.repository

import app.tessera.core.util.Clock
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.dao.TagDao
import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.local.entity.NoteTagCrossRef
import app.tessera.data.local.entity.TagEntity
import app.tessera.data.mapper.NoteMapper
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotesRepositoryImplTest {
    @Test
    fun `createNote stamps timestamps and stores draft`() =
        runTest {
            val noteDao = FakeNoteDao()
            val tagDao = FakeTagDao()
            val clock = MutableClock(111L)
            val repository = NotesRepositoryImpl(noteDao, tagDao, NoteMapper(), clock, FakeSyncScheduler())

            val note = repository.createNote(ownerId = UserId("user-1"))

            val stored = noteDao.getById(note.id.value)
            assertNotNull(stored)
            assertEquals(111L, note.createdAt)
            assertEquals(111L, note.updatedAt)
            assertEquals(null, note.syncedAt)
            assertEquals("user-1", stored?.ownerId)
        }

    @Test
    fun `updateNote persists normalized tags and bumps updatedAt`() =
        runTest {
            val noteDao = FakeNoteDao()
            val tagDao = FakeTagDao()
            val clock = MutableClock(10L)
            val repository = NotesRepositoryImpl(noteDao, tagDao, NoteMapper(), clock, FakeSyncScheduler())
            val created = repository.createNote()

            clock.now = 25L
            val updated =
                repository.updateNote(
                    created.copy(
                        title = "Hello",
                        content = "World",
                        tags = listOf(Tag("beta"), Tag(" alpha "), Tag("beta")),
                        syncedAt = 99L,
                    ),
                )

            val stored = noteDao.getById(created.id.value)
            assertEquals(25L, stored?.updatedAt)
            assertEquals(null, stored?.syncedAt)
            assertEquals(listOf(Tag("alpha"), Tag("beta")), updated.tags)
            assertEquals(listOf("alpha", "beta"), tagDao.tagsForNote(created.id.value))
        }

    @Test
    fun `updateNote hard deletes blank drafts`() =
        runTest {
            val noteDao = FakeNoteDao()
            val tagDao = FakeTagDao()
            val clock = MutableClock(10L)
            val repository = NotesRepositoryImpl(noteDao, tagDao, NoteMapper(), clock, FakeSyncScheduler())
            val created = repository.createNote()

            repository.updateNote(
                created.copy(
                    title = "Filled",
                    content = "Body",
                    tags = listOf(Tag("keep")),
                ),
            )

            repository.updateNote(
                created.copy(
                    title = " ",
                    content = "",
                    tags = emptyList(),
                ),
            )

            assertNull(noteDao.getById(created.id.value))
            assertTrue(tagDao.tagsForNote(created.id.value).isEmpty())
        }

    @Test
    fun `deleteNote soft deletes using current clock time`() =
        runTest {
            val noteDao = FakeNoteDao()
            val tagDao = FakeTagDao()
            val clock = MutableClock(10L)
            val repository = NotesRepositoryImpl(noteDao, tagDao, NoteMapper(), clock, FakeSyncScheduler())
            val created = repository.createNote()

            clock.now = 77L
            repository.deleteNote(created.id)

            val stored = noteDao.getById(created.id.value)
            assertEquals(77L, stored?.deletedAt)
            assertEquals(77L, stored?.updatedAt)
            assertFalse(noteDao.observeActive().first().any { it.id == created.id.value })
        }

    @Test
    fun `togglePin flips existing note state`() =
        runTest {
            val noteDao = FakeNoteDao()
            val tagDao = FakeTagDao()
            val clock = MutableClock(10L)
            val repository = NotesRepositoryImpl(noteDao, tagDao, NoteMapper(), clock, FakeSyncScheduler())
            val created = repository.createNote()
            repository.updateNote(created.copy(title = "Pinned", content = "later"))

            clock.now = 33L
            repository.togglePin(created.id)

            val stored = noteDao.getById(created.id.value)
            assertEquals(true, stored?.isPinned)
            assertEquals(33L, stored?.updatedAt)
            assertEquals(null, stored?.syncedAt)
        }

    @Test
    fun `searchNotes returns mapped notes with tags`() =
        runTest {
            val noteDao = FakeNoteDao()
            val tagDao = FakeTagDao()
            val clock = MutableClock(10L)
            val repository = NotesRepositoryImpl(noteDao, tagDao, NoteMapper(), clock, FakeSyncScheduler())
            val first = repository.createNote()
            val second = repository.createNote()

            repository.updateNote(
                first.copy(
                    title = "Kotlin tips",
                    content = "Flow and Room",
                    tags = listOf(Tag("android")),
                ),
            )
            repository.updateNote(
                second.copy(
                    title = "Gardening",
                    content = "Tomatoes only",
                    tags = listOf(Tag("home")),
                ),
            )

            val results = repository.searchNotes("flow").first()

            assertEquals(1, results.size)
            assertEquals(NoteId(first.id.value), results.single().id)
            assertEquals(listOf(Tag("android")), results.single().tags)
        }

    @Test
    fun `observeNotes excludes blank drafts`() =
        runTest {
            val noteDao = FakeNoteDao()
            val tagDao = FakeTagDao()
            val repository = NotesRepositoryImpl(noteDao, tagDao, NoteMapper(), MutableClock(10L), FakeSyncScheduler())
            val draft = repository.createNote()

            assertTrue(repository.observeNotes().first().isEmpty())
            assertNotNull(noteDao.getById(draft.id.value))
        }

    @Test
    fun `restoreNote clears deletedAt and resurfaces the note`() =
        runTest {
            val noteDao = FakeNoteDao()
            val tagDao = FakeTagDao()
            val clock = MutableClock(10L)
            val repository = NotesRepositoryImpl(noteDao, tagDao, NoteMapper(), clock, FakeSyncScheduler())
            val created = repository.createNote()
            repository.updateNote(created.copy(title = "Live", content = "Body"))

            clock.now = 50L
            repository.deleteNote(created.id)

            clock.now = 88L
            repository.restoreNote(created.id)

            val stored = noteDao.getById(created.id.value)
            assertNull(stored?.deletedAt)
            assertEquals(88L, stored?.updatedAt)
            assertTrue(noteDao.observeActive().first().any { it.id == created.id.value })
        }

    @Test
    fun `togglePin on blank draft keeps the note instead of deleting it`() =
        runTest {
            val noteDao = FakeNoteDao()
            val tagDao = FakeTagDao()
            val repository = NotesRepositoryImpl(noteDao, tagDao, NoteMapper(), MutableClock(10L), FakeSyncScheduler())
            val draft = repository.createNote()

            repository.togglePin(draft.id)

            val stored = noteDao.getById(draft.id.value)
            assertNotNull(stored)
            assertEquals(true, stored?.isPinned)
        }

    @Test
    fun `togglePin preserves existing tags`() =
        runTest {
            val noteDao = FakeNoteDao()
            val tagDao = FakeTagDao()
            val repository = NotesRepositoryImpl(noteDao, tagDao, NoteMapper(), MutableClock(10L), FakeSyncScheduler())
            val created = repository.createNote()
            repository.updateNote(created.copy(title = "Pin", content = "me", tags = listOf(Tag("keep"))))

            repository.togglePin(created.id)

            assertEquals(listOf("keep"), tagDao.tagsForNote(created.id.value))
        }

    @Test
    fun `searchNotes falls back to all notes when query has only operators`() =
        runTest {
            val noteDao = FakeNoteDao()
            val tagDao = FakeTagDao()
            val repository = NotesRepositoryImpl(noteDao, tagDao, NoteMapper(), MutableClock(10L), FakeSyncScheduler())
            val created = repository.createNote()
            repository.updateNote(created.copy(title = "Hello", content = "World"))

            val results = repository.searchNotes("\"*").first()

            assertEquals(1, results.size)
        }

    @Test
    fun `toFtsMatchQuery strips operators and appends prefix wildcard`() {
        assertEquals("ab*", "a\"b".toFtsMatchQuery())
        assertEquals("foobar*", "foo-bar".toFtsMatchQuery())
        assertEquals("kotlin* flow*", "kotlin flow".toFtsMatchQuery())
        assertEquals("", "  \"^*  ".toFtsMatchQuery())
    }

    @Test
    fun `mutations request a sync push`() = runTest {
        val noteDao = FakeNoteDao()
        val tagDao = FakeTagDao()
        val clock = MutableClock(1L)
        val scheduler = FakeSyncScheduler()
        val repository = NotesRepositoryImpl(noteDao, tagDao, NoteMapper(), clock, scheduler)

        val note = repository.createNote(ownerId = UserId("u1"))
        assertEquals(1, scheduler.requestCount, "createNote should request sync")

        repository.updateNote(note.copy(title = "Hi", content = "There"))
        assertEquals(2, scheduler.requestCount, "updateNote should request sync")

        repository.togglePin(note.id)
        assertEquals(3, scheduler.requestCount, "togglePin should request sync")

        repository.deleteNote(note.id)
        assertEquals(4, scheduler.requestCount, "deleteNote should request sync")

        repository.restoreNote(note.id)
        assertEquals(5, scheduler.requestCount, "restoreNote should request sync")

        // Blank-draft branch of updateNote (hardDelete path) also requests a sync.
        val draft = repository.createNote(ownerId = UserId("u1"))
        val before = scheduler.requestCount
        repository.updateNote(draft.copy(title = "", content = ""))
        assertEquals(before + 1, scheduler.requestCount, "blank-draft updateNote should request sync")
    }
}

private class FakeSyncScheduler : SyncScheduler {
    var requestCount = 0
    override fun requestSync() { requestCount++ }
}

private class MutableClock(
    var now: Long,
) : Clock {
    override fun nowMs(): Long = now
}

private class FakeNoteDao : NoteDao {
    private val notes = linkedMapOf<String, NoteEntity>()
    private val activeFlow = MutableStateFlow(emptyList<NoteEntity>())
    private val noteFlows = mutableMapOf<String, MutableStateFlow<NoteEntity?>>()

    override fun observeActive(): Flow<List<NoteEntity>> = activeFlow

    override fun observeById(id: String): Flow<NoteEntity?> =
        noteFlows.getOrPut(id) { MutableStateFlow(notes[id]) }

    override suspend fun getById(id: String): NoteEntity? = notes[id]

    override fun search(query: String): Flow<List<NoteEntity>> =
        activeFlow.map { list ->
            val terms = query.split(" ").map { it.removeSuffix("*").lowercase() }
            list.filter { note ->
                val haystack = "${note.title} ${note.content}".lowercase()
                terms.all(haystack::contains)
            }
        }

    override suspend fun upsert(note: NoteEntity) {
        notes[note.id] = note
        emitChanges(note.id)
    }

    override suspend fun upsertAll(notes: List<NoteEntity>) {
        notes.forEach { note -> this.notes[note.id] = note }
        emitChanges(*notes.map { it.id }.toTypedArray())
    }

    override suspend fun softDelete(id: String, ts: Long) {
        val current = notes[id] ?: return
        notes[id] = current.copy(deletedAt = ts, updatedAt = ts, syncedAt = null)
        emitChanges(id)
    }

    override suspend fun restore(id: String, ts: Long) {
        val current = notes[id] ?: return
        notes[id] = current.copy(deletedAt = null, updatedAt = ts, syncedAt = null)
        emitChanges(id)
    }

    override suspend fun setPinned(id: String, pinned: Boolean, ts: Long) {
        val current = notes[id] ?: return
        notes[id] = current.copy(isPinned = pinned, updatedAt = ts, syncedAt = null)
        emitChanges(id)
    }

    override suspend fun purgeOldTombstones(threshold: Long): Int {
        val toDelete = notes.values.filter { it.deletedAt != null && it.deletedAt < threshold }
        toDelete.forEach { notes.remove(it.id) }
        emitChanges(*toDelete.map { it.id }.toTypedArray())
        return toDelete.size
    }

    override suspend fun hardDelete(id: String) {
        notes.remove(id)
        emitChanges(id)
    }

    override suspend fun getPendingSync(): List<NoteEntity> =
        notes.values.filter { it.syncedAt == null }

    override suspend fun markSynced(id: String, ts: Long) {
        val current = notes[id] ?: return
        notes[id] = current.copy(syncedAt = ts)
        emitChanges(id)
    }

    override suspend fun markPendingSync(id: String, ts: Long) {
        val current = notes[id] ?: return
        notes[id] = current.copy(syncedAt = null, updatedAt = ts)
        emitChanges(id)
    }

    override suspend fun getCurrentOwnerId(): String? =
        notes.values.firstOrNull { it.ownerId != null }?.ownerId

    override suspend fun adoptLocalNotes(uid: String) {
        notes.entries.forEach { entry ->
            val note = entry.value
            if (note.ownerId == null) {
                entry.setValue(note.copy(ownerId = uid, syncedAt = null))
            }
        }
        emitChanges(*notes.keys.toTypedArray())
    }

    override suspend fun reassignAllNotes(uid: String) {
        notes.entries.forEach { entry ->
            entry.setValue(entry.value.copy(ownerId = uid, syncedAt = null))
        }
        emitChanges(*notes.keys.toTypedArray())
    }

    override suspend fun deleteByOwner(uid: String) {
        val toDelete = notes.values.filter { it.ownerId == uid }.map { it.id }
        toDelete.forEach { notes.remove(it) }
        emitChanges(*toDelete.toTypedArray())
    }

    private fun emitChanges(vararg ids: String) {
        activeFlow.value =
            notes.values
                .filter { it.deletedAt == null }
                .sortedWith(compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.updatedAt })

        ids.distinct().forEach { id ->
            noteFlows.getOrPut(id) { MutableStateFlow(notes[id]) }.value = notes[id]
        }
    }
}

private class FakeTagDao : TagDao {
    private val tagsByName = linkedMapOf<String, TagEntity>()
    private val tagIdsByNote = mutableMapOf<String, MutableSet<String>>()
    private val allTagsFlow = MutableStateFlow(emptyList<TagEntity>())
    private val tagsForNoteFlows = mutableMapOf<String, MutableStateFlow<List<String>>>()

    override fun observeAll(): Flow<List<TagEntity>> = allTagsFlow

    override suspend fun insertTags(tags: List<TagEntity>) {
        tags.forEach { tag ->
            tagsByName.putIfAbsent(tag.name, tag)
        }
        emitAllTags()
    }

    override suspend fun findByNames(names: List<String>): List<TagEntity> =
        names.mapNotNull(tagsByName::get)

    override suspend fun linkTagsToNote(refs: List<NoteTagCrossRef>) {
        refs.forEach { ref ->
            val noteTags = tagIdsByNote.getOrPut(ref.noteId) { linkedSetOf() }
            noteTags.add(ref.tagId)
        }
        refs.map(NoteTagCrossRef::noteId).distinct().forEach { noteId ->
            emitTagsForNote(noteId)
        }
    }

    override suspend fun clearTagsForNote(noteId: String) {
        tagIdsByNote.remove(noteId)
        emitTagsForNote(noteId)
    }

    override suspend fun tagsForNote(noteId: String): List<String> =
        tagIdsByNote[noteId]
            .orEmpty()
            .mapNotNull { id -> tagsByName.values.firstOrNull { it.id == id }?.name }

    override fun observeTagsForNote(noteId: String): Flow<List<String>> =
        tagsForNoteFlows.getOrPut(noteId) { MutableStateFlow(emptyList()) }

    override fun observeAllCrossRefs(): Flow<List<NoteTagCrossRef>> =
        MutableStateFlow(emptyList())

    private fun emitAllTags() {
        allTagsFlow.value = tagsByName.values.sortedBy { it.name }
    }

    private suspend fun emitTagsForNote(noteId: String) {
        tagsForNoteFlows.getOrPut(noteId) { MutableStateFlow(emptyList()) }.value = tagsForNote(noteId)
    }
}
