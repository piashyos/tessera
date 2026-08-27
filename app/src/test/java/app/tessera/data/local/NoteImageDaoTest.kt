package app.tessera.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tessera.data.local.dao.NoteImageDao
import app.tessera.data.local.entity.ImageStatus
import app.tessera.data.local.entity.NoteImageEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteImageDaoTest {

    private lateinit var db: NotesDatabase
    private lateinit var dao: NoteImageDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NotesDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.noteImageDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun image(
        imageId: String,
        noteId: String = "note-1",
        ownerUid: String? = "uid-1",
        status: ImageStatus = ImageStatus.LOCAL_ONLY,
    ) = NoteImageEntity(
        imageId = imageId,
        noteId = noteId,
        ownerUid = ownerUid,
        localPath = "/local/$imageId",
        status = status,
        width = 100,
        height = 200,
    )

    @Test
    fun `insert then byId returns the row`() = runTest {
        dao.upsert(image("img-1"))

        val loaded = dao.byId("img-1")

        assertEquals("img-1", loaded?.imageId)
        assertEquals("note-1", loaded?.noteId)
        assertEquals(ImageStatus.LOCAL_ONLY, loaded?.status)
    }

    @Test
    fun `byId returns null when missing`() = runTest {
        assertNull(dao.byId("missing"))
    }

    @Test
    fun `byOwnerAndStatus filters on both owner and status`() = runTest {
        dao.upsert(image("img-1", ownerUid = "uid-1", status = ImageStatus.UPLOADED))
        dao.upsert(image("img-2", ownerUid = "uid-1", status = ImageStatus.LOCAL_ONLY))
        dao.upsert(image("img-3", ownerUid = "uid-2", status = ImageStatus.UPLOADED))

        val result = dao.byOwnerAndStatus("uid-1", ImageStatus.UPLOADED)

        assertEquals(listOf("img-1"), result.map { it.imageId })
    }

    @Test
    fun `backfillOwner only touches rows with null owner`() = runTest {
        dao.upsert(image("img-1", ownerUid = null))
        dao.upsert(image("img-2", ownerUid = "uid-existing"))

        dao.backfillOwner("uid-new")

        assertEquals("uid-new", dao.byId("img-1")?.ownerUid)
        assertEquals("uid-existing", dao.byId("img-2")?.ownerUid)
    }

    @Test
    fun `transition only touches rows matching owner and from-status`() = runTest {
        dao.upsert(image("img-1", ownerUid = "uid-1", status = ImageStatus.UPLOADING))
        dao.upsert(image("img-2", ownerUid = "uid-1", status = ImageStatus.LOCAL_ONLY))
        dao.upsert(image("img-3", ownerUid = "uid-2", status = ImageStatus.UPLOADING))

        dao.transition("uid-1", ImageStatus.UPLOADING, ImageStatus.UPLOADED)

        assertEquals(ImageStatus.UPLOADED, dao.byId("img-1")?.status)
        assertEquals(ImageStatus.LOCAL_ONLY, dao.byId("img-2")?.status)
        assertEquals(ImageStatus.UPLOADING, dao.byId("img-3")?.status)
    }

    @Test
    fun `delete removes the row`() = runTest {
        dao.upsert(image("img-1"))

        dao.delete("img-1")

        assertNull(dao.byId("img-1"))
    }

    @Test
    fun `byNote returns only rows for that note`() = runTest {
        dao.upsert(image("img-1", noteId = "note-a"))
        dao.upsert(image("img-2", noteId = "note-b"))

        val result = dao.byNote("note-a")

        assertEquals(listOf("img-1"), result.map { it.imageId })
    }
}
