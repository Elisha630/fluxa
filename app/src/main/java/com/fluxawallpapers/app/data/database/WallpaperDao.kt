package com.fluxawallpapers.app.data.database

import androidx.room.*
import com.fluxawallpapers.app.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WallpaperDao {
    // Wallpaper queries
    @Query("SELECT * FROM wallpapers ORDER BY cachedAt DESC")
    fun getAllWallpapers(): Flow<List<WallpaperEntity>>

    @Query("SELECT * FROM wallpapers WHERE isPinned = 1 ORDER BY cachedAt DESC")
    fun getPinnedWallpapers(): Flow<List<WallpaperEntity>>

    @Query("SELECT * FROM wallpapers WHERE downloadedPath IS NOT NULL ORDER BY cachedAt DESC")
    fun getDownloadedWallpapers(): Flow<List<WallpaperEntity>>

    @Query("SELECT * FROM wallpapers WHERE id = :id LIMIT 1")
    suspend fun getWallpaperById(id: String): WallpaperEntity?

    @Query("SELECT * FROM wallpapers WHERE id IN (:ids)")
    suspend fun getWallpapersByIds(ids: List<String>): List<WallpaperEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallpaper(wallpaper: WallpaperEntity)

    @Update
    suspend fun updateWallpaper(wallpaper: WallpaperEntity)

    @Delete
    suspend fun deleteWallpaper(wallpaper: WallpaperEntity)

    @Query("SELECT * FROM wallpapers WHERE downloadedPath IS NOT NULL AND isPinned = 0 ORDER BY cachedAt ASC")
    suspend fun getCachedUnpinnedWallpapers(): List<WallpaperEntity>

    // Search History queries
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearch(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    // Preference Tags queries
    @Query("SELECT * FROM preference_tags ORDER BY weight DESC")
    suspend fun getPreferenceTags(): List<PreferenceTagEntity>

    @Query("SELECT * FROM preference_tags WHERE tag = :tag LIMIT 1")
    suspend fun getPreferenceTag(tag: String): PreferenceTagEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreferenceTag(prefTag: PreferenceTagEntity)

    // Slideshow History queries
    @Query("SELECT id FROM slideshow_history WHERE shownAt >= :since")
    suspend fun getRecentSlideshowIds(since: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlideshowHistory(history: SlideshowHistoryEntity)

    @Query("DELETE FROM slideshow_history")
    suspend fun clearSlideshowHistory()

    // AI Metadata queries
    @Query("SELECT * FROM ai_metadata WHERE imageHash = :hash LIMIT 1")
    suspend fun getAiMetadata(hash: String): AiMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAiMetadata(metadata: AiMetadataEntity)

    // Recommendation Cache queries
    @Query("SELECT * FROM recommendation_cache WHERE imageHash = :hash LIMIT 1")
    suspend fun getRecommendationCache(hash: String): RecommendationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecommendationCache(cache: RecommendationCacheEntity)
}
