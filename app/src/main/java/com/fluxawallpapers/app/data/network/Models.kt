package com.fluxawallpapers.app.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Unsplash Models
@JsonClass(generateAdapter = true)
data class UnsplashPhoto(
    @field:Json(name = "id") val id: String,
    @field:Json(name = "urls") val urls: UnsplashUrls,
    @field:Json(name = "user") val user: UnsplashUser,
    @field:Json(name = "tags") val tags: List<UnsplashTag>?
)

@JsonClass(generateAdapter = true)
data class UnsplashUrls(
    @field:Json(name = "full") val full: String,
    @field:Json(name = "regular") val regular: String,
    @field:Json(name = "small") val small: String
)

@JsonClass(generateAdapter = true)
data class UnsplashUser(
    @field:Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class UnsplashTag(
    @field:Json(name = "title") val title: String
)

@JsonClass(generateAdapter = true)
data class UnsplashSearchResponse(
    @field:Json(name = "results") val results: List<UnsplashPhoto>
)

// Pexels Models
@JsonClass(generateAdapter = true)
data class PexelsSearchResponse(
    @field:Json(name = "photos") val photos: List<PexelsPhoto>
)

@JsonClass(generateAdapter = true)
data class PexelsPhoto(
    @field:Json(name = "id") val id: Long,
    @field:Json(name = "photographer") val photographer: String,
    @field:Json(name = "src") val src: PexelsSrc
)

@JsonClass(generateAdapter = true)
data class PexelsSrc(
    @field:Json(name = "original") val original: String,
    @field:Json(name = "portrait") val portrait: String,
    @field:Json(name = "medium") val medium: String
)

// Pixabay Models
@JsonClass(generateAdapter = true)
data class PixabayResponse(
    @field:Json(name = "hits") val hits: List<PixabayHit>
)

@JsonClass(generateAdapter = true)
data class PixabayHit(
    @field:Json(name = "id") val id: Long,
    @field:Json(name = "fullHDURL") val fullHDURL: String?,
    @field:Json(name = "largeImageURL") val largeImageURL: String,
    @field:Json(name = "webformatURL") val webformatURL: String,
    @field:Json(name = "user") val user: String,
    @field:Json(name = "tags") val tags: String
)

// Standard Wallpaper representation used app-wide (unified model)
data class Wallpaper(
    val id: String,
    val source: String, // "unsplash", "pexels", "pixabay"
    val imageUrl: String,
    val thumbnailUrl: String,
    val author: String,
    val tags: List<String>,
    val isCached: Boolean = false,
    val isPinned: Boolean = false
)
