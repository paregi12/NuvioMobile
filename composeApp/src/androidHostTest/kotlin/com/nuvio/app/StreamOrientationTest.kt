package com.nuvio.app

import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
import androidx.compose.ui.test.junit4.createComposeRule
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.PlayerSettingsStorage
import com.nuvio.app.features.streams.BingeGroupCacheRepository
import com.nuvio.app.features.streams.BingeGroupCacheStorage
import com.nuvio.app.features.streams.StreamLaunch
import com.nuvio.app.features.streams.StreamLaunchStore
import com.nuvio.app.features.streams.StreamsRepository
import com.nuvio.app.features.updater.AndroidAppUpdaterPlatform
import com.nuvio.app.navigation.StreamRoute
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowActivity
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    application = Application::class,
    qualifiers = "w360dp-h640dp-port",
    shadows = [OrientationTrackingActivity::class],
)
class StreamOrientationTest {
    @get:Rule
    val compose = createComposeRule()

    @BeforeTest
    fun initialize() {
        val context = RuntimeEnvironment.getApplication()
        PlayerSettingsStorage.initialize(context)
        BingeGroupCacheStorage.initialize(context)
        AndroidAppUpdaterPlatform.initialize(context)
        PlayerSettingsRepository.clearLocalState()
        MetaDetailsRepository.clear()
        StreamsRepository.clear()
        StreamLaunchStore.clear()
        OrientationTrackingActivity.requests.clear()
    }

    @AfterTest
    fun clearState() {
        PlayerSettingsRepository.clearLocalState()
        StreamsRepository.clear()
        StreamLaunchStore.clear()
        MetaDetailsRepository.clear()
        BingeGroupCacheRepository.remove("orientation-series")
    }

    @Test
    fun openingManualStreamListWithNoCachedLinkDoesNotRequestLandscape() {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.setStreamReuseLastLinkEnabled(true)
        openStreamList(manualSelection = false)
        assertNoLandscapeRequest()
    }

    @Test
    fun openingManualStreamListWithNoSavedBingeGroupDoesNotRequestLandscape() {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.setStreamAutoPlayReuseBingeGroup(true)
        openStreamList(manualSelection = false)
        assertNoLandscapeRequest()
    }

    @Test
    fun openingManualStreamListWithDefaultSettingsDoesNotRequestLandscape() {
        PlayerSettingsRepository.ensureLoaded()
        openStreamList(manualSelection = false)
        assertNoLandscapeRequest()
    }

    @Test
    fun explicitManualSelectionDoesNotRequestLandscape() {
        PlayerSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.setStreamReuseLastLinkEnabled(true)
        openStreamList(manualSelection = true)
        assertNoLandscapeRequest()
    }

    private fun openStreamList(manualSelection: Boolean) {
        openStreamList(
            StreamLaunch(
                profileId = 0,
                type = "movie",
                videoId = "orientation-test",
                title = "Example movie",
                manualSelection = manualSelection,
            ),
        )
    }

    private fun openStreamList(launch: StreamLaunch) {
        val launchId = StreamLaunchStore.put(launch)
        compose.setContent {
            NuvioTheme {
                MainAppContent(
                    initialRoute = StreamRoute(launchId, launch.title),
                    ownsAppRuntime = false,
                    showLaunchOverlay = false,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun assertNoLandscapeRequest() {
        compose.runOnIdle {
            assertFalse(
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE in OrientationTrackingActivity.requests,
                "Opening the stream list requested these orientations: ${OrientationTrackingActivity.requests}",
            )
        }
    }
}

@Implements(Activity::class)
class OrientationTrackingActivity : ShadowActivity() {
    companion object {
        val requests = mutableListOf<Int>()
    }

    @Implementation
    override fun setRequestedOrientation(requestedOrientation: Int) {
        requests += requestedOrientation
        super.setRequestedOrientation(requestedOrientation)
    }
}
