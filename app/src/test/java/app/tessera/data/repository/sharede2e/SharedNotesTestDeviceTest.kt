package app.tessera.data.repository.sharede2e

import androidx.test.core.app.ApplicationProvider
import app.tessera.data.local.entity.NoteEntity
import app.tessera.domain.model.NoteId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedNotesTestDeviceTest {
    private val backend = InMemorySharedBackend()
    private val devices = mutableListOf<SharedNotesTestDevice>()

    private fun device(uid: String, email: String): SharedNotesTestDevice =
        SharedNotesTestDevice(
            context = ApplicationProvider.getApplicationContext(),
            backend = backend,
            uid = uid,
            displayName = uid.replaceFirstChar { it.uppercase() },
            email = email,
        ).also { devices += it }

    @After fun tearDown() = devices.forEach { it.close() }

    @Test
    fun `owner can create a private note and share it`() = runTest {
        val alice = device("alice", "alice@x.io")
        alice.noteDao.upsert(
            NoteEntity("n1", "Title", "Body", false, 1L, 1L, null, 1L, ownerId = "alice"),
        )

        val result = alice.gateway.shareNote(NoteId("n1"))

        assertEquals(true, result.isSuccess)
        assertEquals(NoteEntity.SHARE_STATE_SHARED, alice.noteDao.getById("n1")?.shareState)
    }
}
