package com.nuvio.app.core.tracking

import com.nuvio.app.features.mdblist.MdbListTracker
import com.nuvio.app.features.simkl.SimklAuthRepository
import com.nuvio.app.features.simkl.SimklSyncRepository
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import com.nuvio.app.features.trakt.TraktAuthRepository

/**
 * Remote media trackers are disabled while anime-specific tracking is rebuilt.
 * Keep this lifecycle entry point so callers stay provider-neutral.
 */
fun ensureTrackingProvidersRegistered() {
    // Keep account/profile cleanup aware of saved credentials without exposing
    // any remote tracker to the active tracking registry.
    TrackingProviderRegistry.registerProfileStore(TraktAuthRepository)
    TrackingProviderRegistry.registerProfileStore(SimklAuthRepository)
    TrackingProviderRegistry.registerProfileStore(SimklSyncRepository)
    TrackingProviderRegistry.registerProfileStore(MdbListTracker)
}
