package app.tessera.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds `note_images`, the local mirror of Drive-backed image attachments. No foreign key to
 * `notes` — an image row must outlive its note's local disappearance (ACCESS_LOST recovery flow).
 */
internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `note_images` (
                `imageId` TEXT NOT NULL,
                `noteId` TEXT NOT NULL,
                `ownerUid` TEXT,
                `localPath` TEXT,
                `driveFileId` TEXT,
                `resourceKey` TEXT,
                `status` TEXT NOT NULL,
                `width` INTEGER NOT NULL,
                `height` INTEGER NOT NULL,
                `permissionsSyncedAt` INTEGER,
                `orphanedAt` INTEGER,
                PRIMARY KEY(`imageId`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_images_noteId` ON `note_images` (`noteId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_images_status` ON `note_images` (`status`)")
    }
}
