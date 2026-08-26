package app.tessera.data.remote

import com.google.firebase.firestore.util.CustomClassMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the Firestore wire mapping of [RemoteNote]/[RemoteSharedNote] against the JavaBean
 * `is`-prefix pitfall: a Kotlin `val isArchived` SERIALIZES via the `isArchived()` getter to the
 * doc key "archived", but DESERIALIZATION (no setters on a val) falls back to field injection
 * against the field named "isArchived" — the names don't match, so the flag silently reads back
 * as `false` on the other device. This was the "archive/trash don't sync" bug.
 */
class RemoteNoteSerializationTest {

    @Test
    fun `remote note round-trips pinned, archived and deletedAt through the firestore mapper`() {
        val note = RemoteNote(
            id = "n1",
            title = "t",
            content = "c",
            pinned = true,
            createdAt = 1L,
            updatedAt = 2L,
            deletedAt = 3L,
            archived = true,
        )

        @Suppress("UNCHECKED_CAST")
        val serialized = CustomClassMapper.convertToPlainJavaTypes(note) as Map<String, Any?>
        val restored = CustomClassMapper.convertToCustomClass(serialized, RemoteNote::class.java, null)

        assertTrue("isArchived must survive the round-trip", restored.archived)
        assertTrue("isPinned must survive the round-trip", restored.pinned)
        assertEquals(3L, restored.deletedAt)
    }

    @Test
    fun `remote shared note round-trips pinned and archived through the firestore mapper`() {
        val note = RemoteSharedNote(
            id = "s1",
            pinned = true,
            archived = true,
        )

        @Suppress("UNCHECKED_CAST")
        val serialized = CustomClassMapper.convertToPlainJavaTypes(note) as Map<String, Any?>
        val restored = CustomClassMapper.convertToCustomClass(serialized, RemoteSharedNote::class.java, null)

        assertTrue("isArchived must survive the round-trip", restored.archived)
        assertTrue("isPinned must survive the round-trip", restored.pinned)
    }
}
