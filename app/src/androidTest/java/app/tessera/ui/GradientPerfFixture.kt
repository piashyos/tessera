package app.tessera.ui

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.WorkManagerTestInitHelper
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.ThemePalette
import app.tessera.core.preferences.UserPreferences
import app.tessera.data.local.NotesDatabase
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.entity.NoteEntity
import app.tessera.ui.notes.NotesViewMode
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Not a test: the data for the QA's scroll-smoothness measurement of the live background (docs/superpowers/qa/animated-background-gestures.md),
 * run by hand between measurements and skipped in a normal run:
 * `adb shell am instrument -w -e perfGradient on|off -e class app.tessera.ui.GradientPerfFixture app.tessera.test/app.tessera.HiltTestRunner`.
 * Leaves 30 notes in the list (light, WARM, grain at its default) and the gradient on or off; the app is then started as a user starts it
 * (`am start`), with no instrumentation (and no accessibility client) in its process.
 */
@HiltAndroidTest
class GradientPerfFixture {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var db: NotesDatabase
    @Inject lateinit var noteDao: NoteDao
    @Inject lateinit var prefs: UserPreferences

    @Test
    fun seed() {
        val gradient = InstrumentationRegistry.getArguments().getString("perfGradient")
        assumeTrue("run by hand with -e perfGradient on|off", gradient == "on" || gradient == "off")
        WorkManagerTestInitHelper.initializeTestWorkManager(InstrumentationRegistry.getInstrumentation().targetContext)
        hilt.inject()
        db.clearAllTables()
        runBlocking {
            repeat(30) { i ->
                val at = Base + i * 60_000L
                noteDao.upsert(
                    NoteEntity(
                        id = "perf-$i", title = "Note $i", content = "A first line of note $i\nand a second one, a little longer",
                        isPinned = false, createdAt = at, updatedAt = at, deletedAt = null, syncedAt = at, ownerId = null,
                    ),
                )
            }
            prefs.setLanguageTag("")
            prefs.setThemeMode(ThemeMode.LIGHT)
            prefs.setDynamicColor(false)
            prefs.setThemePalette(ThemePalette.WARM)
            prefs.setAmoled(false)
            prefs.setGrainEnabled(true)
            prefs.setNotesViewMode(NotesViewMode.List)
            prefs.setGradientEnabled(gradient == "on")
        }
    }

    private companion object {
        const val Base = 1_700_000_000_000L
    }
}
