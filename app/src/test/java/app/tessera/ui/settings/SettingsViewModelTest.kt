package app.tessera.ui.settings

import app.cash.turbine.test
import app.tessera.core.preferences.ThemeMode
import app.tessera.core.preferences.UserPreferences
import app.tessera.domain.model.UserId
import app.tessera.domain.model.UserProfile
import app.tessera.domain.repository.AccountRepository
import app.tessera.domain.repository.AuthRepository
import app.tessera.domain.repository.SyncScheduler
import app.tessera.domain.usecase.HandleAccountSwitchUseCase
import app.tessera.domain.usecase.SignInUseCase
import app.tessera.domain.usecase.SignOutUseCase
import app.tessera.sync.SyncIndicator
import app.tessera.sync.SyncStatusProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sign-in with no prior owner adopts local notes without a dialog`() =
        runTest(dispatcher.scheduler) {
            val auth = FakeAuthRepository(signInResult = Result.success(UserId("uid-new")))
            val account = FakeAccountRepository(owner = null)
            val viewModel = createViewModel(auth, account)

            viewModel.state.test {
                awaitItem()
                viewModel.onSignIn()
                advanceUntilIdle()

                val current = expectMostRecentItem()
                assertTrue(current.isSignedIn)
                assertEquals("uid-new", current.userId)
                assertFalse(current.isBusy)
                assertNull(current.pendingSwitch)
                assertEquals(listOf(UserId("uid-new")), account.adopted)
            }
        }

    @Test
    fun `sign-in with a different owner opens the account-switch dialog`() =
        runTest(dispatcher.scheduler) {
            val auth = FakeAuthRepository(signInResult = Result.success(UserId("uid-new")))
            val account = FakeAccountRepository(owner = UserId("uid-old"))
            val viewModel = createViewModel(auth, account)

            viewModel.state.test {
                awaitItem()
                viewModel.onSignIn()
                advanceUntilIdle()

                val current = expectMostRecentItem()
                assertEquals(
                    PendingAccountSwitch(existingUid = UserId("uid-old"), newUid = UserId("uid-new")),
                    current.pendingSwitch,
                )
                assertTrue(account.adopted.isEmpty())
            }
        }

    @Test
    fun `confirm move reassigns all notes to the new account`() =
        runTest(dispatcher.scheduler) {
            val auth = FakeAuthRepository(signInResult = Result.success(UserId("uid-new")))
            val account = FakeAccountRepository(owner = UserId("uid-old"))
            val viewModel = createViewModel(auth, account)

            viewModel.state.test {
                awaitItem()
                viewModel.onSignIn()
                advanceUntilIdle()

                viewModel.onConfirmMove()
                advanceUntilIdle()

                assertEquals(listOf(UserId("uid-new")), account.moved)
                assertNull(expectMostRecentItem().pendingSwitch)
            }
        }

    @Test
    fun `delete local removes the old owner's notes and adopts for the new account`() =
        runTest(dispatcher.scheduler) {
            val auth = FakeAuthRepository(signInResult = Result.success(UserId("uid-new")))
            val account = FakeAccountRepository(owner = UserId("uid-old"))
            val viewModel = createViewModel(auth, account)

            viewModel.state.test {
                awaitItem()
                viewModel.onSignIn()
                advanceUntilIdle()

                viewModel.onDeleteLocal()
                advanceUntilIdle()

                assertEquals(listOf(UserId("uid-old")), account.deletedOwners)
                assertEquals(listOf(UserId("uid-new")), account.adopted)
                assertNull(expectMostRecentItem().pendingSwitch)
            }
        }

    @Test
    fun `sign-out clears the signed-in user`() =
        runTest(dispatcher.scheduler) {
            val auth = FakeAuthRepository(signInResult = Result.success(UserId("uid-new")))
            val account = FakeAccountRepository(owner = null)
            val viewModel = createViewModel(auth, account)

            viewModel.state.test {
                awaitItem()
                viewModel.onSignIn()
                advanceUntilIdle()
                assertTrue(expectMostRecentItem().isSignedIn)

                viewModel.onSignOut()
                advanceUntilIdle()

                val current = expectMostRecentItem()
                assertFalse(current.isSignedIn)
                assertNull(current.userId)
            }
        }

    @Test
    fun `sync status from the provider is reflected in state`() =
        runTest(dispatcher.scheduler) {
            val auth = FakeAuthRepository(signInResult = Result.success(UserId("uid-new")))
            val account = FakeAccountRepository(owner = null)
            val syncStatus = MutableStateFlow<SyncIndicator>(SyncIndicator.Syncing)
            val viewModel = createViewModel(
                auth,
                account,
                syncStatusProvider = FakeSyncStatusProvider(syncStatus),
            )

            viewModel.state.test {
                awaitItem()
                advanceUntilIdle()
                assertEquals(SyncIndicator.Syncing, expectMostRecentItem().syncStatus)

                syncStatus.value = SyncIndicator.Ok(42L)
                advanceUntilIdle()
                assertEquals(SyncIndicator.Ok(42L), expectMostRecentItem().syncStatus)
            }
        }

    @Test
    fun `retry sync requests a sync from the scheduler`() =
        runTest(dispatcher.scheduler) {
            val auth = FakeAuthRepository(signInResult = Result.success(UserId("uid-new")))
            val account = FakeAccountRepository(owner = null)
            val scheduler = FakeSyncScheduler()
            val viewModel = createViewModel(auth, account, syncScheduler = scheduler)

            viewModel.onRetrySync()

            assertEquals(1, scheduler.requestCount)
        }

    private fun createViewModel(
        auth: AuthRepository,
        account: AccountRepository,
        userPreferences: UserPreferences = FakeUserPreferences(),
        syncScheduler: SyncScheduler = FakeSyncScheduler(),
        syncStatusProvider: SyncStatusProvider = FakeSyncStatusProvider(),
    ): SettingsViewModel =
        SettingsViewModel(
            authRepository = auth,
            accountRepository = account,
            signInUseCase = SignInUseCase(auth),
            signOutUseCase = SignOutUseCase(auth),
            handleAccountSwitchUseCase = HandleAccountSwitchUseCase(account),
            userPreferences = userPreferences,
            syncScheduler = syncScheduler,
            syncStatusProvider = syncStatusProvider,
        )
}

private class FakeSyncScheduler : SyncScheduler {
    var requestCount = 0
        private set

    override fun requestSync() {
        requestCount++
    }
}

private class FakeSyncStatusProvider(
    override val syncStatus: Flow<SyncIndicator> = MutableStateFlow(SyncIndicator.Ok()),
) : SyncStatusProvider

private class FakeUserPreferences : UserPreferences {
    private val theme = MutableStateFlow(ThemeMode.AUTO)
    private val lang = MutableStateFlow("")
    private val viewMode = MutableStateFlow(app.tessera.ui.notes.NotesViewMode.List)
    private val recent = MutableStateFlow<List<String>>(emptyList())
    override val themeMode: Flow<ThemeMode> get() = theme
    override val cachedThemeMode: ThemeMode get() = theme.value
    override val languageTag: Flow<String> get() = lang
    override val notesViewMode: Flow<app.tessera.ui.notes.NotesViewMode> get() = viewMode
    override val recentSearches: Flow<List<String>> get() = recent
    override suspend fun setThemeMode(mode: ThemeMode) {
        theme.value = mode
    }

    override suspend fun setLanguageTag(tag: String) {
        lang.value = tag
    }

    override suspend fun setNotesViewMode(mode: app.tessera.ui.notes.NotesViewMode) {
        viewMode.value = mode
    }

    override suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            recent.value = (listOf(trimmed) + recent.value.filterNot { it.equals(trimmed, ignoreCase = true) }).take(8)
        }
    }

    override suspend fun removeRecentSearch(query: String) {
        recent.value = recent.value.filterNot { it.equals(query, ignoreCase = true) }
    }

    override suspend fun clearRecentSearches() {
        recent.value = emptyList()
    }
}

private class FakeAuthRepository(
    private val signInResult: Result<UserId>,
) : AuthRepository {
    private val mutableUser = MutableStateFlow<UserId?>(null)
    private val mutableProfile = MutableStateFlow<UserProfile?>(null)

    override val currentUser: StateFlow<UserId?> = mutableUser.asStateFlow()

    override val currentProfile: StateFlow<UserProfile?> = mutableProfile.asStateFlow()

    override suspend fun signIn(): Result<UserId> {
        signInResult.onSuccess {
            mutableUser.value = it
            mutableProfile.value = UserProfile(id = it, displayName = null, email = null, photoUrl = null)
        }
        return signInResult
    }

    override suspend fun signOut() {
        mutableUser.value = null
        mutableProfile.value = null
    }
}

private class FakeAccountRepository(
    private val owner: UserId?,
) : AccountRepository {
    val adopted = mutableListOf<UserId>()
    val moved = mutableListOf<UserId>()
    val deletedOwners = mutableListOf<UserId>()

    override suspend fun currentOwnerId(): UserId? = owner

    override suspend fun adoptLocalNotes(uid: UserId) {
        adopted += uid
    }

    override suspend fun moveAllNotesTo(uid: UserId) {
        moved += uid
    }

    override suspend fun deleteNotesOwnedBy(uid: UserId) {
        deletedOwners += uid
    }
}
