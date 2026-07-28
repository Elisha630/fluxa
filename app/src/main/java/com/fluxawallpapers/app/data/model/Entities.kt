package com.fluxawallpapers.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallpapers")
data class WallpaperEntity(
    @PrimaryKey val id: String, // e.g. "unsplash_123", "pexels_123", "pixabay_123"
    val source: String, // "unsplash", "pexels", "pixabay"
    val imageUrl: String,
    val thumbnailUrl: String,
    val author: String,
    val tags: String, // Comma-separated tags
    val isPinned: Boolean = false,
    val downloadedPath: String? = null, // Sandboxed private relative file path if cached
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "preference_tags")
data class PreferenceTagEntity(
    @PrimaryKey val tag: String,
    val weight: Float = 0f
)

@Entity(tableName = "slideshow_history")
data class SlideshowHistoryEntity(
    @PrimaryKey val id: String,
    val shownAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_metadata")
data class AiMetadataEntity(
    @PrimaryKey val imageHash: String,
    val primaryTheme: String,
    val secondaryThemes: String, // Comma-separated
    val keywords: String, // Comma-separated
    val searchQueries: String, // Comma-separated
    val confidence: Float,
    val generatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recommendation_cache")
data class RecommendationCacheEntity(
    @PrimaryKey val imageHash: String,
    val recommendedIds: String, // Comma-separated IDs
    val generatedAt: Long = System.currentTimeMillis()
)
