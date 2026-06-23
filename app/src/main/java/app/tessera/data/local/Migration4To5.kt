package app.tessera.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the `isArchived` flag to `notes`. The FTS shadow table mirrors only title/content, so
 * adding an unrelated column to the content table leaves the FTS index and its triggers untouched.
 */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
    }
}
