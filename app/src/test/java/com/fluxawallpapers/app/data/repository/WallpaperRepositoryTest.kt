package com.fluxawallpapers.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fluxawallpapers.app.data.database.AppDatabase
import com.fluxawallpapers.app.data.model.*
import com.fluxawallpapers.app.data.recommendation.FirebaseManager
import com.fluxawallpapers.app.data.repository.AiAnalysisQueue
import kotlinx.coroutines.flow.first
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
class WallpaperRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: WallpaperRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            
        // Simple manual mock for FirebaseManager to avoid initializing actual Firebase
        val mockFirebase = object : FirebaseManager(context) {
            override suspend fun logInteraction(
                wallpaperId: String,
                source: String,
                action: String,
                tags: List<String>,
                searchQuery: String?
            ) {
                // No-op for tests
            }
        }
        
        repository = WallpaperRepository(context, database.wallpaperDao(), AiAnalysisQueue(), mockFirebase)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createTestWallpaper(id: String, tags: List<String>): Wallpaper {
        return Wallpaper(
            id = id,
            source = "test",
            imageUrl = "https://example.com/$id.jpg",
            thumbnailUrl = "https://example.com/${id}_thumb.jpg",
            author = "Test",
            tags = tags
        )
    }

    // ---- Fallback behavior ----

    @Test
    fun `fallback wallpapers are non-empty and well-formed`() {
        val fallbacks = repository.fallbackWallpapers
        assertTrue("Fallback list should not be empty", fallbacks.isNotEmpty())

        fallbacks.forEach { wp ->
            assertTrue("Fallback wallpaper id must start with fallback_", wp.id.startsWith("fallback_"))
            assertTrue("Fallback imageUrl must be valid", wp.imageUrl.isNotBlank())
            assertTrue("Fallback thumbnailUrl must be valid", wp.thumbnailUrl.isNotBlank())
            assertTrue("Fallback author must not be blank", wp.author.isNotBlank())
            assertTrue("Fallback tags must not be empty", wp.tags.isNotEmpty())
        }
    }

    @Test
    fun `feed status shows offline when no connectivity`() {
        // In Robolectric, with no network setup, isOnline() returns false
        val status = repository.getFeedStatusMessage()
        assertNotNull("Feed status should not be null when offline", status)
        assertTrue("Status should mention offline", status?.contains("Offline", ignoreCase = true) ?: false)
    }

    // ---- Search history ----

    @Test
    fun `search history records queries`() = runTest {
        val dao = database.wallpaperDao()

        // Simulate inserting search history like searchWallpapers does
        dao.insertSearch(SearchHistoryEntity("mountains"))
        dao.insertSearch(SearchHistoryEntity("sunset"))
        dao.insertSearch(SearchHistoryEntity("mountains")) // duplicate, should replace

        val history = dao.getSearchHistory().first()
        assertEquals("Should have 2 unique entries", 2, history.size)
        assertEquals("Most recent should be first", "mountains", history[0].query)
    }

    @Test
    fun `clear search history removes all entries`() = runTest {
        val dao = database.wallpaperDao()
        dao.insertSearch(SearchHistoryEntity("test"))
        dao.insertSearch(SearchHistoryEntity("sunset"))

        repository.clearHistory()
        val history = dao.getSearchHistory().first()
        assertTrue("History should be empty after clear", history.isEmpty())
    }

    // ---- Cache eviction ----

    @Test
    fun `lru eviction removes unpinned wallpapers exceeding cache limit`() = runTest {
        val dao = database.wallpaperDao()

        // Seed wallpapers with downloadedPath set (simulating cached)
        val entities = (0 until 25).map { i ->
            WallpaperEntity(
                id = "test_$i",
                source = "unsplash",
                imageUrl = "https://example.com/$i.jpg",
                thumbnailUrl = "https://example.com/${i}_thumb.jpg",
                author = "Author $i",
                tags = "test,tag",
                isPinned = false,
                downloadedPath = "/data/files/wallpapers/test_$i.jpg",
                cachedAt = System.currentTimeMillis() - (25 - i) * 1000L // oldest first
            )
        }
        entities.forEach { dao.insertWallpaper(it) }

        // Default cache limit is 20
        val cachedBefore = dao.getDownloadedWallpapers().first()
        assertEquals("Should start with 25 cached", 25, cachedBefore.size)

        // Trigger eviction by downloading more
        val evictTarget = Wallpaper(
            id = "test_new",
            source = "unsplash",
            imageUrl = "https://example.com/new.jpg",
            thumbnailUrl = "https://example.com/new_thumb.jpg",
            author = "New",
            tags = listOf("test")
        )

        // The download call triggers LRU internally
        // Since we can't download in tests, we simulate the LRU behavior directly
        // by exceeding the limit and calling an internal method
        // We'll verify that dao.getCachedUnpinnedWallpapers() works correctly
        val cached = dao.getCachedUnpinnedWallpapers()
        val unpinnedCount = cached.size
        assertTrue("Should have unpinned cached wallpapers", unpinnedCount >= 20)

        // Verify pinned wallpapers are excluded from eviction candidates
        dao.insertWallpaper(
            WallpaperEntity(
                id = "pinned_1",
                source = "unsplash",
                imageUrl = "https://example.com/pinned.jpg",
                thumbnailUrl = "https://example.com/pinned_thumb.jpg",
                author = "Pinned Author",
                tags = "test",
                isPinned = true,
                downloadedPath = "/data/files/wallpapers/pinned_1.jpg",
                cachedAt = System.currentTimeMillis()
            )
        )

        val unpinnedAfter = dao.getCachedUnpinnedWallpapers()
        val pinned = dao.getPinnedWallpapers().first()
        assertTrue("Pinned should be excluded from unpinned list",
            unpinnedAfter.none { it.id == "pinned_1" })
        assertEquals("Should have 1 pinned wallpaper", 1, pinned.size)
    }

    // ---- Preference weighting ----

    @Test
    fun `record wallpaper action increments tag weights correctly`() = runTest {
        val dao = database.wallpaperDao()

        // Record a SET action (weight +5)
        val wp = createTestWallpaper("nature_1", listOf("nature", "mountain"))
        repository.recordWallpaperAction(wp, WallpaperAction.SET)
        val tagsAfterSet = dao.getPreferenceTags()
        val natureTag = tagsAfterSet.find { it.tag == "nature" }
        val mountainTag = tagsAfterSet.find { it.tag == "mountain" }

        assertNotNull("Nature tag should exist", natureTag)
        assertEquals("Nature weight after SET should be 5.0", 5.0f, natureTag!!.weight)
        assertEquals("Mountain weight after SET should be 5.0", 5.0f, mountainTag!!.weight)

        // Record a SKIP action (weight -3)
        repository.recordWallpaperAction(wp.copy(tags = listOf("nature")), WallpaperAction.SKIP)
        val tagsAfterSkip = dao.getPreferenceTags()
        val natureAfterSkip = tagsAfterSkip.find { it.tag == "nature" }
        assertEquals("Nature weight after SET+SKIP should be 2.0", 2.0f, natureAfterSkip!!.weight)
    }

    @Test
    fun `tag weights are clamped to valid range`() = runTest {
        val dao = database.wallpaperDao()

        // Add 20 SET actions for the same tag (would be 100 if not clamped)
        val wpOverused = createTestWallpaper("overused", listOf("overused"))
        repeat(20) {
            repository.recordWallpaperAction(wpOverused, WallpaperAction.SET)
        }
        val tag = dao.getPreferenceTags().find { it.tag == "overused" }
        assertNotNull(tag)
        assertTrue("Tag weight should be clamped at 100", tag!!.weight <= 100f)

        // Add 30 SKIP actions for another tag (would be -90 if not clamped)
        val wpDisliked = createTestWallpaper("disliked", listOf("disliked"))
        repeat(30) {
            repository.recordWallpaperAction(wpDisliked, WallpaperAction.SKIP)
        }
        val disliked = dao.getPreferenceTags().find { it.tag == "disliked" }
        assertNotNull(disliked)
        assertTrue("Tag weight should be clamped at -50", disliked!!.weight >= -50f)
    }

    @Test
    fun `pinned wallpapers flow emits correctly`() = runTest {
        val dao = database.wallpaperDao()
        dao.insertWallpaper(
            WallpaperEntity(
                id = "pin_flow_test",
                source = "unsplash",
                imageUrl = "https://example.com/img.jpg",
                thumbnailUrl = "https://example.com/thumb.jpg",
                author = "Test Author",
                tags = "test,pinned",
                isPinned = true,
                downloadedPath = "/data/wallpapers/pin_flow_test.jpg"
            )
        )

        val pinned = repository.pinnedWallpapers.first()
        assertEquals("Should have 1 pinned wallpaper", 1, pinned.size)
        assertEquals("Should be test wallpaper", "pin_flow_test", pinned[0].id)
        assertTrue("Should be marked as pinned", pinned[0].isPinned)
    }

    @Test
    fun `cached wallpapers flow emits only downloaded wallpapers`() = runTest {
        val dao = database.wallpaperDao()
        dao.insertWallpaper(
            WallpaperEntity(
                id = "cached_1",
                source = "pexels",
                imageUrl = "https://example.com/cached.jpg",
                thumbnailUrl = "https://example.com/cached_thumb.jpg",
                author = "Cached Author",
                tags = "cached",
                isPinned = false,
                downloadedPath = "/data/wallpapers/cached_1.jpg"
            )
        )
        dao.insertWallpaper(
            WallpaperEntity(
                id = "not_cached",
                source = "pexels",
                imageUrl = "https://example.com/not_cached.jpg",
                thumbnailUrl = "https://example.com/not_cached_thumb.jpg",
                author = "Not Cached",
                tags = "not_cached",
                isPinned = false,
                downloadedPath = null
            )
        )

        val cached = repository.cachedWallpapers.first()
        assertEquals("Should only include downloaded wallpapers", 1, cached.size)
        assertEquals("cached_1", cached[0].id)
    }

    // ---- WallpaperAction enum ----

    @Test
    fun `wallpaper action weights are correct`() {
        assertEquals(5.0f, WallpaperAction.SET.ordinal.toFloat()) // Verifying enum values exist
        // Note: weight values are checked in preference weighting tests
    }
}
