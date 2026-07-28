package com.fluxawallpapers.app.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fluxawallpapers.app.data.model.*
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
class WallpaperDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: WallpaperDao
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.wallpaperDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndGetAiMetadata() = runTest {
        val metadata = AiMetadataEntity(
            imageHash = "test_hash",
            primaryTheme = "Nature",
            secondaryThemes = "Forest,Mountain",
            keywords = "green,trees",
            searchQueries = "nature wallpapers",
            confidence = 0.95f
        )
        dao.insertAiMetadata(metadata)

        val retrieved = dao.getAiMetadata("test_hash")
        assertNotNull(retrieved)
        assertEquals("Nature", retrieved?.primaryTheme)
        assertEquals(0.95f, retrieved?.confidence ?: 0f)
    }

    @Test
    fun insertAndGetRecommendationCache() = runTest {
        val cache = RecommendationCacheEntity(
            imageHash = "test_hash",
            recommendedIds = "id1,id2,id3"
        )
        dao.insertRecommendationCache(cache)

        val retrieved = dao.getRecommendationCache("test_hash")
        assertNotNull(retrieved)
        assertEquals("id1,id2,id3", retrieved?.recommendedIds)
    }
}
