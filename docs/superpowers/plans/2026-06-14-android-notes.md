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
      java/com/notes/
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
      java/com/notes/
        domain/usecase/...
        data/repository/SyncRepositoryImplTest.kt
        data/mapper/NoteMapperTest.kt

    androidTest/                                 # instrumented (Room, UI)
      java/com/notes/
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
- Create: `app/src/main/java/com/notes/NotesApplication.kt`
- Create: `app/src/main/java/com/notes/MainActivity.kt`
- Create: `app/src/main/java/com/notes/NotesApp.kt`
- Create: `app/src/main/java/com/notes/core/ui/theme/{Color,Theme,Type}.kt`
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
    namespace = "com.notes"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.notes"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.notes.HiltTestRunner"
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
package com.notes

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NotesApplication : Application()
```

- [ ] **Step 4: Create theme files**

`core/ui/theme/Color.kt`:
```kotlin
package com.notes.core.ui.theme

import androidx.compose.ui.graphics.Color

val SurfaceLight = Color(0xFFF7F4EF)
val SurfaceDark = Color(0xFF1A1A1A)
val CapsuleBorder = Color.White.copy(alpha = 0.20f)
val CapsuleTintLight = Color.White.copy(alpha = 0.65f)
val CapsuleTintDark = Color.Black.copy(alpha = 0.55f)
```

`core/ui/theme/Type.kt`:
```kotlin
package com.notes.core.ui.theme

import androidx.compose.material3.Typography
val NotesTypography = Typography()
```

`core/ui/theme/Theme.kt`:
```kotlin
package com.notes.core.ui.theme

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
package com.notes

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
package com.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.notes.core.ui.theme.NotesTheme

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

Run: `./gradlew :app:installDebug && adb shell am start -n com.notes/.MainActivity`
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
- Test: `app/src/androidTest/java/com/notes/data/local/NoteDaoTest.kt`
- Test: `app/src/androidTest/java/com/notes/HiltTestRunner.kt`

### Task 2.1: Entities

- [ ] **Step 1: Create `NoteEntity.kt`**

```kotlin
package com.notes.data.local.entity

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
package com.notes.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = NoteEntity::class)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(val title: String, val content: String)
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/notes/data/local/entity
git commit -m "feat(data): note + tag + FTS entities"
```

### Task 2.2: DAOs

- [ ] **Step 1: Create `NoteDao.kt`**

```kotlin
package com.notes.data.local.dao

import androidx.room.*
import com.notes.data.local.entity.NoteEntity
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
package com.notes.data.local.dao

import androidx.room.*
import com.notes.data.local.entity.NoteTagCrossRef
import com.notes.data.local.entity.TagEntity
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
git add app/src/main/java/com/notes/data/local/dao
git commit -m "feat(data): NoteDao + TagDao"
```

### Task 2.3: Database + DI

- [ ] **Step 1: Create `NotesDatabase.kt`**

```kotlin
package com.notes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.notes.data.local.dao.NoteDao
import com.notes.data.local.dao.TagDao
import com.notes.data.local.entity.NoteEntity
import com.notes.data.local.entity.NoteFtsEntity
import com.notes.data.local.entity.NoteTagCrossRef
import com.notes.data.local.entity.TagEntity

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
package com.notes.core.util

interface Clock { fun nowMs(): Long }
class SystemClock : Clock { override fun nowMs(): Long = System.currentTimeMillis() }
```

- [ ] **Step 3: Create `core/di/DatabaseModule.kt`**

```kotlin
package com.notes.core.di

import android.content.Context
import androidx.room.Room
import com.notes.core.util.Clock
import com.notes.core.util.SystemClock
import com.notes.data.local.NotesDatabase
import com.notes.data.local.dao.NoteDao
import com.notes.data.local.dao.TagDao
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
git add app/src/main/java/com/notes/data/local/NotesDatabase.kt \
        app/src/main/java/com/notes/core/util/Clock.kt \
        app/src/main/java/com/notes/core/di/DatabaseModule.kt \
        app/build.gradle.kts
git commit -m "feat(data): NotesDatabase + DatabaseModule + Clock"
```

### Task 2.4: Instrumented DAO tests

- [ ] **Step 1: Create custom test runner**

`app/src/androidTest/java/com/notes/HiltTestRunner.kt`:
```kotlin
package com.notes

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

`app/src/androidTest/java/com/notes/data/local/NoteDaoTest.kt`:
```kotlin
package com.notes.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.notes.data.local.entity.NoteEntity
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

## Chunks 3–8: outline (full task breakdown to be expanded after Chunks 1–2 are reviewed and accepted)

To avoid a 3000-line plan that drifts before being implemented, the remaining chunks are scoped here at task level. Each will be expanded to TDD step granularity (same style as Chunks 1–2) **before its execution begins**, not all upfront — this keeps the plan responsive to surprises discovered during the early chunks.

### Chunk 3: Domain + Repository (local-only)

Tasks:
1. **Domain models** — `Note`, `Tag`, value classes `NoteId`, `UserId`.
2. **Repository interfaces** — `NotesRepository`, `AuthRepository` (stub), `SyncRepository` (stub returning success).
3. **NoteMapper** — `NoteEntity` ↔ `Note` (+ tags). Unit-test the round-trip (JVM).
4. **NotesRepositoryImpl** — wraps `NoteDao` + `TagDao`. Inserts/updates set `updatedAt = Clock.nowMs()` and `syncedAt = null`. Empty-note hard-delete logic lives here.
5. **Use-cases** — thin wrappers over repo (Observe, Get, Create, Update, Delete, TogglePin, Search). JVM unit tests with fake `NotesRepository`.
6. **RepositoryModule** with `@Binds`.

Verification: All use-case JVM tests pass. App still launches (still "Hello, Notes").
Commit cadence: one per task.

### Chunk 4: UI core — floating capsules + theme polish

Tasks:
1. **`FloatingCapsule` composable** — rounded surface, white-alpha border, alpha tint, optional blur of background via `Modifier.blur` on a captured background snapshot (API 31+, fallback simple tint). Compose preview in light/dark.
2. **`GlassFab`** — same look, circular, with icon slot.
3. **`MarkdownText`** composable wrapping Markwon via `AndroidView`. Unit-rendered via screenshot preview only (no logic to test).
4. **Capsule scroll-shrink modifier** — `Modifier.capsuleScale(scrollProgress: Float)`.

Verification: Compose previews render in Android Studio. Manual smoke run.

### Chunk 5: Notes list screen

Tasks:
1. **`NotesListState`** — sealed UI state (Loading / Empty / Content(notes, pinned, query, selection)).
2. **`NotesListViewModel`** — combines `ObserveNotesUseCase` and `SearchNotesUseCase` via query StateFlow. JVM unit test for query debounce + state transitions.
3. **`NoteCard`** — staggered card with title + preview + tag chips. Preview composable.
4. **`SwipeableNoteCard`** — anchored draggable (Compose `anchoredDraggable`) with two action slots (Pin, Delete) revealed by left swipe; long swipe past threshold triggers delete with undo via shared snackbar host. Animation: spring-based reveal.
5. **`NotesListScreen`** — `LazyVerticalStaggeredGrid` + top capsule (profile icon + title) + bottom capsule (search field + tag filter) + FAB. Pull-to-refresh wired to a no-op for now (chunk 8 plugs sync).
6. **Animations** — `animateItemPlacement`, FAB → editor circular morph via shared element (`SharedTransitionLayout`, API 34+; fallback `slideInVertically`).
7. **Compose UI tests** — create note (FAB tap → editor route), swipe-left reveals actions, delete shows undo snackbar that restores the note.

Verification: All UI tests pass. Manual: create / pin / delete / undo on device.

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
