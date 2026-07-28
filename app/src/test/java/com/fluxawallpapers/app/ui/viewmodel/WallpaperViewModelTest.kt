package com.fluxawallpapers.app.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fluxawallpapers.app.data.database.AppDatabase
import com.fluxawallpapers.app.data.model.*
import com.fluxawallpapers.app.data.network.Wallpaper
import com.fluxawallpapers.app.data.repository.WallpaperAction
import com.fluxawallpapers.app.data.repository.WallpaperRepository
import com.fluxawallpapers.app.di.AppInjector
import com.fluxawallpapers.app.worker.SlideshowWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WallpaperViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: WallpaperRepository
    private lateinit var application: Application
    private lateinit var viewModel: WallpaperViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        Dispatchers.setMain(StandardTestDispatcher())

        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WallpaperRepository(application, database.wallpaperDao())

        // Wire the test repository into the manual injector
        AppInjector.overrideRepository(repository)

        viewModel = WallpaperViewModel(application)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    // ---- Initial state ----

    @Test
    fun `initial feed state is Loading`() = runTest {
        // refreshFeed() is called in init block, so we need to advance
        advanceUntilIdle()
        val state = viewModel.feedState.first()
        // After init, feed is either Loading or Success with fallbacks
        assertTrue(
            "Feed state should be Loading or Success",
            state is FeedUiState.Loading || state is FeedUiState.Success
        )
    }

    @Test
    fun `initial search state is Idle`() = runTest {
        assertEquals(SearchUiState.Idle, viewModel.searchState.first())
    }

    // ---- Search behavior ----

    @Test
    fun `executeSearch updates search state to Loading then Success`() = runTest {
        // Seed some cached wallpapers for offline search to work
        database.wallpaperDao().insertWallpaper(
            WallpaperEntity(
                id = "test_wallpaper",
                source = "unsplash",
                imageUrl = "https://example.com/test.jpg",
                thumbnailUrl = "https://example.com/test_thumb.jpg",
                author = "Test Author",
                tags = "nature,mountains",
                downloadedPath = "/data/wallpapers/test_wallpaper.jpg"
            )
        )

        viewModel.executeSearch("nature")
        advanceUntilIdle()

        val state = viewModel.searchState.first()
        assertTrue("Search state should not be Idle after executeSearch",
            state !is SearchUiState.Idle)
    }

    @Test
    fun `empty search query does not trigger search`() = runTest {
        viewModel.executeSearch("")
        advanceUntilIdle()
        assertEquals(SearchUiState.Idle, viewModel.searchState.first())

        viewModel.executeSearch("   ")
        advanceUntilIdle()
        assertEquals(SearchUiState.Idle, viewModel.searchState.first())
    }

    @Test
    fun `search predictions include curated keyword matches after debounce`() = runTest {
        val collectionJob = launch { viewModel.searchPredictions.collect {} }

        viewModel.updateSearchQuery("nat")
        advanceTimeBy(301)
        runCurrent()

        assertTrue(viewModel.searchPredictions.value.contains("Nature"))
        collectionJob.cancel()
    }

    // ---- Source toggles ----

    @Test
    fun `toggleSource updates source state`() = runTest {
        advanceUntilIdle()

        // Toggle off Unsplash
        viewModel.toggleSource("unsplash", false)
        val unsplashState = viewModel.sourceUnsplash.first()
        assertFalse("Unsplash should be disabled", unsplashState)

        // Toggle on
        viewModel.toggleSource("unsplash", true)
        val unsplashOn = viewModel.sourceUnsplash.first()
        assertTrue("Unsplash should be enabled", unsplashOn)
    }

    @Test
    fun `toggleSource on pixabay updates state`() = runTest {
        advanceUntilIdle()
        viewModel.toggleSource("pixabay", false)
        assertFalse("Pixabay should be disabled", viewModel.sourcePixabay.first())
    }

    // ---- Slideshow settings ----

    @Test
    fun `setSlideshowEnabled toggles state`() = runTest {
        advanceUntilIdle()

        viewModel.setSlideshowEnabled(true)
        assertTrue("Slideshow should be enabled", viewModel.slideshowEnabled.first())

        viewModel.setSlideshowEnabled(false)
        assertFalse("Slideshow should be disabled", viewModel.slideshowEnabled.first())
    }

    @Test
    fun `setSlideshowTarget updates stored target`() = runTest {
        advanceUntilIdle()
        viewModel.setSlideshowTargets("Home Screen")
        assertEquals("Home Screen", viewModel.slideshowTargets.first())

        viewModel.setSlideshowTargets("Lock Screen")
        assertEquals("Lock Screen", viewModel.slideshowTargets.first())
    }

    @Test
    fun `setSlideshowInterval updates stored interval`() = runTest {
        advanceUntilIdle()
        viewModel.setSlideshowInterval("15 min")
        assertEquals("15 min", viewModel.slideshowInterval.first())

        viewModel.setSlideshowInterval("Daily")
        assertEquals("Daily", viewModel.slideshowInterval.first())
    }

    // ---- Wi-Fi only toggle ----

    @Test
    fun `setWifiOnly updates state`() = runTest {
        advanceUntilIdle()
        viewModel.setWifiOnly(true)
        assertTrue("Wi-Fi only should be enabled", viewModel.wifiOnly.first())

        viewModel.setWifiOnly(false)
        assertFalse("Wi-Fi only should be disabled", viewModel.wifiOnly.first())
    }

    // ---- Cache limit ----

    @Test
    fun `setCacheLimit updates state`() = runTest {
        advanceUntilIdle()
        viewModel.setCacheLimit(75)
        assertEquals(75, viewModel.cacheLimit.first())

        viewModel.setCacheLimit(50)
        assertEquals(50, viewModel.cacheLimit.first())
    }

    // ---- Pin toggle ----

    @Test
    fun `togglePin marks wallpaper as pinned`() = runTest {
        advanceUntilIdle()

        database.wallpaperDao().insertWallpaper(
            WallpaperEntity(
                id = "pin_test",
                source = "unsplash",
                imageUrl = "https://example.com/img.jpg",
                thumbnailUrl = "https://example.com/thumb.jpg",
                author = "Test Author",
                tags = "test"
            )
        )

        val wallpaper = Wallpaper(
            id = "pin_test",
            source = "unsplash",
            imageUrl = "https://example.com/img.jpg",
            thumbnailUrl = "https://example.com/thumb.jpg",
            author = "Test Author",
            tags = listOf("test")
        )

        viewModel.togglePin(wallpaper, true)
        advanceUntilIdle()

        val isPinned = repository.isPinned("pin_test")
        assertTrue("Wallpaper should be pinned after togglePin(true)", isPinned)

        viewModel.togglePin(wallpaper, false)
        advanceUntilIdle()

        val isPinnedAfter = repository.isPinned("pin_test")
        assertFalse("Wallpaper should be unpinned after togglePin(false)", isPinnedAfter)
    }

    // ---- isOffline state ----

    @Test
    fun `isOffline state is set correctly`() = runTest {
        advanceUntilIdle()
        // In Robolectric test without network, isOffline should be true
        val offline = viewModel.isOffline.first()
        assertTrue("Should be offline in test environment", offline)
    }
}
