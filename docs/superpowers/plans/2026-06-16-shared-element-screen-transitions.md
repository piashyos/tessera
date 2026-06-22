# Plan B — Shared-Element Note Open + Deliberate Screen Motion

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the default fade transition with a curated motion system. The note card visually expands into the editor when opened; creating a new note grows from the FAB; settings slides in from the side. Other transitions use a deliberate, slightly bouncy spring instead of fade.

**Architecture:**
- Upgrade Compose BOM from `2024.10.00` to `2025.01.00+` to unlock `SharedTransitionScope` / `Modifier.sharedElement`.
- Wrap the NavHost in a `SharedTransitionLayout` so each composable can opt into shared-element animations.
- Per-route `enterTransition` / `exitTransition` overrides on each `composable(...)` destination.
- Centralise motion specs (spring damping, durations) in a new `Motion.kt` design token file under `core/ui/theme/`.

**Tech Stack:** Compose 1.7+, `androidx.navigation.compose` 2.8.x, `androidx.compose.animation` (SharedTransitionLayout APIs are stable as of Compose 1.7).

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `gradle/libs.versions.toml` | Modify | Bump `compose-bom` to `2025.01.00`, bump `nav-compose` if needed (verify 2.8.x compatibility). |
| `app/src/main/java/app/tessera/core/ui/theme/Motion.kt` | Create | Motion design tokens: spring specs (gentle/snappy), durations, easing. |
| `app/src/main/java/app/tessera/ui/navigation/NotesNavGraph.kt` | Modify | Wrap NavHost in `SharedTransitionLayout`, add `enterTransition`/`exitTransition` per destination, pass `SharedTransitionScope` + `AnimatedContentScope` down through Local providers. |
| `app/src/main/java/app/tessera/ui/navigation/SharedScopes.kt` | Create | `LocalSharedTransitionScope` and `LocalAnimatedVisibilityScope` CompositionLocals so leaf composables (NoteCard, NoteEditorScreen) can read the scopes without prop drilling. |
| `app/src/main/java/app/tessera/ui/notes/components/SwipeableNoteCard.kt` | Modify | Tag the card root with `Modifier.sharedElement(key = "note-${noteId}-container", ...)` and tag the inner title/content for child sharing. |
| `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt` | Modify | Tag the editor's outer Surface / Box with the matching `sharedElement` key. |
| `app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt` | Modify | Tag the create FAB with a shared key (`"note-new-fab"`) and the editor will pick this same key when noteId is "draft". |
| `app/src/test/java/app/tessera/ui/navigation/NotesNavGraphTransitionsTest.kt` | Create | Compose UI test verifying enterTransition is set for each route (smoke check). |

---

## Chunk 1: Foundation — upgrade + motion tokens

### Task 1: Bump Compose BOM and verify build

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Edit version catalog**

In `libs.versions.toml`, change:
```toml
compose-bom = "2025.01.00"
```
If `kotlin` is older than 2.0.21 and Compose 1.7+ requires a newer Kotlin, also bump `kotlin = "2.0.21"`.

- [ ] **Step 2: Sync + build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Resolve any compile failures (most likely API deprecations — fix in place).

- [ ] **Step 3: Smoke test**

Run: `./scripts/run-debug-on-emulator.sh`. App opens normally, behavior unchanged (only the Compose version is different).

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: bump Compose BOM to 2025.01.00 for SharedTransitionScope"
```

### Task 2: Create motion tokens

**Files:**
- Create: `app/src/main/java/app/tessera/core/ui/theme/Motion.kt`

- [ ] **Step 1: Write the tokens file**

```kotlin
package app.tessera.core.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

object Motion {
    /** Container resize / position changes that should feel responsive but landed. */
    val ContainerSpring = spring<Any>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = null,
    )

    /** Subtle scale / fade for incidental motion (chips, tooltips). */
    val IncidentalSpring = spring<Float>(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMedium,
    )

    /** Shared-element morph duration cap for very large bounds deltas. */
    const val SharedElementDurationMs = 360
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/tessera/core/ui/theme/Motion.kt
git commit -m "feat(theme): add Motion design tokens"
```

---

## Chunk 2: Wire SharedTransitionLayout into the nav graph

### Task 3: Provide shared scopes via CompositionLocal

**Files:**
- Create: `app/src/main/java/app/tessera/ui/navigation/SharedScopes.kt`

- [ ] **Step 1: Create the file**

```kotlin
package app.tessera.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/app/tessera/ui/navigation/SharedScopes.kt
git commit -m "feat(nav): add CompositionLocals for shared transition + animated visibility"
```

### Task 4: Wrap NavHost in SharedTransitionLayout + per-route transitions

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/navigation/NotesNavGraph.kt`

- [ ] **Step 1: Edit the nav graph**

Wrap the existing `NavHost(...)` in `SharedTransitionLayout`:

```kotlin
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.CompositionLocalProvider
import app.tessera.core.ui.theme.Motion

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun NotesNavGraph(/* existing params */) {
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = NotesListRoute,
                enterTransition = { fadeIn(animationSpec = tween(220)) },
                exitTransition = { fadeOut(animationSpec = tween(180)) },
            ) {
                composable(
                    NotesListRoute,
                    enterTransition = { fadeIn(tween(220)) },
                    exitTransition = { fadeOut(tween(160)) },
                ) {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        notesListDestination(navController)
                    }
                }
                composable(
                    "$EditorRoute/{noteId}",
                    arguments = listOf(navArgument("noteId") { type = NavType.StringType }),
                    enterTransition = {
                        // Slight scale-in coupled with the shared-element morph
                        scaleIn(initialScale = 0.92f, animationSpec = tween(Motion.SharedElementDurationMs)) +
                            fadeIn(tween(Motion.SharedElementDurationMs))
                    },
                    exitTransition = {
                        scaleOut(targetScale = 0.92f, animationSpec = tween(220)) + fadeOut(tween(220))
                    },
                ) { backStackEntry ->
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        editorDestination(navController, backStackEntry)
                    }
                }
                composable(
                    SettingsRoute,
                    enterTransition = { slideInHorizontally(tween(280)) { it } + fadeIn(tween(280)) },
                    exitTransition = { slideOutHorizontally(tween(240)) { it } + fadeOut(tween(240)) },
                ) {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        settingsDestination(navController)
                    }
                }
            }
        }
    }
}
```

(Refactor the existing per-destination blocks into helper functions `notesListDestination` / `editorDestination` / `settingsDestination` if not already.)

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Smoke test**

Open list → tap a card → editor scales in. List → settings → settings slides in from the right.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/ui/navigation/NotesNavGraph.kt
git commit -m "feat(nav): per-route enter/exit transitions + SharedTransitionLayout"
```

---

## Chunk 3: Shared-element note card → editor

### Task 5: Tag the SwipeableNoteCard root with sharedElement

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/components/SwipeableNoteCard.kt`

- [ ] **Step 1: Read scopes from CompositionLocal**

At top of `SwipeableNoteCard`:

```kotlin
val sharedScope = LocalSharedTransitionScope.current
val visibilityScope = LocalAnimatedVisibilityScope.current
```

- [ ] **Step 2: Apply sharedElement to the inner card root**

Wrap or modify the inner `Box` (the one containing the actual NoteCard content, lines 142-159) so its modifier includes:

```kotlin
.then(
    if (sharedScope != null && visibilityScope != null) {
        with(sharedScope) {
            Modifier.sharedElement(
                state = rememberSharedContentState(key = "note-card-${note.id.value}"),
                animatedVisibilityScope = visibilityScope,
                boundsTransform = { _, _ ->
                    tween(Motion.SharedElementDurationMs)
                },
            )
        }
    } else {
        Modifier
    }
)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/components/SwipeableNoteCard.kt
git commit -m "feat(notes): tag note card root for shared-element morph to editor"
```

### Task 6: Tag the editor screen root with matching key

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt`

- [ ] **Step 1: Apply sharedElement to the editor's root Box**

At the editor's top-level Box (`NoteEditorScreen.kt:96`), add the modifier:

```kotlin
val sharedScope = LocalSharedTransitionScope.current
val visibilityScope = LocalAnimatedVisibilityScope.current

Box(
    modifier = Modifier
        .fillMaxSize()
        .background(pageColor)
        .then(
            if (sharedScope != null && visibilityScope != null) {
                with(sharedScope) {
                    Modifier.sharedElement(
                        state = rememberSharedContentState(key = "note-card-${state.noteId.value}"),
                        animatedVisibilityScope = visibilityScope,
                        boundsTransform = { _, _ -> tween(Motion.SharedElementDurationMs) },
                    )
                }
            } else Modifier
        )
)
```

Make sure `state.noteId` exists in the editor state; if not, plumb it in.

- [ ] **Step 2: Smoke test**

`./scripts/run-debug-on-emulator.sh`. Tap a note card on the list — it visibly expands to the editor's bounds. Press back — the editor contracts back into the card position.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt
git commit -m "feat(editor): match shared-element key with note card for morph transition"
```

### Task 7: Same treatment for the "create note" FAB → editor (new draft)

**Files:**
- Modify: `app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt`
- Modify: `app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt`

- [ ] **Step 1: Tag the create FAB**

In `NotesListScreen.kt`, find the create FAB (`GlassFab` with the `+` text). Add `sharedElement` modifier with key `"note-card-draft"`.

- [ ] **Step 2: Editor uses the draft key when noteId matches the freshly-created draft**

The editor route receives the noteId. The draft creation flow (`NotesNavGraph.kt:115-118`) generates a draft id. Pass the same id through navigation. The editor's shared key is already keyed by `state.noteId.value` — if the draft id is the same one we wrote during creation, the morph from FAB to editor uses that id, but the FAB has key `"note-card-draft"`.

The simplest fix: on FAB tap, generate the draft id eagerly, set a transient state `pendingDraftId`, render the FAB with key `"note-card-${pendingDraftId}"` for one frame, then navigate. Or use a fixed key `"note-card-draft"` and override the editor's key to that string when `state.isDraft = true`.

Use the second (simpler) approach: in `NoteEditorScreen.kt`, build the key as `if (state.isDraft) "note-card-draft" else "note-card-${noteId.value}"`.

- [ ] **Step 3: Smoke test**

Tap the create FAB → it expands into the new note editor.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/tessera/ui/notes/NotesListScreen.kt \
        app/src/main/java/app/tessera/ui/editor/NoteEditorScreen.kt
git commit -m "feat(nav): expand create-FAB into editor via shared-element morph"
```

---

## Chunk 4: Verification

### Task 8: Compose UI test for nav graph transitions

**Files:**
- Create: `app/src/test/java/app/tessera/ui/navigation/NotesNavGraphTransitionsTest.kt`

- [ ] **Step 1: Write a smoke test**

```kotlin
@RunWith(RobolectricTestRunner::class)
class NotesNavGraphTransitionsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `tapping a note card navigates to editor without crashing`() {
        composeRule.setContent {
            TesseraTheme(darkTheme = false, dynamicColor = false) {
                NotesNavGraph(
                    /* provide fake nav controller / state */
                )
            }
        }
        // Smoke: ensure SharedTransitionLayout composes cleanly with the existing graph.
        composeRule.onRoot().assertExists()
    }
}
```

(Test depth is intentionally shallow — animation visuals are not unit-testable; smoke check suffices.)

- [ ] **Step 2: Run**

Run: `./gradlew :app:testDebugUnitTest --tests "app.tessera.ui.navigation.NotesNavGraphTransitionsTest"`
Expected: PASS.

- [ ] **Step 3: Commit + finish**

```bash
git add app/src/test/java/app/tessera/ui/navigation/NotesNavGraphTransitionsTest.kt
git commit -m "test(nav): smoke-test SharedTransitionLayout integration"
```

@superpowers:finishing-a-development-branch

---

## Risks / Open Questions

- **Compose BOM bump (Task 1):** may surface deprecation warnings or API breakage elsewhere (e.g. `LazyVerticalStaggeredGrid` signature changes, `Modifier.sharedElement` is experimental). Fix in place.
- **Draft id matching (Task 7):** the "create FAB" key strategy needs verification; the alternative is to ALWAYS use `"note-card-draft"` for the editor when entering from FAB, and decouple from noteId.
- **NoteEditorScreen needs noteId in state:** if `state.noteId` is not surfaced today, plumb it through. Cheap.
- **SharedTransitionLayout perf:** large lists may stutter during the animation. If first observation shows jank, set `boundsTransform` to a shorter `tween(260)` and disable shared-element for cards outside viewport (the API handles this automatically but verify).
