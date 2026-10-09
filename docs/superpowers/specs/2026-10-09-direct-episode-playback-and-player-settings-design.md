# Direct Episode Playback & In-Player Dub/Sub & Quality Settings Design

## Summary
Bypass the intermediate `StreamsScreen` / `StreamLoadingScreen` when clicking an episode, directly opening `PlayerScreen` and resolving streams inside the player lifecycle. In addition, enhance player controls with fixed Dub/Sub stream matching and unified Quality & Server selection.

## Requirements & Scope
1. **Direct Episode Playback**:
   - Tapping an episode in details or continuing watching immediately navigates to `PlayerRoute`.
   - The intermediate `StreamRoute` / `StreamLoadingScreen` is bypassed.
   - If a stream URL is in `StreamLinkCacheRepository`, it starts playing immediately with no wait.
   - If not cached, the player displays its native loading state while `PlayerStreamsRepository.loadSources` executes. Once streams arrive, it auto-selects the stream and begins playback.
   - If no playable streams are found, an error modal is shown.

2. **In-Player Dub / Sub Selector**:
   - Fix format matching in `StreamItem.matchesFormat()`: streams with `format = "sub"` or title/name containing `[SUB]` / `sub` match Sub formats.
   - In `DubSubModal`, present Dub and Sub options with live counts of matching streams.
   - Selecting a format auto-switches to the matching stream and adjusts audio/subtitle tracks accordingly.

3. **In-Player Quality & Server Selector**:
   - In `VideoQualityModal`, display:
     - Internal resolution tracks (e.g. `1080p`, `720p`, `480p`, `Auto`).
     - Server / source mirrors from scraped episode streams (e.g. `HD-1`, `HD-2`, `HD-3`, `HD-4`).
   - Tapping a server switches the active stream without leaving the player.

## Architecture & Data Flow

### 1. Navigation Flow
- In `MainAppContent.kt` (`launchPlaybackWithDownloadPreference`):
  - Check if `seasonNumber != null && episodeNumber != null`.
  - Check `StreamLinkCacheRepository.getValid(cacheKey, maxAgeMs)`.
  - Create `PlayerLaunch` with `sourceUrl = cached?.url ?: ""`.
  - Store in `PlayerLaunchStore` and navigate to `PlayerRoute(launchId)`.

### 2. Player Lifecycle
- In `PlayerScreenRuntimeUi.kt`:
  - When `activeSourceUrl.isBlank()`, bypass `PlatformPlayerSurface` and show `NuvioLoadingIndicator()`.
- In `PlayerScreenRuntimeEffects.kt`:
  - When `activeSourceUrl.isBlank()`, monitor `sourceStreamsState.allStreams`.
  - As soon as playable streams appear:
    - Select stream matching preferred format (`currentDubSubFormat ?: DubSubFormat.SOFTSUB`) or first stream.
    - Call `switchToSource(stream)`.
  - If loading completes with no streams found, display error.

### 3. Track & Server Switching
- In `PlayerScreenRuntimeTrackActions.kt`:
  - Fix `StreamItem.matchesFormat()` to correctly handle `format = "sub"`, `"dub"`, and text-based tokens.
- In `VideoQualityModal.kt`:
  - Display available video resolution tracks and server streams for one-tap switching.

## Verification
- Test by tapping an episode in anime details: verify instant navigation into `PlayerScreen` with native loader.
- Verify playback begins automatically as soon as Reanime scraper streams resolve.
- Verify Dub/Sub modal shows correct stream counts and switches stream on tap.
- Verify Quality modal shows video tracks and server mirrors and allows switching.
