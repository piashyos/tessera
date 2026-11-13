package app.tessera.ui.editor.screen

import android.app.LocaleManager
import android.os.LocaleList
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.WorkManagerTestInitHelper
import app.tessera.core.preferences.UserPreferences
import app.tessera.data.local.NotesDatabase
import app.tessera.data.local.dao.NoteDao
import dagger.hilt.android.testing.HiltAndroidRule
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule

/**
 * The real app under Hilt with the native editor switched on: an empty database, the flag set, a test WorkManager (the test
 * application is not the app's `Configuration.Provider`; sync work is enqueued and never runs). Subclasses are `@HiltAndroidTest`.
 */
abstract class NativeAppTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var dao: NoteDao
    @Inject lateinit var db: NotesDatabase
    @Inject lateinit var prefs: UserPreferences

    internal lateinit var app: AppDriver

    @Before
    fun setUpApp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(InstrumentationRegistry.getInstrumentation().targetContext)
        hilt.inject()
        db.clearAllTables()
        runBlocking {
            prefs.setLanguageTag("") // a test that switched to Russian must not leave the app (and Gboard) in it
            // the live background as a user has it (on by default) — whatever an earlier class left in DataStore;
            // `-e gradient off` runs a class without it (to tell its effect from a flake)
            prefs.setGradientEnabled(InstrumentationRegistry.getArguments().getString("gradient") != "off")
        }
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.getEmptyLocaleList()
        app = AppDriver(compose, dao)
    }

    @After
    fun tearDownApp() {
        app.close()
    }
}
