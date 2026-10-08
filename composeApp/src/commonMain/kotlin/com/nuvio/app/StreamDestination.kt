package com.nuvio.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.player.PlayerLaunch
import com.nuvio.app.features.player.PlayerLaunchStore
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.resolveContentLanguage
import com.nuvio.app.features.player.sanitizePlaybackHeaders
import com.nuvio.app.features.player.sanitizePlaybackResponseHeaders
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamLaunchStore
import com.nuvio.app.features.streams.StreamLinkCacheRepository
import com.nuvio.app.features.streams.StreamsRepository
import com.nuvio.app.features.streams.StreamsScreen
import com.nuvio.app.features.streams.shouldShowAutoPlayLoading
import com.nuvio.app.features.streams.shouldUseLandscapeAutoPlayLoading
import com.nuvio.app.features.streams.StreamsUiState
import com.nuvio.app.navigation.*
import kotlinx.coroutines.launch

@Composable
internal fun StreamDestination(
    route: StreamRoute,
    navController: NuvioNavigator,
    openExternalPlayback: suspend (PlayerLaunch) -> Boolean,
    openExternalStreamUrl: (String) -> Boolean,
    onLandscapeLoadingChanged: (Boolean) -> Unit,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    val launch = remember(route.launchId) {
        StreamLaunchStore.get(route.launchId)
    }
    if (launch == null) {
        LaunchedEffect(route.launchId) {
            onBack()
        }
        return
    }
    val pauseDescription = launch.pauseDescription
    val streamRouteScope = rememberCoroutineScope()
    var autoPlayNavigationStarted by remember(route.launchId) { mutableStateOf(false) }
    val shouldResolveEpisodeVideoId =
        launch.parentMetaId != null &&
            launch.seasonNumber != null &&
            launch.episodeNumber != null
    var effectiveVideoId by rememberSaveable(
        launch.videoId,
        launch.parentMetaId,
        launch.seasonNumber,
        launch.episodeNumber,
    ) {
        mutableStateOf(launch.videoId)
    }
    var hasResolvedVideoId by rememberSaveable(
        launch.videoId,
        launch.parentMetaId,
        launch.seasonNumber,
        launch.episodeNumber,
    ) {
        mutableStateOf(!shouldResolveEpisodeVideoId)
    }

    LaunchedEffect(
        launch.videoId,
        launch.parentMetaId,
        launch.parentMetaType,
        launch.type,
        launch.seasonNumber,
        launch.episodeNumber,
    ) {
        effectiveVideoId = launch.videoId
        if (!shouldResolveEpisodeVideoId) {
            hasResolvedVideoId = true
            return@LaunchedEffect
        }

        hasResolvedVideoId = false
        val metaType = launch.parentMetaType ?: launch.type
        val metaId = launch.parentMetaId ?: return@LaunchedEffect
        val resolvedVideoId = runCatching {
            MetaDetailsRepository.fetch(metaType, metaId)
        }.getOrNull()
            ?.videos
            ?.firstOrNull { video ->
                video.season == launch.seasonNumber &&
                    video.episode == launch.episodeNumber
            }
            ?.id
            ?.takeIf { it.isNotBlank() }

        effectiveVideoId = resolvedVideoId ?: launch.videoId
        hasResolvedVideoId = true
    }

    val playerSettings by remember {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.uiState
    }.collectAsStateWithLifecycle()

    fun resolveLaunchContentLanguage(fallbackLanguage: String? = null): String? {
        val meta = MetaDetailsRepository.peek(
            type = launch.parentMetaType ?: launch.type,
            id = launch.parentMetaId ?: effectiveVideoId,
        )
        return resolveContentLanguage(
            language = meta?.language?.takeIf { it.isNotBlank() } ?: fallbackLanguage,
            country = meta?.country,
        )
    }

    var reuseHandled by rememberSaveable(launch.videoId, effectiveVideoId) { mutableStateOf(false) }
    var reuseNavigated by remember { mutableStateOf(false) }
    LaunchedEffect(effectiveVideoId, hasResolvedVideoId, playerSettings.streamReuseLastLinkEnabled, launch.manualSelection) {
        if (!hasResolvedVideoId) return@LaunchedEffect
        if (reuseHandled) return@LaunchedEffect
        reuseHandled = true
        if (launch.manualSelection) return@LaunchedEffect
        if (!playerSettings.streamReuseLastLinkEnabled) return@LaunchedEffect
        val cacheKey = StreamLinkCacheRepository.contentKey(
            type = launch.type,
            videoId = effectiveVideoId,
            parentMetaId = launch.parentMetaId,
            season = launch.seasonNumber,
            episode = launch.episodeNumber,
        )
        val maxAgeMs = playerSettings.streamReuseLastLinkCacheHours * 60L * 60L * 1000L
        val cached = StreamLinkCacheRepository.getValid(cacheKey, maxAgeMs)
        if (cached != null && cached.url.isNotBlank()) {
            val playerLaunch = PlayerLaunch(
                profileId = launch.profileId,
                title = launch.title,
                sourceUrl = cached.url,
                sourceHeaders = sanitizePlaybackHeaders(cached.requestHeaders),
                sourceResponseHeaders = sanitizePlaybackResponseHeaders(cached.responseHeaders),
                externalSubtitles = emptyList(),
                streamType = cached.streamType,
                logo = launch.logo,
                poster = launch.poster,
                background = launch.background,
                seasonNumber = launch.seasonNumber,
                episodeNumber = launch.episodeNumber,
                episodeTitle = launch.episodeTitle,
                episodeThumbnail = launch.episodeThumbnail,
                streamTitle = cached.streamName,
                streamSubtitle = null,
                bingeGroup = cached.bingeGroup,
                pauseDescription = pauseDescription,
                providerName = cached.addonName,
                providerAddonId = cached.addonId,
                contentType = launch.type,
                videoId = effectiveVideoId,
                parentMetaId = launch.parentMetaId ?: effectiveVideoId,
                parentMetaType = launch.parentMetaType ?: launch.type,
                initialPositionMs = launch.resumePositionMs ?: 0L,
                initialProgressFraction = launch.resumeProgressFraction,
                contentLanguage = resolveLaunchContentLanguage(cached.contentLanguage),
            )
            if (playerSettings.externalPlayerEnabled) {
                openExternalPlayback(playerLaunch)
                StreamsRepository.cancelLoading()
                StreamsRepository.setOverlayVisible(false)
                reuseNavigated = true
                return@LaunchedEffect
            }
            StreamsRepository.clear()
            reuseNavigated = true
            autoPlayNavigationStarted = true
            val launchId = PlayerLaunchStore.put(playerLaunch)
            navController.navigate(PlayerRoute(launchId = launchId, title = playerLaunch.title)) {
                popUpTo<StreamRoute> { inclusive = true }
            }
        }
    }

    val streamsUiState by StreamsRepository.uiState.collectAsStateWithLifecycle()
    val expectedStreamsRequestToken = StreamsRepository.requestToken(
        type = launch.type,
        videoId = effectiveVideoId,
        season = launch.seasonNumber,
        episode = launch.episodeNumber,
        manualSelection = launch.manualSelection,
    )
    val showLoadingScreen = autoPlayNavigationStarted || streamsUiState.shouldShowAutoPlayLoading(
        expectedRequestToken = expectedStreamsRequestToken,
        settings = playerSettings,
        manualSelection = launch.manualSelection,
    )
    val useLandscapeLoading = autoPlayNavigationStarted || streamsUiState.shouldUseLandscapeAutoPlayLoading(
        expectedRequestToken = expectedStreamsRequestToken,
        settings = playerSettings,
        manualSelection = launch.manualSelection,
    )
    SideEffect { onLandscapeLoadingChanged(useLandscapeLoading) }
    var autoPlayHandled by rememberSaveable(launch.videoId, effectiveVideoId) { mutableStateOf(false) }
    LaunchedEffect(
        streamsUiState.autoPlayStream,
        streamsUiState.requestToken,
        expectedStreamsRequestToken,
        reuseHandled,
        launch.manualSelection,
    ) {
        if (!reuseHandled) return@LaunchedEffect
        if (launch.manualSelection) return@LaunchedEffect
        if (reuseNavigated) return@LaunchedEffect
        if (autoPlayHandled) return@LaunchedEffect
        if (streamsUiState.requestToken != expectedStreamsRequestToken) return@LaunchedEffect
        val selectedStream = streamsUiState.autoPlayStream ?: return@LaunchedEffect
        val stream = selectedStream
        val sourceUrl = stream.playableDirectUrl
        if (sourceUrl == null) {
            StreamsRepository.skipAutoPlayStream(selectedStream)
            return@LaunchedEffect
        }
        autoPlayHandled = true
        if (playerSettings.streamReuseLastLinkEnabled) {
            val cacheKey = StreamLinkCacheRepository.contentKey(
                type = launch.type,
                videoId = effectiveVideoId,
                parentMetaId = launch.parentMetaId,
                season = launch.seasonNumber,
                episode = launch.episodeNumber,
            )
            StreamLinkCacheRepository.save(
                contentKey = cacheKey,
                url = sourceUrl,
                streamName = stream.streamLabel,
                addonName = stream.addonName,
                addonId = stream.addonId,
                requestHeaders = sanitizePlaybackHeaders(stream.behaviorHints.proxyHeaders?.request),
                responseHeaders = sanitizePlaybackResponseHeaders(stream.behaviorHints.proxyHeaders?.response),
                filename = stream.behaviorHints.filename,
                videoSize = stream.behaviorHints.videoSize,
                bingeGroup = stream.behaviorHints.bingeGroup,
                streamType = stream.streamType,
                contentLanguage = resolveLaunchContentLanguage(),
            )
        }
        val playerLaunch = PlayerLaunch(
            profileId = launch.profileId,
            title = launch.title,
            sourceUrl = sourceUrl,
            sourceHeaders = sanitizePlaybackHeaders(stream.behaviorHints.proxyHeaders?.request),
            sourceResponseHeaders = sanitizePlaybackResponseHeaders(stream.behaviorHints.proxyHeaders?.response),
            externalSubtitles = stream.externalSubtitles,
            streamType = stream.streamType,
            logo = launch.logo,
            poster = launch.poster,
            background = launch.background,
            seasonNumber = launch.seasonNumber,
            episodeNumber = launch.episodeNumber,
            episodeTitle = launch.episodeTitle,
            episodeThumbnail = launch.episodeThumbnail,
            streamTitle = stream.streamLabel,
            streamSubtitle = stream.streamSubtitle,
            bingeGroup = stream.behaviorHints.bingeGroup,
            pauseDescription = pauseDescription,
            providerName = stream.addonName,
            providerAddonId = stream.addonId,
            contentType = launch.type,
            videoId = effectiveVideoId,
            parentMetaId = launch.parentMetaId ?: effectiveVideoId,
            parentMetaType = launch.parentMetaType ?: launch.type,
            initialPositionMs = launch.resumePositionMs ?: 0L,
            initialProgressFraction = launch.resumeProgressFraction,
            contentLanguage = resolveLaunchContentLanguage(),
        )
        if (playerSettings.externalPlayerEnabled) {
            openExternalPlayback(playerLaunch)
            StreamsRepository.consumeAutoPlay()
            StreamsRepository.cancelLoading()
            return@LaunchedEffect
        }
        StreamsRepository.consumeAutoPlay()
        StreamsRepository.cancelLoading()
        autoPlayNavigationStarted = true
        val launchId = PlayerLaunchStore.put(playerLaunch)
        navController.navigate(PlayerRoute(launchId = launchId, title = playerLaunch.title)) {
            popUpTo<StreamRoute> { inclusive = true }
        }
    }

    if (!hasResolvedVideoId) {
        if (showLoadingScreen) {
            StreamLoadingScreen(
                launch = launch,
                state = StreamsUiState(),
                showStatus = playerSettings.showPlayerLoadingStatus,
                resolvingDebridStream = false,
                onBack = onBack,
            )
            return
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            NuvioLoadingIndicator()
        }
        return
    }

    fun openSelectedStream(
        stream: StreamItem,
        resolvedResumePositionMs: Long?,
        resolvedResumeProgressFraction: Float?,
        forceExternal: Boolean,
        forceInternal: Boolean,
    ) {
        if (stream.shouldOpenExternally) {
            val opened = stream.externalOpenUrl?.let { url -> openExternalStreamUrl(url) } == true
            if (opened) {
                StreamsRepository.cancelLoading()
            }
            return
        }
        val sourceUrl = stream.playableDirectUrl ?: return
        if (playerSettings.streamReuseLastLinkEnabled) {
            val cacheKey = StreamLinkCacheRepository.contentKey(
                type = launch.type,
                videoId = effectiveVideoId,
                parentMetaId = launch.parentMetaId,
                season = launch.seasonNumber,
                episode = launch.episodeNumber,
            )
            StreamLinkCacheRepository.save(
                contentKey = cacheKey,
                url = sourceUrl,
                streamName = stream.streamLabel,
                addonName = stream.addonName,
                addonId = stream.addonId,
                requestHeaders = sanitizePlaybackHeaders(stream.behaviorHints.proxyHeaders?.request),
                responseHeaders = sanitizePlaybackResponseHeaders(stream.behaviorHints.proxyHeaders?.response),
                filename = stream.behaviorHints.filename,
                videoSize = stream.behaviorHints.videoSize,
                bingeGroup = stream.behaviorHints.bingeGroup,
                streamType = stream.streamType,
                contentLanguage = resolveLaunchContentLanguage(),
            )
        }
        val playerLaunch = PlayerLaunch(
            profileId = launch.profileId,
            title = launch.title,
            sourceUrl = sourceUrl,
            sourceHeaders = sanitizePlaybackHeaders(stream.behaviorHints.proxyHeaders?.request),
            sourceResponseHeaders = sanitizePlaybackResponseHeaders(stream.behaviorHints.proxyHeaders?.response),
            externalSubtitles = stream.externalSubtitles,
            streamType = stream.streamType,
            logo = launch.logo,
            poster = launch.poster,
            background = launch.background,
            seasonNumber = launch.seasonNumber,
            episodeNumber = launch.episodeNumber,
            episodeTitle = launch.episodeTitle,
            episodeThumbnail = launch.episodeThumbnail,
            streamTitle = stream.streamLabel,
            streamSubtitle = stream.streamSubtitle,
            bingeGroup = stream.behaviorHints.bingeGroup,
            pauseDescription = pauseDescription,
            providerName = stream.addonName,
            providerAddonId = stream.addonId,
            contentType = launch.type,
            videoId = effectiveVideoId,
            parentMetaId = launch.parentMetaId ?: effectiveVideoId,
            parentMetaType = launch.parentMetaType ?: launch.type,
            initialPositionMs = resolvedResumePositionMs ?: 0L,
            initialProgressFraction = resolvedResumeProgressFraction,
            contentLanguage = resolveLaunchContentLanguage(),
        )

        if (!forceInternal && (forceExternal || playerSettings.externalPlayerEnabled)) {
            streamRouteScope.launch {
                openExternalPlayback(playerLaunch)
                StreamsRepository.cancelLoading()
            }
            return
        }

        val launchId = PlayerLaunchStore.put(playerLaunch)
        StreamsRepository.cancelLoading()
        navController.navigate(
            PlayerRoute(launchId = launchId, title = playerLaunch.title)
        )
    }

    LaunchedEffect(reuseNavigated) {
        if (reuseNavigated) {
            StreamsRepository.setOverlayVisible(false)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        StreamsScreen(
            showLoadingScreen = showLoadingScreen,
            type = launch.type,
            videoId = effectiveVideoId,
            parentMetaId = launch.parentMetaId ?: effectiveVideoId,
            parentMetaType = launch.parentMetaType ?: launch.type,
            title = launch.title,
            logo = launch.logo,
            poster = launch.poster,
            background = launch.background,
            seasonNumber = launch.seasonNumber,
            episodeNumber = launch.episodeNumber,
            episodeTitle = launch.episodeTitle,
            episodeThumbnail = launch.episodeThumbnail,
            resumePositionMs = launch.resumePositionMs,
            resumeProgressFraction = launch.resumeProgressFraction,
            manualSelection = launch.manualSelection,
            startFromBeginning = launch.startFromBeginning,
            onStreamSelected = { stream, resolvedResumePositionMs, resolvedResumeProgressFraction ->
                openSelectedStream(
                    stream = stream,
                    resolvedResumePositionMs = resolvedResumePositionMs,
                    resolvedResumeProgressFraction = resolvedResumeProgressFraction,
                    forceExternal = false,
                    forceInternal = false,
                )
            },
            onStreamActionOpen = { stream, openExternally, resolvedResumePositionMs, resolvedResumeProgressFraction ->
                openSelectedStream(
                    stream = stream,
                    resolvedResumePositionMs = resolvedResumePositionMs,
                    resolvedResumeProgressFraction = resolvedResumeProgressFraction,
                    forceExternal = openExternally,
                    forceInternal = !openExternally,
                )
            },
            onBack = onBack,
            modifier = Modifier.fillMaxSize(),
        )
        if (showLoadingScreen) {
            StreamLoadingScreen(
                launch = launch,
                state = streamsUiState.takeIf { it.requestToken == expectedStreamsRequestToken } ?: StreamsUiState(),
                showStatus = playerSettings.showPlayerLoadingStatus,
                resolvingDebridStream = false,
                onBack = onBack,
            )
        }
    }
}
