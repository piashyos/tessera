package app.tessera.data.local

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import app.tessera.data.local.entity.FolderEntity
import app.tessera.data.local.entity.NoteFolderCrossRef
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A remote change to an existing folder (what `mergeRemoteFolders` does: `upsert` of the same PK
 * in one transaction) must leave the folder's `note_folders` rows and its subfolders' `parentId`
 * in place. A REPLACE conflict strategy is DELETE + INSERT in SQLite and would fire the FK actions
 * (CASCADE on `note_folders`, SET_NULL on `parentId`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderUpsertKeepsAssignmentsTest {

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

    @Test
    fun `remote update of a folder keeps its note assignments and subfolder parent`() = runTest {
        val dao = db.folderDao()
        db.withTransaction {
            dao.upsert(FolderEntity(id = "parent", name = "Work", createdAt = 1L))
            dao.upsert(FolderEntity(id = "child", name = "Sub", parentId = "parent", createdAt = 1L))
            dao.assignNote(NoteFolderCrossRef(noteId = "n1", folderId = "parent"))
        }

        // Same row, new name / updatedAt — as applied from a remote snapshot.
        db.withTransaction {
            dao.upsert(
                FolderEntity(id = "parent", name = "Work renamed", createdAt = 1L, updatedAt = 5L, syncedAt = 5L),
            )
        }

        assertEquals("parent", dao.folderIdForNote("n1"))
        assertEquals("parent", dao.getById("child")?.parentId)
        assertEquals("Work renamed", dao.getById("parent")?.name)
    }
}
