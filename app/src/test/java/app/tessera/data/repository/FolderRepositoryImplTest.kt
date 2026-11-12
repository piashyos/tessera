package app.tessera.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tessera.core.util.Clock
import app.tessera.data.local.NotesDatabase
import app.tessera.data.local.entity.FolderEntity
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderSaveResult
import app.tessera.domain.model.NoteId
import app.tessera.domain.repository.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderRepositoryImplTest {
    private lateinit var db: NotesDatabase
    private lateinit var repo: FolderRepositoryImpl
    private var syncRequests = 0
    private var now = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NotesDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = FolderRepositoryImpl(
            db.folderDao(),
            db.noteDao(),
            db.tagDao(),
            app.tessera.data.mapper.NoteMapper(),
            object : Clock { override fun nowMs() = ++now },
            object : SyncScheduler { override fun requestSync() { syncRequests++ } },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun create(name: String, color: String? = null, icon: String? = null) =
        (repo.createFolder(name, color, icon) as FolderSaveResult.Saved).folder

    @Test
    fun `createFolder with a live name in any case is NameTaken and writes nothing`() = runTest {
        create("Work")

        assertEquals(FolderSaveResult.NameTaken, repo.createFolder("Work", null, null))
        assertEquals(FolderSaveResult.NameTaken, repo.createFolder("  wORK ", null, null))
        assertEquals(FolderSaveResult.NameTaken, repo.createSubfolder(FolderId("x"), "work", null, null))
        assertEquals(1, repo.observeFolders().first().size)
    }

    @Test
    fun `name check is case-insensitive for Cyrillic too`() = runTest {
        create("Работа")

        assertEquals(FolderSaveResult.NameTaken, repo.createFolder("работа", null, null))
    }

    @Test
    fun `updateFolder to another live folder's name is NameTaken and changes nothing`() = runTest {
        create("Work")
        val home = create("Home")

        val result = repo.updateFolder(home.id, "WORK", "red", "lucide:star")

        assertEquals(FolderSaveResult.NameTaken, result)
        val stored = db.folderDao().getById(home.id.value)!!
        assertEquals("Home", stored.name)
        assertNull(stored.color)
        assertNull(stored.icon)
    }

    @Test
    fun `tombstone name is free for create and update`() = runTest {
        val old = create("Work")
        repo.deleteFolder(old.id)
        val home = create("Home")

        assertTrue(repo.createFolder("Work", null, null) is FolderSaveResult.Saved)
        // `Work` is now live again; renaming Home onto the tombstone's name must also be allowed
        // once the live one is gone.
        val third = create("Third")
        repo.deleteFolder(third.id)
        assertTrue(repo.updateFolder(home.id, "Third", null, null) is FolderSaveResult.Saved)
    }

    @Test
    fun `recolouring one of two same-named live folders is Saved`() = runTest {
        db.folderDao().upsert(FolderEntity(id = "a", name = "Work", createdAt = 1L))
        db.folderDao().upsert(FolderEntity(id = "b", name = "work", createdAt = 1L))

        val result = repo.updateFolder(FolderId("a"), "Work", "red", null)

        assertTrue(result is FolderSaveResult.Saved)
        assertEquals("red", db.folderDao().getById("a")!!.color)
        assertNull(db.folderDao().getById("b")!!.color)
    }

    @Test
    fun `case-only rename onto a live namesake is NameTaken`() = runTest {
        create("Work")
        val lower = create("home")

        val result = repo.updateFolder(lower.id, "Work", null, null)

        assertEquals(FolderSaveResult.NameTaken, result)
        assertEquals("home", db.folderDao().getById(lower.id.value)!!.name)
    }

    @Test
    fun `updateFolder on a tombstone is Invalid and writes nothing`() = runTest {
        val work = create("Work")
        repo.deleteFolder(work.id)
        val before = db.folderDao().getById(work.id.value)!!
        syncRequests = 0

        val result = repo.updateFolder(work.id, "Renamed", "red", null)

        assertEquals(FolderSaveResult.Invalid("folder not found"), result)
        assertEquals(before, db.folderDao().getById(work.id.value))
        assertEquals(0, syncRequests)
    }

    @Test
    fun `updateFolder on a missing id is Invalid`() = runTest {
        assertEquals(FolderSaveResult.Invalid("folder not found"), repo.updateFolder(FolderId("ghost"), "X", null, null))
    }

    @Test
    fun `two parallel createFolder of one name give one Saved and one NameTaken`() = runTest {
        val results = listOf(
            async(Dispatchers.Default) { repo.createFolder("X", null, null) },
            async(Dispatchers.Default) { repo.createFolder("X", null, null) },
        ).awaitAll()

        assertEquals(1, results.count { it is FolderSaveResult.Saved })
        assertEquals(1, results.count { it == FolderSaveResult.NameTaken })
        assertEquals(1, repo.observeFolders().first().size)
    }

    @Test
    fun `own name is kept, only case and appearance change`() = runTest {
        val work = create("Work")

        val result = repo.updateFolder(work.id, "WORK", "blue", "emoji:🏠")

        val saved = (result as FolderSaveResult.Saved).folder
        assertEquals("WORK", saved.name)
        assertEquals("blue", saved.color)
        assertEquals("emoji:🏠", saved.icon)
    }

    @Test
    fun `colour and icon are written and the folder waits for sync`() = runTest {
        val work = create("Work", color = "teal", icon = "lucide:book")
        db.folderDao().markSynced(work.id.value, 5_000L, db.folderDao().getById(work.id.value)!!.updatedAt)
        assertEquals(5_000L, db.folderDao().getById(work.id.value)!!.syncedAt)

        syncRequests = 0
        repo.updateFolder(work.id, "Work", "pink", null)

        val stored = db.folderDao().getById(work.id.value)!!
        assertEquals("pink", stored.color)
        assertNull(stored.icon)
        assertNull(stored.syncedAt)
        assertTrue(stored.updatedAt > 1_000L)
        assertEquals(1, syncRequests)
    }

    @Test
    fun `created folder carries colour and icon and is pending sync`() = runTest {
        val folder = create("Work", color = "teal", icon = "lucide:book")

        assertEquals("teal", folder.color)
        assertEquals("lucide:book", folder.icon)
        val stored = db.folderDao().getById(folder.id.value)!!
        assertNull(stored.syncedAt)
        assertEquals(1, syncRequests)
    }

    @Test
    fun `observeFolderAssignments maps notes to folders`() = runTest {
        val work = create("Work")
        repo.assignNote(NoteId("n1"), work.id)

        assertEquals(mapOf(NoteId("n1") to work.id), repo.observeFolderAssignments().first())
    }
}
