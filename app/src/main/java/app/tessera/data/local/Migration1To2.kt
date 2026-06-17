package app.tessera.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS folders (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_folders_name ON folders(name)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS note_folders (
                noteId TEXT NOT NULL,
                folderId TEXT NOT NULL,
                PRIMARY KEY(noteId, folderId),
                FOREIGN KEY(folderId) REFERENCES folders(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_note_folders_noteId ON note_folders(noteId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_note_folders_folderId ON note_folders(folderId)")
    }
}
