package app.tessera.ui.notes

import app.cash.turbine.test
import app.tessera.domain.model.Folder
import app.tessera.domain.model.FolderId
import app.tessera.domain.model.FolderSaveResult
import app.tessera.domain.model.FolderTreeNode
import app.tessera.domain.model.Note
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.Tag
import app.tessera.domain.model.TagAppearance
import app.tessera.domain.model.UserId
import app.tessera.domain.repository.FolderRepository
import app.tessera.domain.repository.NotesRepository
import app.tessera.domain.repository.TagRepository
import app.tessera.domain.usecase.CreateNoteUseCase
import app.tessera.domain.usecase.DeleteNoteUseCase
import app.tessera.domain.usecase.ObserveNotesUseCase
import app.tessera.domain.usecase.RestoreNoteUseCase
import app.tessera.domain.usecase.SearchNotesUseCase
import app.tessera.domain.usecase.SetTagAppearanceUseCase
import app.tessera.domain.usecase.TogglePinUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesListViewModelTest {
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
    fun `state transitions from loading to content`() =
        runTest(dispatcher.scheduler) {
            val repository = FakeNotesRepository(initialNotes = listOf(sampleNote("note-1")))
            val viewModel = repository.createViewModel()

            viewModel.state.test {
                assertEquals(NotesListState.Loading, awaitItem())

                val content = awaitItem() as NotesListState.Content
                assertEquals("note-1", content.notes.single().id.value)
                assertEquals(0, content.pinnedCount)
                assertEquals("", content.query)
            }
        }

    @Test
    fun `state transitions from loading to empty when repository has no notes`() =
        runTest(dispatcher.scheduler) {
            val repository = FakeNotesRepository(initialNotes = emptyList())
            val viewModel = repository.createViewModel()

            viewModel.state.test {
                assertEquals(NotesListState.Loading, awaitItem())

                val empty = awaitItem() as NotesListState.Empty
                assertEquals("", empty.query)
                assertTrue(empty.availableTags.isEmpty())
            }
        }

    @Test
    fun `query updates immediately while search waits for debounce`() =
        runTest(dispatcher.scheduler) {
            val repository =
                FakeNotesRepository(
                    initialNotes = listOf(sampleNote("local-1", title = "Draft")),
                    searchResults =
                        mapOf(
                            "kotlin" to listOf(sampleNote("search-1", title = "Kotlin tips", tags = listOf("kotlin"))),
                        ),
                )
            val viewModel = repository.createViewModel()

            viewModel.state.test {
                awaitItem()
                awaitItem()

                viewModel.onQueryChange("kotlin")

                val optimistic = awaitItem() as NotesListState.Content
                assertEquals("kotlin", optimistic.query)
                assertEquals("local-1", optimistic.notes.single().id.value)
                assertTrue(repository.searchQueries.isEmpty())

                advanceTimeBy(299)
                assertTrue(repository.searchQueries.isEmpty())

                advanceTimeBy(1)

                val searched = awaitItem() as NotesListState.Content
                assertEquals(listOf("kotlin"), repository.searchQueries)
                assertEquals("search-1", searched.notes.single().id.value)
                assertEquals(listOf("kotlin"), searched.availableTags)
            }
        }

    @Test
    fun `toggling view mode flips between Tiles and List`() =
        runTest(dispatcher.scheduler) {
            val repository = FakeNotesRepository(initialNotes = listOf(sampleNote("n1")))
            val viewModel = repository.createViewModel()

            viewModel.state.test {
                awaitItem() // Loading
                val content = awaitItem() as NotesListState.Content
                assertEquals(NotesViewMode.List, content.viewMode)

                viewModel.onToggleViewMode()
                val next = awaitItem() as NotesListState.Content
                assertEquals(NotesViewMode.Tiles, next.viewMode)
            }
        }

    @Test
    fun `selection mode enters with the long-pressed note then select-all toggles`() =
        runTest(dispatcher.scheduler) {
            val repository =
                FakeNotesRepository(initialNotes = listOf(sampleNote("n1"), sampleNote("n2")))
            val viewModel = repository.createViewModel()

            viewModel.state.test {
                awaitItem() // Loading
                awaitItem() // Content

                viewModel.enterSelectionMode(NoteId("n1"))
                val entered = awaitItem() as NotesListState.Content
                assertTrue(entered.selectionMode)
                assertEquals(setOf(NoteId("n1")), entered.selection)

                viewModel.toggleSelectAll()
                val all = awaitItem() as NotesListState.Content
                assertEquals(setOf(NoteId("n1"), NoteId("n2")), all.selection)

                viewModel.toggleSelectAll()
                val none = awaitItem() as NotesListState.Content
                assertTrue(none.selection.isEmpty())

                viewModel.exitSelectionMode()
                val exited = awaitItem() as NotesListState.Content
                assertTrue(!exited.selectionMode)
            }
        }

    @Test
    fun `deleteSelected returns the selected ids and leaves selection mode`() =
        runTest(dispatcher.scheduler) {
            val repository =
                FakeNotesRepository(initialNotes = listOf(sampleNote("n1"), sampleNote("n2")))
            val viewModel = repository.createViewModel()

            viewModel.state.test {
                awaitItem() // Loading
                awaitItem() // Content

                viewModel.enterSelectionMode(NoteId("n1"))
                awaitItem()
                viewModel.toggleSelectAll()
                awaitItem()

                val deleted = viewModel.deleteSelected()
                assertEquals(setOf(NoteId("n1"), NoteId("n2")), deleted.toSet())

                val exited = awaitItem() as NotesListState.Content
                assertTrue(!exited.selectionMode)
                assertTrue(exited.selection.isEmpty())
            }
        }

    @Test
    fun `shared view shows only shared notes and exposes hasSharedNotes`() =
        runTest(dispatcher.scheduler) {
            val repository =
                FakeNotesRepository(
                    initialNotes = listOf(
                        sampleNote("private-1"),
                        sampleSharedNote("shared-1"),
                    ),
                )
            val viewModel = repository.createViewModel()

            viewModel.state.test {
                awaitItem() // Loading
                val all = awaitItem() as NotesListState.Content
                assertEquals(2, all.notes.size)
                assertTrue(all.hasSharedNotes)
                assertTrue(!all.isSharedView)

                viewModel.showSharedNotes()
                val shared = awaitItem() as NotesListState.Content
                assertTrue(shared.isSharedView)
                assertEquals(listOf("shared-1"), shared.notes.map { it.id.value })
            }
        }

    @Test
    fun `shareNote success emits a snackbar event`() =
        runTest(dispatcher.scheduler) {
            val repository = FakeNotesRepository(initialNotes = listOf(sampleNote("n1")))
            val viewModel = repository.createViewModel(currentUser = UserId("me"))

            viewModel.events.test {
                viewModel.shareNote(NoteId("n1"))
                val event = awaitItem() as NotesListEvent.ShowSnackbar
                assertTrue(event.message.isNotBlank())
            }
        }

    @Test
    fun `shareNote failure emits a snackbar event`() =
        runTest(dispatcher.scheduler) {
            val repository =
                FakeNotesRepository(
                    initialNotes = listOf(sampleNote("n1")),
                    shareResult = Result.failure(IllegalStateException("nope")),
                )
            val viewModel = repository.createViewModel(currentUser = UserId("me"))

            viewModel.events.test {
                viewModel.shareNote(NoteId("n1"))
                val event = awaitItem() as NotesListEvent.ShowSnackbar
                assertTrue(event.message.isNotBlank())
            }
        }

    @Test
    fun `createNoteDraft stamps the signed-in user as owner`() =
        runTest(dispatcher.scheduler) {
            val repository = FakeNotesRepository(initialNotes = emptyList())
            val viewModel = repository.createViewModel(currentUser = UserId("me"))

            viewModel.createNoteDraft()

            assertEquals(UserId("me"), repository.lastCreateOwnerId)
        }

    @Test
    fun `createNoteDraft leaves owner null when signed out`() =
        runTest(dispatcher.scheduler) {
            val repository = FakeNotesRepository(initialNotes = emptyList())
            val viewModel = repository.createViewModel()

            viewModel.createNoteDraft()

            assertNull(repository.lastCreateOwnerId)
        }

    @Test
    fun `noteTints is empty inside one folder and filled in a search started from it`() =
        runTest(dispatcher.scheduler) {
            val work = Folder(FolderId("work"), "Work", null, 1L, color = "blue")
            val inWork = sampleNote("n1")
            val repository =
                FakeNotesRepository(
                    initialNotes = listOf(inWork, sampleNote("n2"), sampleNote("n4")),
                    searchResults = mapOf("kot" to listOf(inWork, sampleNote("n3"))),
                )
            val folderRepo = FolderRepoStub(
                folders = listOf(work),
                assignments = mapOf(NoteId("n1") to work.id),
                notesInFolder = mapOf(work.id to listOf(inWork)),
            )
            val viewModel = repository.createViewModel(folderRepo = folderRepo)
            val collector = backgroundScope.launch { viewModel.state.collect {} }

            // "All": every card takes its folder colour.
            val all = viewModel.state.first { it is NotesListState.Content && it.notes.size == 3 } as NotesListState.Content
            assertEquals(mapOf(NoteId("n1") to "blue"), all.noteTints)

            // The list of one folder: nothing is tinted.
            viewModel.selectFolder(work.id)
            val inFolder = viewModel.state.first {
                it is NotesListState.Content && it.selectedFolderId == work.id && it.notes.size == 1
            } as NotesListState.Content
            assertTrue(inFolder.noteTints.isEmpty())

            // A search from inside that folder reads every folder, so it is tinted again.
            viewModel.onQueryChange("kot")
            val searched = viewModel.state.first {
                it is NotesListState.Content && it.selectedFolderId == work.id && it.notes.any { n -> n.id.value == "n3" }
            } as NotesListState.Content
            assertEquals(mapOf(NoteId("n1") to "blue"), searched.noteTints)
            collector.cancel()
        }

    @Test
    fun `a taken folder name reaches the caller instead of being swallowed`() =
        runTest(dispatcher.scheduler) {
            val folderRepo = FolderRepoStub(
                folders = listOf(Folder(FolderId("p"), "Parent", null, 1L)),
                saveResult = FolderSaveResult.NameTaken,
            )
            val viewModel = FakeNotesRepository(initialNotes = emptyList()).createViewModel(folderRepo = folderRepo)

            assertEquals(FolderSaveResult.NameTaken, viewModel.createFolder("Work", "blue", null))
            assertEquals(FolderSaveResult.NameTaken, viewModel.createSubfolder(FolderId("p"), "Work", null, "lucide:star"))
            assertEquals(FolderSaveResult.NameTaken, viewModel.updateFolder(FolderId("f"), "Work", "red", null))
        }

    @Test
    fun `clearing query returns to observe flow without debounce`() =
        runTest(dispatcher.scheduler) {
            val repository =
                FakeNotesRepository(
                    initialNotes = listOf(sampleNote("local-1", title = "Local")),
                    searchResults = mapOf("jetpack" to listOf(sampleNote("search-1", title = "Jetpack"))),
                )
            val viewModel = repository.createViewModel()

            viewModel.state.test {
                awaitItem()
                awaitItem()

                viewModel.onQueryChange("jetpack")
                awaitItem()
                advanceTimeBy(300)
                awaitItem()

                viewModel.onQueryChange("")

                val firstCleared = awaitItem() as NotesListState.Content
                val cleared =
                    if (firstCleared.notes.single().id.value == "local-1") {
                        firstCleared
                    } else {
                        awaitItem() as NotesListState.Content
                    }
                assertEquals("", cleared.query)
                assertEquals("local-1", cleared.notes.single().id.value)
                assertTrue(repository.observeSubscriptions >= 2)
            }
        }
}

private class FakeNotesRepository(
    initialNotes: List<Note>,
    private val searchResults: Map<String, List<Note>> = emptyMap(),
    private val shareResult: Result<Unit> = Result.success(Unit),
) : NotesRepository {
    private val notesFlow = MutableStateFlow(initialNotes)
    val archivedFlow = MutableStateFlow<List<Note>>(emptyList())
    val archivedCalls = mutableListOf<Pair<NoteId, Boolean>>()

    val searchQueries = mutableListOf<String>()
    var observeSubscriptions = 0

    override fun observeNotes(): Flow<List<Note>> {
        observeSubscriptions += 1
        return notesFlow
    }

    override fun observeNote(id: NoteId): Flow<Note?> = flowOf(notesFlow.value.firstOrNull { it.id == id })

    override fun searchNotes(query: String): Flow<List<Note>> {
        searchQueries += query
        return flowOf(searchResults[query].orEmpty())
    }

    override suspend fun getNote(id: NoteId): Note? = notesFlow.value.firstOrNull { it.id == id }

    var lastCreateOwnerId: UserId? = null
    override suspend fun createNote(ownerId: UserId?): Note {
        lastCreateOwnerId = ownerId
        return sampleNote("created")
    }

    override suspend fun updateNote(note: Note): Note = note

    override suspend fun deleteNote(id: NoteId) {}

    val permanentlyDeletedIds = mutableListOf<NoteId>()
    override suspend fun permanentlyDeleteNote(id: NoteId) {
        permanentlyDeletedIds += id
    }

    override suspend fun restoreNote(id: NoteId) {}

    override suspend fun togglePin(id: NoteId) {}

    override fun observeArchivedNotes(): Flow<List<Note>> = archivedFlow

    val deletedFlow = MutableStateFlow<List<Note>>(emptyList())
    override fun observeDeletedNotes(): Flow<List<Note>> = deletedFlow

    override suspend fun setArchived(id: NoteId, archived: Boolean) {
        archivedCalls += id to archived
    }

    override suspend fun shareNote(id: NoteId): Result<Unit> = shareResult
    override suspend fun inviteByEmail(
        id: NoteId,
        email: String,
        role: app.tessera.domain.model.ShareRole,
    ): Result<app.tessera.domain.model.InviteResult> =
        Result.success(app.tessera.domain.model.InviteResult.Added)
    override suspend fun changeRole(
        id: NoteId,
        uid: app.tessera.domain.model.UserId,
        role: app.tessera.domain.model.ShareRole,
    ): Result<Unit> = Result.success(Unit)
    override suspend fun removeMember(id: NoteId, uid: app.tessera.domain.model.UserId): Result<Unit> =
        Result.success(Unit)
    override suspend fun leaveSharedNote(id: NoteId): Result<Unit> = Result.success(Unit)
    override suspend fun unshareNote(id: NoteId): Result<Unit> = Result.success(Unit)
    override fun observeMembers(id: NoteId): Flow<List<app.tessera.domain.model.ShareMember>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    fun createViewModel(
        currentUser: UserId? = null,
        folderRepo: FolderRepository = app.tessera.test.fakes.FakeFolderRepository(),
    ): NotesListViewModel {
        val authRepository = FakeAuthRepository().apply { this.currentUser.value = currentUser }
        return NotesListViewModel(
            observeNotesUseCase = ObserveNotesUseCase(this),
            searchNotesUseCase = SearchNotesUseCase(this),
            createNoteUseCase = CreateNoteUseCase(this),
            deleteNoteUseCase = DeleteNoteUseCase(this),
            permanentlyDeleteNoteUseCase = app.tessera.domain.usecase.PermanentlyDeleteNoteUseCase(
                this,
                FakeSyncRepository(),
                FakeAuthRepository(),
            ),
            restoreNoteUseCase = RestoreNoteUseCase(this),
            togglePinUseCase = TogglePinUseCase(this),
            getNoteUseCase = app.tessera.domain.usecase.GetNoteUseCase(this),
            updateNoteUseCase = app.tessera.domain.usecase.UpdateNoteUseCase(this),
            duplicateNoteUseCase = app.tessera.domain.usecase.DuplicateNoteUseCase(this),
            archiveNoteUseCase = app.tessera.domain.usecase.ArchiveNoteUseCase(this),
            observeArchivedNotesUseCase = app.tessera.domain.usecase.ObserveArchivedNotesUseCase(this),
            observeDeletedNotesUseCase = app.tessera.domain.usecase.ObserveDeletedNotesUseCase(this),
            observeFoldersUseCase = app.tessera.domain.usecase.ObserveFoldersUseCase(folderRepo),
            observeFolderTreeUseCase = app.tessera.domain.usecase.ObserveFolderTreeUseCase(folderRepo),
            observeFolderAssignmentsUseCase = app.tessera.domain.usecase.ObserveFolderAssignmentsUseCase(folderRepo),
            observeNotesInFolderUseCase = app.tessera.domain.usecase.ObserveNotesInFolderUseCase(folderRepo),
            createFolderUseCase = app.tessera.domain.usecase.CreateFolderUseCase(folderRepo),
            createSubfolderUseCase = app.tessera.domain.usecase.CreateSubfolderUseCase(folderRepo),
            updateFolderUseCase = app.tessera.domain.usecase.UpdateFolderUseCase(folderRepo),
            setTagAppearanceUseCase = SetTagAppearanceUseCase(NoOpTagRepository),
            moveFolderUseCase = app.tessera.domain.usecase.MoveFolderUseCase(folderRepo),
            deleteFolderUseCase = app.tessera.domain.usecase.DeleteFolderUseCase(folderRepo),
            moveNoteToFolderUseCase = app.tessera.domain.usecase.MoveNoteToFolderUseCase(folderRepo),
            shareNoteUseCase = app.tessera.domain.usecase.ShareNoteUseCase(this, authRepository),
            userPreferences = FakeUserPreferences(),
            authRepository = authRepository,
        )
    }
}

/** A folder repository with canned folders, assignments, folder lists and one save result. */
private class FolderRepoStub(
    private val folders: List<Folder> = emptyList(),
    private val assignments: Map<NoteId, FolderId> = emptyMap(),
    private val notesInFolder: Map<FolderId, List<Note>> = emptyMap(),
    private val saveResult: FolderSaveResult? = null,
) : FolderRepository {
    override fun observeFolders(): Flow<List<Folder>> = flowOf(folders)
    override fun observeFolderTree(): Flow<List<FolderTreeNode>> =
        flowOf(folders.filter { it.parentId == null }.map { root -> FolderTreeNode(root, folders.filter { it.parentId == root.id }) })
    override fun observeFolderForNote(noteId: NoteId): Flow<FolderId?> = flowOf(assignments[noteId])
    override fun observeNotesInFolder(folderId: FolderId): Flow<List<Note>> = flowOf(notesInFolder[folderId].orEmpty())
    override fun observeFolderAssignments(): Flow<Map<NoteId, FolderId>> = flowOf(assignments)
    override suspend fun createFolder(name: String, color: String?, icon: String?): FolderSaveResult =
        saveResult ?: FolderSaveResult.Saved(Folder(FolderId("new"), name, null, 0L, color, icon))
    override suspend fun createSubfolder(parentId: FolderId, name: String, color: String?, icon: String?): FolderSaveResult =
        saveResult ?: FolderSaveResult.Saved(Folder(FolderId("new"), name, parentId, 0L, color, icon))
    override suspend fun updateFolder(id: FolderId, name: String, color: String?, icon: String?): FolderSaveResult =
        saveResult ?: FolderSaveResult.Saved(Folder(id, name, null, 0L, color, icon))
    override suspend fun moveFolder(id: FolderId, newParentId: FolderId?) = Unit
    override suspend fun deleteFolder(id: FolderId) = Unit
    override suspend fun assignNote(noteId: NoteId, folderId: FolderId?) = Unit
}

private object NoOpTagRepository : TagRepository {
    override fun observeAppearances(): Flow<Map<String, TagAppearance>> = flowOf(emptyMap())
    override suspend fun setAppearance(name: String, color: String?, icon: String?) = Unit
}

private class FakeUserPreferences : app.tessera.core.preferences.UserPreferences {
    private val theme = MutableStateFlow(app.tessera.core.preferences.ThemeMode.AUTO)
    private val dynamic = MutableStateFlow(true)
    private val palette = MutableStateFlow(app.tessera.core.preferences.ThemePalette.Default)
    private val lang = MutableStateFlow("")
    private val viewMode = MutableStateFlow(NotesViewMode.List)
    private val recent = MutableStateFlow<List<String>>(emptyList())
    override val themeMode: Flow<app.tessera.core.preferences.ThemeMode> get() = theme
    override val cachedThemeMode: app.tessera.core.preferences.ThemeMode get() = theme.value
    override val dynamicColor: Flow<Boolean> get() = dynamic
    override val cachedDynamicColor: Boolean get() = dynamic.value
    override val themePalette: Flow<app.tessera.core.preferences.ThemePalette> get() = palette
    override val cachedThemePalette: app.tessera.core.preferences.ThemePalette get() = palette.value
    private val style = MutableStateFlow(app.tessera.core.preferences.ThemeStyle.Default)
    private val amoledFlow = MutableStateFlow(false)
    override val themeStyle: Flow<app.tessera.core.preferences.ThemeStyle> get() = style
    override val cachedThemeStyle: app.tessera.core.preferences.ThemeStyle get() = style.value
    override val amoled: Flow<Boolean> get() = amoledFlow
    override val cachedAmoled: Boolean get() = amoledFlow.value
    private val grainEnabledFlow = MutableStateFlow(true)
    private val grainIntensityFlow = MutableStateFlow(0.5f)
    private val grainSizeFlow = MutableStateFlow(1)
    private val gradientEnabledFlow = MutableStateFlow(true)
    override val grainEnabled: Flow<Boolean> get() = grainEnabledFlow
    override val cachedGrainEnabled: Boolean get() = grainEnabledFlow.value
    override val grainIntensity: Flow<Float> get() = grainIntensityFlow
    override val cachedGrainIntensity: Float get() = grainIntensityFlow.value
    override val grainSize: Flow<Int> get() = grainSizeFlow
    override val cachedGrainSize: Int get() = grainSizeFlow.value
    override val gradientEnabled: Flow<Boolean> get() = gradientEnabledFlow
    override val cachedGradientEnabled: Boolean get() = gradientEnabledFlow.value
    override val languageTag: Flow<String> get() = lang
    override val notesViewMode: Flow<NotesViewMode> get() = viewMode
    override val recentSearches: Flow<List<String>> get() = recent
    override suspend fun setThemeMode(mode: app.tessera.core.preferences.ThemeMode) {
        theme.value = mode
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dynamic.value = enabled
    }

    override suspend fun setThemePalette(palette: app.tessera.core.preferences.ThemePalette) {
        this.palette.value = palette
    }

    override suspend fun setThemeStyle(style: app.tessera.core.preferences.ThemeStyle) {
        this.style.value = style
    }

    override suspend fun setAmoled(enabled: Boolean) {
        amoledFlow.value = enabled
    }

    override suspend fun setGrainEnabled(enabled: Boolean) {
        grainEnabledFlow.value = enabled
    }

    override suspend fun setGrainIntensity(intensity: Float) {
        grainIntensityFlow.value = intensity.coerceIn(0f, 1f)
    }

    override suspend fun setGrainSize(size: Int) {
        grainSizeFlow.value = size.coerceIn(1, 4)
    }

    override suspend fun setGradientEnabled(enabled: Boolean) {
        gradientEnabledFlow.value = enabled
    }

    override suspend fun setLanguageTag(tag: String) {
        lang.value = tag
    }

    override suspend fun setNotesViewMode(mode: NotesViewMode) {
        viewMode.value = mode
    }

    override suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        recent.value = (listOf(trimmed) + recent.value.filterNot { it.equals(trimmed, ignoreCase = true) }).take(8)
    }

    override suspend fun removeRecentSearch(query: String) {
        recent.value = recent.value.filterNot { it.equals(query, ignoreCase = true) }
    }

    override suspend fun clearRecentSearches() {
        recent.value = emptyList()
    }
}

private class FakeSyncRepository : app.tessera.domain.repository.SyncRepository {
    override suspend fun syncNow(uid: UserId): Result<Unit> = Result.success(Unit)
    override suspend fun pushLocalChanges(uid: UserId): Result<Unit> = Result.success(Unit)
    override suspend fun purgeTombstones(uid: UserId): Result<Unit> = Result.success(Unit)
    override suspend fun purgeLocalTombstones(): Result<Unit> = Result.success(Unit)
    override suspend fun permanentlyDelete(uid: UserId, ids: List<NoteId>): Result<Unit> = Result.success(Unit)
    override fun observeRemote(uid: UserId): Flow<Unit> = flowOf(Unit)
}

private class FakeAuthRepository : app.tessera.domain.repository.AuthRepository {
    override val currentUser = MutableStateFlow<UserId?>(null)
    override val currentProfile = MutableStateFlow<app.tessera.domain.model.UserProfile?>(null)
    override suspend fun signIn(): Result<UserId> = Result.failure(UnsupportedOperationException())
    override suspend fun signOut() {}
}

private fun sampleNote(
    id: String,
    title: String = "Sample",
    content: String = "Body",
    pinned: Boolean = false,
    tags: List<String> = emptyList(),
): Note =
    Note(
        id = NoteId(id),
        title = title,
        content = content,
        tags = tags.map(::Tag),
        isPinned = pinned,
        createdAt = 1L,
        updatedAt = 1L,
        deletedAt = null,
        syncedAt = null,
        ownerId = null,
    )

private fun sampleSharedNote(id: String): Note =
    sampleNote(id).copy(
        shareState = app.tessera.domain.model.ShareState.Shared(
            members = listOf(
                app.tessera.domain.model.ShareMember(
                    uid = UserId("owner"),
                    displayName = "Owner",
                    email = "owner@example.com",
                    photoUrl = null,
                    role = app.tessera.domain.model.ShareRole.OWNER,
                    joinedAt = 1L,
                ),
            ),
            myRole = app.tessera.domain.model.ShareRole.OWNER,
        ),
    )
