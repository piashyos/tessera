package app.tessera.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Tag and folder appearance (colour, icon). `tags` also gains `updatedAt`/`syncedAt` so the
 * appearance can sync. The folder-name index becomes non-unique: uniqueness among live folders is
 * enforced by `FolderRepositoryImpl`, so a tombstone no longer blocks a name and REPLACE can't
 * delete a namesake row.
 */
internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE folders ADD COLUMN color TEXT")
        db.execSQL("ALTER TABLE folders ADD COLUMN icon TEXT")
        db.execSQL("DROP INDEX IF EXISTS `index_folders_name`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_name` ON `folders` (`name`)")
        db.execSQL("ALTER TABLE tags ADD COLUMN color TEXT")
        db.execSQL("ALTER TABLE tags ADD COLUMN icon TEXT")
        db.execSQL("ALTER TABLE tags ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tags ADD COLUMN syncedAt INTEGER")
    }
}
