package app.tessera.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Shared-notes access layer:
 *  - adds `shareState` (default PRIVATE) and nullable `myRole` to `notes`;
 *  - creates the `shared_members` table mirroring `sharedNotes/{id}.members`.
 *
 * Adding plain columns to `notes` leaves the FTS shadow table and its triggers untouched
 * (they mirror only title/content).
 */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN shareState TEXT NOT NULL DEFAULT 'PRIVATE'")
        db.execSQL("ALTER TABLE notes ADD COLUMN myRole TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shared_members` (
                `noteId` TEXT NOT NULL,
                `uid` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `email` TEXT NOT NULL,
                `photoUrl` TEXT,
                `role` TEXT NOT NULL,
                `joinedAt` INTEGER NOT NULL,
                PRIMARY KEY(`noteId`, `uid`),
                FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shared_members_noteId` ON `shared_members` (`noteId`)")
    }
}
