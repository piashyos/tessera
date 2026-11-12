package app.tessera.ui.navigation

import app.cash.turbine.test
import app.tessera.core.ui.tessera.EntityIcon
import app.tessera.core.ui.theme.AccentColor
import app.tessera.domain.model.TagAppearance
import app.tessera.domain.repository.TagRepository
import app.tessera.domain.usecase.ObserveTagAppearancesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Robolectric: parsing an emoji icon uses android.icu.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class TagAppearanceViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Repo(initial: Map<String, TagAppearance>) : TagRepository {
        val flow = MutableStateFlow(initial)
        override fun observeAppearances(): Flow<Map<String, TagAppearance>> = flow
        override suspend fun setAppearance(name: String, color: String?, icon: String?) = Unit
    }

    @Test
    fun `maps stored keys to colour and icon`() = runTest {
        val vm = TagAppearanceViewModel(
            ObserveTagAppearancesUseCase(
                Repo(mapOf("work" to TagAppearance("work", "blue", "lucide:star"), "joy" to TagAppearance("joy", null, "emoji:🏠"))),
            ),
        )
        vm.styles.test {
            var styles = awaitItem()
            if (styles.isEmpty()) styles = awaitItem()
            assertEquals(AccentColor.Blue, styles.getValue("work").color)
            assertEquals("star", (styles.getValue("work").icon as EntityIcon.Vector).key)
            assertNull(styles.getValue("joy").color)
            assertEquals(EntityIcon.Emoji("🏠"), styles.getValue("joy").icon)
        }
    }

    @Test
    fun `a key from a newer app version gives null, not a crash`() = runTest {
        val vm = TagAppearanceViewModel(
            ObserveTagAppearancesUseCase(Repo(mapOf("x" to TagAppearance("x", "neon", "lucide:no-such-icon")))),
        )
        vm.styles.test {
            var styles = awaitItem()
            if (styles.isEmpty()) styles = awaitItem()
            assertNull(styles.getValue("x").color)
            assertNull(styles.getValue("x").icon)
            assertTrue(styles.containsKey("x"))
        }
    }

    @Test
    fun `starts empty and follows changes`() = runTest {
        val repo = Repo(emptyMap())
        val vm = TagAppearanceViewModel(ObserveTagAppearancesUseCase(repo))
        vm.styles.test {
            assertTrue(awaitItem().isEmpty())
            repo.flow.value = mapOf("a" to TagAppearance("a", "red", null))
            assertEquals(AccentColor.Red, awaitItem().getValue("a").color)
        }
    }
}
