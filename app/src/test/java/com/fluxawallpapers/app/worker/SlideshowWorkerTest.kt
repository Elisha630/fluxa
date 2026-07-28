package com.fluxawallpapers.app.worker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.fluxawallpapers.app.data.database.AppDatabase
import com.fluxawallpapers.app.data.model.*
import com.fluxawallpapers.app.data.repository.WallpaperRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SlideshowWorkerTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: WallpaperRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WallpaperRepository(context, database.wallpaperDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `worker returns success when slideshow is disabled`() = runTest {
        repository.setSlideshowEnabled(false)

        val worker = TestListenableWorkerBuilder<SlideshowWorker>(context).build()
        val result = worker.doWork()

        assertEquals("Should return success when slideshow disabled",
            ListenableWorker.Result.success(), result)
    }

    @Test
    fun `worker retries when wifi only is enabled but not on wifi`() = runTest {
        repository.setSlideshowEnabled(true)
        repository.setWifiOnlyToggle(true)
        // In Robolectric without network, isWifiOnlyAvailable will be false

        val worker = TestListenableWorkerBuilder<SlideshowWorker>(context).build()
        val result = worker.doWork()

        // When wifi only is on but not connected, worker should retry
        assertEquals("Should retry when Wi-Fi only is enabled but not connected",
            ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `worker returns success when no cached wallpapers available`() = runTest {
        repository.setSlideshowEnabled(true)
        repository.setWifiOnlyToggle(false)

        val worker = TestListenableWorkerBuilder<SlideshowWorker>(context).build()
        val result = worker.doWork()

        // No wallpapers cached — should succeed gracefully (fallback)
        assertTrue("Should succeed when no wallpapers available",
            result is ListenableWorker.Result.Success)
    }

    @Test
    fun `slideshow worker companion enums parse correctly`() {
        // Verify SlideshowInterval enums match expected values
        assertEquals(15L, SlideshowInterval.FIFTEEN_MIN.durationMinutes)
        assertEquals(30L, SlideshowInterval.THIRTY_MIN.durationMinutes)
        assertEquals(60L, SlideshowInterval.ONE_HOUR.durationMinutes)
        assertEquals(360L, SlideshowInterval.SIX_HOURS.durationMinutes)
        assertEquals(720L, SlideshowInterval.TWELVE_HOURS.durationMinutes)
        assertEquals(1440L, SlideshowInterval.DAILY.durationMinutes)

        // Verify parsing
        assertEquals(SlideshowInterval.ONE_HOUR, SlideshowInterval.fromDisplayName("1 hour"))
        assertEquals(SlideshowInterval.ONE_HOUR, SlideshowInterval.fromDisplayNameOrDefault("unknown"))
        assertEquals(SlideshowInterval.DAILY, SlideshowInterval.fromDisplayNameOrDefault("Daily"))
    }

    @Test
    fun `slideshow target enums parse correctly`() {
        assertEquals(SlideshowTarget.HOME_SCREEN, SlideshowTarget.fromDisplayName("Home Screen"))
        assertEquals(SlideshowTarget.LOCK_SCREEN, SlideshowTarget.fromDisplayName("Lock Screen"))
        assertEquals(SlideshowTarget.BOTH, SlideshowTarget.fromDisplayName("Both"))
        assertEquals(SlideshowTarget.BOTH, SlideshowTarget.fromDisplayNameOrDefault("invalid"))
    }

    @Test
    fun `wallpaper source enums parse correctly`() {
        assertEquals(WallpaperSource.UNSPLASH, WallpaperSource.fromKey("unsplash"))
        assertEquals(WallpaperSource.PEXELS, WallpaperSource.fromKey("pexels"))
        assertEquals(WallpaperSource.PIXABAY, WallpaperSource.fromKey("pixabay"))
        assertEquals(WallpaperSource.UNSPLASH, WallpaperSource.fromKeyOrDefault("unknown"))
    }
}
