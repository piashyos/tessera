# Android Notes Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a fast, simple Android notes app — local-first (Room) with optional cloud sync (Firebase Auth + Firestore) and a modern Compose UI with floating blur capsules and rich animations.

**Architecture:** Single Gradle module (`:app`), Clean-ish layers (UI / Domain / Data). Room is the source of truth; Firestore is a replica reached via WorkManager and a snapshot listener. UI never blocks on the network. LWW conflict resolution on `updatedAt`.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, Room (with FTS4), Hilt, Firebase Auth (Credential Manager), Firestore, WorkManager, Markwon (markdown rendering), JUnit5 + Turbine + Compose Test.

**Spec:** [`docs/superpowers/specs/2026-06-14-android-notes-design.md`](../specs/2026-06-14-android-notes-design.md)

---

## File Structure (target)

```
app/
  build.gradle.kts
  google-services.json                          # gitignored
  src/
    main/
      AndroidManifest.xml
      java/app/tessera/
        NotesApplication.kt                     # @HiltAndroidApp
        MainActivity.kt                         # setContent { NotesApp() }
        NotesApp.kt                             # NavHost root

        core/
          di/
            AppModule.kt                        # @Module providing Clock, dispatchers
            DatabaseModule.kt                   # Room
            FirebaseModule.kt                   # FirebaseAuth, Firestore
            RepositoryModule.kt                 # @Binds
          util/
            Clock.kt                            # interface Clock { fun nowMs(): Long }
            DispatcherProvider.kt
          ui/
            theme/{Color.kt, Theme.kt, Type.kt}
            components/
              FloatingCapsule.kt                # blur + light border + alpha bg
              GlassFab.kt
              MarkdownText.kt                   # Markwon AndroidView

        data/
          local/
            NotesDatabase.kt                    # @Database
            entity/
              NoteEntity.kt
              TagEntity.kt
              NoteTagCrossRef.kt
              NoteFtsEntity.kt                  # @Fts4(contentEntity = NoteEntity::class)
            dao/
              NoteDao.kt
              TagDao.kt
            converter/Converters.kt             # Long? <-> Instant if needed
          remote/
            FirestoreNotesClient.kt             # raw Firestore CRUD
          mapper/NoteMapper.kt                  # Entity <-> Domain <-> RemoteDto
          repository/
            NotesRepositoryImpl.kt
            AuthRepositoryImpl.kt
            SyncRepositoryImpl.kt

        domain/
          model/{Note.kt, Tag.kt, NoteId.kt, UserId.kt}
          repository/
            NotesRepository.kt                  # interface
            AuthRepository.kt
            SyncRepository.kt
          usecase/
            ObserveNotesUseCase.kt
            GetNoteUseCase.kt
            CreateNoteUseCase.kt
            UpdateNoteUseCase.kt
            DeleteNoteUseCase.kt
            TogglePinUseCase.kt
            SearchNotesUseCase.kt
            SignInUseCase.kt
            SignOutUseCase.kt
            HandleAccountSwitchUseCase.kt

        ui/
          notes/
            NotesListScreen.kt
            NotesListViewModel.kt
            NotesListState.kt
            components/{NoteCard.kt, SwipeableNoteCard.kt, BottomSearchBar.kt}
          editor/
            NoteEditorScreen.kt
            NoteEditorViewModel.kt
            NoteEditorState.kt
            components/{MarkdownToolbar.kt, TagChips.kt}
          settings/
            SettingsScreen.kt
            SettingsViewModel.kt
          navigation/NotesNavGraph.kt

        sync/
          SyncWorker.kt                          # periodic + on-change
          DeleteTombstonesWorker.kt              # daily

    test/                                        # JVM unit tests
      java/app/tessera/
        domain/usecase/...
        data/repository/SyncRepositoryImplTest.kt
        data/mapper/NoteMapperTest.kt

    androidTest/                                 # instrumented (Room, UI)
      java/app/tessera/
        data/local/NoteDaoTest.kt
        ui/notes/NotesListScreenTest.kt
        ui/editor/NoteEditorScreenTest.kt
        sync/DeleteTombstonesWorkerTest.kt
```

---

## Chunks

The plan is split into **8 chunks**. Each is self-contained and ends with a runnable, committable state.

| # | Chunk | Outcome |
|---|-------|---------|
| 1 | Project bootstrap | Gradle, Hilt, Compose, theme, "Hello world" app installs and runs |
| 2 | Data layer (Room) | Entities, DAO, FTS, migrations, instrumented DAO tests pass |
| 3 | Domain + Repository (local-only) | Use-cases, NotesRepositoryImpl backed by Room; JVM tests pass |
| 4 | UI core: floating capsules + theme | `FloatingCapsule`, `GlassFab` components with previews |
| 5 | Notes list screen | List, swipe-left actions, pin/delete with undo, search, animations |
| 6 | Note editor screen | Title/body, autosave, checkbox toggle, tag picker, markdown toolbar |
| 7 | Firebase Auth + Settings | Credential Manager sign-in, sign-out, account-switch dialog |
| 8 | Firestore sync | SyncRepository (LWW), SyncWorker, DeleteTombstonesWorker, race handling |

---

## Chunk 1: Project bootstrap

**Outcome:** A runnable Android app on emulator/device with Compose, Hilt, and theme wired. No features yet — just `"Hello, Notes"` on screen.

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts` (root), `app/build.gradle.kts`, `gradle/libs.versions.toml`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/app/tessera/NotesApplication.kt`
- Create: `app/src/main/java/app/tessera/MainActivity.kt`
- Create: `app/src/main/java/app/tessera/NotesApp.kt`
- Create: `app/src/main/java/app/tessera/core/ui/theme/{Color,Theme,Type}.kt`
- Create: `.gitignore`

### Task 1.1: Bootstrap Gradle project

- [ ] **Step 1: Create `.gitignore`**

```
.gradle/
build/
.idea/
*.iml
local.properties
google-services.json
.DS_Store
```

- [ ] **Step 2: Create `gradle/libs.versions.toml`** (Version Catalog)

Pin versions in one place. Use latest stable from BOMs.

```toml
[versions]
agp = "8.7.0"
kotlin = "2.0.21"
compose-bom = "2024.10.00"
hilt = "2.52"
hilt-nav-compose = "1.2.0"
room = "2.6.1"
nav-compose = "2.8.3"
work = "2.9.1"
firebase-bom = "33.5.1"
credentials = "1.3.0"
googleid = "1.1.1"
turbine = "1.1.0"
junit5 = "5.11.0"
markwon = "4.6.2"
coroutines = "1.9.0"
kotlin-ksp = "2.0.21-1.0.25"

[libraries]
androidx-core-ktx = "androidx.core:core-ktx:1.13.1"
androidx-lifecycle-viewmodel-compose = "androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6"
androidx-activity-compose = "androidx.activity:activity-compose:1.9.3"
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-animation = { module = "androidx.compose.animation:animation" }
compose-foundation = { module = "androidx.compose.foundation:foundation" }
nav-compose = { module = "androidx.navigation:navigation-compose", version.ref = "nav-compose" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
hilt-nav-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hilt-nav-compose" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
hilt-work = { module = "androidx.hilt:hilt-work", version = "1.2.0" }
hilt-work-compiler = { module = "androidx.hilt:hilt-compiler", version = "1.2.0" }
firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebase-bom" }
firebase-auth = { module = "com.google.firebase:firebase-auth-ktx" }
firebase-firestore = { module = "com.google.firebase:firebase-firestore-ktx" }
credentials = { module = "androidx.credentials:credentials", version.ref = "credentials" }
credentials-play-services = { module = "androidx.credentials:credentials-play-services-auth", version.ref = "credentials" }
googleid = { module = "com.google.android.libraries.identity.googleid:googleid", version.ref = "googleid" }
markwon-core = { module = "io.noties.markwon:core", version.ref = "markwon" }
coroutines = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
junit5 = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit5" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
androidx-room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
androidx-test-runner = "androidx.test:runner:1.6.2"
androidx-test-ext-junit = "androidx.test.ext:junit:1.2.1"
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-ksp = { id = "com.google.devtools.ksp", version.ref = "kotlin-ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
google-services = { id = "com.google.gms.google-services", version = "4.4.2" }
```

- [ ] **Step 3: Create root `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google { content { includeGroupByRegex("com\\.android.*"); includeGroupByRegex("com\\.google.*"); includeGroupByRegex("androidx.*") } }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "notes"
include(":app")
```

- [ ] **Step 4: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
}
```

- [ ] **Step 5: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

android {
    namespace = "app.tessera"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.tessera"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "app.tessera.HiltTestRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.nav.compose)
    implementation(libs.coroutines)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.nav.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)

    implementation(libs.markwon.core)

    testImplementation(libs.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
```

- [ ] **Step 6: Verify build succeeds**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (a couple of "no google-services.json" warnings can be silenced by stubbing the plugin in a later step before sync chunk; for now temporarily remove `google.services` plugin if it errors — we'll re-add in Chunk 7).

If it errors on missing `google-services.json`: remove `alias(libs.plugins.google.services)` from `app/build.gradle.kts` for now, and remove the four firebase/credentials/googleid lines from dependencies until Chunk 7. Add a TODO comment in `app/build.gradle.kts`:

```kotlin
// TODO(chunk 7): re-enable google-services plugin + Firebase/Credentials deps
```

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle/libs.versions.toml app/build.gradle.kts .gitignore
git commit -m "chore: bootstrap gradle + version catalog"
```

### Task 1.2: AndroidManifest + Application class + theme

- [ ] **Step 1: Create `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:name=".NotesApplication"
        android:label="Notes"
        android:theme="@style/Theme.Notes"
        android:supportsRtl="true">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Notes">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 2: Create `app/src/main/res/values/themes.xml`**

```xml
<resources>
    <style name="Theme.Notes" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 3: Create `NotesApplication.kt`**

```kotlin
package app.tessera

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NotesApplication : Application()
```

- [ ] **Step 4: Create theme files**

`core/ui/theme/Color.kt`:
```kotlin
package app.tessera.core.ui.theme

import androidx.compose.ui.graphics.Color

val SurfaceLight = Color(0xFFF7F4EF)
val SurfaceDark = Color(0xFF1A1A1A)
val CapsuleBorder = Color.White.copy(alpha = 0.20f)
val CapsuleTintLight = Color.White.copy(alpha = 0.65f)
val CapsuleTintDark = Color.Black.copy(alpha = 0.55f)
```

`core/ui/theme/Type.kt`:
```kotlin
package app.tessera.core.ui.theme

import androidx.compose.material3.Typography
val NotesTypography = Typography()
```

`core/ui/theme/Theme.kt`:
```kotlin
package app.tessera.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun NotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val ctx = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, typography = NotesTypography, content = content)
}
```

- [ ] **Step 5: Create `MainActivity.kt` + `NotesApp.kt`**

`MainActivity.kt`:
```kotlin
package app.tessera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { NotesApp() }
    }
}
```

`NotesApp.kt`:
```kotlin
package app.tessera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.tessera.core.ui.theme.NotesTheme

@Composable
fun NotesApp() {
    NotesTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("Hello, Notes")
            }
        }
    }
}
```

- [ ] **Step 6: Run on device/emulator**

Run: `./gradlew :app:installDebug && adb shell am start -n app.tessera/.MainActivity`
Expected: "Hello, Notes" centred on screen.

- [ ] **Step 7: Commit**

```bash
git add app/src/main
git commit -m "feat(bootstrap): minimal Compose app with Hilt + theme"
```

**End of Chunk 1.** App builds and runs.

---

## Chunk 2: Data layer (Room)

**Outcome:** Room DB with entities + FTS, DAO with reactive Flows, instrumented tests pass.

**Files:**
- Create: `data/local/entity/{NoteEntity,TagEntity,NoteTagCrossRef,NoteFtsEntity}.kt`
- Create: `data/local/dao/{NoteDao,TagDao}.kt`
- Create: `data/local/NotesDatabase.kt`
- Create: `core/di/DatabaseModule.kt`
- Create: `core/util/Clock.kt`
- Test: `app/src/androidTest/java/app/tessera/data/local/NoteDaoTest.kt`
- Test: `app/src/androidTest/java/app/tessera/HiltTestRunner.kt`

### Task 2.1: Entities

- [ ] **Step 1: Create `NoteEntity.kt`**

```kotlin
package app.tessera.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        Index("ownerId", "updatedAt"),
        Index("isPinned", "updatedAt"),
        Index("deletedAt")
    ]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncedAt: Long?,
    val ownerId: String?
)
```

- [ ] **Step 2: Create `TagEntity.kt` + `NoteTagCrossRef.kt`**

```kotlin
// TagEntity.kt
@Entity(tableName = "tags", indices = [Index("name", unique = true)])
data class TagEntity(@PrimaryKey val id: String, val name: String)

// NoteTagCrossRef.kt
@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    indices = [Index("tagId")]
)
data class NoteTagCrossRef(val noteId: String, val tagId: String)
```

- [ ] **Step 3: Create `NoteFtsEntity.kt`**

```kotlin
package app.tessera.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = NoteEntity::class)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(val title: String, val content: String)
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/data/local/entity
git commit -m "feat(data): note + tag + FTS entities"
```

### Task 2.2: DAOs

- [ ] **Step 1: Create `NoteDao.kt`**

```kotlin
package app.tessera.data.local.dao

import androidx.room.*
import app.tessera.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY isPinned DESC, updatedAt DESC")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Query("""
        SELECT notes.* FROM notes
        JOIN notes_fts ON notes.rowid = notes_fts.rowid
        WHERE notes_fts MATCH :query AND notes.deletedAt IS NULL
        ORDER BY notes.isPinned DESC, notes.updatedAt DESC
    """)
    fun search(query: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Query("UPDATE notes SET deletedAt = :ts, updatedAt = :ts, syncedAt = NULL WHERE id = :id")
    suspend fun softDelete(id: String, ts: Long)

    @Query("UPDATE notes SET deletedAt = NULL, updatedAt = :ts, syncedAt = NULL WHERE id = :id")
    suspend fun restore(id: String, ts: Long)

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun purgeOldTombstones(threshold: Long): Int

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT * FROM notes WHERE syncedAt IS NULL")
    suspend fun getPendingSync(): List<NoteEntity>

    @Query("UPDATE notes SET syncedAt = :ts WHERE id = :id")
    suspend fun markSynced(id: String, ts: Long)

    @Query("SELECT DISTINCT ownerId FROM notes WHERE ownerId IS NOT NULL LIMIT 1")
    suspend fun getCurrentOwnerId(): String?

    @Query("UPDATE notes SET ownerId = :uid, syncedAt = NULL WHERE ownerId IS NULL")
    suspend fun adoptLocalNotes(uid: String)
}
```

- [ ] **Step 2: Create `TagDao.kt`**

```kotlin
package app.tessera.data.local.dao

import androidx.room.*
import app.tessera.data.local.entity.NoteTagCrossRef
import app.tessera.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Query("SELECT * FROM tags WHERE name IN (:names)")
    suspend fun findByNames(names: List<String>): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun linkTagsToNote(refs: List<NoteTagCrossRef>)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun clearTagsForNote(noteId: String)

    @Query("SELECT tags.name FROM tags JOIN note_tags ON tags.id = note_tags.tagId WHERE note_tags.noteId = :noteId")
    suspend fun tagsForNote(noteId: String): List<String>

    @Query("SELECT tags.name FROM tags JOIN note_tags ON tags.id = note_tags.tagId WHERE note_tags.noteId = :noteId")
    fun observeTagsForNote(noteId: String): Flow<List<String>>
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/tessera/data/local/dao
git commit -m "feat(data): NoteDao + TagDao"
```

### Task 2.3: Database + DI

- [ ] **Step 1: Create `NotesDatabase.kt`**

```kotlin
package app.tessera.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.dao.TagDao
import app.tessera.data.local.entity.NoteEntity
import app.tessera.data.local.entity.NoteFtsEntity
import app.tessera.data.local.entity.NoteTagCrossRef
import app.tessera.data.local.entity.TagEntity

@Database(
    entities = [
        NoteEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        NoteFtsEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
}
```

Also set schema export dir in `app/build.gradle.kts`:
```kotlin
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
```

- [ ] **Step 2: Create `core/util/Clock.kt`**

```kotlin
package app.tessera.core.util

interface Clock { fun nowMs(): Long }
class SystemClock : Clock { override fun nowMs(): Long = System.currentTimeMillis() }
```

- [ ] **Step 3: Create `core/di/DatabaseModule.kt`**

```kotlin
package app.tessera.core.di

import android.content.Context
import androidx.room.Room
import app.tessera.core.util.Clock
import app.tessera.core.util.SystemClock
import app.tessera.data.local.NotesDatabase
import app.tessera.data.local.dao.NoteDao
import app.tessera.data.local.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): NotesDatabase =
        Room.databaseBuilder(ctx, NotesDatabase::class.java, "notes.db").build()

    @Provides fun provideNoteDao(db: NotesDatabase): NoteDao = db.noteDao()
    @Provides fun provideTagDao(db: NotesDatabase): TagDao = db.tagDao()

    @Provides @Singleton
    fun provideClock(): Clock = SystemClock()
}
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Room generates DAO impls.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/data/local/NotesDatabase.kt \
        app/src/main/java/app/tessera/core/util/Clock.kt \
        app/src/main/java/app/tessera/core/di/DatabaseModule.kt \
        app/build.gradle.kts
git commit -m "feat(data): NotesDatabase + DatabaseModule + Clock"
```

### Task 2.4: Instrumented DAO tests

- [ ] **Step 1: Create custom test runner**

`app/src/androidTest/java/app/tessera/HiltTestRunner.kt`:
```kotlin
package app.tessera

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, ctx: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, ctx)
}
```

Add to `app/build.gradle.kts`:
```kotlin
androidTestImplementation("com.google.dagger:hilt-android-testing:2.52")
kspAndroidTest("com.google.dagger:hilt-android-compiler:2.52")
```

- [ ] **Step 2: Write failing DAO test**

`app/src/androidTest/java/app/tessera/data/local/NoteDaoTest.kt`:
```kotlin
package app.tessera.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.tessera.data.local.entity.NoteEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NoteDaoTest {
    private lateinit var db: NotesDatabase
    private val dao get() = db.noteDao()

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, NotesDatabase::class.java).build()
    }
    @After fun teardown() = db.close()

    private fun note(id: String, title: String = "t", pinned: Boolean = false, ts: Long = 1L) =
        NoteEntity(id, title, "c", pinned, ts, ts, null, null, null)

    @Test fun observeActive_returnsPinnedFirstThenByUpdatedAtDesc() = runTest {
        dao.upsert(note("a", "A", pinned = false, ts = 10))
        dao.upsert(note("b", "B", pinned = true, ts = 5))
        dao.upsert(note("c", "C", pinned = false, ts = 20))

        dao.observeActive().test {
            val list = awaitItem()
            assertEquals(listOf("b", "c", "a"), list.map { it.id })
            cancel()
        }
    }

    @Test fun softDelete_excludesFromObserveActive() = runTest {
        dao.upsert(note("x"))
        dao.softDelete("x", ts = 99)

        dao.observeActive().test {
            assertTrue(awaitItem().isEmpty())
            cancel()
        }
        val raw = dao.getById("x")!!
        assertEquals(99L, raw.deletedAt)
        assertNull(raw.syncedAt)
    }

    @Test fun fts_searchMatchesTitleAndContent() = runTest {
        dao.upsert(note("a").copy(title = "Kotlin tips"))
        dao.upsert(note("b").copy(title = "Other", content = "Includes kotlin examples"))
        dao.upsert(note("c").copy(title = "Unrelated", content = "python"))

        dao.search("kotlin").test {
            val ids = awaitItem().map { it.id }.toSet()
            assertEquals(setOf("a", "b"), ids)
            cancel()
        }
    }

    @Test fun adoptLocalNotes_assignsOwnerIdAndMarksUnsynced() = runTest {
        dao.upsert(note("a").copy(syncedAt = 1L))
        dao.upsert(note("b").copy(ownerId = "other", syncedAt = 1L))

        dao.adoptLocalNotes("uid-1")

        val a = dao.getById("a")!!
        val b = dao.getById("b")!!
        assertEquals("uid-1", a.ownerId)
        assertNull(a.syncedAt)
        assertEquals("other", b.ownerId)
    }

    @Test fun purgeOldTombstones_deletesEntriesOlderThanThreshold() = runTest {
        dao.upsert(note("old").copy(deletedAt = 100))
        dao.upsert(note("recent").copy(deletedAt = 500))
        val purged = dao.purgeOldTombstones(threshold = 200)
        assertEquals(1, purged)
        assertNull(dao.getById("old"))
        assertEquals("recent", dao.getById("recent")?.id)
    }
}
```

- [ ] **Step 3: Run tests, verify they pass**

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: All 5 tests PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest app/build.gradle.kts
git commit -m "test(data): NoteDao instrumented tests"
```

**End of Chunk 2.** Room schema works; CRUD, FTS, soft-delete, adopt-local, and tombstone purge are exercised by tests.

---

## Chunk 3: Domain + Repository (local-only)

**Outcome:** Domain models, repository abstractions, local Room-backed repository implementation, mapper, and core note use-cases all compile and are covered by JVM tests.

**Files:**
- Create: `app/src/main/java/app/tessera/domain/model/{Note,Tag,NoteId,UserId}.kt`
- Create: `app/src/main/java/app/tessera/domain/repository/{NotesRepository,AuthRepository,SyncRepository}.kt`
- Create: `app/src/main/java/app/tessera/domain/usecase/{ObserveNotesUseCase,GetNoteUseCase,CreateNoteUseCase,UpdateNoteUseCase,DeleteNoteUseCase,TogglePinUseCase,SearchNotesUseCase}.kt`
- Create: `app/src/main/java/app/tessera/data/mapper/NoteMapper.kt`
- Create: `app/src/main/java/app/tessera/data/repository/{NotesRepositoryImpl,AuthRepositoryImpl,SyncRepositoryImpl}.kt`
- Create: `app/src/main/java/app/tessera/core/di/RepositoryModule.kt`
- Test: `app/src/test/java/app/tessera/data/mapper/NoteMapperTest.kt`
- Test: `app/src/test/java/app/tessera/data/repository/NotesRepositoryImplTest.kt`
- Test: `app/src/test/java/app/tessera/domain/usecase/NotesUseCasesTest.kt`
- Update: `app/build.gradle.kts` (enable JUnit 5 JVM tests if still missing)

### Task 3.1: Domain models + repository interfaces

- [ ] **Step 1: Create `NoteId.kt` and `UserId.kt`**

Use `@JvmInline value class` wrappers over `String` so IDs stay type-safe in the domain layer.

- [ ] **Step 2: Create `Tag.kt` and `Note.kt`**

Model a local-first note with:
- stable `NoteId`
- `title`, `content`
- `tags: List<Tag>`
- `isPinned`
- `createdAt`, `updatedAt`
- nullable `deletedAt`, `syncedAt`, `ownerId`

Keep the domain shape aligned with the current Room schema so mapping stays mechanical in Chunk 3 and sync can be layered in later chunks.

- [ ] **Step 3: Create repository interfaces**

`NotesRepository` should expose:
- observe active notes
- observe/get note by id
- create note draft
- update note
- delete note
- toggle pin
- search notes

`AuthRepository` should be a stub contract that already exposes `currentUser`, ready for Chunk 7.

`SyncRepository` should be a stub contract that can return a successful no-op sync result for now, ready for Chunk 8.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/domain
git commit -m "feat(domain): add note models and repository contracts"
```

### Task 3.2: Mapper + failing JVM tests

- [ ] **Step 1: Create `NoteMapperTest.kt`**

Write JVM tests first for:
- entity + tag names -> domain note
- domain note -> entity
- round-trip preserves IDs, timestamps, owner, and pin state

Keep tags normalized in assertion order so the mapper contract is explicit before repo logic is added.

- [ ] **Step 2: Create `NoteMapper.kt`**

Implement:
- `NoteEntity` -> `Note`
- `Note` -> `NoteEntity`
- `List<String>` / tag names -> `List<Tag>`

Do not add business logic here beyond straightforward transformation and light tag normalization.

- [ ] **Step 3: Run mapper JVM tests**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.data.mapper.NoteMapperTest"`

Expected: mapper tests PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/data/mapper app/src/test/java/app/tessera/data/mapper
git commit -m "test(data): cover note mapper round-trip"
```

### Task 3.3: NotesRepositoryImpl (local-only)

- [ ] **Step 1: Create `NotesRepositoryImplTest.kt`**

Write JVM tests against fake `NoteDao`, `TagDao`, and fixed `Clock` for:
- create note draft stamps created/updated timestamps and clears `syncedAt`
- update note persists note + tags and bumps `updatedAt`
- blank title + blank content hard-deletes instead of keeping an empty row
- delete note soft-deletes with current timestamp
- toggle pin flips existing note state
- search delegates through mapper and returns tagged notes

- [ ] **Step 2: Create `NotesRepositoryImpl.kt`**

Implementation rules:
- source of truth remains Room
- all writes use `Clock.nowMs()`
- any write that changes note content/pin state sets `syncedAt = null`
- blank notes (`title.isBlank() && content.isBlank()`) are hard-deleted
- tag persistence flow:
  1. upsert note row
  2. ensure missing tags exist
  3. replace note-tag cross refs

- [ ] **Step 3: Add stub repository implementations**

Create:
- `AuthRepositoryImpl.kt` with inert `currentUser`
- `SyncRepositoryImpl.kt` with successful no-op sync

These only need enough behavior to satisfy DI and keep later auth/sync chunks incremental.

- [ ] **Step 4: Run repository JVM tests**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.data.repository.NotesRepositoryImplTest"`

Expected: repository tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tessera/data/repository app/src/test/java/app/tessera/data/repository
git commit -m "feat(data): add local notes repository implementation"
```

### Task 3.4: Use-cases + DI wiring

- [ ] **Step 1: Create `NotesUseCasesTest.kt`**

Use a fake `NotesRepository` and write JVM tests that verify each use-case is a thin wrapper around repository behavior:
- observe notes
- get note
- create note
- update note
- delete note
- toggle pin
- search notes

- [ ] **Step 2: Create note use-case classes**

Each use-case should stay tiny: constructor-injected, one public `operator fun invoke(...)`.

- [ ] **Step 3: Create `RepositoryModule.kt`**

Bind:
- `NotesRepository` -> `NotesRepositoryImpl`
- `AuthRepository` -> `AuthRepositoryImpl`
- `SyncRepository` -> `SyncRepositoryImpl`

- [ ] **Step 4: Enable JUnit 5 if needed**

If JVM tests are not yet configured, update `app/build.gradle.kts` so `testDebugUnitTest` runs on the JUnit Platform.

- [ ] **Step 5: Run full Chunk 3 verification**

Run:
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleDebug`

Expected:
- all Chunk 3 JVM tests PASS
- app still assembles and launches to the existing hello screen

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/app/tessera/core/di app/src/main/java/app/tessera/domain app/src/test/java/app/tessera/domain
git commit -m "feat(domain): wire note use cases and repository bindings"
```

**End of Chunk 3.** The app still builds, while note read/write logic is now expressed through domain contracts and a local repository implementation.

---

## Chunks 4–8: outline (full task breakdown to be expanded after Chunk 3 is reviewed and accepted)

To avoid a 3000-line plan that drifts before being implemented, the remaining chunks are scoped here at task level. Each will be expanded to TDD step granularity (same style as Chunks 1–3) **before its execution begins**, not all upfront — this keeps the plan responsive to surprises discovered during the early chunks.

## Chunk 4: UI core — floating capsules + theme polish

**Outcome:** Reusable glass-style UI primitives compile, render in previews, and are wired into a lightweight runtime showcase so the app visibly moves beyond the hello screen before list/editor work begins.

**Files:**
- Update: `app/src/main/java/app/tessera/core/ui/theme/{Color,Theme}.kt`
- Create: `app/src/main/java/app/tessera/core/ui/components/{FloatingCapsule,GlassFab,MarkdownText}.kt`
- Update: `app/src/main/java/app/tessera/TesseraApp.kt`
- Test: `app/src/test/java/app/tessera/core/ui/components/CapsuleScaleTest.kt`

### Task 4.1: Theme polish for glass surfaces

- [ ] **Step 1: Update `Color.kt` with explicit glass tokens**

Keep the existing warm light / dark neutral base, but add:
- background tints for the showcase surface
- stronger on-surface colors for text legibility
- optional highlight/shadow colors used by glass capsules

Name tokens so later screens can reuse them mechanically instead of hardcoding alpha values.

- [ ] **Step 2: Update `Theme.kt` to use a polished fallback color scheme**

Rules:
- keep Material 3 dynamic color on Android 12+ when enabled
- provide manual light/dark schemes that map to the Tessera palette on older devices
- ensure `surface`, `surfaceVariant`, and `background` are usable for translucent capsules and note cards in future chunks

- [ ] **Step 3: Verify existing app entrypoints still compile against the updated theme**

No behavior change yet beyond visuals; this step exists to catch theme-token drift before UI components are added.

### Task 4.2: `FloatingCapsule` + scroll shrink helper

- [ ] **Step 1: Create `CapsuleScaleTest.kt`**

Write JVM tests first for the pure scale helper behind `Modifier.capsuleScale(...)`:
- progress below `0f` clamps to resting scale
- progress above `1f` clamps to minimum scale
- midpoint progress interpolates between `1f` and the chosen collapsed scale

This keeps at least one deterministic piece of Chunk 4 under automated test even though most of the work is visual.

- [ ] **Step 2: Create `FloatingCapsule.kt`**

Implement:
- rounded capsule container (`RoundedCornerShape(28.dp)` by default)
- white-alpha 1dp border
- translucent tint that adapts to light/dark theme
- soft shadow/elevation
- API 31+ blur-enhanced backing layer, with a simple tinted fallback on lower APIs
- `Modifier.capsuleScale(scrollProgress: Float)` using the tested helper

Also include light and dark `@Preview`s so the component can be inspected in isolation.

- [ ] **Step 3: Run the capsule helper JVM test**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.components.CapsuleScaleTest"`

Expected: tests PASS.

### Task 4.3: `GlassFab` + `MarkdownText`

- [ ] **Step 1: Create `GlassFab.kt`**

Build it on the same visual language as `FloatingCapsule`:
- circular shape
- slot-based content API
- sensible default size for future create-note FAB usage
- light/dark previews

- [ ] **Step 2: Create `MarkdownText.kt`**

Wrap Markwon in `AndroidView` and keep the API intentionally small:
- markdown string input
- modifier passthrough
- TextView-based rendering that respects current Material text color

Add a preview for visual smoke coverage. No dedicated unit test is required here because the behavior is mostly library delegation.

### Task 4.4: Runtime showcase + chunk verification

- [ ] **Step 1: Update `TesseraApp.kt` to render a lightweight UI core showcase**

Replace the plain hello-state with a simple composition that exercises:
- top floating capsule
- markdown body sample
- bottom floating capsule
- glass FAB

Do **not** build the real notes list yet; this showcase is only to validate the reusable primitives in a running app.

- [ ] **Step 2: Run Chunk 4 verification**

Run:
- `./gradlew :app:testDebugUnitTest --tests "app.tessera.core.ui.components.CapsuleScaleTest"`
- `./gradlew :app:assembleDebug`

Expected:
- helper test passes
- app assembles with the new UI core components
- previews are available for manual inspection in Android Studio

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/plans/2026-06-14-android-notes.md \
        app/src/main/java/app/tessera/core/ui/theme \
        app/src/main/java/app/tessera/core/ui/components \
        app/src/main/java/app/tessera/TesseraApp.kt \
        app/src/test/java/app/tessera/core/ui/components
git commit -m "feat(ui): add glass capsule primitives and theme polish"
```

**End of Chunk 4.** The project has a reusable visual foundation for later list and editor screens.

## Chunk 5: Notes list screen

**Outcome:** The app boots into a real notes list route backed by the repository, supports debounced search, visual note cards, swipe actions, and basic navigation into the future editor route.

**Files:**
- Create: `app/src/main/java/app/tessera/ui/notes/{NotesListState,NotesListViewModel,NotesListScreen}.kt`
- Create: `app/src/main/java/app/tessera/ui/notes/components/{NoteCard,SwipeableNoteCard,BottomSearchBar}.kt`
- Create: `app/src/main/java/app/tessera/ui/navigation/NotesNavGraph.kt`
- Create: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt` (placeholder route shell for Chunk 6)
- Update: `app/src/main/java/app/tessera/TesseraApp.kt`
- Update: `app/src/main/java/app/tessera/core/ui/UiTestTags.kt`
- Test: `app/src/test/java/app/tessera/ui/notes/NotesListViewModelTest.kt`
- Test: `app/src/androidTest/java/app/tessera/ui/notes/NotesListScreenTest.kt`

### Task 5.1: Expand app shell from showcase to list navigation

- [ ] **Step 1: Create `NotesNavGraph.kt`**

Start a minimal navigation graph with:
- `notesList` start destination
- `editor/{noteId}` destination (route placeholder for now)

Keep the route naming explicit so Chunk 6 can plug in a real editor without rewriting list navigation tests.

- [ ] **Step 2: Update `TesseraApp.kt` to host the nav graph**

Replace the Chunk 4 showcase root with the list flow:
- keep `TesseraTheme`
- host shared snackbar/navigation state
- route FAB taps from the list into the editor destination

- [ ] **Step 3: Add placeholder `NoteEditorScreen.kt`**

Do not build the editor feature yet. The placeholder only needs to:
- render the note id
- expose a back affordance
- make FAB navigation observable in tests

### Task 5.2: List state + ViewModel

- [ ] **Step 1: Create `NotesListState.kt`**

Define a sealed UI model with:
- `Loading`
- `Empty(query, availableTags, selectedTags)`
- `Content(notes, pinnedCount, query, selection, availableTags, selectedTags)`

Keep the state screen-ready so the composables stay mostly stateless.

- [ ] **Step 2: Create `NotesListViewModelTest.kt`**

Write JVM tests first for:
- initial `Loading -> Content` transition when notes arrive
- `Loading -> Empty` when repository emits no notes
- query updates immediately in UI state but search invocation waits for debounce
- clearing the query swaps back to `ObserveNotesUseCase` without debounce lag

Use a fake `NotesRepository` behind the real use-cases so tests cover the actual flow wiring instead of a hand-waved mock.

- [ ] **Step 3: Create `NotesListViewModel.kt`**

Implementation requirements:
- combine `ObserveNotesUseCase` and `SearchNotesUseCase` through a query `StateFlow`
- blank query uses observe flow immediately
- non-blank query debounces before switching to search flow
- derive available tags from loaded notes
- keep tag selection local to the list feature for now
- expose intent handlers for query change, tag toggle, pin, delete, undo delete, and create note draft

- [ ] **Step 4: Run the ViewModel JVM tests**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.notes.NotesListViewModelTest"`

Expected: debounce and state transition tests PASS.

### Task 5.3: Note card visuals

- [ ] **Step 1: Create `NoteCard.kt`**

Build a reusable staggered card using the Chunk 4 glass primitives:
- title with sensible untitled fallback
- body preview trimmed to a few lines
- pin indicator
- tag chips
- click callback

Include light/dark previews with varied content heights so the staggered layout can be inspected in Studio.

- [ ] **Step 2: Create `BottomSearchBar.kt`**

Wrap the bottom controls in a floating capsule:
- search text field
- optional selected-tag chips
- a horizontally scrollable or wrapping tag filter row

This can stay purely visual/local; server-backed filtering is out of scope until sync lands.

### Task 5.4: Swipe actions

- [ ] **Step 1: Create `SwipeableNoteCard.kt`**

Implement an `anchoredDraggable` card with three conceptual states:
- resting
- actions revealed
- destructive overswipe

Behavior:
- swipe left reveals Pin/Delete actions
- tapping Pin calls the provided callback and snaps closed
- crossing the delete threshold triggers delete automatically
- animation uses spring specs for both reveal and settle

- [ ] **Step 2: Wire snackbar-driven undo**

Use a shared `SnackbarHostState` from the route/screen shell:
- deleting shows snackbar with `Undo`
- undo restores the previously deleted note via the ViewModel

### Task 5.5: Screen composition + motion

- [ ] **Step 1: Create `NotesListScreen.kt`**

Compose the real screen:
- gradient/glow background consistent with Chunk 4
- top floating capsule with title/profile affordance
- `LazyVerticalStaggeredGrid` of note cards
- bottom floating capsule with search/filter controls
- glass FAB

Also wire pull-to-refresh to a no-op callback so the gesture surface is already present before Chunk 8 adds sync.

- [ ] **Step 2: Add list animations**

Include:
- item placement / visibility animation for note cards
- smooth bottom/FAB presence during state changes
- editor navigation fallback transition (`slideInVertically`) even if shared transitions are deferred on older API levels

### Task 5.6: Compose UI verification

- [ ] **Step 1: Create `NotesListScreenTest.kt`**

Cover the highest-risk list interactions:
- FAB tap navigates to the editor route
- swipe-left reveals Pin/Delete actions
- delete shows undo snackbar and undo restores the note

- [ ] **Step 2: Run Chunk 5 verification**

Run:
- `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.notes.NotesListViewModelTest"`
- `./gradlew :app:connectedDebugAndroidTest`
- `./gradlew :app:assembleDebug`

Expected:
- list ViewModel JVM tests PASS
- compose UI tests PASS
- app assembles and launches into the notes list screen

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/plans/2026-06-14-android-notes.md \
        docs/superpowers/PROGRESS.md \
        app/src/main/java/app/tessera/ui \
        app/src/main/java/app/tessera/core/ui/UiTestTags.kt \
        app/src/main/java/app/tessera/TesseraApp.kt \
        app/src/test/java/app/tessera/ui \
        app/src/androidTest/java/app/tessera/ui
git commit -m "feat(ui): add notes list screen flow"
```

**End of Chunk 5.** Tessera now opens into a real list experience with search, swipe actions, and navigation into the editor flow.

### Chunk 6: Note editor screen

Tasks:
1. **`NoteEditorState` + `NoteEditorViewModel`** — loads note by id or creates empty draft; exposes `TextFieldState` (Compose) for title and content; debounce-500ms autosave via `UpdateNoteUseCase`; on exit, if both fields empty, delegates to repo for hard-delete.
2. **Inline checkbox toggle** — parse `[ ]` / `[x]` lines, render as `Row { Checkbox + Text }` with the same spring scale animation on toggle. Toggling rewrites the line in source text.
3. **`MarkdownToolbar`** — buttons that insert markdown tokens at the cursor (`**bold**`, `- `, `- [ ] `).
4. **`TagChips`** — chips + add-tag bottom sheet picker that reads existing tags from DAO.
5. **JVM tests** — `NoteEditorViewModel` autosave debounce; empty-on-exit hard deletes; checkbox toggle correctly rewrites text.
6. **Compose UI tests** — type title → leave screen → reopen, see title; tap checkbox → state flips and persists.

Verification: All tests pass.

### Chunk 7: Firebase Auth + Settings

Tasks:
1. Re-enable `google-services` plugin + firebase/credentials/googleid deps (revert TODO from Chunk 1). User adds real `google-services.json` to `app/` (gitignored). Document this in `README.md`.
2. **`FirebaseModule`** — `@Provides FirebaseAuth`, `@Provides FirebaseFirestore`.
3. **`AuthRepositoryImpl`** — Credential Manager → GoogleIdOption → Firebase Auth `signInWithCredential`. Exposes `currentUser: StateFlow<UserId?>`.
4. **`HandleAccountSwitchUseCase`** — on sign-in, compares `dao.getCurrentOwnerId()` with new uid; emits `AccountSwitchDecision.None / SameAccount / DifferentAccount(existingUid)`.
5. **`SettingsScreen` + `SettingsViewModel`** — sign-in button, account row, theme picker, sync status row (placeholder text until Chunk 8), about row.
6. **Account-switch dialog** — on `DifferentAccount`, show modal with "Move to new account" (updates `ownerId` to new uid + `syncedAt = null`) or "Delete local notes" (hard-deletes those rows).
7. **JVM tests** — `HandleAccountSwitchUseCase` decision tree with fake DAO.
8. **Compose UI tests** — sign-in stub flow (mock `AuthRepository`), account-switch dialog appears and each option performs the right DAO mutation.

Verification: Sign in manually on device with real Google account, see user row in Settings.

### Chunk 8: Firestore sync

Tasks:
1. **`FirestoreNotesClient`** — typed CRUD for `users/{uid}/notes/{noteId}`. Snapshot listener returning Flow.
2. **`NoteMapper` extensions** — Domain ↔ Firestore DTO (tags as `List<String>`).
3. **`SyncRepositoryImpl`**:
   - `initialMerge(uid)` — pulls all docs, applies LWW per-note in a transaction.
   - `pushPending()` — reads `dao.getPendingSync()`, writes to Firestore, marks `syncedAt = Clock.nowMs()`. Empty notes (`title.isBlank() && content.isBlank()`) are skipped.
   - `observeRemote(uid)` — Firestore listener; for each remote change, **compare `remote.updatedAt` to local `updatedAt`** before overwriting Room (race-safe).
4. **`SyncWorker`** (Hilt-injected `CoroutineWorker`) — runs on connectivity + on app start when signed in. Idempotent.
5. **`DeleteTombstonesWorker`** — daily PeriodicWorkRequest. Calls `dao.purgeOldTombstones(now - 30d)` and Firestore counterpart.
6. **Wire-up** — schedule workers in `NotesApplication.onCreate()` when `currentUser != null`. Cancel on sign-out.
7. **JVM tests** — `SyncRepositoryImpl` merge logic with fakes (LWW both directions, tombstone propagation, listener-vs-local-edit race).
8. **Instrumented test** — `DeleteTombstonesWorker` purges at the 30-day boundary using fixed `Clock`.

Verification: Sign in on device A, create note, see it on device B. Edit on B, see update on A. Sign out keeps notes locally. Re-sign-in restores from cloud (LWW correct).

---

## Open work after this plan

Each chunk's TDD-step expansion is created at the start of that chunk (when execution begins), keeping the plan honest about what's actually next. Chunks 1 and 2 are detailed in full above and ready to execute.
