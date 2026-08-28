package app.tessera.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration6To7Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NotesDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `creates note_images table accepting inserts`() {
        val dbName = "migration-note-images"
        helper.createDatabase(dbName, 6).close()

        val db = helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)

        db.execSQL(
            """
            INSERT INTO note_images (imageId, noteId, ownerUid, localPath, driveFileId, resourceKey,
                status, width, height, permissionsSyncedAt, orphanedAt)
            VALUES ('img1', 'note1', 'uid1', '/local/img1', NULL, NULL, 'LOCAL_ONLY', 100, 200, NULL, NULL)
            """.trimIndent(),
        )

        db.query("SELECT noteId, status FROM note_images WHERE imageId = 'img1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("note1", c.getString(0))
            assertEquals("LOCAL_ONLY", c.getString(1))
        }
    }

    @Test
    fun `note_images survives without a matching note row`() {
        val dbName = "migration-note-images-orphan"
        helper.createDatabase(dbName, 6).close()

        val db = helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)

        // No corresponding row in `notes` — must succeed since there is no foreign key.
        db.execSQL(
            """
            INSERT INTO note_images (imageId, noteId, ownerUid, localPath, driveFileId, resourceKey,
                status, width, height, permissionsSyncedAt, orphanedAt)
            VALUES ('img-orphan', 'missing-note', NULL, NULL, 'drive-id', 'key', 'ACCESS_LOST', 10, 20, NULL, 999)
            """.trimIndent(),
        )

        db.query("SELECT orphanedAt FROM note_images WHERE imageId = 'img-orphan'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(999L, c.getLong(0))
        }
    }
}
