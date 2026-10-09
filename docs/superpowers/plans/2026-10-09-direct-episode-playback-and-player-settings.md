# Direct Episode Playback & In-Player Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to directly play episodes by bypassing the intermediate stream loading screen, resolving streams inside the player with native loading indicator, and provide in-player Dub/Sub and Quality/Server switching.

**Architecture:** Route episode playback directly to `PlayerRoute` via `PlayerLaunchStore`. Handle initial empty `sourceUrl` in `PlayerScreen` by rendering native player loading state while `PlayerStreamsRepository.loadSources` scrapes links in the background. Fix `matchesFormat` in `PlayerScreenRuntimeTrackActions` so Dub and Sub streams are accurately matched and switched. Enhance `VideoQualityModal` to display both ExoPlayer resolution tracks and scraper server streams.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose Multiplatform, ExoPlayer / MPV.

## Global Constraints
- Never run `./gradlew` locally in Termux. Build via GitHub Actions.
- Minimal comments. Only comment non-obvious logic.
- Short, simple commit messages. No bullet lists in commit body.

---

### Task 1: Fix Dub/Sub Format Matching in Player Runtime

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeTrackActions.kt:370-435`

- [ ] **Step 1: Fix format matching in `StreamItem.matchesFormat`**

In `StreamItem.matchesFormat(targetFormat: DubSubFormat)`:
Ensure streams with `format = "sub"`, `format = "softsub"`, `format = "hardsub"`, or text containing `[SUB]` or `sub` correctly map to Sub formats (`SOFTSUB` and `HARDSUB`). Streams with `format = "dub"` or text containing `[DUB]` or `dub` map to `DUB`.

- [ ] **Step 2: Commit changes**

```bash
git add composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeTrackActions.kt
git commit -m "Fix Dub/Sub stream matching logic"
```

---

### Task 2: Enhance Video Quality Modal with Quality Tracks & Server Streams

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/VideoQualityModal.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenModalHosts.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt`

- [ ] **Step 1: Update `VideoQualityModal` parameters and UI**

Add support for listing available scraper server streams alongside video resolution tracks:
- Displays `Video Tracks` (Auto, 1080p, 720p, 480p) when ExoPlayer exposes video tracks.
- Displays `Servers / Streams` (e.g., HD-1, HD-2, HD-3) from `sourceStreamsState.allStreams`.
- Allows switching active stream when a server is tapped.

- [ ] **Step 2: Wire `VideoQualityModal` in `PlayerScreenModalHosts.kt` and `PlayerScreenRuntimeUi.kt`**

Pass `sourceStreams = sourceStreamsState.allStreams`, `activeStreamUrl = activeSourceUrl`, and `onStreamSelected = { switchToSource(it) }`.

- [ ] **Step 3: Commit changes**

```bash
git add composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/VideoQualityModal.kt composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenModalHosts.kt composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt
git commit -m "Add server stream switching to VideoQualityModal"
```

---

### Task 3: In-Player Stream Resolution & Loading Lifecycle

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt:65-75`
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt:70-95`

- [ ] **Step 1: Guard `PlatformPlayerSurface` when `activeSourceUrl.isBlank()`**

In `PlayerScreenRuntimeUi.kt`:
Only render `PlatformPlayerSurface` if `playerSurfaceSourceUrl.isNotBlank()`.
When blank, display a centered `NuvioLoadingIndicator()`.

- [ ] **Step 2: Auto-select initial stream in `PlayerScreenRuntimeEffects.kt`**

Add an effect in `PlayerScreenRuntimeEffects.kt`:
When `activeSourceUrl.isBlank()` and `sourceStreamsState.allStreams` emits playable streams:
- Find preferred stream matching `currentDubSubFormat ?: DubSubFormat.SOFTSUB` or take the first playable stream.
- Call `switchToSource(stream)`.
- If loading finishes with no streams, set `errorMessage = "No playable stream found"`.

- [ ] **Step 3: Commit changes**

```bash
git add composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeUi.kt composeApp/src/commonMain/kotlin/com/nuvio/app/features/player/PlayerScreenRuntimeEffects.kt
git commit -m "Support in-player stream resolution and loading state"
```

---

### Task 4: Bypass Intermediate Stream Screen for Episode Playback

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/nuvio/app/MainAppContent.kt:945-985`

- [ ] **Step 1: Route episode playback directly to `PlayerRoute`**

In `launchPlaybackWithDownloadPreference` in `MainAppContent.kt`:
When `seasonNumber != null && episodeNumber != null`:
- Query `StreamLinkCacheRepository` for a valid cached stream link.
- Construct `PlayerLaunch` with `sourceUrl = cached?.url.orEmpty()`.
- Store `playerLaunch` in `PlayerLaunchStore`.
- Navigate directly to `PlayerRoute(launchId = launchId, title = playerLaunch.title)`.
- Skip navigating to `StreamRoute`.

- [ ] **Step 2: Commit changes**

```bash
git add composeApp/src/commonMain/kotlin/com/nuvio/app/MainAppContent.kt
git commit -m "Bypass StreamRoute and directly launch PlayerRoute for episodes"
```

---

### Task 5: Build, Push & Verify via ADB

- [ ] **Step 1: Push commits to `cmp`**
- [ ] **Step 2: Monitor GitHub Actions run**
- [ ] **Step 3: Verify on device via ADB**
