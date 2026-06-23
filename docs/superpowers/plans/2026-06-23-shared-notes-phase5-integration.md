# Shared Notes — Фаза 5: Интеграция и приёмка блока A (Implementation Plan)

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Доказать E2E-тестами, что весь блок A (доступ + UI, фазы 1–4) работает как единое целое для двух пользователей — инвайт → появление в «Общих» → правка (document-level sync) → смена роли → выход/исключение/анишеринг — и зафиксировать приёмку milestone-блока A.

**Architecture:** Два уровня E2E. (1) **JVM-логика** — единый in-memory backend, имитирующий Firestore (`sharedNotes` + приватные поддеревья + индекс `usersByEmail`), и два «устройства», каждое со своей Robolectric-Room-БД, реальными `SharedNotesGatewayImpl` (действия пользователя) и `SyncRepositoryImpl` (фоновый sync), подключёнными к общему backend. Это тестирует настоящую логику приложения (routing push, merge last-write-wins, rollback при понижении роли, эвикция при исключении). (2) **Node-протокол** — расширение существующего эмуляторного харнесса `firestore-rules-tests/` полным lifecycle-сценарием на уровне Firestore-документов с per-uid authenticated-контекстами.

**Tech Stack:** Kotlin, JUnit5 + `kotlinx-coroutines-test` (`runTest`), Robolectric (`@Config(sdk=[34])`) + Room in-memory, существующие маппер/фейк-паттерны из `SyncRepositoryImplTest`/`SharedNotesGatewayImplTest`; Node `node:test` + `@firebase/rules-unit-testing` + Firestore emulator.

**Ключевые ссылки на код (already exists):**
- Sync-движок: `app/src/main/java/app/tessera/data/repository/SyncRepositoryImpl.kt` (public `syncNow`, internal `mergeRemoteShared`).
- Действия пользователя: `app/src/main/java/app/tessera/data/repository/SharedNotesGatewayImpl.kt`.
- Реальный клиент (контракт, который имитируем): `app/src/main/java/app/tessera/data/remote/FirestoreSharedNotesClient.kt`.
- Эталонные фейки/паттерны: `app/src/test/java/app/tessera/data/repository/SyncRepositoryImplTest.kt`, `.../SharedNotesGatewayImplTest.kt`.
- Node-харнесс: `firestore-rules-tests/rules.test.js`, `firestore-rules-tests/package.json`.
- Роадмап (строку статуса Фазы 5 обновляем в конце): `docs/roadmap/ROADMAP-shared-notes.md`.

**Важные контрактные факты (не нарушать в фейках):**
- `RemoteSharedNotesDataSource.fetchAll(uid)` возвращает только заметки, где `uid in memberIds`.
- `addMember` / `updateRole` / `removeMember` / `leaveNote` мутируют `members` + `memberIds` канонического документа (в реальном клиенте — `FieldValue.arrayUnion/arrayRemove` + `members.<uid>` пути). В существующих фейках это **no-op** — для двух-пользовательского E2E их надо реализовать честно.
- `pushSharedNote(note)` обновляет **только контент** (`title/content/tags/isPinned/isArchived/updatedAt/deletedAt`), сохраняя `members`/`memberIds`/`ownerId` (см. реальный клиент и фейк в `SyncRepositoryImplTest`).
- `shareNote(ownerUid, note)` = атомарный move: запись в `sharedNotes`, удаление приватного `users/{owner}/notes/{id}`. `unshareNote` — обратный move.
- `mergeRemoteShared` ставит `myRole = members[uid].role`; эвиктит не-owner заметку, исчезнувшую из member-set; откатывает несинхронизированные правки при понижении роли ниже EDITOR.

**Замечание по запуску:** все JVM-тесты идут под Robolectric (нужен `ApplicationProvider` для Room), поэтому файлы кладём в `app/src/test/...` с `@RunWith(RobolectricTestRunner::class)`. Команда: `./gradlew :app:testDebugUnitTest`. Node-тесты: `cd firestore-rules-tests && npm test` (требует установленный `firebase-tools`/эмулятор).

---

## Chunk 1: Многопользовательский тест-харнесс

Цель чанка: переиспользуемая инфраструктура «общий backend + N устройств», на которой строятся все сценарии. Это единственный новый «продакшн-подобный» код; сами сценарии (Chunk 2) — тонкие.

### Task 1: `InMemorySharedBackend` — канонический имитатор Firestore

Единый объект, который держит состояние «облака» и раздаёт per-device адаптеры. Реализует контракты `RemoteSharedNotesDataSource`, `RemoteNotesDataSource`, `UserDirectoryDataSource` поверх общего состояния, чтобы мутации одного устройства были видны другому.

**Files:**
- Create: `app/src/test/java/app/tessera/data/repository/sharede2e/InMemorySharedBackend.kt`
- Test: `app/src/test/java/app/tessera/data/repository/sharede2e/InMemorySharedBackendTest.kt`

- [ ] **Step 1: Написать падающий тест на backend-контракт**

```kotlin
package app.tessera.data.repository.sharede2e

import app.tessera.data.remote.RemoteShareMember
import app.tessera.data.remote.RemoteSharedNote
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemorySharedBackendTest {
    private fun sharedDoc(owner: String) =
        RemoteSharedNote(
            id = "n1",
            ownerId = owner,
            title = "T",
            content = "B",
            updatedAt = 1L,
            members = mapOf(owner to RemoteShareMember(role = "OWNER", displayName = "Own", email = "own@x.io", joinedAt = 1L)),
            memberIds = listOf(owner),
        )

    @Test
    fun `addMember makes the note visible to the new member's fetchAll`() = runTest {
        val backend = InMemorySharedBackend()
        val shared = backend.sharedFor("alice")
        shared.shareNote("alice", sharedDoc("alice"))

        assertTrue(backend.sharedFor("bob").fetchAll("bob").isEmpty())

        shared.addMember("n1", "bob", RemoteShareMember(role = "EDITOR", displayName = "Bob", email = "bob@x.io", joinedAt = 2L))

        val bobView = backend.sharedFor("bob").fetchAll("bob").single()
        assertEquals("EDITOR", bobView.members["bob"]?.role)
        assertTrue("bob" in bobView.memberIds)
    }

    @Test
    fun `pushSharedNote updates content but preserves membership`() = runTest {
        val backend = InMemorySharedBackend()
        backend.sharedFor("alice").shareNote("alice", sharedDoc("alice"))
        backend.sharedFor("alice").addMember("n1", "bob", RemoteShareMember(role = "EDITOR", displayName = "Bob", email = "bob@x.io", joinedAt = 2L))

        backend.sharedFor("bob").pushSharedNote(
            sharedDoc("alice").copy(content = "edited-by-bob", updatedAt = 99L),
        )

        val doc = backend.sharedFor("alice").fetchAll("alice").single()
        assertEquals("edited-by-bob", doc.content)
        assertEquals(99L, doc.updatedAt)
        assertEquals(setOf("alice", "bob"), doc.memberIds.toSet()) // membership intact
    }

    @Test
    fun `removeMember hides the note from the removed user`() = runTest {
        val backend = InMemorySharedBackend()
        backend.sharedFor("alice").shareNote("alice", sharedDoc("alice"))
        backend.sharedFor("alice").addMember("n1", "bob", RemoteShareMember(role = "EDITOR", displayName = "Bob", email = "bob@x.io", joinedAt = 2L))

        backend.sharedFor("alice").removeMember("n1", "bob")

        assertTrue(backend.sharedFor("bob").fetchAll("bob").isEmpty())
    }

    @Test
    fun `shareNote removes the private doc and unshareNote restores it`() = runTest {
        val backend = InMemorySharedBackend()
        backend.privateFor("alice")
            .upsertAll("alice", listOf(app.tessera.data.remote.RemoteNote("n1", "T", "B", updatedAt = 1L)))

        backend.sharedFor("alice").shareNote("alice", sharedDoc("alice"))
        assertTrue(backend.privateFor("alice").fetchAll("alice").none { it.id == "n1" })

        backend.sharedFor("alice").unshareNote(
            "alice",
            app.tessera.data.remote.RemoteNote("n1", "T", "B", updatedAt = 2L),
        )
        assertTrue(backend.privateFor("alice").fetchAll("alice").any { it.id == "n1" })
        assertTrue(backend.sharedFor("alice").fetchAll("alice").isEmpty())
    }

    @Test
    fun `directory resolves a registered email to its uid`() = runTest {
        val backend = InMemorySharedBackend()
        backend.register("bob", "Bob", "bob@x.io", null)

        val user = backend.directory().lookupByEmail("BOB@x.io")

        assertEquals("bob", user?.uid?.value)
    }
}
```

- [ ] **Step 2: Запустить тест — убедиться, что не компилируется/падает**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.data.repository.sharede2e.InMemorySharedBackendTest"`
Expected: FAIL — `InMemorySharedBackend` не существует.

- [ ] **Step 3: Реализовать `InMemorySharedBackend`**

Каноническое состояние общее; per-device адаптеры — тонкие view над ним. Контракты копируем из:
`app/src/main/java/app/tessera/data/remote/RemoteSharedNotesDataSource.kt`,
`.../RemoteNotesDataSource.kt`, `.../UserDirectoryDataSource.kt`.
Логику `addMember`/`removeMember`/`pushSharedNote` калькируем с `FirestoreSharedNotesClient.kt` и фейка в `SyncRepositoryImplTest.kt`.

```kotlin
package app.tessera.data.repository.sharede2e

import app.tessera.data.remote.DirectoryUser
import app.tessera.data.remote.RemoteNote
import app.tessera.data.remote.RemoteNotesDataSource
import app.tessera.data.remote.RemoteShareMember
import app.tessera.data.remote.RemoteSharedNote
import app.tessera.data.remote.RemoteSharedNotesDataSource
import app.tessera.data.remote.UserDirectoryDataSource
import app.tessera.data.remote.emailKey
import app.tessera.domain.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory stand-in for the whole Firestore backend used by the two-user E2E tests.
 * Holds the canonical `sharedNotes` collection, per-owner private subtrees and the
 * `usersByEmail` directory. Hand out a per-device adapter via [sharedFor]/[privateFor];
 * all adapters mutate the same state, so one device's writes are visible to another's reads.
 */
class InMemorySharedBackend {
    private val sharedNotes = linkedMapOf<String, RemoteSharedNote>()
    private val privateByOwner = linkedMapOf<String, LinkedHashMap<String, RemoteNote>>()
    private val usersByEmail = linkedMapOf<String, DirectoryUser>()
    private val sharedFlow = MutableStateFlow<List<RemoteSharedNote>>(emptyList())

    fun register(uid: String, displayName: String?, email: String, photoUrl: String?) {
        usersByEmail[emailKey(email)] = DirectoryUser(UserId(uid), displayName, email, photoUrl)
    }

    fun directory(): UserDirectoryDataSource = object : UserDirectoryDataSource {
        override suspend fun lookupByEmail(email: String): DirectoryUser? = usersByEmail[emailKey(email)]
    }

    fun sharedFor(uid: String): RemoteSharedNotesDataSource = SharedAdapter(uid)
    fun privateFor(uid: String): RemoteNotesDataSource = PrivateAdapter(uid)

    private fun privateBucket(owner: String) = privateByOwner.getOrPut(owner) { LinkedHashMap() }

    private fun emitShared() {
        sharedFlow.value = sharedNotes.values.toList()
    }

    private inner class SharedAdapter(private val deviceUid: String) : RemoteSharedNotesDataSource {
        override suspend fun shareNote(ownerUid: String, note: RemoteSharedNote) {
            sharedNotes[note.id] = note
            privateBucket(ownerUid).remove(note.id)
            emitShared()
        }

        override suspend fun unshareNote(ownerUid: String, privateNote: RemoteNote) {
            sharedNotes.remove(privateNote.id)
            privateBucket(ownerUid)[privateNote.id] = privateNote
            emitShared()
        }

        override suspend fun addMember(noteId: String, uid: String, member: RemoteShareMember) {
            sharedNotes[noteId]?.let {
                sharedNotes[noteId] = it.copy(
                    members = it.members + (uid to member),
                    memberIds = (it.memberIds + uid).distinct(),
                )
            }
            emitShared()
        }

        override suspend fun updateRole(noteId: String, uid: String, role: String) {
            sharedNotes[noteId]?.let { doc ->
                val m = doc.members[uid] ?: return@let
                sharedNotes[noteId] = doc.copy(members = doc.members + (uid to m.copy(role = role)))
            }
            emitShared()
        }

        override suspend fun removeMember(noteId: String, uid: String) {
            sharedNotes[noteId]?.let {
                sharedNotes[noteId] = it.copy(
                    members = it.members - uid,
                    memberIds = it.memberIds - uid,
                )
            }
            emitShared()
        }

        override suspend fun leaveNote(noteId: String, myUid: String) = removeMember(noteId, myUid)

        override suspend fun pushSharedNote(note: RemoteSharedNote) {
            val existing = sharedNotes[note.id]
            sharedNotes[note.id] = existing?.copy(
                title = note.title,
                content = note.content,
                tags = note.tags,
                isPinned = note.isPinned,
                isArchived = note.isArchived,
                updatedAt = note.updatedAt,
                deletedAt = note.deletedAt,
            ) ?: note
            emitShared()
        }

        override suspend fun fetchAll(uid: String): List<RemoteSharedNote> =
            sharedNotes.values.filter { uid in it.memberIds }

        override fun observeSharedNotes(uid: String): Flow<List<RemoteSharedNote>> = sharedFlow
    }

    private inner class PrivateAdapter(private val deviceUid: String) : RemoteNotesDataSource {
        override suspend fun fetchAll(uid: String): List<RemoteNote> = privateBucket(uid).values.toList()

        override suspend fun upsertAll(uid: String, notes: List<RemoteNote>) {
            notes.forEach { privateBucket(uid)[it.id] = it }
        }

        override fun observeNotes(uid: String): Flow<List<RemoteNote>> =
            MutableStateFlow(privateBucket(uid).values.toList())

        override suspend fun deleteOlderTombstones(uid: String, threshold: Long): Int {
            val stale = privateBucket(uid).values.filter { it.deletedAt != null && it.deletedAt!! < threshold }.map { it.id }
            stale.forEach { privateBucket(uid).remove(it) }
            return stale.size
        }

        override suspend fun deleteByIds(uid: String, ids: List<String>) {
            ids.forEach { privateBucket(uid).remove(it) }
        }
    }
}
```

> ⚠️ Сверить точные сигнатуры `RemoteNotesDataSource`/`RemoteSharedNotesDataSource` с интерфейсами в `app/src/main/java/app/tessera/data/remote/` (имена методов, nullable `deletedAt`). При расхождении — править фейк под интерфейс, не наоборот.

- [ ] **Step 4: Запустить тест — убедиться, что зелёный**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.data.repository.sharede2e.InMemorySharedBackendTest"`
Expected: PASS (5 тестов).

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/app/tessera/data/repository/sharede2e/InMemorySharedBackend.kt \
        app/src/test/java/app/tessera/data/repository/sharede2e/InMemorySharedBackendTest.kt
git commit -m "test(shared-notes): in-memory backend for two-user E2E (phase 5)"
```

### Task 2: `SharedNotesTestDevice` — фабрика «устройства» (Room + gateway + sync)

Каждое устройство: своя Robolectric in-memory Room-БД, реальные `SharedNotesGatewayImpl` и `SyncRepositoryImpl`, подключённые к общему backend. Дублируем wiring из `SharedNotesGatewayImplTest` (gateway) и `SyncRepositoryImplTest`'s `create()` (sync). Для зависимостей `SyncRepositoryImpl`, которые не участвуют в shared-flow (folders), берём тривиальные пустые фейки — переиспользуем существующие приватные фейки, скопировав минимально необходимое.

**Files:**
- Create: `app/src/test/java/app/tessera/data/repository/sharede2e/SharedNotesTestDevice.kt`
- Test: `app/src/test/java/app/tessera/data/repository/sharede2e/SharedNotesTestDeviceTest.kt`

- [ ] **Step 1: Написать падающий smoke-тест устройства**

```kotlin
package app.tessera.data.repository.sharede2e

import androidx.test.core.app.ApplicationProvider
import app.tessera.data.local.entity.NoteEntity
import app.tessera.domain.model.NoteId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedNotesTestDeviceTest {
    private val backend = InMemorySharedBackend()
    private val devices = mutableListOf<SharedNotesTestDevice>()

    private fun device(uid: String, email: String): SharedNotesTestDevice =
        SharedNotesTestDevice(
            context = ApplicationProvider.getApplicationContext(),
            backend = backend,
            uid = uid,
            displayName = uid.replaceFirstChar { it.uppercase() },
            email = email,
        ).also { devices += it }

    @After fun tearDown() = devices.forEach { it.close() }

    @Test
    fun `owner can create a private note and share it`() = runTest {
        val alice = device("alice", "alice@x.io")
        alice.noteDao.upsert(
            NoteEntity("n1", "Title", "Body", false, 1L, 1L, null, 1L, ownerId = "alice"),
        )

        val result = alice.gateway.shareNote(NoteId("n1"))

        assertEquals(true, result.isSuccess)
        assertEquals(NoteEntity.SHARE_STATE_SHARED, alice.noteDao.getById("n1")?.shareState)
    }
}
```

- [ ] **Step 2: Запустить — убедиться, что падает (нет класса)**

Run: `./gradlew :app:testDebugUnitTest --tests "*SharedNotesTestDeviceTest"`
Expected: FAIL — `SharedNotesTestDevice` не найден.

- [ ] **Step 3: Реализовать `SharedNotesTestDevice`**

```kotlin
package app.tessera.data.repository.sharede2e

import android.content.Context
import androidx.room.Room
import app.tessera.core.util.Clock
import app.tessera.data.local.NotesDatabase
import app.tessera.data.mapper.NoteMapper
import app.tessera.data.mapper.SharedNoteMapper
import app.tessera.data.repository.SharedNotesGatewayImpl
import app.tessera.data.repository.SyncRepositoryImpl
import app.tessera.domain.model.UserId
import app.tessera.domain.model.UserProfile
import app.tessera.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One simulated device: its own Room DB plus the real [SharedNotesGatewayImpl] (user actions)
 * and [SyncRepositoryImpl] (background sync), wired to the shared [backend]. Registers the user
 * in the backend directory so other devices can invite them by email.
 */
class SharedNotesTestDevice(
    context: Context,
    backend: InMemorySharedBackend,
    val uid: String,
    displayName: String,
    email: String,
    private val clock: Clock = MonotonicClock(),
) {
    private val db: NotesDatabase =
        Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    val noteDao = db.noteDao()
    val tagDao = db.tagDao()
    val sharedMemberDao = db.sharedMemberDao()

    private val auth: AuthRepository = FixedAuth(uid, UserProfile(UserId(uid), displayName, email, null))

    val gateway = SharedNotesGatewayImpl(
        noteDao = noteDao,
        tagDao = tagDao,
        sharedMemberDao = sharedMemberDao,
        remoteShared = backend.sharedFor(uid),
        userDirectory = backend.directory(),
        authRepository = auth,
        noteMapper = NoteMapper(),
        sharedMapper = SharedNoteMapper(),
        clock = clock,
    )

    val sync = SyncRepositoryImpl(
        noteDao,
        tagDao,
        db.folderDao(),
        sharedMemberDao,
        backend.privateFor(uid),
        EmptyRemoteFolders(),
        backend.sharedFor(uid),
        NoteMapper(),
        SharedNoteMapper(),
        clock,
    )

    /** Pull + merge + push, as the WorkManager job would. */
    suspend fun syncNow() = sync.syncNow(UserId(uid))

    fun close() = db.close()

    init {
        backend.register(uid, displayName, email, null)
    }
}

private class MonotonicClock : Clock {
    private var t = 1_000L
    override fun nowMs(): Long = ++t
}

private class FixedAuth(uid: String, profile: UserProfile) : AuthRepository {
    override val currentUser: StateFlow<UserId?> = MutableStateFlow(UserId(uid))
    override val currentProfile: StateFlow<UserProfile?> = MutableStateFlow(profile)
    override suspend fun signIn() = Result.success(currentUser.value!!)
    override suspend fun signOut() {}
}
```

> ⚠️ Точный конструктор `SyncRepositoryImpl` — взять из `SyncRepositoryImplTest.create()` (порядок/число аргументов могли измениться). `EmptyRemoteFolders` — скопировать минимальный `RemoteFoldersDataSource` из `SyncFakeRemoteFoldersDataSource` в `SyncRepositoryImplTest.kt` в отдельный файл `sharede2e/EmptyRemoteFolders.kt` либо вложенным классом.
> ⚠️ `MonotonicClock` гарантирует строго возрастающие `updatedAt` — критично для last-write-wins: «правка после синка» обязана иметь больший `updatedAt`, иначе merge её отбросит.

- [ ] **Step 4: Создать `EmptyRemoteFolders.kt` (если выносим отдельно)**

```kotlin
package app.tessera.data.repository.sharede2e

import app.tessera.data.remote.RemoteFolder
import app.tessera.data.remote.RemoteFoldersDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class EmptyRemoteFolders : RemoteFoldersDataSource {
    override suspend fun fetchAll(uid: String): List<RemoteFolder> = emptyList()
    override suspend fun upsertAll(uid: String, folders: List<RemoteFolder>) {}
    override fun observeFolders(uid: String): Flow<List<RemoteFolder>> = MutableStateFlow(emptyList())
    override suspend fun deleteOlderTombstones(uid: String, threshold: Long): Int = 0
}
```

- [ ] **Step 5: Запустить smoke — зелёный**

Run: `./gradlew :app:testDebugUnitTest --tests "*SharedNotesTestDeviceTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/test/java/app/tessera/data/repository/sharede2e/
git commit -m "test(shared-notes): two-user test-device factory (phase 5)"
```

---

## Chunk 2: JVM E2E-сценарии двух пользователей

Каждый сценарий — отдельный `@Test` в одном файле `TwoUserSharedNotesE2ETest`, использующем харнесс из Chunk 1. Сценарии покрывают полный путь из роадмапа: инвайт → «Общие» → правка → смена роли → выход/исключение. Один тест = один шаг пути; держим их независимыми.

### Task 3: Инвайт → появление в «Общих» у приглашённого

**Files:**
- Create: `app/src/test/java/app/tessera/data/repository/sharede2e/TwoUserSharedNotesE2ETest.kt`

- [ ] **Step 1: Тест — Bob видит общую заметку после инвайта и синка**

```kotlin
package app.tessera.data.repository.sharede2e

import androidx.test.core.app.ApplicationProvider
import app.tessera.data.local.entity.NoteEntity
import app.tessera.domain.model.InviteResult
import app.tessera.domain.model.NoteId
import app.tessera.domain.model.ShareRole
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TwoUserSharedNotesE2ETest {
    private val backend = InMemorySharedBackend()
    private val devices = mutableListOf<SharedNotesTestDevice>()

    private fun device(uid: String) =
        SharedNotesTestDevice(
            context = ApplicationProvider.getApplicationContext(),
            backend = backend,
            uid = uid,
            displayName = uid.replaceFirstChar { it.uppercase() },
            email = "$uid@x.io",
        ).also { devices += it }

    @After fun tearDown() = devices.forEach { it.close() }

    private suspend fun SharedNotesTestDevice.seedPrivate(id: String, title: String = "T", body: String = "B") {
        noteDao.upsert(NoteEntity(id, title, body, false, 1L, 1L, null, 1L, ownerId = uid))
    }

    @Test
    fun `invite then sync surfaces the shared note for the invitee with EDITOR role`() = runTest {
        val alice = device("alice")
        val bob = device("bob")
        alice.seedPrivate("n1")

        alice.gateway.shareNote(NoteId("n1"))
        val invite = alice.gateway.inviteByEmail(NoteId("n1"), "bob@x.io", ShareRole.EDITOR)
        assertEquals(InviteResult.Added, invite.getOrNull())

        bob.syncNow()

        val bobNote = bob.noteDao.getById("n1")
        assertNotNull(bobNote)
        assertEquals(NoteEntity.SHARE_STATE_SHARED, bobNote?.shareState)
        assertEquals(ShareRole.EDITOR.name, bobNote?.myRole)
        assertTrue(bob.sharedMemberDao.getForNote("n1").map { it.uid }.containsAll(listOf("alice", "bob")))
    }
}
```

- [ ] **Step 2: Run — ожидаем PASS (логика уже реализована в фазах 1–2)**

Run: `./gradlew :app:testDebugUnitTest --tests "*TwoUserSharedNotesE2ETest"`
Expected: PASS. Если FAIL — это **настоящий интеграционный баг** (а не тестовый); см. Task 8 (буфер багфиксов) перед тем как «чинить тест».

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/app/tessera/data/repository/sharede2e/TwoUserSharedNotesE2ETest.kt
git commit -m "test(shared-notes): E2E invite surfaces note in Shared (phase 5)"
```

### Task 4: Правка редактором → document-level sync владельцу

- [ ] **Step 1: Добавить тест в `TwoUserSharedNotesE2ETest`**

```kotlin
    @Test
    fun `editor edit propagates to owner on next sync (last-write-wins)`() = runTest {
        val alice = device("alice")
        val bob = device("bob")
        alice.seedPrivate("n1", body = "original")
        alice.gateway.shareNote(NoteId("n1"))
        alice.gateway.inviteByEmail(NoteId("n1"), "bob@x.io", ShareRole.EDITOR)
        bob.syncNow() // Bob pulls the shared note

        // Bob edits content locally and marks it pending (как делает редактор заметки).
        val pulled = bob.noteDao.getById("n1")!!
        bob.noteDao.upsert(pulled.copy(content = "edited-by-bob", updatedAt = 10_000L, syncedAt = null))
        bob.syncNow() // push Bob's edit to the shared doc

        alice.syncNow() // owner pulls

        assertEquals("edited-by-bob", alice.noteDao.getById("n1")?.content)
    }
```

- [ ] **Step 2: Run** → `./gradlew :app:testDebugUnitTest --tests "*TwoUserSharedNotesE2ETest"` → Expected: PASS.
- [ ] **Step 3: Commit** — `test(shared-notes): E2E editor edit syncs to owner (phase 5)`.

### Task 5: Смена роли → понижение до Viewer откатывает несинхронизированную правку и блокирует push

- [ ] **Step 1: Добавить два теста**

```kotlin
    @Test
    fun `downgrade to viewer rolls back the invitee's unsynced edit`() = runTest {
        val alice = device("alice")
        val bob = device("bob")
        alice.seedPrivate("n1", body = "owner-body")
        alice.gateway.shareNote(NoteId("n1"))
        alice.gateway.inviteByEmail(NoteId("n1"), "bob@x.io", ShareRole.EDITOR)
        bob.syncNow()

        // Alice понижает Bob до VIEWER.
        alice.gateway.changeRole(NoteId("n1"), app.tessera.domain.model.UserId("bob"), ShareRole.VIEWER)

        // Bob успел сделать локальную несинхронизированную правку.
        val pulled = bob.noteDao.getById("n1")!!
        bob.noteDao.upsert(pulled.copy(content = "sneaky-edit", updatedAt = 20_000L, syncedAt = null))

        bob.syncNow() // merge видит роль VIEWER -> откат локальной правки, push контента пропущен

        val bobNote = bob.noteDao.getById("n1")!!
        assertEquals(ShareRole.VIEWER.name, bobNote.myRole)
        assertEquals("owner-body", bobNote.content) // правка откатилась к серверной
    }

    @Test
    fun `a viewer's local edit never reaches the shared document`() = runTest {
        val alice = device("alice")
        val bob = device("bob")
        alice.seedPrivate("n1", body = "owner-body")
        alice.gateway.shareNote(NoteId("n1"))
        alice.gateway.inviteByEmail(NoteId("n1"), "bob@x.io", ShareRole.VIEWER)
        bob.syncNow()

        val pulled = bob.noteDao.getById("n1")!!
        bob.noteDao.upsert(pulled.copy(content = "viewer-edit", updatedAt = 30_000L, syncedAt = null))
        bob.syncNow()
        alice.syncNow()

        assertEquals("owner-body", alice.noteDao.getById("n1")?.content)
    }
```

- [ ] **Step 2: Run** → Expected: PASS (логика — фаза 1 `mergeRemoteShared` rollback/skip).
- [ ] **Step 3: Commit** — `test(shared-notes): E2E role downgrade rolls back & blocks viewer push (phase 5)`.

### Task 6: Исключение / выход / анишеринг → эвикция и возврат в приватное

- [ ] **Step 1: Добавить три теста**

```kotlin
    @Test
    fun `removing a member evicts the note from their device on sync`() = runTest {
        val alice = device("alice")
        val bob = device("bob")
        alice.seedPrivate("n1")
        alice.gateway.shareNote(NoteId("n1"))
        alice.gateway.inviteByEmail(NoteId("n1"), "bob@x.io", ShareRole.EDITOR)
        bob.syncNow()
        assertNotNull(bob.noteDao.getById("n1"))

        alice.gateway.removeMember(NoteId("n1"), app.tessera.domain.model.UserId("bob"))
        bob.syncNow()

        assertNull(bob.noteDao.getById("n1")) // non-owner removed from member set -> evicted
        assertNotNull(alice.noteDao.getById("n1")) // owner keeps it
    }

    @Test
    fun `leaving a shared note removes it locally and drops membership remotely`() = runTest {
        val alice = device("alice")
        val bob = device("bob")
        alice.seedPrivate("n1")
        alice.gateway.shareNote(NoteId("n1"))
        alice.gateway.inviteByEmail(NoteId("n1"), "bob@x.io", ShareRole.EDITOR)
        bob.syncNow()

        bob.gateway.leaveSharedNote(NoteId("n1"))

        assertNull(bob.noteDao.getById("n1"))
        assertTrue(backend.sharedFor("alice").fetchAll("alice").single().memberIds.none { it == "bob" })
    }

    @Test
    fun `owner unshare returns the note to private and clears membership`() = runTest {
        val alice = device("alice")
        val bob = device("bob")
        alice.seedPrivate("n1")
        alice.gateway.shareNote(NoteId("n1"))
        alice.gateway.inviteByEmail(NoteId("n1"), "bob@x.io", ShareRole.EDITOR)
        bob.syncNow()

        alice.gateway.unshareNote(NoteId("n1"))
        bob.syncNow()

        val aliceNote = alice.noteDao.getById("n1")!!
        assertEquals(NoteEntity.SHARE_STATE_PRIVATE, aliceNote.shareState)
        assertNull(aliceNote.myRole)
        assertNull(bob.noteDao.getById("n1")) // gone from the (former) member's device
    }
```

- [ ] **Step 2: Run** → Expected: PASS. Эвикция/анишеринг — фаза 1 (`mergeRemoteShared` eviction + private restore).
- [ ] **Step 3: Полный прогон файла + проверка отсутствия флака**

Run: `./gradlew :app:testDebugUnitTest --tests "*TwoUserSharedNotesE2ETest"`
Expected: PASS, все 8 сценариев.

- [ ] **Step 4: Commit** — `test(shared-notes): E2E remove/leave/unshare eviction (phase 5)`.

---

## Chunk 3: Node-протокол — полный lifecycle против эмулятора

Существующий `rules.test.js` проверяет правила по-операционно. Здесь добавляем **сквозной сценарий** на уровне Firestore-документов: владелец создаёт → шарит (move) → читает индекс и приглашает → приглашённый читает/правит → смена роли → исключение → анишеринг. Это ловит регрессии правил в реальной последовательности, а не изолированно.

**Files:**
- Create: `firestore-rules-tests/flow.test.js`
- Modify (если нужно расширить scripts glob): `firestore-rules-tests/package.json` (проверить, что `node --test` подхватывает `*.test.js` — по умолчанию да).

- [ ] **Step 1: Написать сквозной flow-тест**

Переиспользовать helpers из `rules.test.js` (скопировать `initializeTestEnvironment`/`before`/`after`/`beforeEach` и `db(uid)`-хелпер; не дублировать константы — вынести при желании, но для минимализма допустимо локально). Сценарий:

```js
// firestore-rules-tests/flow.test.js
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { assertSucceeds, assertFails, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { arrayRemove, arrayUnion, deleteDoc, deleteField, doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';

const OWNER = 'owner-uid', INVITEE = 'invitee-uid', NOTE_ID = 'note-1';
let testEnv;
const db = (uid) => testEnv.authenticatedContext(uid).firestore();

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'tessera-rules-test',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'), host: '127.0.0.1', port: 8080 },
  });
});
after(async () => { await testEnv.cleanup(); });
beforeEach(async () => { await testEnv.clearFirestore(); });

function member(role, email) {
  return { role, displayName: role, email, photoUrl: null, joinedAt: 1 };
}

describe('full share lifecycle (two users)', () => {
  test('owner shares, invites, invitee edits, owner downgrades, removes, unshares', async () => {
    // 0. Both users publish their email index (as AuthRepository does on sign-in).
    await assertSucceeds(setDoc(doc(db(OWNER), 'usersByEmail', 'owner@x.io'), { uid: OWNER, email: 'owner@x.io' }));
    await assertSucceeds(setDoc(doc(db(INVITEE), 'usersByEmail', 'invitee@x.io'), { uid: INVITEE, email: 'invitee@x.io' }));

    // 1. Owner creates the shared note with self as OWNER.
    const ownerNote = doc(db(OWNER), 'sharedNotes', NOTE_ID);
    await assertSucceeds(setDoc(ownerNote, {
      id: NOTE_ID, ownerId: OWNER, title: 'T', content: 'orig', tags: [], isPinned: false,
      isArchived: false, createdAt: 1, updatedAt: 1, deletedAt: null,
      members: { [OWNER]: member('OWNER', 'owner@x.io') }, memberIds: [OWNER],
    }));

    // 2. Owner resolves invitee email via the index, then adds them as EDITOR.
    await assertSucceeds(getDoc(doc(db(OWNER), 'usersByEmail', 'invitee@x.io')));
    await assertSucceeds(updateDoc(ownerNote, {
      [`members.${INVITEE}`]: member('EDITOR', 'invitee@x.io'),
      memberIds: arrayUnion(INVITEE),
    }));

    // 3. Invitee can now read and edit content.
    await assertSucceeds(getDoc(doc(db(INVITEE), 'sharedNotes', NOTE_ID)));
    await assertSucceeds(updateDoc(doc(db(INVITEE), 'sharedNotes', NOTE_ID), { content: 'edited', updatedAt: 2 }));

    // 4. Owner downgrades invitee to VIEWER; viewer can no longer edit content.
    await assertSucceeds(updateDoc(ownerNote, { [`members.${INVITEE}.role`]: 'VIEWER' }));
    await assertFails(updateDoc(doc(db(INVITEE), 'sharedNotes', NOTE_ID), { content: 'nope', updatedAt: 3 }));

    // 5. Viewer may still remove themselves (leave).
    await assertSucceeds(updateDoc(doc(db(INVITEE), 'sharedNotes', NOTE_ID), {
      [`members.${INVITEE}`]: deleteField(), memberIds: arrayRemove(INVITEE),
    }));

    // 6. After leaving, the former member can no longer read.
    await assertFails(getDoc(doc(db(INVITEE), 'sharedNotes', NOTE_ID)));

    // 7. Owner unshares: write back to the private subtree and delete the shared doc.
    await assertSucceeds(setDoc(doc(db(OWNER), 'users', OWNER, 'notes', NOTE_ID), {
      id: NOTE_ID, title: 'T', content: 'edited', updatedAt: 4, deletedAt: null,
    }));
    await assertSucceeds(deleteDoc(ownerNote));
  });
});
```

> ⚠️ Сверить форму приватного документа (`users/{uid}/notes/{id}`) и его правила с `firestore.rules` — поля, которые требует rule на create/update приватной заметки. При расхождении привести payload шага 7 в соответствие.

- [ ] **Step 2: Запустить эмуляторный харнесс**

Run: `cd firestore-rules-tests && npm test`
Expected: существующие 17 тестов + новый flow-тест — все зелёные. Если `firebase`/эмулятор не установлены локально — зафиксировать как предусловие в роадмапе и прогнать в среде, где эмулятор доступен (CI/локально с `firebase-tools`).

- [ ] **Step 3: Commit**

```bash
git add firestore-rules-tests/flow.test.js
git commit -m "test(shared-notes): emulator full-lifecycle flow for two users (phase 5)"
```

---

## Chunk 4: Багфиксы, приёмка и фиксация состояния

### Task 8: Буфер багфиксов + полная регрессия + обновление роадмапа + демо-чеклист

- [ ] **Step 1: Полный прогон всех JVM-тестов**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, без новых падений в существующих сьютах.

- [ ] **Step 2: Багфиксы (если на шагах Chunk 2/3 что-то упало)**

Для каждого падения, которое НЕ является ошибкой теста:
1. Применить superpowers:systematic-debugging (сначала воспроизвести минимально, затем гипотеза → проверка).
2. Чинить в продакшн-коде (`SyncRepositoryImpl` / `SharedNotesGatewayImpl` / `firestore.rules`), не ослабляя тест.
3. Отдельный атомарный коммит `fix(shared-notes): <что> (phase 5)` на каждый баг.
4. Перепрогнать соответствующий сьют.

Если падений нет — пропустить шаг, отметить чекбокс как N/A.

- [ ] **Step 3: Сборка debug + компиляция androidTest (как в предыдущих фазах)**

Run: `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Демо-чеклист ручной приёмки блока A**

Создать `docs/superpowers/specs/phase5-acceptance-checklist.md` с ручным сценарием на двух Google-аккаунтах (для живой демонстрации поверх автотестов):

```markdown
# Фаза 5 — чеклист ручной приёмки блока A (два аккаунта)
Предусловие: два устройства/эмулятора, два Google-аккаунта (A — владелец, B — приглашённый).

1. [ ] A: создать заметку, ⋮ → «Сделать общей». Заметка получает аватар-стек (только свой — скрыт).
2. [ ] A: ⋮ → «Управление доступом» → пригласить B по email с ролью Editor → статус «Added».
3. [ ] A: пригласить несуществующий email → «Пользователь не найден».
4. [ ] B: дождаться синка → заметка появляется в секции «Общие»; на карточке аватары A+B.
5. [ ] B (Editor): открыть, изменить текст → у A текст обновляется после синка.
6. [ ] A: сменить роль B на Viewer → у B редактор становится read-only (формат-бар скрыт, ввод заблокирован).
7. [ ] B (Viewer): локальная правка (если успел) откатывается после синка.
8. [ ] A: исключить B → заметка исчезает из «Общих» у B.
9. [ ] B: повторный инвайт → ⋮ → «Выйти» → заметка исчезает у B, у A остаётся.
10. [ ] A: ⋮ → «Удалить для всех» (Manager/Owner) → заметка удаляется у всех участников.
11. [ ] A: «Сделать приватной» (unshare) → заметка возвращается в приватный список A.
```

(Чеклист — артефакт демо; галочки проставляются при живом прогоне, не блокируют коммит плана.)

- [ ] **Step 5: Обновить строку Фазы 5 в роадмапе**

В `docs/roadmap/ROADMAP-shared-notes.md` заменить строку таблицы:
`| 5 | Интеграция блока A | ⬜ Todo | — | — | — | |`
на `✅ Done` с датой `2026-06-23`, ссылкой на этот план в колонке «План» и кратким резюме в «Заметки» (что добавлено: `InMemorySharedBackend` + `SharedNotesTestDevice` харнесс, 8 JVM E2E-сценариев, Node flow-тест, демо-чеклист; перечислить найденные/исправленные баги, если были).

- [ ] **Step 6: Финальный коммит**

```bash
git add docs/superpowers/specs/phase5-acceptance-checklist.md docs/roadmap/ROADMAP-shared-notes.md
git commit -m "docs(shared-notes): phase 5 acceptance checklist + roadmap status Done"
```

- [ ] **Step 7: Завершение ветки**

Применить superpowers:finishing-a-development-branch — блок A (фазы 1–5) завершён; решить merge/PR. (Блок B — live co-editing, фазы 6–10 — отдельный milestone.)

---

## Definition of Done (Фаза 5)

- [ ] `InMemorySharedBackend` + `SharedNotesTestDevice` — переиспользуемый двух-пользовательский харнесс, свои тесты зелёные.
- [ ] 8 JVM E2E-сценариев (`TwoUserSharedNotesE2ETest`) зелёные и недетерминированно не флакают.
- [ ] Node flow-тест проходит против эмулятора (или зафиксировано предусловие окружения).
- [ ] `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` — всё зелёное.
- [ ] Все найденные интеграционные баги исправлены в продакшн-коде отдельными коммитами.
- [ ] Демо-чеклист создан; строка Фазы 5 в роадмапе = ✅ Done.
```
