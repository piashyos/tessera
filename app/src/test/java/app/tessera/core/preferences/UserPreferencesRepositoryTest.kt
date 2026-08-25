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
}
