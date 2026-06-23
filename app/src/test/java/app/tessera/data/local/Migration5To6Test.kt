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
class Migration5To6Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NotesDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `migrates v5 note rows and adds shared columns with defaults`() {
        val dbName = "migration-test"
        helper.createDatabase(dbName, 5).use { db ->
            db.execSQL(
                """
                INSERT INTO notes (id, title, content, isPinned, createdAt, updatedAt,
                    deletedAt, syncedAt, ownerId, isArchived)
                VALUES ('n1', 'Title', 'Body', 0, 100, 200, NULL, NULL, 'owner', 0)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        db.query("SELECT shareState, myRole FROM notes WHERE id = 'n1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("PRIVATE", c.getString(0))
            assertTrue(c.isNull(1))
        }
    }

    @Test
    fun `creates shared_members table accepting inserts`() {
        val dbName = "migration-members"
        helper.createDatabase(dbName, 5).close()

        val db = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        // Parent note required by the FK before a member can be inserted.
        db.execSQL(
            """
            INSERT INTO notes (id, title, content, isPinned, createdAt, updatedAt,
                deletedAt, syncedAt, ownerId, isArchived, shareState, myRole)
            VALUES ('n1', 'T', 'B', 0, 1, 1, NULL, NULL, 'owner', 0, 'SHARED', 'OWNER')
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO shared_members (noteId, uid, displayName, email, photoUrl, role, joinedAt)
            VALUES ('n1', 'u1', 'Alice', 'a@x.io', NULL, 'EDITOR', 5)
            """.trimIndent(),
        )

        db.query("SELECT role FROM shared_members WHERE noteId = 'n1' AND uid = 'u1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("EDITOR", c.getString(0))
        }
    }
}
