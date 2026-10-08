package com.nuvio.app.features.home

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.collection.CollectionMobileSettingsRepository
import com.nuvio.app.features.collection.CollectionMobileSettingsStorage
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.collection.CollectionSource
import com.nuvio.app.features.collection.CollectionStorage
import com.nuvio.app.features.plugins.PluginHomeSection
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HomeCatalogSettingsRepositoryTest {
    private lateinit var preferences: RecordingPreferences
    private lateinit var originalLocale: Locale

    @BeforeTest
    fun initialize() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        val context = RuntimeEnvironment.getApplication()
        val stored = context.getSharedPreferences("nuvio_home_catalog_settings", Context.MODE_PRIVATE)
        stored.edit().clear().commit()
        preferences = RecordingPreferences(stored)
        HomeCatalogSettingsStorage.initialize(object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = preferences
        })
        CollectionRepository.clearLocalState()
        HomeRepository.clear()
        HomeCatalogSettingsRepository.clearLocalState()
        HomeCatalogSettingsRepository.setHeroEnabled(false)
    }

    @AfterTest
    fun tearDown() {
        Locale.setDefault(originalLocale)
        HomeRepository.clear()
        HomeCatalogSettingsRepository.clearLocalState()
        CollectionRepository.clearLocalState()
    }

    @Test
    fun collectionSourceChangesAreAppliedWithTheSameTitleAndFolderCount() {
        val collection = collection()
        HomeCatalogSettingsRepository.syncCollections(listOf(collection))
        val editCount = preferences.editCount
        val settings = HomeCatalogSettingsRepository.uiState.value
        val changed = collection.copy(
            folders = collection.folders.map { folder ->
                folder.copy(sources = listOf(CollectionSource(addonId = "other", type = "anime", catalogId = "new")))
            },
        )

        HomeCatalogSettingsRepository.syncCollections(listOf(changed))

        assertTrue(preferences.editCount > editCount)
        assertEquals(settings, HomeCatalogSettingsRepository.uiState.value)
    }

    @Test
    fun localeChangesRebuildCatalogAndCollectionLabels() {
        val sections = listOf(section())
        val collections = listOf(collection())
        HomeCatalogSettingsRepository.syncCatalogs(sections)
        HomeCatalogSettingsRepository.syncCollections(collections)
        val editCount = preferences.editCount

        Locale.setDefault(Locale.FRENCH)
        HomeCatalogSettingsRepository.syncCatalogs(sections)
        HomeCatalogSettingsRepository.syncCollections(collections)

        assertEquals(editCount + 2, preferences.editCount)
    }

    @Test
    fun profileChangesRebuildTheSameCatalogAndCollectionInputs() {
        val sections = listOf(section())
        val collections = listOf(collection())
        HomeCatalogSettingsRepository.syncCatalogs(sections)
        HomeCatalogSettingsRepository.syncCollections(collections)
        val settings = HomeCatalogSettingsRepository.uiState.value

        HomeCatalogSettingsRepository.onProfileChanged()
        HomeCatalogSettingsRepository.syncCatalogs(sections)
        HomeCatalogSettingsRepository.syncCollections(collections)

        assertEquals(settings, HomeCatalogSettingsRepository.uiState.value)
    }

    private fun section() = PluginHomeSection(
        pluginId = "anime-plugin",
        pluginName = "Anime Plugin",
        title = "Popular Anime",
        items = emptyList(),
    )

    private fun collection() = Collection(
        id = "favorites",
        title = "Favorites",
        folders = listOf(CollectionFolder(id = "anime", title = "Anime")),
    )

    private class RecordingPreferences(
        private val delegate: SharedPreferences,
    ) : SharedPreferences by delegate {
        var editCount = 0

        override fun edit(): SharedPreferences.Editor {
            editCount += 1
            return delegate.edit()
        }
    }
}
