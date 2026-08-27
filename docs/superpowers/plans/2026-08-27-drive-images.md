# Drive-Backed Images in Notes — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Insert images into notes; store them in the inserting user's own Google Drive (`drive.file` scope), shared per-email to members of shared notes.

**Architecture:** Image = app-level UUID in markup (`tessera-img://<imageId>`), WebP file in `filesDir`, row in new Room table `note_images`, Drive file after async upload, entry in the note's Firestore `images` manifest. Three WorkManager workers (upload, permission reconciliation, cleanup) keep Drive state converged; an Aztec `ImageGetter` resolves markup → local file → Drive download.

**Tech Stack:** Kotlin, Hilt, Room (v6→7 migration), WorkManager (`@HiltWorker` pattern like `SyncWorker`), OkHttp against Drive REST v3 (no google-api-services-drive), `Identity.getAuthorizationClient` (play-services-auth) for the `drive.file` OAuth scope, Aztec v2.1.7 image spans.

**Spec:** `docs/superpowers/specs/2026-08-27-drive-images-design.md` — read it first; it records all decisions (compression, revocation, `ACCESS_LOST`, `FAILED`, 7-day hysteresis) and the Phase-0 risk.

**Verification commands** (used throughout; run from repo root):
- Unit tests: `./gradlew :app:testDebugUnitTest --tests '<pattern>'`
- Full unit suite: `./gradlew :app:testDebugUnitTest`
- Lint/compile check: `./gradlew :app:compileDebugKotlin`

Known environment gotchas (from project memory): if `:app:kspDebugKotlin` hangs on macOS, run `sudo killall syspolicyd`; the full Robolectric suite can flake with `UnsatisfiedLinkError RenderNodeNatives` — re-run the failing class in isolation before blaming a change.

---

## Chunk 1: Phase 0 spike, dependencies, Drive auth + REST client

### Task 1: Dependencies and manifest

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add version-catalog entries**

In `gradle/libs.versions.toml` — versions section:

```toml
okhttp = "4.12.0"
playServicesAuth = "21.2.0"
```

Libraries section:

```toml
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
play-services-auth = { module = "com.google.android.gms:play-services-auth", version.ref = "playServicesAuth" }
```

- [ ] **Step 2: Wire into `app/build.gradle.kts`**

```kotlin
implementation(libs.okhttp)
implementation(libs.play.services.auth)
testImplementation(libs.okhttp.mockwebserver)
```

(Coil already pulls OkHttp transitively; declaring it pins the version.)

- [ ] **Step 3: Manifest — camera feature + FileProvider**

In `AndroidManifest.xml`, inside `<manifest>`:

```xml
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-permission android:name="android.permission.CAMERA" />
```

Inside `<application>`:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

Create `app/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="camera" path="camera/" />
</paths>
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/res/xml/file_paths.xml
git commit -m "build: okhttp, play-services-auth, camera FileProvider for Drive images"
```

### Task 2: Drive REST client (`DriveApi`)

A thin OkHttp wrapper for the six calls we need. Pure Kotlin + OkHttp, token passed in per call, fully testable with MockWebServer.

**Files:**
- Create: `app/src/main/java/app/tessera/data/drive/DriveApi.kt` (interface — workers in Chunk 4 fake it by hand; the project has no mocking framework)
- Create: `app/src/main/java/app/tessera/data/drive/OkHttpDriveApi.kt` (implementation)
- Create: `app/src/main/java/app/tessera/data/drive/DriveModels.kt`
- Create: `app/src/main/java/app/tessera/core/di/DriveModule.kt`
- Test: `app/src/test/java/app/tessera/data/drive/OkHttpDriveApiTest.kt`

- [ ] **Step 1: Write failing tests (MockWebServer)**

Cover: multipart upload returns `DriveFile(id, resourceKey)`; `createPermission` posts correct JSON with `sendNotificationEmail=false`; `listPermissions` parses `emailAddress`+`id`; `deletePermission`/`deleteFile` hit the right paths; `downloadFile` streams bytes and sends `X-Goog-Drive-Resource-Keys` header when a resourceKey is present; 401 → throws `DriveAuthException`; 403 with reason `storageQuotaExceeded` in the error body → throws `DriveQuotaException`; other 4xx/5xx → `DriveApiException(code)`.

```kotlin
class OkHttpDriveApiTest {
    private val server = MockWebServer()
    private lateinit var api: OkHttpDriveApi

    @Before fun setUp() {
        server.start()
        api = OkHttpDriveApi(OkHttpClient(), baseUrl = server.url("/").toString())
    }
    @After fun tearDown() = server.shutdown()

    @Test fun `uploadFile posts multipart and parses id`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"f123","resourceKey":"rk1"}"""))
        val file = api.uploadFile(
            token = "tok", name = "img.webp", parentId = "folder1",
            appImageId = "uuid-1", bytes = byteArrayOf(1, 2, 3),
        )
        assertEquals("f123", file.id)
        assertEquals("rk1", file.resourceKey)
        val recorded = server.takeRequest()
        assertEquals("Bearer tok", recorded.getHeader("Authorization"))
        assertTrue(recorded.path!!.startsWith("/upload/drive/v3/files?uploadType=multipart"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains(""""tesseraImageId":"uuid-1""""))
        assertTrue(body.contains(""""parents":["folder1"]"""))
    }

    @Test fun `quota 403 maps to DriveQuotaException`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"error":{"errors":[{"reason":"storageQuotaExceeded"}]}}"""),
        )
        assertFailsWith<DriveQuotaException> {
            api.uploadFile("tok", "img.webp", "folder1", "uuid-1", byteArrayOf(1))
        }
    }
    // ...analogous tests for the other calls/errors
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'app.tessera.data.drive.OkHttpDriveApiTest'`
Expected: FAIL — `OkHttpDriveApi` unresolved.

- [ ] **Step 3: Implement models + client**

`DriveModels.kt`:

```kotlin
package app.tessera.data.drive

data class DriveFile(val id: String, val resourceKey: String?)
data class DrivePermission(val id: String, val emailAddress: String?, val type: String, val role: String)

open class DriveApiException(val code: Int, message: String) : Exception(message)
class DriveAuthException : DriveApiException(401, "unauthorized")
class DriveQuotaException : DriveApiException(403, "storageQuotaExceeded")
```

`DriveApi.kt` — the interface (Chunk 4 worker tests implement it as a hand-written fake):

```kotlin
interface DriveApi {
    suspend fun uploadFile(token: String, name: String, parentId: String, appImageId: String, bytes: ByteArray): DriveFile
    suspend fun findFolder(token: String, name: String): String?      // GET /drive/v3/files?q=name='…' and mimeType='application/vnd.google-apps.folder' and trashed=false
    suspend fun createFolder(token: String, name: String): String     // POST /drive/v3/files {mimeType: folder}
    suspend fun downloadFile(token: String, fileId: String, resourceKey: String?): ByteArray  // GET /drive/v3/files/{id}?alt=media
    suspend fun listPermissions(token: String, fileId: String): List<DrivePermission>          // GET …/permissions?fields=permissions(id,emailAddress,type,role)
    suspend fun createPermission(token: String, fileId: String, email: String)                 // POST …/permissions?sendNotificationEmail=false {type:user, role:reader, emailAddress}
    suspend fun createAnyonePermission(token: String, fileId: String)                          // POST …/permissions {type:anyone, role:reader} — spike-fallback mode only (Task 12)
    suspend fun deletePermission(token: String, fileId: String, permissionId: String)
    suspend fun trashFile(token: String, fileId: String)               // PATCH /drive/v3/files/{id} {"trashed":true} — NOT files.delete, which bypasses Drive trash and breaks the spec's 30-day recoverability
}
```

`OkHttpDriveApi.kt` — `class OkHttpDriveApi(private val client: OkHttpClient, private val baseUrl: String = "https://www.googleapis.com/") : DriveApi`, all methods wrap `execute()` in `withContext(Dispatchers.IO)`, plus `private fun check(response: Response)` mapping 401/403-quota/other → typed exceptions. JSON via `org.json.JSONObject` (on Android it's built in; for JVM unit tests add `testImplementation("org.json:json:20240303")` if unresolved). If `assertFailsWith` doesn't resolve in tests, use JUnit's `assertThrows` instead of adding kotlin-test.

Multipart upload: `MultipartBody` with two parts — metadata JSON `{"name":…, "parents":[…], "appProperties":{"tesseraImageId":…}}` as `application/json`, and the bytes as `image/webp`.

Required test list (Step 1's "analogous tests"): `createPermission posts type=user reader with sendNotificationEmail=false`, `createAnyonePermission posts type=anyone reader`, `listPermissions parses id and emailAddress`, `deletePermission hits DELETE path`, `trashFile PATCHes trashed=true`, `downloadFile returns bytes and sends resource-key header`, `downloadFile omits header when resourceKey null`, `401 maps to DriveAuthException`, `500 maps to DriveApiException with code`.

Hilt wiring (nothing provides `OkHttpClient` yet):

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DriveModule {
    @Provides @Singleton fun provideOkHttpClient(): OkHttpClient = OkHttpClient()
    @Provides @Singleton fun provideDriveApi(client: OkHttpClient): DriveApi = OkHttpDriveApi(client)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests 'app.tessera.data.drive.OkHttpDriveApiTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/data/drive app/src/test/java/app/tessera/data/drive
git add app/src/main/java/app/tessera/core/di/DriveModule.kt
git commit -m "feat(drive): thin OkHttp client for Drive REST v3"
```

### Task 3: `DriveAuthManager`

**Files:**
- Create: `app/src/main/java/app/tessera/data/drive/DriveAuthManager.kt` (interface + `PlayServicesDriveAuthManager`)
- Create: `app/src/main/java/app/tessera/data/drive/TokenCache.kt` (pure cache/deadline logic)
- Test: `app/src/test/java/app/tessera/data/drive/TokenCacheTest.kt`

- [ ] **Step 1: Define the seam**

`Identity.getAuthorizationClient()` is Play-Services UI plumbing that can't run in unit tests, so split:

```kotlin
/** Pure interface the rest of the app depends on. */
interface DriveAuthManager {
    /** Cached-or-refreshed access token, or null when consent is missing/declined. */
    suspend fun accessToken(): String?
    /** Emits Consent-needed PendingIntents; the editor screen launches them. */
    val consentRequests: SharedFlow<PendingIntent>
    /** Drop cached token after a DriveAuthException so next call re-authorizes. */
    fun invalidateToken()
}
```

`PlayServicesDriveAuthManager` (the impl, `@Singleton class … @Inject constructor(@ApplicationContext private val context: Context)` — `@Binds` needs the `@Inject` constructor) wraps
`Identity.getAuthorizationClient(context).authorize(AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.file"))).build())`:
- result `hasResolution()` → emit its `PendingIntent` on `consentRequests`, return null.
- else → cache `accessToken` with a deadline `now + 55 min`; return cached while fresh.
- Kotlin-ify the Task API with `kotlinx-coroutines-play-services` `.await()` (already a dependency).

- [ ] **Step 2: Unit-test the caching logic only**

The cache/deadline logic lives in `TokenCache.kt` — `class TokenCache(private val clock: () -> Long)` with `fun get(): String?`, `fun put(token: String, ttlMillis: Long)`, `fun invalidate()` — tested in `TokenCacheTest` (fresh token reused; expired token dropped; `invalidate` clears). The Play-Services wrapper itself is covered by the spike (Task 4), not unit tests.

Version note: `play-services-auth 21.2.0` should resolve fine against AGP 8.13 / Hilt ≤2.58 (project memory constraint); if dependency resolution fails, bump/downgrade the patch version rather than touching Hilt.

- [ ] **Step 3: Hilt binding**

Add to `app/src/main/java/app/tessera/core/di/RepositoryModule.kt` (follow the existing `@Binds` pattern):

```kotlin
@Binds @Singleton
abstract fun bindDriveAuthManager(impl: PlayServicesDriveAuthManager): DriveAuthManager
```

- [ ] **Step 4: Run tests + compile, commit**

Run: `./gradlew :app:testDebugUnitTest --tests 'app.tessera.data.drive.*' :app:compileDebugKotlin`
Expected: PASS / BUILD SUCCESSFUL.

```bash
git add -A app/src/main/java/app/tessera/data/drive app/src/test/java/app/tessera/data/drive app/src/main/java/app/tessera/core/di
git commit -m "feat(drive): auth manager with drive.file scope and token cache"
```

### Task 4: Phase-0 spike — recipient read under `drive.file` (MANUAL GATE)

**Files:**
- Create: `docs/superpowers/specs/2026-08-27-drive-images-spike-findings.md`

This gates the permission design. Do NOT proceed to Chunk 4 until resolved (Chunks 2–3 are unaffected either way).

- [ ] **Step 1: Build a throwaway debug hook** — e.g. a hidden long-press action in `SettingsScreen` (or an `adb` broadcast receiver in debug builds) that runs: authorize → upload a tiny file → `createPermission(email of the second test account)` → log fileId.
- [ ] **Step 2: On device/emulator signed into test account A**, run it; note fileId.
- [ ] **Step 3: Switch to test account B** (second emulator or account switch), authorize `drive.file`, call `downloadFile(fileId)`.
- [ ] **Step 4: Record the outcome** in the findings doc:
  - **B reads the file** → main scheme stands; note it and move on.
  - **B gets 404/403** → fallback per spec: `ImagePermissionWorker` (Task 12) must ALSO ensure one `type=anyone, role=reader` permission per file; per-email grants stay. Recipient code is unchanged. Record the decision.
- [ ] **Step 5: Remove the throwaway hook, commit findings**

Verify `git status` shows no leftover edits from the hook (e.g. `SettingsScreen`) — only the findings doc.

```bash
git add docs/superpowers/specs/2026-08-27-drive-images-spike-findings.md
git commit -m "docs: drive.file recipient-read spike findings"
```

## Chunk 2: Data layer + markup round-trip

### Task 5: Room — `note_images` table, entity, DAO, migration 6→7

**Files:**
- Create: `app/src/main/java/app/tessera/data/local/entity/NoteImageEntity.kt`
- Create: `app/src/main/java/app/tessera/data/local/dao/NoteImageDao.kt`
- Create: `app/src/main/java/app/tessera/data/local/Migration6To7.kt`
- Create: `app/src/main/java/app/tessera/data/local/ImageStatusConverter.kt`
- Modify: `app/src/main/java/app/tessera/data/local/NotesDatabase.kt` (add entity + dao, version 7, `@TypeConverters(ImageStatusConverter::class)` on the class)
- Modify: `app/src/main/java/app/tessera/core/di/DatabaseModule.kt` (register `MIGRATION_6_7`)
- Test: `app/src/test/java/app/tessera/data/local/NoteImageDaoTest.kt`
- Test: `app/src/test/java/app/tessera/data/local/Migration6To7Test.kt`

- [ ] **Step 1: Entity + status enum**

```kotlin
package app.tessera.data.local.entity

/** See spec: LOCAL_ONLY → UPLOADING → UPLOADED; FAILED / ACCESS_LOST = "no automatic retry", both recoverable. */
enum class ImageStatus { LOCAL_ONLY, UPLOADING, UPLOADED, FAILED, ACCESS_LOST }

@Entity(
    tableName = "note_images",
    indices = [Index("noteId"), Index("status")],
)
data class NoteImageEntity(
    @PrimaryKey val imageId: String,
    val noteId: String,
    val ownerUid: String?,        // null while signed out; backfilled to next signed-in account
    val localPath: String?,       // null once the source file is gone
    val driveFileId: String? = null,
    val resourceKey: String? = null,
    val status: ImageStatus = ImageStatus.LOCAL_ONLY,
    val width: Int,
    val height: Int,
    val permissionsSyncedAt: Long? = null,
    val orphanedAt: Long? = null,
)
```

No FK to `notes` — an image row must survive its note's local disappearance (`ACCESS_LOST` flow). The codebase has no TypeConverters yet (`NoteEntity.shareState` is a plain `String`); introduce one for the enum:

```kotlin
// ImageStatusConverter.kt
object ImageStatusConverter {
    @TypeConverter fun fromStatus(status: ImageStatus): String = status.name
    @TypeConverter fun toStatus(value: String): ImageStatus = ImageStatus.valueOf(value)
}
```

registered via `@TypeConverters(ImageStatusConverter::class)` on `NotesDatabase` — this stores `status` as TEXT, consistent with how `shareState` looks on disk.

- [ ] **Step 2: DAO**

```kotlin
@Dao
interface NoteImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(image: NoteImageEntity)
    @Query("SELECT * FROM note_images WHERE imageId = :imageId") suspend fun byId(imageId: String): NoteImageEntity?
    @Query("SELECT * FROM note_images WHERE noteId = :noteId") suspend fun byNote(noteId: String): List<NoteImageEntity>
    @Query("SELECT * FROM note_images WHERE ownerUid = :uid AND status = :status") suspend fun byOwnerAndStatus(uid: String, status: ImageStatus): List<NoteImageEntity>
    @Query("SELECT * FROM note_images WHERE ownerUid = :uid") suspend fun byOwner(uid: String): List<NoteImageEntity>
    @Query("UPDATE note_images SET ownerUid = :uid WHERE ownerUid IS NULL") suspend fun backfillOwner(uid: String)
    @Query("UPDATE note_images SET status = :to WHERE ownerUid = :uid AND status = :from") suspend fun transition(uid: String, from: ImageStatus, to: ImageStatus)
    @Query("DELETE FROM note_images WHERE imageId = :imageId") suspend fun delete(imageId: String)
}
```

- [ ] **Step 3: Migration**

`Migration6To7.kt` mirrors `Migration5To6.kt`'s style, full SQL:

```kotlin
internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `note_images` (
                `imageId` TEXT NOT NULL,
                `noteId` TEXT NOT NULL,
                `ownerUid` TEXT,
                `localPath` TEXT,
                `driveFileId` TEXT,
                `resourceKey` TEXT,
                `status` TEXT NOT NULL,
                `width` INTEGER NOT NULL,
                `height` INTEGER NOT NULL,
                `permissionsSyncedAt` INTEGER,
                `orphanedAt` INTEGER,
                PRIMARY KEY(`imageId`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_images_noteId` ON `note_images` (`noteId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_images_status` ON `note_images` (`status`)")
    }
}
```

Guard it with `Migration6To7Test` mirroring the existing `Migration5To6Test` (Robolectric `MigrationTestHelper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)` — this validates the SQL against the entity automatically; a mismatch would otherwise crash only at app startup on upgraded devices).

- [ ] **Step 4: DAO + migration tests (Robolectric, in-memory DB)** — copy the runner/`@Config` setup from the neighboring DB tests. Cover: insert → byId; `byOwnerAndStatus` filters both dimensions (it's the upload worker's driving query); backfillOwner only touches NULL rows; transition only touches (uid, from) rows. Note: first build here triggers KSP schema export — see the macOS `syspolicyd` gotcha in the plan header if it hangs.

- [ ] **Step 5: Run, then commit**

Run: `./gradlew :app:testDebugUnitTest --tests 'app.tessera.data.local.NoteImageDaoTest' --tests 'app.tessera.data.local.Migration6To7Test'`
Expected: PASS.

```bash
git add -A app/src/main/java/app/tessera/data/local app/src/test/java/app/tessera/data/local app/schemas app/src/main/java/app/tessera/core/di/DatabaseModule.kt
git commit -m "feat(db): note_images table for Drive-backed images (v7)"
```

### Task 6: Markup round-trip in `MarkdownHtmlConverter`

**Files:**
- Modify: `app/src/main/java/app/tessera/core/text/MarkdownHtmlConverter.kt`
- Test: `app/src/test/java/app/tessera/core/text/MarkdownHtmlConverterTest.kt` (append)

- [ ] **Step 1: Failing tests first** — in the existing test class:

```kotlin
@Test fun `image line round-trips`() {
    val md = "before\n![](tessera-img://abc-123)\nafter"
    val html = converter.markdownToHtml(md)
    assertTrue(html.contains("<img src=\"tessera-img://abc-123\" />"))
    assertEquals(md, converter.htmlToMarkdown(html))
}
@Test fun `image adjacent to list round-trips`() { /* image line directly above and below a bullet list */ }
@Test fun `image adjacent to table round-trips`() { /* image line directly above and below a markdown table — tables are this converter's most recent regression area (commit 66c5f58) */ }
@Test fun `image between blank lines round-trips`() { /* interacts with BLANK_LINE_PLACEHOLDER logic */ }
```

- [ ] **Step 2: Run to verify they fail**, then implement:
  - **markdownToHtml:** new `when` branch before the plain-line fallback — a line that IS exactly an image ref (`Regex("""^!\[]\(tessera-img://([A-Za-z0-9-]+)\)$""")`) → `addBlock("<img src=\"tessera-img://$id\" />")`. Treating it as a block avoids the `<br>` phantom-blank-line trap documented at the top of `markdownToHtml`.
  - **htmlToMarkdown:** handle the `img` element (both the block position and Aztec's serialization of an image span — check what `AztecText.toHtml()` emits for an image span in practice; Aztec may add attributes like `id`/`class`, so parse `src` only and ignore the rest).
- [ ] **Step 3: Run the WHOLE converter test class** (round-trip regressions are this file's known failure mode):
  `./gradlew :app:testDebugUnitTest --tests 'app.tessera.core.text.MarkdownHtmlConverterTest'` — PASS.
- [ ] **Step 4: Commit** — `feat(text): tessera-img round-trip in markdown converter`

### Task 7: Card previews — strip image refs in `MarkdownPreview`

**Files:**
- Modify: `app/src/main/java/app/tessera/core/text/MarkdownPreview.kt`
- Test: `app/src/test/java/app/tessera/core/text/MarkdownPreviewTest.kt` (append)

- [ ] **Step 1: Failing test** — a note whose content is only an image ref previews as `"📷"`; mixed content previews the text with image lines dropped.
- [ ] **Step 2: Implement** — filter/replace `![](tessera-img://…)` lines in the existing pipeline (follow whatever stage strips other markup). Edge case: the preview picks the first non-blank line, so an image-only note yields an EMPTY preview after stripping — add the `"📷"` fallback at that point, not deeper in the pipeline. Heads-up: the working tree may already carry uncommitted edits to `MarkdownPreview.kt`/`MarkdownPreviewTest.kt` — reconcile or stash before starting.
- [ ] **Step 3: Run class, commit** — `feat(notes): image refs stripped from card previews`

## Chunk 3: Insert pipeline + rendering

### Task 8: `ImageCompressor`

**Files:**
- Create: `app/src/main/java/app/tessera/data/images/ImageCompressor.kt`
- Test: `app/src/test/java/app/tessera/data/images/ImageCompressorTest.kt` (Robolectric — needs Bitmap)

- [ ] **Step 1: Failing tests** — long side capped at 2048 (both orientations); small images not upscaled; EXIF rotation 90° applied (build a tiny JPEG with `androidx.exifinterface.media.ExifInterface` — add `androidx.exifinterface:exifinterface:1.3.7` to the catalog/`app/build.gradle.kts`; the framework class is unreliable under Robolectric); output file exists and is non-empty WebP.
- [ ] **Step 2: Implement**

```kotlin
@Singleton
class ImageCompressor @Inject constructor(@ApplicationContext private val context: Context) {
    data class Compressed(val file: File, val width: Int, val height: Int)

    /** Decode with inSampleSize (OOM safety), apply EXIF orientation, cap long side at 2048, encode WebP q80. */
    suspend fun compress(source: Uri, target: File): Compressed = withContext(Dispatchers.Default) {
        // 1. BitmapFactory.Options(inJustDecodeBounds) via contentResolver.openInputStream
        // 2. inSampleSize = largest power of 2 keeping long side ≥ 2048
        // 3. decode; ExifInterface(stream).rotationDegrees → Matrix rotate if != 0
        // 4. scale down to long side 2048 if still larger (never upscale)
        // 5. compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, out) — API 30+; use WEBP below (check minSdk in app/build.gradle.kts and branch if needed)
    }
}
```

- [ ] **Step 3: Run, commit** — `feat(images): WebP compressor with EXIF + downsampling`

### Task 9: `InsertImageUseCase` + repository

**Files:**
- Create: `app/src/main/java/app/tessera/domain/repository/NoteImagesRepository.kt` (interface)
- Create: `app/src/main/java/app/tessera/data/repository/NoteImagesRepositoryImpl.kt`
- Create: `app/src/main/java/app/tessera/domain/usecase/InsertImageUseCase.kt`
- Modify: `app/src/main/java/app/tessera/core/di/RepositoryModule.kt`
- Test: `app/src/test/java/app/tessera/domain/usecase/InsertImageUseCaseTest.kt`

- [ ] **Step 1: Failing test** (fake repository + fake compressor): use case returns the new `imageId`; row inserted with `LOCAL_ONLY`, `ownerUid` = current auth uid or null; upload scheduling requested exactly once.
- [ ] **Step 2: Implement**

```kotlin
class InsertImageUseCase @Inject constructor(
    private val imagesRepository: NoteImagesRepository,
) {
    /** Compress [source] into filesDir/images/<uuid>.webp, insert LOCAL_ONLY row, request upload. Returns imageId. */
    suspend operator fun invoke(noteId: NoteId, source: Uri): Result<String>
}
```

`NoteImagesRepositoryImpl` owns: `File(context.filesDir, "images")` dir creation, UUID generation (`UUID.randomUUID()`), the `ImageCompressor` call, the DAO insert, and `ImageWorkScheduler.requestUpload()` (interface stub for now; workers arrive in Chunk 4 — give it a no-op `@Binds` impl so this chunk ships green).
- [ ] **Step 3: Run, commit** — `feat(images): insert pipeline (compress → row → schedule)`

### Task 10: Editor integration — sources + rendering

The riskiest UI task; keep sub-steps small and screenshot-test at the end.

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/components/EditorFormatBar.kt` (image + camera buttons emitting new `RichBodyActions` callbacks)
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt` (activity-result launchers)
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorViewModel.kt` (calls `InsertImageUseCase`, exposes inserted id to splice `![](tessera-img://id)` into content at cursor)
- Modify: `app/src/main/java/app/tessera/ui/editor/components/FlickerlessAztecText.kt` (clipboard `commitContent`)
- Create: `app/src/main/java/app/tessera/ui/editor/components/TesseraImageLoader.kt`
- Modify: `app/src/main/java/app/tessera/ui/editor/components/RichNoteBody.kt` (wire `imageGetter`)
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ru/strings.xml`
- Test: `app/src/test/java/app/tessera/ui/editor/NoteEditorViewModelTest.kt` (append), Roborazzi screenshot

- [ ] **Step 1: Format bar** — two buttons (Lucide/Material icons: image, camera) in a new group; extend `RichBodyActions` with `onInsertImage: () -> Unit` / `onCaptureImage: () -> Unit`; string resources in EN + RU.
- [ ] **Step 2: Launchers in `NoteEditorScreen`** — `rememberLauncherForActivityResult(PickVisualMedia())` and `TakePicture()` writing to a `FileProvider` URI under `cacheDir/camera/`; camera path first requests `CAMERA` permission via `RequestPermission()` launcher when not granted. Results call `viewModel.insertImage(uri)`.
- [ ] **Step 3: ViewModel** — `insertImage(uri)`: launch in `viewModelScope`, call use case, then insert markup at the current cursor. IMPORTANT integration detail: content flows through Aztec, so splice by telling the view layer to insert HTML at selection (`aztec.fromHtml`? No — use `AztecText.insertHtml`-equivalent: check what exists on Aztec 2.1.7; the reliable path used by Aztec's own toolbar is `insertImage(drawable, attributes)`; alternatively replace selection in the editable with a placeholder char + `AztecImageSpan`). Verified against the Aztec 2.1.7 artifact: `AztecText.insertImage(Drawable, Attributes)` exists — use it; no manual span fallback needed. Insert the span only AFTER compression succeeds (simplest; no spinner needed for local inserts — sub-second per spec). The loading-spinner placeholder from the loader (Step 5) is for the download path only.
- [ ] **Step 4: Clipboard** — in `FlickerlessAztecText`, override `onCreateInputConnection`: wrap `super`'s connection with `InputConnectionCompat.createWrapper(connection, editorInfo) { inputContentInfo, flags, _ -> onImageCommitted?.invoke(inputContentInfo) ?: false }`; set `EditorInfoCompat.setContentMimeTypes(editorInfo, arrayOf("image/*"))`. When `flags` has `INPUT_CONTENT_GRANT_READ_URI_PERMISSION`, call `inputContentInfo.requestPermission()` before reading the URI and `releasePermission()` after the copy — otherwise some keyboards throw `SecurityException`. Also override `onTextContextMenuItem(android.R.id.paste)`: if the clipboard's primary clip has an `image/*` URI, route to `onImageCommitted` instead of default paste. New hook `var onImageCommitted: ((Uri) -> Boolean)?` following the existing hook style (`onLightweightRefresh`).
- [ ] **Step 5: `TesseraImageLoader`** — implements `org.wordpress.aztec.Html.ImageGetter` (verified in the 2.1.7 artifact: `loadImage(String, Callbacks, Int)` + `loadImage(String, Callbacks, Int, Int)`). It needs DAO access from the UI layer: construct it in the ViewModel (Hilt-injected `NoteImageDao` + downloader seam) and hand it to `RichNoteBody`/the `AndroidView` factory alongside the existing editor wiring:
  1. Parse `tessera-img://<id>`; non-matching sources → failed callback.
  2. Room row's `localPath` file exists → decode downsampled to `maxWidth` → `callbacks.onImageLoaded(drawable)`.
  3. Else `cacheDir/images/<id>.webp` exists → same.
  4. Else → loading placeholder + delegate to `ImageDownloader` (Chunk 4; until then, show the "unavailable" placeholder with retry) — keep the seam as an injected `suspend (imageId) -> File?`.
  5. Failure → "unavailable" placeholder; retry allowed unless row is `FAILED` with `localPath == null` (spec: retry suppressed when the source file is gone).
- [ ] **Step 6: Tests** — ViewModel test: `insertImage` inserts markup once and only after the use case succeeds. Roborazzi screenshot of the editor showing a local image (use `captureScreenRoboImage` per project memory).
- [ ] **Step 7: Full editor test sweep + commit**

Run: `./gradlew :app:testDebugUnitTest --tests 'app.tessera.ui.editor.*' --tests 'app.tessera.core.text.*'`
Expected: PASS (rerun flaky classes in isolation per memory note).

```bash
git add -A app/src/main/java/app/tessera/ui/editor app/src/main/res app/src/test/java/app/tessera/ui/editor
git commit -m "feat(editor): insert images from picker/camera/clipboard, render via ImageGetter"
```

## Chunk 4: Sync — upload, manifest, permissions, recipient download, cleanup

### Task 11: Firestore manifest + `ImageUploadWorker`

**Files:**
- Modify: `app/src/main/java/app/tessera/data/remote/RemoteNote.kt` and `RemoteSharedNote.kt` — add `val images: Map<String, RemoteImageRef> = emptyMap()`
- Create: `app/src/main/java/app/tessera/data/remote/RemoteImageRef.kt` (`driveFileId`, `resourceKey`, `ownerUid`, `width`, `height` — no-arg defaults for Firestore)
- Modify: `app/src/main/java/app/tessera/data/remote/FirestoreNotesClient.kt` + `FirestoreSharedNotesClient.kt` — `updateImageManifest(noteId, imageId, ref?)` (set or delete a single map key via `FieldPath.of("images", imageId)`)
- Modify: mappers + `SyncRepositoryImpl` so pulled manifests land in Room. Pull rule: upsert any manifest `imageId` that has NO local row (regardless of owner — this covers both recipients AND the owner's own second device / fresh install) with `status = UPLOADED`, `localPath = null`; NEVER overwrite a row that already exists locally.
- Create: `app/src/main/java/app/tessera/sync/ImageUploadWorker.kt` + `ImageWorkScheduler` real impl (replaces Chunk 3's no-op; model on `WorkManagerSyncScheduler`/`SyncWorker`)
- Modify: `app/src/main/java/app/tessera/data/repository/AuthRepositoryImpl.kt` — on successful sign-in, call `noteImageDao.backfillOwner(uid)` + schedule upload
- Test: `app/src/test/java/app/tessera/sync/ImageUploadWorkerTest.kt` (fake `DriveApi`/DAO), mapper tests

- [ ] **Step 1: Failing worker tests** — happy path (`LOCAL_ONLY` → uploaded → row has fileId, status `UPLOADED`, manifest patched); folder found once then cached (DataStore, key `drive_folder_id`); `DriveQuotaException` → status `FAILED`, `Result.success()` (terminal — no WorkManager retry), one-shot user message via the existing snackbar/status channel the app uses (check `SyncStatusProvider` for the pattern); `DriveAuthException` → token invalidated, `Result.retry()`; source file missing → `FAILED` + `localPath = null`; transient `IOException` → `Result.retry()`.

Revocation semantics (spec: "only `UPLOADING`/pending rows revert to `LOCAL_ONLY`; `UPLOADED` untouched"): the worker distinguishes transient 401 from permanent revocation via `DriveAuthManager.accessToken()` — a *permanently* revoked grant makes `authorize()` return `hasResolution()` again, so `accessToken()` returns null (and emits the consent PendingIntent). Test: `accessToken() == null` at worker start → transition my `UPLOADING` rows back to `LOCAL_ONLY` via `dao.transition(uid, UPLOADING, LOCAL_ONLY)`, do NOT touch `UPLOADED` rows, return `Result.success()` (lazy re-consent will re-trigger upload later — same path as "consent declined").
- [ ] **Step 2: Implement worker** (`@HiltWorker`, `CoroutineWorker`, uid via `inputData` like `SyncWorker`); triggers: insert (from Chunk 3 scheduler seam), sign-in, network regained (constraint `NETWORK_CONNECTED` + backoff handles this). Per-row flow: mark `UPLOADING` → `uploadFile` → `UPLOADED` + manifest patch. On entry: reset stale `UPLOADING` rows to `LOCAL_ONLY` before selecting work — deliberately the SAME `dao.transition(uid, UPLOADING, LOCAL_ONLY)` call that the revocation path uses (one operation, two triggers: crashed previous run, or `accessToken() == null`).
- [ ] **Step 3: Manifest plumbing + mappers with tests.** Note the pull direction: manifest rows for images I don't own must NOT create files, only DB rows for the renderer/downloader.
- [ ] **Step 4: Run, commit**

Run: `./gradlew :app:testDebugUnitTest --tests 'app.tessera.sync.ImageUploadWorkerTest' --tests 'app.tessera.data.mapper.*'`
Expected: PASS. Commit: `feat(sync): image upload worker + Firestore image manifest`

### Task 12: `ImagePermissionWorker`

**Files:**
- Create: `app/src/main/java/app/tessera/sync/ImagePermissionWorker.kt`
- Create: `app/src/main/java/app/tessera/domain/usecase/ComputePermissionDiff.kt` (pure function)
- Test: `app/src/test/java/app/tessera/domain/usecase/ComputePermissionDiffTest.kt`, `app/src/test/java/app/tessera/sync/ImagePermissionWorkerTest.kt`

- [ ] **Step 1: Pure diff first (TDD)**

```kotlin
/** target = member emails (lowercased); actual = Drive permissions. My own owner permission is never touched. */
data class PermissionDiff(
    val toGrant: List<String>,
    val toRevoke: List<DrivePermission>,
    /** Fallback mode only: an `anyone` reader permission must be created on this file. */
    val ensureAnyoneLink: Boolean,
)
fun computePermissionDiff(
    targetEmails: Set<String>,
    actual: List<DrivePermission>,
    myEmail: String,
    anyoneLinkMode: Boolean,  // true = spike FAILED, capability-URL fallback active (see spike findings doc)
): PermissionDiff
```

Tests: grant missing; revoke extra `type=user` readers not in target; never revoke `role=owner` or my own email. Fallback branch (`anyoneLinkMode = true`): `ensureAnyoneLink` true when no `type=anyone` permission exists AND the note is shared (target non-empty), false otherwise; `anyone` entries never revoked while the note is shared, revoked when the note went private. Main branch (`anyoneLinkMode = false`): stray `type=anyone` entries always revoked, `ensureAnyoneLink` always false. `anyoneLinkMode` is a compile-time constant in one place (e.g. `DriveImagesConfig.ANYONE_LINK_MODE`), set from the Task 4 spike verdict (the spike findings doc must record which value was chosen). The worker maps `ensureAnyoneLink` → `DriveApi.createAnyonePermission` (already in the Task 2 interface).
- [ ] **Step 2: Worker** — for each of my `UPLOADED` images grouped by note: target = emails of current `shared_members` rows for that note (private note → empty target); apply diff via `DriveApi`; stamp `permissionsSyncedAt`. Triggers: membership change (call site: wherever `SharedMemberDao` rows are written by sync — find it in `SyncRepositoryImpl`), upload success (chained from Task 11), plus `PeriodicWorkRequest` every 24 h (`enqueueUniquePeriodicWork`, registered where the app schedules periodic work — check `WorkManagerModule.kt` / `TesseraApplication`).
- [ ] **Step 3: Run, commit**

Run: `./gradlew :app:testDebugUnitTest --tests 'app.tessera.domain.usecase.ComputePermissionDiffTest' --tests 'app.tessera.sync.ImagePermissionWorkerTest'`
Expected: PASS. Commit: `feat(sync): declarative Drive permission reconciliation`

### Task 13: Recipient download path

**Files:**
- Create: `app/src/main/java/app/tessera/data/images/ImageDownloader.kt`
- Modify: `app/src/main/java/app/tessera/ui/editor/components/TesseraImageLoader.kt` (replace Chunk 3 stub seam)
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt` (launch `consentRequests` PendingIntents via `rememberLauncherForActivityResult(StartIntentSenderForResult())`)
- Test: `app/src/test/java/app/tessera/data/images/ImageDownloaderTest.kt`

- [ ] **Step 1: Failing tests** — resolves row → `downloadFile(driveFileId, resourceKey)` → writes `cacheDir/images/<id>.webp`, returns file; 403/404 → null (loader shows retryable placeholder); no token (consent pending) → null without calling the API; LRU eviction: after writing, if `cacheDir/images` exceeds 200 MB, delete oldest-by-lastModified until under.
- [ ] **Step 2: Implement + wire into the loader.** Consent is lazy: first download attempt with no token triggers `DriveAuthManager.accessToken()` which emits the consent `PendingIntent`; the editor screen collects `consentRequests` and launches them.
- [ ] **Step 3: Tap-to-retry for `FAILED` rows (spec: FAILED is recoverable).** Create `app/src/main/java/app/tessera/domain/usecase/RetryImageUploadUseCase.kt`: for MY row with `status = FAILED` and `localPath != null` → set `LOCAL_ONLY`, call `ImageWorkScheduler.requestUpload()`; no-op when `localPath == null` (retry suppressed per spec). Wire it to the loader's placeholder tap alongside the download-retry path. Test: `app/src/test/java/app/tessera/domain/usecase/RetryImageUploadUseCaseTest.kt` (both branches).
- [ ] **Step 4: Run, commit**

Run: `./gradlew :app:testDebugUnitTest --tests 'app.tessera.data.images.ImageDownloaderTest' --tests 'app.tessera.domain.usecase.RetryImageUploadUseCaseTest'`
Expected: PASS. Commit: `feat(images): Drive download + cache for shared notes, FAILED retry`

### Task 14: `ImageCleanupWorker` + `ACCESS_LOST`

**Files:**
- Create: `app/src/main/java/app/tessera/sync/ImageCleanupWorker.kt`
- Create: `app/src/main/java/app/tessera/domain/usecase/FindOrphanImages.kt` (pure)
- Modify: `app/src/main/java/app/tessera/data/repository/SyncRepositoryImpl.kt` — distinguish access-loss from deletion when a shared note vanishes: a `FirebaseFirestoreException` with `code == PERMISSION_DENIED` surfacing from the shared-note fetch/listener → mark that note's image rows `ACCESS_LOST`; a document-delete event in the snapshot → genuine deletion, leave rows for normal orphan flow. (Executor: locate where `SyncRepositoryImpl` currently handles a shared note disappearing — the same branch that removes the local note row — and hook both cases there.) On a shared note REAPPEARING (re-added), flip its `ACCESS_LOST` rows back — status-aware, NOT blanket `UPLOADED`: rows with `driveFileId != null` → `UPLOADED`, rows without → `LOCAL_ONLY` (a blanket flip would hand null fileIds to the permission worker and manifest patch). Simplest DAO shape: `byNote(noteId)` + per-row update.
- Test: `app/src/test/java/app/tessera/domain/usecase/FindOrphanImagesTest.kt`, `app/src/test/java/app/tessera/sync/ImageCleanupWorkerTest.kt`, and NEW cases in the existing `app/src/test/java/app/tessera/data/repository/SyncRepositoryImplTest.kt`: PERMISSION_DENIED marks rows `ACCESS_LOST`; delete event does NOT; reappearing note flips `ACCESS_LOST` → `UPLOADED` when `driveFileId != null` and → `LOCAL_ONLY` when null.

- [ ] **Step 1: Pure orphan logic (TDD)**

```kotlin
/** notesContent covers ALL live notes incl. app-trash (deletedAt != null still counts as live). */
fun findOrphans(images: List<NoteImageEntity>, notesContent: Map<String, String>, now: Long): OrphanResult
// marks orphanedAt on unreferenced rows, RESETS it when a reference reappears, skips ACCESS_LOST rows entirely
```

Tests: unreferenced → stamped; re-referenced → reset to null; `ACCESS_LOST` → untouched; app-trash note references still count.
- [ ] **Step 2: Worker** — scope: Drive-side actions apply ONLY to MY rows (`ownerUid == currentUid`) — only the owner can trash their file or is responsible for its manifest entry. My rows with `orphanedAt` older than the hysteresis window: if `driveFileId != null` → `trashFile` (PATCH `trashed:true`, preserving the spec's 30-day Drive-trash recoverability — never `files.delete`) + manifest entry removal; always delete local file + row. Other-owner rows whose note is genuinely gone: drop only the Room row (their cached file is the LRU's job per spec). Drive 404 on trash → treat as already-gone, proceed. Runs daily (same periodic registration as Task 12) + after note save (the single persist path in `NotesRepositoryImpl` — the method the editor's save flow calls; executor confirms the exact name there). Inject the window as a constructor param defaulting to 7 days — Task 15's manual pass shortens it without a debug hack.
- [ ] **Step 3: Run the FULL unit suite** — `./gradlew :app:testDebugUnitTest` — everything green (isolate Robolectric flakes per memory note).
- [ ] **Step 4: Commit** — `feat(sync): image cleanup with 7-day hysteresis + ACCESS_LOST handling`

### Task 15: End-to-end manual pass + docs

- [ ] **Step 1: Manual checklist on two accounts** (from spec): insert from picker/camera/clipboard offline → visible instantly; sign in → uploads land in Drive "Tessera" folder; share note to account B → B sees images after consent; remove B → B gets placeholder on fresh install/cache-clear; delete image → Drive file trashed after hysteresis (use the injectable window from Task 14 to shorten it for the test). Deliberately deferred from the spec's UI polish: the persistent "not synced" badge on `LOCAL_ONLY`/`FAILED` images is v1.1 — v1 ships the three loader placeholder states only; note this in the findings doc.
- [ ] **Step 2: Update `firestore.rules` if the manifest needs explicit mention** (it lives inside note docs, so existing rules should cover; verify against `firestore-rules-tests/` and add a test there if rules change).
- [ ] **Step 3: Final commit + use superpowers:finishing-a-development-branch.**
