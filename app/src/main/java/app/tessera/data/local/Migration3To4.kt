package app.tessera.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds `parentId` column to `folders` with self-referencing FK (ON DELETE SET NULL).
 * SQLite cannot add an FK constraint via ALTER TABLE, so we recreate the table.
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE folders_new (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                parentId TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                deletedAt INTEGER,
                syncedAt INTEGER,
                FOREIGN KEY(parentId) REFERENCES folders(id) ON UPDATE NO ACTION ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO folders_new (id, name, parentId, createdAt, updatedAt, deletedAt, syncedAt)
            SELECT id, name, NULL, createdAt, updatedAt, deletedAt, syncedAt FROM folders
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE folders")
        db.execSQL("ALTER TABLE folders_new RENAME TO folders")
        db.execSQL("CREATE UNIQUE INDEX index_folders_name ON folders(name)")
        db.execSQL("CREATE INDEX index_folders_parentId ON folders(parentId)")
    }
}
