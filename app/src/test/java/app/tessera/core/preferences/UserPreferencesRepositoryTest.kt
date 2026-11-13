package app.tessera.core.preferences

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserPreferencesRepositoryTest {

    private lateinit var repo: UserPreferencesRepository

    @Before
    fun setUp() = runBlocking {
        repo = UserPreferencesRepository(ApplicationProvider.getApplicationContext())
        // DataStore is a process-singleton keyed by file name, so reset between tests
        // via the public API to keep cases independent.
        repo.setThemeMode(ThemeMode.AUTO)
        repo.setDynamicColor(true)
        repo.setLanguageTag("")
        repo.setGradientEnabled(true)
        repo.setGrainSize(1)
    }

    @Test
    fun `default theme is AUTO`() = runBlocking {
        assertEquals(ThemeMode.AUTO, repo.themeMode.first())
    }

    @Test
    fun `set then read theme persists`() = runBlocking {
        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.themeMode.first())
    }

    @Test
    fun `dynamic color persists independently of theme`() = runBlocking {
        repo.setThemeMode(ThemeMode.AUTO)
        repo.setDynamicColor(false)
        assertEquals(ThemeMode.AUTO, repo.themeMode.first())
        assertEquals(false, repo.dynamicColor.first())
    }

    @Test
    fun `gradient is on and grain size is 1 by default`() = runBlocking {
        // setUp wrote these values, so the flows alone cannot guard the defaults: a fresh instance's cache can
        // (it starts from the same DEFAULT_* as the flows' fallback).
        val fresh = UserPreferencesRepository(ApplicationProvider.getApplicationContext())
        assertEquals(true, fresh.cachedGradientEnabled)
        assertEquals(1, fresh.cachedGrainSize)
        assertEquals(true, repo.gradientEnabled.first())
        assertEquals(1, repo.grainSize.first())
    }

    @Test
    fun `gradient flag persists`() = runBlocking {
        repo.setGradientEnabled(false)
        assertEquals(false, repo.gradientEnabled.first())
        repo.setGradientEnabled(true)
        assertEquals(true, repo.gradientEnabled.first())
    }

    @Test
    fun `grain size persists`() = runBlocking {
        repo.setGrainSize(3)
        assertEquals(3, repo.grainSize.first())
    }

    @Test
    fun `grain size is clamped to 1 to 4`() = runBlocking {
        repo.setGrainSize(0)
        assertEquals(1, repo.grainSize.first())
        repo.setGrainSize(9)
        assertEquals(4, repo.grainSize.first())
    }

    @Test
    fun `cached gradient and grain size follow the flows`() = runBlocking {
        repo.setGradientEnabled(false)
        repo.setGrainSize(4)
        repo.gradientEnabled.first()
        repo.grainSize.first()
        assertEquals(false, repo.cachedGradientEnabled)
        assertEquals(4, repo.cachedGrainSize)
    }
}
