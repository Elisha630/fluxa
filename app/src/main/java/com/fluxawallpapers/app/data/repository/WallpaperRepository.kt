package com.fluxawallpapers.app.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import androidx.core.content.edit
import com.fluxawallpapers.app.BuildConfig
import com.fluxawallpapers.app.data.BitmapUtils
import com.fluxawallpapers.app.data.database.WallpaperDao
import com.fluxawallpapers.app.data.model.*
import com.fluxawallpapers.app.data.network.*
import com.fluxawallpapers.app.data.recommendation.FirebaseManager
import com.fluxawallpapers.app.util.FluxaLog
import com.fluxawallpapers.app.util.HashUtils
import com.fluxawallpapers.app.util.PinterestScraper
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

enum class PredictionType {
    RECENT,
    LIVE_SUGGESTION,
    TRENDING_TAG,
    CATEGORY
}

data class SearchPredictionItem(
    val query: String,
    val type: PredictionType,
    val subtitle: String? = null
)

enum class WallpaperAction { SET, SAVE, SKIP, VIEW_LONG }

class WallpaperRepository(
    private val context: Context,
    private val wallpaperDao: WallpaperDao,
    private val aiAnalysisQueue: AiAnalysisQueue,
    private val firebaseManager: FirebaseManager
) {
    private val sharedPrefs = context.getSharedPreferences("fluxa_settings", Context.MODE_PRIVATE)
    private val httpClient = RetrofitClient.okHttpClient

    // Simple in-memory cache for searches to save quota during a session
    private val searchCache = java.util.concurrent.ConcurrentHashMap<String, List<Wallpaper>>()

    // Curated fallback wallpapers when offline or API keys are missing/invalid
    val fallbackWallpapers = listOf(
        Wallpaper("fallback_1", "unsplash", "https://images.unsplash.com/photo-1579783902614-a3fb3927b6a5?q=80&w=1200", "https://images.unsplash.com/photo-1579783902614-a3fb3927b6a5?q=80&w=800", "Julia", listOf("Abstract", "Aesthetic", "Colorful")),
        Wallpaper("fallback_2", "unsplash", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?q=80&w=1200", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?q=80&w=800", "Sean O.", listOf("Nature", "Beach", "Sunset")),
        Wallpaper("fallback_3", "unsplash", "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?q=80&w=1200", "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?q=80&w=800", "Lukasz Sz.", listOf("Nature", "Mountains", "Mist")),
        Wallpaper("fallback_4", "unsplash", "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?q=80&w=1200", "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?q=80&w=800", "Kamil S.", listOf("Nature", "Forest", "Green")),
        Wallpaper("fallback_5", "unsplash", "https://images.unsplash.com/photo-1541701494587-cb58502866ab?q=80&w=1200", "https://images.unsplash.com/photo-1541701494587-cb58502866ab?q=80&w=800", "Joel F.", listOf("Abstract", "Minimal", "Fluid")),
        Wallpaper("fallback_6", "unsplash", "https://images.unsplash.com/photo-1535223289827-42f1e9919769?q=80&w=1200", "https://images.unsplash.com/photo-1535223289827-42f1e9919769?q=80&w=800", "Alex K.", listOf("Cyberpunk", "City", "Tokyo")),
        Wallpaper("fallback_7", "unsplash", "https://images.unsplash.com/photo-1506744038136-46273834b3fb?q=80&w=1200", "https://images.unsplash.com/photo-1506744038136-46273834b3fb?q=80&w=800", "Ansel A.", listOf("Nature", "Yosemite", "Valley")),
        Wallpaper("fallback_8", "unsplash", "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=1200", "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?q=80&w=800", "Saba s.", listOf("Abstract", "Lines", "Waves")),
        Wallpaper("fallback_9", "unsplash", "https://images.unsplash.com/photo-1518770660439-4636190af475?q=80&w=1200", "https://images.unsplash.com/photo-1518770660439-4636190af475?q=80&w=800", "Umberto", listOf("Tech", "Circuit", "Future")),
        Wallpaper("fallback_10", "unsplash", "https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=1200", "https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=800", "Anders J.", listOf("Tech", "Globe", "Blue")),
    )

    // Connectivity check helpers
    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isWifiOnlyAvailable(): Boolean {
        if (!getWifiOnlyToggle()) return true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    // --- Settings persistence ---

    fun getSourceToggle(source: String): Boolean =
        sharedPrefs.getBoolean("source_$source", true)

    fun setSourceToggle(source: String, enabled: Boolean) {
        sharedPrefs.edit { putBoolean("source_$source", enabled) }
    }

    fun getCacheSizeLimit(): Int =
        sharedPrefs.getInt("cache_size_limit", 20)

    fun setCacheSizeLimit(limit: Int) {
        sharedPrefs.edit { putInt("cache_size_limit", limit) }
    }

    fun getWifiOnlyToggle(): Boolean =
        sharedPrefs.getBoolean("wifi_only", false)

    fun setWifiOnlyToggle(enabled: Boolean) {
        sharedPrefs.edit { putBoolean("wifi_only", enabled) }
    }

    fun getSlideshowEnabled(): Boolean =
        sharedPrefs.getBoolean("slideshow_enabled", false)

    fun setSlideshowEnabled(enabled: Boolean) {
        sharedPrefs.edit { putBoolean("slideshow_enabled", enabled) }
    }

    /** Returns the stored interval display name; prefer [getSlideshowIntervalEnum] for typesafe access. */
    fun getSlideshowInterval(): String =
        sharedPrefs.getString("slideshow_interval", SlideshowInterval.ONE_HOUR.displayName) ?: "1 hour"

    fun getSlideshowIntervalEnum(): SlideshowInterval =
        SlideshowInterval.fromDisplayNameOrDefault(getSlideshowInterval())

    fun setSlideshowInterval(interval: String) {
        sharedPrefs.edit { putString("slideshow_interval", interval) }
    }

    /** Returns the stored target display name; prefer [getSlideshowTargetEnum] for typesafe access. */
    fun getSlideshowTargets(): String =
        sharedPrefs.getString("slideshow_targets", SlideshowTarget.BOTH.displayName) ?: "Both"

    fun getSlideshowTargetEnum(): SlideshowTarget =
        SlideshowTarget.fromDisplayNameOrDefault(getSlideshowTargets())

    fun setSlideshowTargets(target: String) {
        sharedPrefs.edit { putString("slideshow_targets", target) }
    }

    fun getSlideshowSource(): String =
        sharedPrefs.getString("slideshow_source", "Mixed (All Sources)") ?: "Mixed (All Sources)"

    fun setSlideshowSource(source: String) {
        sharedPrefs.edit { putString("slideshow_source", source) }
    }

    // API key accessors — kept private; keys remain in BuildConfig from secrets plugin
    private fun getUnsplashKey(): String = BuildConfig.UNSPLASH_ACCESS_KEY
    private fun getPexelsKey(): String = BuildConfig.PEXELS_ACCESS_KEY
    private fun getPixabayKey(): String = BuildConfig.PIXABAY_ACCESS_KEY
    private fun getNvidiaKey(): String = BuildConfig.NVIDIA_API_KEY
    private fun isKeyConfigured(key: String): Boolean {
        return key.isNotEmpty() && key != "MY_UNSPLASH_ACCESS_KEY" && key != "MY_PEXELS_ACCESS_KEY" && key != "MY_PIXABAY_ACCESS_KEY" && key != "MY_NVIDIA_API_KEY"
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    suspend fun analyzeAndCache(wallpaper: Wallpaper): AiMetadataEntity? = withContext(Dispatchers.IO) {
        val hash = HashUtils.sha256(wallpaper.imageUrl)
        
        // 1. Check Cache
        val existing = wallpaperDao.getAiMetadata(hash)
        if (existing != null) return@withContext existing

        // 2. Execute with rate-limiting
        return@withContext aiAnalysisQueue.execute {
            try {
                val key = getNvidiaKey()
                if (!isKeyConfigured(key)) return@execute null

                val prompt = """
                    Analyze this wallpaper image. Provide a JSON response with:
                    - primaryTheme (String)
                    - secondaryThemes (List of Strings)
                    - keywords (List of Strings, excluding garbage like 'photo', 'wallpaper', 'image')
                    - searchQueries (List of 3-5 specific queries for finding similar wallpapers)
                    - confidence (Float 0-1)
                    Return ONLY raw JSON.
                """.trimIndent()

                val response = RetrofitClient.nvidiaApi.analyzeImage(
                    auth = "Bearer $key",
                    request = NvidiaChatRequest(
                        messages = listOf(
                            NvidiaMessage(
                                role = "user",
                                content = listOf(
                                    NvidiaContent(type = "text", text = prompt),
                                    NvidiaContent(type = "image_url", imageUrl = NvidiaImageUrl(wallpaper.imageUrl))
                                )
                            )
                        )
                    )
                )

                val content = response.choices.firstOrNull()?.message?.content ?: return@execute null
                val jsonStr = extractJsonObject(content) ?: return@execute null

                val aiData = moshi.adapter(AiResponseContent::class.java).fromJson(jsonStr) ?: return@execute null
                
                val entity = AiMetadataEntity(
                    imageHash = hash,
                    primaryTheme = aiData.primaryTheme,
                    secondaryThemes = aiData.secondaryThemes.joinToString(","),
                    keywords = aiData.keywords.joinToString(","),
                    searchQueries = aiData.searchQueries.joinToString(","),
                    confidence = aiData.confidence
                )
                
                wallpaperDao.insertAiMetadata(entity)
                FluxaLog.d("AI Analysis cached for ${wallpaper.id}: ${aiData.primaryTheme}")
                entity
            } catch (e: Exception) {
                FluxaLog.e("AI Analysis failed: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Vision models are inconsistent about how they wrap JSON in prose/markdown — some use
     * ```json fences, some use plain ``` fences, some add a sentence before/after, some don't
     * fence at all. Instead of matching one exact fence style (which silently failed for
     * anything else and made the whole AI path look "broken"), just grab the first balanced
     * {...} block found anywhere in the text.
     */
    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start == -1) return null
        var depth = 0
        for (i in start until raw.length) {
            when (raw[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun WallpaperEntity.toWallpaper(): Wallpaper {
        return Wallpaper(
            id = id,
            source = source,
            imageUrl = imageUrl,
            thumbnailUrl = thumbnailUrl,
            author = author,
            tags = tags.split(",").filter { it.isNotEmpty() },
            isCached = downloadedPath != null,
            isPinned = isPinned
        )
    }

    suspend fun getLocalWallpapersSnapshot(): List<Wallpaper> = withContext(Dispatchers.IO) {
        wallpaperDao.getDownloadedWallpapers().first().map { it.toWallpaper() }
    }

    // --- Collection management using SharedPreferences storing a simple map of name -> list of wallpaper IDs
    fun getCollections(): Map<String, List<String>> {
        val raw = sharedPrefs.getString("collections_json", "{}") ?: "{}"
        return try {
            @Suppress("UNCHECKED_CAST")
            val parsed = moshi.adapter(Map::class.java).fromJson(raw) as Map<String, List<String>>?
            parsed ?: emptyMap<String, List<String>>()
        } catch (e: Exception) {
            FluxaLog.e("Failed to parse collections: ${e.message}", e)
            emptyMap<String, List<String>>()
        }
    }

    fun saveCollections(map: Map<String, List<String>>) {
        val json = moshi.adapter(Map::class.java).toJson(map)
        sharedPrefs.edit { putString("collections_json", json) }
    }

    fun createCollection(name: String) {
        val normalized = name.trim()
        if (normalized.isEmpty()) return
        val current = getCollections().toMutableMap()
        if (!current.containsKey(normalized)) {
            current[normalized] = emptyList()
            saveCollections(current)
        }
    }

    fun addToCollection(name: String, wallpaperId: String) {
        val current = getCollections().toMutableMap()
        val list = current[name]?.toMutableList() ?: mutableListOf()
        if (!list.contains(wallpaperId)) {
            list.add(wallpaperId)
            current[name] = list
            saveCollections(current)
        }
    }

    fun removeFromCollection(name: String, wallpaperId: String) {
        val current = getCollections().toMutableMap()
        val list = current[name]?.toMutableList() ?: mutableListOf()
        if (list.remove(wallpaperId)) {
            current[name] = list
            saveCollections(current)
        }
    }

    suspend fun getCollectionWallpapers(name: String): List<Wallpaper> = withContext(Dispatchers.IO) {
        val ids = getCollections()[name] ?: return@withContext emptyList()
        val out = mutableListOf<Wallpaper>()
        ids.forEach { id ->
            val entity = wallpaperDao.getWallpaperById(id)
            if (entity != null) out.add(entity.toWallpaper())
        }
        return@withContext out
    }

    /**
     * Returns a status/notice message explaining the current feed state.
     * Now includes detailed per-source diagnostics when no sources are ready.
     */
    fun getFeedStatusMessage(): String? {
        if (!isOnline()) return "Offline: showing wallpapers saved on this device."
        if (!isWifiOnlyAvailable()) return "Wi-Fi only is enabled. Connect to Wi-Fi or turn it off in Settings."

        val unsplashKeyOk = isKeyConfigured(getUnsplashKey())
        val pexelsKeyOk = isKeyConfigured(getPexelsKey())
        val pixabayKeyOk = isKeyConfigured(getPixabayKey())

        val enabledSources = listOf(
            "Unsplash" to Pair(getSourceToggle("unsplash"), unsplashKeyOk),
            "Pexels" to Pair(getSourceToggle("pexels"), pexelsKeyOk),
            "Pixabay" to Pair(getSourceToggle("pixabay"), pixabayKeyOk),
            "Pinterest" to Pair(getSourceToggle("pinterest"), true) // no key needed (Jsoup scraper)
        )

        val readySources = enabledSources.filter { it.second.first && it.second.second }
        if (readySources.isEmpty()) {
            // Build diagnostic message with per-source status
            val parts = mutableListOf<String>()
            for ((name, state) in enabledSources) {
                val (enabled, keyReady) = state
                if (enabled && !keyReady) {
                    parts.add("$name (missing key)")
                } else if (!enabled && keyReady) {
                    parts.add("$name (disabled)")
                } else if (!enabled) {
                    parts.add("$name (disabled & missing key)")
                }
            }
            if (parts.isNotEmpty()) {
                return "Source diagnostics: ${parts.joinToString("; ")}."
            }
            return "No active API sources are ready. Enable a source and add its key, or use cached wallpapers."
        }
        return null
    }

    /**
     * Returns a per-source health map for displaying in Settings. Values: "ready", "missing key", "disabled", or "disabled & missing key".
     */
    fun getSourceHealth(): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        val unsplashKeyOk = isKeyConfigured(getUnsplashKey())
        val pexelsKeyOk = isKeyConfigured(getPexelsKey())
        val pixabayKeyOk = isKeyConfigured(getPixabayKey())

        val sources = listOf(
            Triple("Unsplash", getSourceToggle("unsplash"), unsplashKeyOk),
            Triple("Pexels", getSourceToggle("pexels"), pexelsKeyOk),
            Triple("Pixabay", getSourceToggle("pixabay"), pixabayKeyOk),
            Triple("Pinterest", getSourceToggle("pinterest"), true)
        )

        for ((name, enabled, keyOk) in sources) {
            val status = when {
                enabled && keyOk -> "ready"
                enabled && !keyOk -> "missing key"
                !enabled && keyOk -> "disabled"
                else -> "disabled & missing key"
            }
            entries[name] = status
        }
        return entries
    }

    // Fetch feed with page support, now falls back to cached wallpapers when offline
    suspend fun getCuratedFeed(page: Int, darkTheme: Boolean = true, forceRefresh: Boolean = false): List<Wallpaper> = withContext(Dispatchers.IO) {
        if (!isOnline() || !isWifiOnlyAvailable()) {
            FluxaLog.d("Feed returned offline: showing cached wallpapers")
            return@withContext getLocalWallpapersSnapshot().shuffled()
        }

        val results = mutableListOf<Wallpaper>()
        val targetApiPage = if (forceRefresh && page == 1) (1..10).random() else page

        // 1. Unsplash Source
        if (getSourceToggle("unsplash")) {
            val key = getUnsplashKey()
            if (isKeyConfigured(key)) {
                try {
                    val photos = RetrofitClient.unsplashApi.getCurated(
                        auth = "Client-ID $key",
                        page = targetApiPage,
                        perPage = 15
                    )
                    photos.forEach {
                        results.add(
                            Wallpaper(
                                id = "${WallpaperSource.UNSPLASH.key}_${it.id}",
                                source = WallpaperSource.UNSPLASH.key,
                                imageUrl = it.urls.full,
                                thumbnailUrl = it.urls.regular,
                                author = it.user.name,
                                tags = it.tags?.map { t -> t.title } ?: listOf("photo")
                            )
                        )
                    }
                    FluxaLog.d("Unsplash curated page $targetApiPage: ${photos.size} photos")
                } catch (e: Exception) {
                    FluxaLog.e("Failed Unsplash curated: ${e.message}", e)
                }
            }
        }

        // 2. Pexels Source
        if (getSourceToggle("pexels")) {
            val key = getPexelsKey()
            if (isKeyConfigured(key)) {
                try {
                    val response = RetrofitClient.pexelsApi.getCurated(
                        auth = key,
                        page = targetApiPage,
                        perPage = 15
                    )
                    response.photos.forEach {
                        results.add(
                            Wallpaper(
                                id = "${WallpaperSource.PEXELS.key}_${it.id}",
                                source = WallpaperSource.PEXELS.key,
                                imageUrl = it.src.original,
                                thumbnailUrl = it.src.portrait,
                                author = it.photographer,
                                tags = listOf("wallpaper", "photography")
                            )
                        )
                    }
                    FluxaLog.d("Pexels curated page $targetApiPage: ${response.photos.size} photos")
                } catch (e: Exception) {
                    FluxaLog.e("Failed Pexels curated: ${e.message}", e)
                }
            }
        }

        // 3. Pixabay Source
        if (getSourceToggle("pixabay")) {
            val key = getPixabayKey()
            if (isKeyConfigured(key)) {
                try {
                    val response = RetrofitClient.pixabayApi.getPopular(
                        key = key,
                        page = targetApiPage,
                        perPage = 15
                    )
                    response.hits.forEach {
                        results.add(
                            Wallpaper(
                                id = "${WallpaperSource.PIXABAY.key}_${it.id}",
                                source = WallpaperSource.PIXABAY.key,
                                imageUrl = it.fullHDURL ?: it.largeImageURL,
                                thumbnailUrl = it.largeImageURL,
                                author = it.user,
                                tags = it.tags.split(",").map { t -> t.trim() }
                            )
                        )
                    }
                    FluxaLog.d("Pixabay curated page $targetApiPage: ${response.hits.size} photos")
                } catch (e: Exception) {
                    FluxaLog.e("Failed Pixabay curated: ${e.message}", e)
                }
            }
        }

        // 4. Pinterest Source (Jsoup HTML scraper)
        if (getSourceToggle("pinterest") && page == 1) {
            try {
                val pinterestQuery = if (forceRefresh) {
                    listOf(
                        "aesthetic wallpaper 4k",
                        "phone background amoled",
                        "minimalist wallpapers",
                        "nature dark landscape",
                        "cyberpunk aesthetic",
                        "abstract artwork wallpaper",
                        "anime aesthetic 4k",
                        "architecture phone wallpaper"
                    ).random()
                } else {
                    "wallpaper"
                }
                val pins = PinterestScraper.searchPins(pinterestQuery, 15)
                pins.forEach { pin ->
                    val pinTags = if (pin.keywords.isNotEmpty()) {
                        pin.keywords + "wallpaper"
                    } else {
                        listOf("wallpaper", "curated")
                    }
                    results.add(
                        Wallpaper(
                            id = "${WallpaperSource.PINTEREST.key}_${pin.imageUrl.hashCode()}",
                            source = WallpaperSource.PINTEREST.key,
                            imageUrl = pin.imageUrl,
                            thumbnailUrl = pin.imageUrl,
                            author = pin.author,
                            tags = pinTags
                        )
                    )
                }
                FluxaLog.d("Pinterest curated ('$pinterestQuery'): ${pins.size} pins scraped")
            } catch (e: Exception) {
                FluxaLog.e("Failed Pinterest curated: ${e.message}", e)
            }
        }

        // 5. Theme-biased suggestion blending (page 1 only)
        if (page == 1) {
            val darkThemes = listOf(
                "dark neon cyberpunk night amoled abstract",
                "amoled dark minimal 4k wallpaper",
                "cosmic space galaxy night dark",
                "dark foggy forest misty nature"
            )
            val lightThemes = listOf(
                "light minimal bright clean pastel aesthetic",
                "white clean architecture modern minimal",
                "pastel sun ocean beach light",
                "minimalist soft aesthetic pastel 4k"
            )
            val themeQuery = if (darkTheme) {
                if (forceRefresh) darkThemes.random() else darkThemes.first()
            } else {
                if (forceRefresh) lightThemes.random() else lightThemes.first()
            }
            try {
                val themedResults = searchWallpapersInternal(
                    query = themeQuery,
                    page = if (forceRefresh) (1..3).random() else 1,
                    perPage = 6
                )
                results.addAll(0, themedResults.filter { r -> !results.any { it.id == r.id } })
                FluxaLog.d("Theme-biased ($themeQuery): ${themedResults.size} added")
            } catch (e: Exception) {
                FluxaLog.e("Theme-biased search failed: ${e.message}", e)
            }
        }

        // Weighted Taste learning personalization inclusion
        if (results.isNotEmpty() && page == 1) {
            try {
                val recommended = getRecommendations(5)
                if (recommended.isNotEmpty()) {
                    // Inject recommendations at high-ranking indices
                    results.addAll(0, recommended.filter { r -> !results.any { it.id == r.id } })
                }
            } catch (e: Exception) {
                FluxaLog.e("Failed recommendation mix: ${e.message}", e)
            }
        }

        // If no API results, return fallback wallpapers (shuffled on refresh)
        if (results.isEmpty() && page == 1) {
            FluxaLog.d("No API results — using fallback wallpapers")
            return@withContext fallbackWallpapers.shuffled()
        }

        return@withContext results.shuffled()
    }

    suspend fun saveSearchHistory(query: String) = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length >= 2) {
            wallpaperDao.insertSearch(SearchHistoryEntity(clean))
            val tagsInQuery = clean.split(" ").filter { it.length > 2 }
            tagsInQuery.forEach { tag ->
                incrementTagWeight(tag, 1.0f)
            }
        }
    }

    // Search function across all enabled APIs
    suspend fun searchWallpapers(query: String, page: Int, saveHistory: Boolean = false): List<Wallpaper> = withContext(Dispatchers.IO) {
        val cacheKey = "${query.lowercase().trim()}_page_$page"
        if (page == 1) {
            // Check cache first for page 1
            searchCache[cacheKey]?.let {
                FluxaLog.d("Search '$query' hit cache")
                return@withContext it
            }

            if (saveHistory) {
                saveSearchHistory(query)
            }
        }

        if (!isOnline() || !isWifiOnlyAvailable()) {
            val queryWords = query.lowercase().split(" ").filter { it.length >= 2 }
            FluxaLog.d("Search '$query' returned offline: filtering cached wallpapers")
            return@withContext getLocalWallpapersSnapshot().filter { wp ->
                queryWords.any { word ->
                    wp.author.lowercase().startsWith(word) ||
                    wp.tags.any { tag -> tag.lowercase().startsWith(word) }
                }
            }
        }

        val results = mutableListOf<Wallpaper>()

        // 1. Unsplash Source
        if (getSourceToggle("unsplash")) {
            val key = getUnsplashKey()
            if (isKeyConfigured(key)) {
                try {
                    val response = RetrofitClient.unsplashApi.search(
                        auth = "Client-ID $key",
                        query = query,
                        page = page,
                        perPage = 15
                    )
                    response.results.forEach {
                        results.add(
                            Wallpaper(
                                id = "${WallpaperSource.UNSPLASH.key}_${it.id}",
                                source = WallpaperSource.UNSPLASH.key,
                                imageUrl = it.urls.full,
                                thumbnailUrl = it.urls.regular,
                                author = it.user.name,
                                tags = it.tags?.map { t -> t.title } ?: query.split(" ").filter { it.isNotBlank() }.ifEmpty { listOf("photo") }
                            )
                        )
                    }
                } catch (e: Exception) {
                    FluxaLog.e("Unsplash search failed: ${e.message}", e)
                }
            }
        }

        // 2. Pexels Source
        if (getSourceToggle("pexels")) {
            val key = getPexelsKey()
            if (isKeyConfigured(key)) {
                try {
                    val response = RetrofitClient.pexelsApi.search(
                        auth = key,
                        query = query,
                        page = page,
                        perPage = 15
                    )
                    response.photos.forEach {
                        results.add(
                            Wallpaper(
                                id = "${WallpaperSource.PEXELS.key}_${it.id}",
                                source = WallpaperSource.PEXELS.key,
                                imageUrl = it.src.original,
                                thumbnailUrl = it.src.portrait,
                                author = it.photographer,
                                tags = query.split(" ").filter { it.isNotBlank() }.ifEmpty { listOf("wallpaper") }
                            )
                        )
                    }
                } catch (e: Exception) {
                    FluxaLog.e("Pexels search failed: ${e.message}", e)
                }
            }
        }

        // 3. Pixabay Source
        if (getSourceToggle("pixabay")) {
            val key = getPixabayKey()
            if (isKeyConfigured(key)) {
                try {
                    val response = RetrofitClient.pixabayApi.search(
                        key = key,
                        query = query,
                        page = page,
                        perPage = 15
                    )
                    response.hits.forEach {
                        results.add(
                            Wallpaper(
                                id = "${WallpaperSource.PIXABAY.key}_${it.id}",
                                source = WallpaperSource.PIXABAY.key,
                                imageUrl = it.fullHDURL ?: it.largeImageURL,
                                thumbnailUrl = it.largeImageURL,
                                author = it.user,
                                tags = it.tags.split(",").map { t -> t.trim() }
                            )
                        )
                    }
                } catch (e: Exception) {
                    FluxaLog.e("Pixabay search failed: ${e.message}", e)
                }
            }
        }

        // 4. Pinterest Source (Jsoup HTML scraper)
        if (getSourceToggle("pinterest") && page == 1) {
            try {
                val pins = PinterestScraper.searchPins(query, 15)
                pins.forEach { pin ->
                    val pinTags = if (pin.keywords.isNotEmpty()) {
                        pin.keywords + query
                    } else {
                        query.split(" ").filter { it.isNotBlank() }.ifEmpty { listOf(query) }
                    }
                    results.add(
                        Wallpaper(
                            id = "${WallpaperSource.PINTEREST.key}_${pin.imageUrl.hashCode()}",
                            source = WallpaperSource.PINTEREST.key,
                            imageUrl = pin.imageUrl,
                            thumbnailUrl = pin.imageUrl,
                            author = pin.author,
                            tags = pinTags
                        )
                    )
                }
                FluxaLog.d("Pinterest search '$query': ${pins.size} pins scraped")
            } catch (e: Exception) {
                FluxaLog.e("Pinterest search failed for '$query': ${e.message}", e)
            }
        }

        // If no keys or all failed, fallback to querying on fallbackWallpapers locally for tags
        if (results.isEmpty() && page == 1) {
            val queryWords = query.lowercase().split(" ").filter { it.length >= 2 }
            val fallbackResults = fallbackWallpapers.filter { wp ->
                queryWords.any { word ->
                    wp.author.lowercase().startsWith(word) ||
                    wp.tags.any { tag -> tag.lowercase().startsWith(word) }
                }
            }
            if (fallbackResults.isNotEmpty()) {
                searchCache[cacheKey] = fallbackResults
            }
            return@withContext fallbackResults
        }

        val finalResults = results.shuffled()
        if (finalResults.isNotEmpty()) {
            searchCache[cacheKey] = finalResults
        }
        return@withContext finalResults
    }

    /**
     * Internal search without recording history or updating tag weights.
     * Used by theme-biased feed blending and other background recommendation tasks.
     */
    private suspend fun searchWallpapersInternal(
        query: String,
        page: Int,
        perPage: Int = 15
    ): List<Wallpaper> = withContext(Dispatchers.IO) {
        if (!isOnline() || !isWifiOnlyAvailable()) return@withContext emptyList()

        val results = mutableListOf<Wallpaper>()

        // 1. Unsplash Source
        if (getSourceToggle("unsplash")) {
            val key = getUnsplashKey()
            if (isKeyConfigured(key)) {
                try {
                    val response = RetrofitClient.unsplashApi.search(
                        auth = "Client-ID $key",
                        query = query,
                        page = page,
                        perPage = perPage
                    )
                    response.results.forEach {
                        results.add(
                            Wallpaper(
                                id = "${WallpaperSource.UNSPLASH.key}_${it.id}",
                                source = WallpaperSource.UNSPLASH.key,
                                imageUrl = it.urls.full,
                                thumbnailUrl = it.urls.regular,
                                author = it.user.name,
                                tags = it.tags?.map { t -> t.title } ?: query.split(" ").filter { it.isNotBlank() }
                                    .ifEmpty { listOf("photo") }
                            )
                        )
                    }
                } catch (_: Exception) { }
            }
        }

        // 2. Pexels Source
        if (getSourceToggle("pexels")) {
            val key = getPexelsKey()
            if (isKeyConfigured(key)) {
                try {
                    val response = RetrofitClient.pexelsApi.search(
                        auth = key,
                        query = query,
                        page = page,
                        perPage = perPage
                    )
                    response.photos.forEach {
                        results.add(
                            Wallpaper(
                                id = "${WallpaperSource.PEXELS.key}_${it.id}",
                                source = WallpaperSource.PEXELS.key,
                                imageUrl = it.src.original,
                                thumbnailUrl = it.src.portrait,
                                author = it.photographer,
                                tags = query.split(" ").filter { it.isNotBlank() }
                                    .ifEmpty { listOf("wallpaper") }
                            )
                        )
                    }
                } catch (_: Exception) { }
            }
        }

        // 3. Pixabay Source
        if (getSourceToggle("pixabay")) {
            val key = getPixabayKey()
            if (isKeyConfigured(key)) {
                try {
                    val response = RetrofitClient.pixabayApi.search(
                        key = key,
                        query = query,
                        page = page,
                        perPage = perPage
                    )
                    response.hits.forEach {
                        results.add(
                            Wallpaper(
                                id = "${WallpaperSource.PIXABAY.key}_${it.id}",
                                source = WallpaperSource.PIXABAY.key,
                                imageUrl = it.fullHDURL ?: it.largeImageURL,
                                thumbnailUrl = it.largeImageURL,
                                author = it.user,
                                tags = it.tags.split(",").map { t -> t.trim() }
                            )
                        )
                    }
                } catch (_: Exception) { }
            }
        }

        return@withContext results.shuffled()
    }

    // Persistent storage download & LRU management, with optional progress reporting
    suspend fun downloadWallpaperToCache(
        wallpaper: Wallpaper,
        onProgress: ((Float) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        val existing = wallpaperDao.getWallpaperById(wallpaper.id)
        if (existing?.downloadedPath != null) {
            // Already cached, update access time for LRU
            wallpaperDao.insertWallpaper(existing.copy(cachedAt = System.currentTimeMillis()))
            return@withContext existing.downloadedPath
        }

        try {
            // Create target folders
            val wallpapersDir = File(context.filesDir, "wallpapers")
            if (!wallpapersDir.exists()) wallpapersDir.mkdirs()

            val targetFile = File(wallpapersDir, "${wallpaper.id}.jpg")

            // Make network call to fetch image stream
            val request = Request.Builder().url(wallpaper.imageUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body
            val totalBytes = body.contentLength()

            // Track progress while writing to file
            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        // Check for cancellation
                        ensureActive()
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (onProgress != null && totalBytes > 0) {
                            onProgress(totalRead.toFloat() / totalBytes.toFloat())
                        }
                    }
                }
            }

            val savedPath = targetFile.absolutePath

            // Save details to database
            val entity = WallpaperEntity(
                id = wallpaper.id,
                source = wallpaper.source,
                imageUrl = wallpaper.imageUrl,
                thumbnailUrl = wallpaper.thumbnailUrl,
                author = wallpaper.author,
                tags = wallpaper.tags.joinToString(","),
                isPinned = existing?.isPinned ?: false,
                downloadedPath = savedPath,
                cachedAt = System.currentTimeMillis()
            )
            wallpaperDao.insertWallpaper(entity)

            // Trigger Cache Eviction (LRU)
            applyLruEviction()

            return@withContext savedPath
        } catch (e: CancellationException) {
            // Re-throw cancellation exceptions
            throw e
        } catch (e: Exception) {
            FluxaLog.e("Download failed: ${e.message}", e)
            return@withContext null
        }
    }

    // Evict unpinned wallpapers exceeding cache limit
    private suspend fun applyLruEviction() {
        val limit = getCacheSizeLimit()
        val cachedUnpinned = wallpaperDao.getCachedUnpinnedWallpapers()
        if (cachedUnpinned.size > limit) {
            val numToEvict = cachedUnpinned.size - limit
            for (i in 0 until numToEvict) {
                val target = cachedUnpinned[i]
                target.downloadedPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        file.delete().also { if (it) FluxaLog.d("Evicted cached file: $path") }
                    }
                }
                // Update in DB: set downloadedPath to null so we don't treat it as cached
                wallpaperDao.insertWallpaper(target.copy(downloadedPath = null))
            }
        }
    }

    // Download to local external storage (publicly accessible "files" directory)
    suspend fun downloadToLocalStorage(
        wallpaper: Wallpaper,
        onProgress: ((Float) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            // Use the public Downloads/Fluxa directory so photos are visible to the user
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val fluxaDir = File(downloadsDir, "Fluxa")
            if (!fluxaDir.exists()) fluxaDir.mkdirs()

            val targetFile = File(fluxaDir, "${wallpaper.id}.jpg")

            // If already downloaded to this location, skip
            if (targetFile.exists()) {
                return@withContext targetFile.absolutePath
            }

            // Download from URL
            val request = Request.Builder().url(wallpaper.imageUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body
            val totalBytes = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        ensureActive()
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (onProgress != null && totalBytes > 0) {
                            onProgress(totalRead.toFloat() / totalBytes.toFloat())
                        }
                    }
                }
            }

            FluxaLog.d("Downloaded to local storage: ${targetFile.absolutePath}")
            return@withContext targetFile.absolutePath
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FluxaLog.e("Download to local storage failed: ${e.message}", e)
            return@withContext null
        }
    }

    // Toggle Pin status to prevent eviction
    suspend fun togglePin(wallpaper: Wallpaper, pin: Boolean) = withContext(Dispatchers.IO) {
        val existing = wallpaperDao.getWallpaperById(wallpaper.id)
        if (existing != null) {
            val updated = existing.copy(isPinned = pin, cachedAt = System.currentTimeMillis())
            wallpaperDao.updateWallpaper(updated)
        } else {
            // Safe fallback: download it as part of pin
            val entity = WallpaperEntity(
                id = wallpaper.id,
                source = wallpaper.source,
                imageUrl = wallpaper.imageUrl,
                thumbnailUrl = wallpaper.thumbnailUrl,
                author = wallpaper.author,
                tags = wallpaper.tags.joinToString(","),
                isPinned = pin,
                downloadedPath = null,
                cachedAt = System.currentTimeMillis()
            )
            wallpaperDao.insertWallpaper(entity)
        }

        // Record pin as a positive learning event
        if (pin) {
            recordWallpaperAction(wallpaper, WallpaperAction.SAVE)
        }
    }

    // Flows for UI
    val pinnedWallpapers: Flow<List<Wallpaper>> = wallpaperDao.getPinnedWallpapers().map { entities ->
        entities.map { entity -> entity.toWallpaper() }
    }

    val cachedWallpapers: Flow<List<Wallpaper>> = wallpaperDao.getDownloadedWallpapers().map { entities ->
        entities.map { entity -> entity.toWallpaper() }
    }

    val searchHistoryFlow = wallpaperDao.getSearchHistory()

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        wallpaperDao.clearSearchHistory()
    }

    suspend fun isPinned(id: String): Boolean {
        return wallpaperDao.getWallpaperById(id)?.isPinned ?: false
    }

    /** Returns the entity for a given wallpaper ID, or null if not in the database. */
    suspend fun getCachedWallpaperEntity(id: String): WallpaperEntity? {
        return wallpaperDao.getWallpaperById(id)
    }

    /** Returns the downloaded file path for a cached wallpaper, or null. */
    suspend fun getCachedPath(id: String): String? {
        return wallpaperDao.getWallpaperById(id)?.downloadedPath
    }

    suspend fun updateHeartbeat() {
        firebaseManager.updateHeartbeat()
    }

    // Taste learning mechanism
    suspend fun recordWallpaperAction(wallpaper: Wallpaper, action: WallpaperAction) = withContext(Dispatchers.IO) {
        val weightDelta = when (action) {
            WallpaperAction.SET -> 5.0f      // Strong positive signal
            WallpaperAction.SAVE -> 3.0f     // Positive signal
            WallpaperAction.VIEW_LONG -> 1.0f // Mild positive
            WallpaperAction.SKIP -> -3.0f     // Negative signal
        }

        wallpaper.tags.forEach { tag ->
            incrementTagWeight(tag, weightDelta)
        }

        // Sync with Firebase
        firebaseManager.logInteraction(
            wallpaperId = wallpaper.id,
            source = wallpaper.source,
            action = action.name,
            tags = wallpaper.tags
        )
    }

    private suspend fun incrementTagWeight(tag: String, delta: Float) {
        val normalizedTag = tag.trim().lowercase()
        if (normalizedTag.isEmpty()) return

        val existing = wallpaperDao.getPreferenceTag(normalizedTag)
        if (existing != null) {
            val newWeight = (existing.weight + delta).coerceIn(-50f, 100f)
            wallpaperDao.insertPreferenceTag(PreferenceTagEntity(normalizedTag, newWeight))
        } else {
            wallpaperDao.insertPreferenceTag(PreferenceTagEntity(normalizedTag, delta.coerceIn(-50f, 100f)))
        }
    }

    // Recommendation logic: Hybrid Firebase + Local tag preferences
    suspend fun getRecommendations(limit: Int): List<Wallpaper> = withContext(Dispatchers.IO) {
        // 1. Get interests from both sources, WITH their scores this time — previously
        // getTopTags() discarded the actual weight, so every Firebase tag was treated as equally
        // strong as every local tag regardless of how much (or little) signal backed it.
        val firebaseTagScores = try {
            firebaseManager.getTopTagsWithScores(10)
        } catch (_: Exception) {
            emptyList()
        }

        val localPreferenceTags = wallpaperDao.getPreferenceTags().filter { it.weight > 0f }

        // 2. Merge into a single tag -> score map. Both sources use the same underlying
        // action weights (SET/SAVE/VIEW_LONG/SKIP), so their magnitudes are directly comparable;
        // summing lets a tag reinforced through both local and cloud signals rank higher than one
        // seen through only one source.
        val combinedScores = mutableMapOf<String, Double>()
        for ((tag, score) in firebaseTagScores) {
            val normalized = tag.trim().lowercase()
            if (normalized.isNotEmpty()) combinedScores[normalized] = (combinedScores[normalized] ?: 0.0) + score
        }
        for (pref in localPreferenceTags) {
            val normalized = pref.tag.trim().lowercase()
            if (normalized.isNotEmpty()) combinedScores[normalized] = (combinedScores[normalized] ?: 0.0) + pref.weight
        }

        // Only ever seek out MORE of what's positively scored — a tag with a net-negative score
        // means the user has actively skipped that kind of content more than they've engaged
        // with it, so it has no business being used to find recommendations.
        val positiveTags = combinedScores.filter { it.value > 0.0 }
        if (positiveTags.isEmpty()) return@withContext emptyList()

        val tagsRankedByScore = positiveTags.entries.sortedByDescending { it.value }.map { it.key }

        // 3. Query a broader set of top tags (weighted toward the strongest ones, with a little
        // randomness among the rest for freshness) instead of just 2 uniformly-random tags from
        // a tiny pool of 5 — that discarded most of the signal we just computed.
        val topGuaranteed = tagsRankedByScore.take(3)
        val remainderPool = tagsRankedByScore.drop(3)
        val tagsToQuery = (topGuaranteed + remainderPool.shuffled().take(2)).distinct().take(5)

        // 4. Fetch candidates, excluding anything already pinned — a "recommendation" the user
        // already saved isn't new to them.
        val pinnedIds = wallpaperDao.getPinnedWallpapers().first().map { it.id }.toSet()
        val recWallpapers = mutableListOf<Wallpaper>()
        for (tag in tagsToQuery) {
            try {
                val results = searchWallpapers(tag, 1).take(5)
                recWallpapers.addAll(results)
            } catch (e: Exception) {
                FluxaLog.e("Error recommending for tag $tag: ${e.message}", e)
            }
        }

        // 5. Rank by the actual combined score of each candidate's matching tags, rather than a
        // blind shuffle that threw away everything we just computed about relative tag strength.
        val ranked = recWallpapers
            .filter { it.id !in pinnedIds }
            .distinctBy { it.id }
            .map { wp ->
                val matchScore = wp.tags.sumOf { tag -> combinedScores[tag.trim().lowercase()] ?: 0.0 }
                // Small random jitter so near-equal candidates don't always appear in the same order.
                wp to (matchScore + Random.nextDouble(0.0, 0.5))
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)

        return@withContext ranked
    }

    // Smart rotation slideshow: combining selected pool sources and avoiding recently shown
    suspend fun getNextSlideshowWallpaper(): Wallpaper? = withContext(Dispatchers.IO) {
        val sourceSetting = getSlideshowSource()
        val pool = mutableListOf<Wallpaper>()

        // 1. Collect Pinned / Favorite wallpapers (include ALL pinned items regardless of cached state)
        val pinnedEntities = wallpaperDao.getPinnedWallpapers().first()
        val pinnedWallpapers = pinnedEntities.map { it.toWallpaper() }

        // 2. Collect Collection wallpapers
        val collectionWallpapers = mutableListOf<Wallpaper>()
        try {
            val collections = getCollections()
            val allCollectionIds = collections.values.flatten().distinct()
            if (allCollectionIds.isNotEmpty()) {
                val colEntities = wallpaperDao.getWallpapersByIds(allCollectionIds)
                collectionWallpapers.addAll(colEntities.map { it.toWallpaper() })
            }
        } catch (e: Exception) {
            FluxaLog.e("Error fetching collection wallpapers for slideshow: ${e.message}")
        }

        // 3. Collect Downloaded / Cached wallpapers
        val cachedEntities = wallpaperDao.getDownloadedWallpapers().first()
        val cachedWallpapers = cachedEntities.map { it.toWallpaper() }

        // 4. Collect All DB stored wallpapers
        val allDbEntities = wallpaperDao.getAllWallpapers().first()
        val allDbWallpapers = allDbEntities.map { it.toWallpaper() }

        when (sourceSetting) {
            "Favorites & Collections" -> {
                pool.addAll(pinnedWallpapers)
                pool.addAll(collectionWallpapers)
                pool.addAll(cachedWallpapers)
            }
            "Curated Online Feed" -> {
                if (isOnline() && isWifiOnlyAvailable()) {
                    try {
                        val curated = getCuratedFeed(page = (1..3).random(), darkTheme = true)
                        pool.addAll(curated)
                    } catch (e: Exception) {
                        FluxaLog.e("Error fetching curated feed for slideshow: ${e.message}")
                    }
                }
                // Fallback to local DB if online fetch returned nothing
                if (pool.isEmpty()) {
                    pool.addAll(cachedWallpapers)
                    pool.addAll(allDbWallpapers)
                }
            }
            else -> { // "Mixed (All Sources)" or default
                pool.addAll(pinnedWallpapers)
                pool.addAll(collectionWallpapers)
                pool.addAll(cachedWallpapers)
                pool.addAll(allDbWallpapers)

                // If pool is small (< 10 items) and online, supplement with curated feed & recommendations
                if (pool.size < 10 && isOnline() && isWifiOnlyAvailable()) {
                    try {
                        val curated = getCuratedFeed(page = 1, darkTheme = true)
                        pool.addAll(curated)
                        val recs = getRecommendations(5)
                        pool.addAll(recs)
                    } catch (e: Exception) {
                        FluxaLog.e("Error fetching online supplement for slideshow: ${e.message}")
                    }
                }
            }
        }

        // Ultimate fallback if pool is still empty (e.g. fresh install offline)
        if (pool.isEmpty()) {
            pool.addAll(fallbackWallpapers)
        }

        // Deduplicate pool by ID
        val distinctPool = pool.distinctBy { it.id }

        // Get shown IDs in last 12 hours
        val shownIds = wallpaperDao.getRecentSlideshowIds(System.currentTimeMillis() - (12 * 60 * 60 * 1000L)).toSet()

        // Filter out recently shown wallpapers to enforce true rotation diversity
        val available = distinctPool.filter { it.id !in shownIds }

        val selected = if (available.isNotEmpty()) {
            available.random()
        } else {
            // If all items in pool were shown recently, clear slideshow history and pick from pool
            FluxaLog.d("Slideshow history full for pool of size ${distinctPool.size}, clearing history and resetting cycle.")
            wallpaperDao.clearSlideshowHistory()
            distinctPool.randomOrNull()
        } ?: return@withContext null

        // Record in slideshow history to prevent repeat in upcoming cycles
        wallpaperDao.insertSlideshowHistory(SlideshowHistoryEntity(selected.id))

        FluxaLog.d("getNextSlideshowWallpaper selected: ${selected.id} (Pool size: ${distinctPool.size}, Unshown available: ${available.size})")
        return@withContext selected
    }

    /**
     * Ensure all pinned wallpapers are cached for slideshow rotation.
     * This downloads any pinned wallpapers not yet in cache.
     */
    suspend fun ensureAllPinnedWallpapersAreCached() = withContext(Dispatchers.IO) {
        try {
            val pinned = wallpaperDao.getPinnedWallpapers().first()
            val notCached = pinned.filter { it.downloadedPath == null }
            if (notCached.isEmpty()) {
                FluxaLog.d("All pinned wallpapers already cached")
                return@withContext
            }
            FluxaLog.d("Caching ${notCached.size} uncached pinned wallpapers...")
            notCached.forEach { entity ->
                val wp = entity.toWallpaper()
                try {
                    downloadWallpaperToCache(wp)
                    FluxaLog.d("Cached pinned wallpaper: ${wp.id}")
                } catch (e: Exception) {
                    FluxaLog.e("Failed to cache pinned ${wp.id}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            FluxaLog.e("ensureAllPinnedWallpapersAreCached failed: ${e.message}", e)
        }
    }

    /**
     * Downloads a wallpaper's (small) thumbnail and computes its average RGB color. This is a
     * cheap, fully on-device stand-in for "does this look like the source photo" — no network AI
     * call, typically well under a second even for a dozen candidates in parallel. It's what lets
     * us narrow down a broad tag like "dark" to results that actually share the source image's
     * tone, instead of any dark photo at all.
     */
    private suspend fun averageThumbnailColor(imageUrl: String): Triple<Int, Int, Int>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(imageUrl).build()
            httpClient.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes() ?: return@withContext null
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
                if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return@withContext null

                // We only need a handful of samples for an average color — decode tiny.
                val sampleSize = BitmapUtils.calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, 32, 32)
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return@withContext null

                var rSum = 0L
                var gSum = 0L
                var bSum = 0L
                var count = 0L
                val stepX = maxOf(1, bitmap.width / 12)
                val stepY = maxOf(1, bitmap.height / 12)
                var y = 0
                while (y < bitmap.height) {
                    var x = 0
                    while (x < bitmap.width) {
                        val pixel = bitmap.getPixel(x, y)
                        rSum += (pixel shr 16) and 0xFF
                        gSum += (pixel shr 8) and 0xFF
                        bSum += pixel and 0xFF
                        count++
                        x += stepX
                    }
                    y += stepY
                }
                bitmap.recycle()
                if (count == 0L) return@withContext null
                Triple((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
            }
        } catch (e: Exception) {
            FluxaLog.d("Color sampling skipped for $imageUrl: ${e.message}")
            null
        }
    }

    private fun colorDistance(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Double {
        val dr = (a.first - b.first).toDouble()
        val dg = (a.second - b.second).toDouble()
        val db = (a.third - b.third).toDouble()
        return kotlin.math.sqrt(dr * dr + dg * dg + db * db) // 0 (identical) .. ~441 (max)
    }

    suspend fun getSimilarWallpapers(wallpaper: Wallpaper, limit: Int = 10): List<Wallpaper> = withContext(Dispatchers.IO) {
        val hash = HashUtils.sha256(wallpaper.imageUrl)
        
        // 1. Check recommendation cache (valid for 24h)
        val cachedRecs = wallpaperDao.getRecommendationCache(hash)
        if (cachedRecs != null && (System.currentTimeMillis() - cachedRecs.generatedAt) < 24 * 60 * 60 * 1000) {
            val ids = cachedRecs.recommendedIds.split(",").filter { it.isNotEmpty() }
            if (ids.isNotEmpty()) {
                val cachedEntities = wallpaperDao.getWallpapersByIds(ids)
                if (cachedEntities.isNotEmpty()) {
                    FluxaLog.d("Similar for ${wallpaper.id}: Cache hit (${cachedEntities.size} items)")
                    return@withContext cachedEntities.map { it.toWallpaper() }
                }
            }
        }

        // 2. Try AI Metadata (Nvidia) with short timeout for real-time responsiveness
        val aiMetadata = withTimeoutOrNull(8000) {
            analyzeAndCache(wallpaper)
        }

        // Reference color for reranking, computed once and reused by both the AI and fallback paths.
        val sourceColor = averageThumbnailColor(wallpaper.thumbnailUrl)

        if (aiMetadata != null) {
            val queries = aiMetadata.searchQueries.split(",").filter { it.isNotBlank() }
            
            if (queries.isNotEmpty()) {
                val allResults = mutableListOf<Wallpaper>()
                
                // Query up to 3 queries in parallel for speed
                queries.take(3).forEach { query ->
                    try {
                        val results = searchWallpapers(query, 1)
                        allResults.addAll(results)
                    } catch (e: Exception) {
                        FluxaLog.e("AI query '$query' failed: ${e.message}")
                    }
                }

                // Dedupe, then rank by (a) how many of the AI's queries surfaced this candidate,
                // and (b) how close its color is to the source photo, so we don't just get
                // "loosely on-theme" results but ones that actually look similar.
                val candidates = allResults.filter { it.id != wallpaper.id }.groupBy { it.id }.map { it.value.first() to it.value.size }
                val ranked = rankByColorAndOverlap(candidates, sourceColor, limit)

                if (ranked.isNotEmpty()) {
                    wallpaperDao.insertRecommendationCache(
                        RecommendationCacheEntity(
                            imageHash = hash,
                            recommendedIds = ranked.joinToString(",") { it.id }
                        )
                    )
                    FluxaLog.d("Similar for ${wallpaper.id} resolved via AI: ${ranked.size} results")
                    return@withContext ranked
                }
            }
        }

        // 3. Fallback: tag-based search when Nvidia is slow, unavailable or returned no results.
        //
        // Previously this concatenated just the top 2 tags into a single search string (e.g.
        // "dark car" as one literal query), which is very lossy — a broad tag like "dark" would
        // dominate and pull back any dark photo at all, not ones that actually resemble the
        // source. Instead: query each meaningful tag SEPARATELY to gather a wider candidate pool,
        // then rank candidates by how many of the source wallpaper's own tags they share PLUS how
        // close their average color is to the source photo. That color signal is what lets us
        // tell a black car apart from a dark forest even though both are tagged "dark" — without
        // depending on a slow/unreliable cloud vision model to do it.
        FluxaLog.d("Nvidia slow/unavailable for ${wallpaper.id}, falling back to tag-based similar search")

        val noiseWords = setOf("unsplash", "pexels", "pixabay", "pinterest",
            "photo", "photography", "wallpaper", "curated", "image",
            "background", "hd", "4k", "8k", "download", "free", "best",
            "top", "new", "popular", "trending", "awesome", "cool", "nice")

        val sourceTags = wallpaper.tags.map { it.lowercase().trim() }.toSet()
        val meaningfulTags = wallpaper.tags
            .map { it.lowercase().trim() }
            .filter { it.length >= 2 && it !in noiseWords }
            .distinct()

        val fallbackTagPool = if (meaningfulTags.isNotEmpty()) meaningfulTags.take(4) else listOf("background")

        val fallbackResults = mutableListOf<Wallpaper>()
        for (tag in fallbackTagPool) {
            try {
                fallbackResults.addAll(searchWallpapers(tag, 1))
            } catch (e: Exception) {
                FluxaLog.e("Fallback tag search for '$tag' failed: ${e.message}", e)
            }
        }

        val fallbackCandidates = fallbackResults
            .filter { it.id != wallpaper.id }
            .distinctBy { it.id }
            .map { wp -> wp to wp.tags.map { it.lowercase().trim() }.count { it in sourceTags } }

        val finalResults = rankByColorAndOverlap(fallbackCandidates, sourceColor, limit)
        FluxaLog.d("Similar for ${wallpaper.id} resolved via tag fallback (${fallbackTagPool.joinToString(", ")}): ${finalResults.size} results")
        return@withContext finalResults
    }

    /**
     * Shared ranking step for [getSimilarWallpapers]: takes candidates paired with a tag-overlap
     * score, downloads their (small) thumbnails in parallel to compare average color against the
     * source, and combines both signals into a final ranking. Falls back to overlap-only ranking
     * if the source color couldn't be determined (e.g. offline).
     */
    private suspend fun rankByColorAndOverlap(
        candidates: List<Pair<Wallpaper, Int>>,
        sourceColor: Triple<Int, Int, Int>?,
        limit: Int
    ): List<Wallpaper> = coroutineScope {
        if (candidates.isEmpty()) return@coroutineScope emptyList()

        // Cap how many thumbnails we bother downloading for color comparison — this is a
        // reranking step, not the primary filter, so we don't need to sample every candidate.
        val toSample = candidates.sortedByDescending { it.second }.take(24)

        val withColor = if (sourceColor != null) {
            val deferred = toSample.map { (wp, overlap) ->
                async { Triple(wp, overlap, averageThumbnailColor(wp.thumbnailUrl)) }
            }
            deferred.awaitAll()
        } else {
            toSample.map { (wp, overlap) -> Triple(wp, overlap, null) }
        }

        withColor
            .map { (wp, overlap, color) ->
                val colorScore = if (sourceColor != null && color != null) {
                    // Convert distance (0 best..~441 worst) into a 0..1 similarity so it combines
                    // sensibly with the (unbounded but usually small) tag-overlap count.
                    (1.0 - (colorDistance(sourceColor, color) / 441.0)).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
                // Tag overlap is the primary signal (an actual shared subject/theme beats color
                // alone), color similarity breaks ties and narrows an overly broad shared tag.
                val score = overlap * 2.0 + colorScore + Random.nextDouble(0.0, 0.05)
                wp to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
    }

    suspend fun fetchGoogleAutoSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2 || !isOnline()) return@withContext emptyList()
        val suggestions = mutableListOf<String>()
        try {
            // 1. Direct query autocomplete (e.g., "porsche" -> "porsche 911", "porsche gt3 rs")
            val directEncoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val urlDirect = "https://suggestqueries.google.com/complete/search?client=firefox&q=$directEncoded"
            val reqDirect = Request.Builder()
                .url(urlDirect)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            RetrofitClient.okHttpClient.newCall(reqDirect).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val adapter = moshi.adapter(List::class.java)
                    val parsed = adapter.fromJson(bodyString)
                    if (parsed != null && parsed.size >= 2) {
                        @Suppress("UNCHECKED_CAST")
                        val raw = parsed[1] as? List<String> ?: emptyList()
                        raw.map { sug ->
                            sug.replace(" wallpaper", "", ignoreCase = true)
                               .replace(" wallpapers", "", ignoreCase = true)
                               .trim()
                        }
                        .filter { it.isNotBlank() && !it.equals(cleanQuery, ignoreCase = true) }
                        .forEach { suggestions.add(it) }
                    }
                }
            }

            // 2. Wallpaper-tailored autocomplete (e.g., "porsche wallpaper" -> "porsche classic wallpaper", "porsche interior wallpaper")
            if (suggestions.size < 6) {
                val wpEncoded = URLEncoder.encode("$cleanQuery wallpaper", "UTF-8")
                val urlWp = "https://suggestqueries.google.com/complete/search?client=firefox&q=$wpEncoded"
                val reqWp = Request.Builder()
                    .url(urlWp)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

                RetrofitClient.okHttpClient.newCall(reqWp).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val adapter = moshi.adapter(List::class.java)
                        val parsed = adapter.fromJson(bodyString)
                        if (parsed != null && parsed.size >= 2) {
                            @Suppress("UNCHECKED_CAST")
                            val raw = parsed[1] as? List<String> ?: emptyList()
                            raw.map { sug ->
                                sug.replace(" wallpaper", "", ignoreCase = true)
                                   .replace(" wallpapers", "", ignoreCase = true)
                                   .trim()
                            }
                            .filter { it.isNotBlank() && !it.equals(cleanQuery, ignoreCase = true) }
                            .forEach { suggestions.add(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            FluxaLog.e("Failed to fetch Google auto-suggestions: ${e.message}")
        }
        return@withContext suggestions
            .distinctBy { it.lowercase() }
            .take(10)
    }

    suspend fun fetchSearchPredictions(query: String): List<SearchPredictionItem> = withContext(Dispatchers.IO) {
        val lowerQuery = query.lowercase().trim()
        val predictions = mutableListOf<SearchPredictionItem>()

        val history = wallpaperDao.getSearchHistory().first()

        // 1. Matching Recent Searches
        val matchingHistory = if (lowerQuery.isEmpty()) {
            history.take(4)
        } else {
            history.filter { it.query.lowercase().contains(lowerQuery) }.take(4)
        }
        matchingHistory.forEach {
            predictions.add(
                SearchPredictionItem(
                    query = it.query,
                    type = PredictionType.RECENT,
                    subtitle = "Recent search"
                )
            )
        }

        if (lowerQuery.isEmpty()) {
            // Show top tags from indexed database when search input is empty
            val topDbTags = getLocalWallpapersSnapshot()
                .flatMap { it.tags }
                .groupBy { it.lowercase() }
                .mapValues { entry -> entry.value.size }
                .entries
                .sortedByDescending { it.value }
                .map { entry -> entry.key.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
                .take(8)

            val defaultTrending = if (topDbTags.isNotEmpty()) topDbTags else listOf(
                "Cyberpunk City", "OLED Dark", "Minimalist", "Neon Art",
                "Anime", "Nature Landscape", "Space Nebulae", "Supercars"
            )

            defaultTrending.forEach {
                predictions.add(
                    SearchPredictionItem(
                        query = it,
                        type = PredictionType.TRENDING_TAG,
                        subtitle = "Trending topic"
                    )
                )
            }
            return@withContext predictions.distinctBy { it.query.lowercase() }
        }

        // 2. Fetch Live Google & Pinterest Autocomplete Suggestions
        if (lowerQuery.length >= 2) {
            val googleSuggestions = fetchGoogleAutoSuggestions(query)
            googleSuggestions.forEach { sug ->
                if (predictions.none { it.query.equals(sug, ignoreCase = true) }) {
                    predictions.add(
                        SearchPredictionItem(
                            query = sug,
                            type = PredictionType.LIVE_SUGGESTION,
                            subtitle = "Search suggestion"
                        )
                    )
                }
            }
        }

        // 3. Dynamic Local Database Tag Token Index Matches (Prefix matching on tags array)
        val dbMatches = getLocalWallpapersSnapshot()
            .flatMap { it.tags }
            .filter { tag ->
                val tagLower = tag.lowercase()
                tagLower.startsWith(lowerQuery) && !tagLower.equals(lowerQuery)
            }
            .distinctBy { it.lowercase() }
            .sortedBy { it.length }
            .take(5)

        dbMatches.forEach { tag ->
            if (predictions.none { it.query.equals(tag, ignoreCase = true) }) {
                predictions.add(
                    SearchPredictionItem(
                        query = tag,
                        type = PredictionType.CATEGORY,
                        subtitle = "Indexed tag"
                    )
                )
            }
        }

        return@withContext predictions
            .distinctBy { it.query.lowercase() }
            .take(12)
    }

    suspend fun fetchSearchPreviewThumbnails(query: String): List<Wallpaper> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return@withContext emptyList()

        val cachedMatches = getLocalWallpapersSnapshot().filter { wp ->
            wp.tags.any { it.lowercase().startsWith(cleanQuery) } ||
            wp.author.lowercase().startsWith(cleanQuery)
        }.take(4)

        if (cachedMatches.size >= 3) {
            return@withContext cachedMatches
        }

        if (isOnline()) {
            try {
                val liveResults = searchWallpapers(query, 1).take(4)
                if (liveResults.isNotEmpty()) {
                    return@withContext liveResults
                }
            } catch (e: Exception) {
                FluxaLog.e("Failed to fetch search preview thumbnails: ${e.message}")
            }
        }

        return@withContext cachedMatches
    }

    suspend fun deleteSearchHistoryQuery(query: String) = withContext(Dispatchers.IO) {
        wallpaperDao.deleteSearch(query)
    }

    suspend fun clearAllSearchHistory() = withContext(Dispatchers.IO) {
        wallpaperDao.clearSearchHistory()
    }

}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class AiResponseContent(
    val primaryTheme: String,
    val secondaryThemes: List<String>,
    val keywords: List<String>,
    val searchQueries: List<String>,
    val confidence: Float
)
