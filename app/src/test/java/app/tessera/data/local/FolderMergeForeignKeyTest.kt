package app.tessera.data.local

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import app.tessera.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Reproduces the sign-in crash trigger: `FolderEntity.parentId` is a `deferred = true` self
 * foreign key (checked at COMMIT). Upserting a child folder before its parent in SEPARATE
 * transactions fails the deferred check at each row's commit — which is what the folder merge did,
 * corrupting the transaction-executor session and crashing the next write with
 * "cannot start a transaction within a transaction".
 *
 * Merging the whole batch inside ONE transaction defers the check to the single commit, by which
 * point every parent exists — so it must succeed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderMergeForeignKeyTest {

    private lateinit var db: NotesDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NotesDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun folder(id: String, parentId: String?) =
        FolderEntity(id = id, name = "name-$id", parentId = parentId, createdAt = 1L)

    @Test
    fun `child-before-parent inside one transaction succeeds`() = runTest {
        val dao = db.folderDao()
        // Single transaction → deferred FK checked once at commit, when the parent already exists.
        db.withTransaction {
            dao.upsert(folder("child", parentId = "parent"))
            dao.upsert(folder("parent", parentId = null))
        }
        assertEquals(2, dao.observeAll().first().size)
    }
}
