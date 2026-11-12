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
class Migration7To8Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NotesDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `folders tags and links survive, new columns are null or zero, schema matches the export`() {
        val dbName = "migration-appearance"
        helper.createDatabase(dbName, 7).apply {
            execSQL(
                "INSERT INTO folders (id, name, parentId, createdAt, updatedAt, deletedAt, syncedAt) " +
                    "VALUES ('f1', 'Work', NULL, 1, 2, NULL, 3), ('f2', 'Sub', 'f1', 1, 2, NULL, NULL)",
            )
            execSQL("INSERT INTO tags (id, name) VALUES ('t1', 'idea')")
            execSQL("INSERT INTO note_tags (noteId, tagId) VALUES ('n1', 't1')")
            execSQL("INSERT INTO note_folders (noteId, folderId) VALUES ('n1', 'f1')")
            close()
        }

        // `validateDroppedTables = true` also compares the result with schemas/…/8.json.
        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db.query("SELECT name, parentId, updatedAt, syncedAt, color, icon FROM folders ORDER BY id").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Work", c.getString(0))
            assertTrue(c.isNull(1))
            assertEquals(2L, c.getLong(2))
            assertEquals(3L, c.getLong(3))
            assertTrue(c.isNull(4))
            assertTrue(c.isNull(5))
            assertTrue(c.moveToNext())
            assertEquals("f1", c.getString(1))
        }
        db.query("SELECT name, color, icon, updatedAt, syncedAt FROM tags WHERE id = 't1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("idea", c.getString(0))
            assertTrue(c.isNull(1))
            assertTrue(c.isNull(2))
            assertEquals(0L, c.getLong(3))
            assertTrue(c.isNull(4))
        }
        db.query("SELECT tagId FROM note_tags WHERE noteId = 'n1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("t1", c.getString(0))
        }
        db.query("SELECT folderId FROM note_folders WHERE noteId = 'n1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("f1", c.getString(0))
        }
    }

    @Test
    fun `two live folders with the same name can be inserted after the migration`() {
        val dbName = "migration-folder-name-index"
        helper.createDatabase(dbName, 7).close()

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db.execSQL(
            "INSERT INTO folders (id, name, parentId, createdAt, updatedAt, deletedAt, syncedAt, color, icon) " +
                "VALUES ('a', 'Same', NULL, 1, 1, NULL, NULL, NULL, NULL), ('b', 'Same', NULL, 1, 1, NULL, NULL, 'red', 'emoji:🏠')",
        )

        db.query("SELECT COUNT(*) FROM folders WHERE name = 'Same'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }
    }
}
