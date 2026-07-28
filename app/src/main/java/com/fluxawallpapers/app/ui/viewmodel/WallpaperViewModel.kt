package com.fluxawallpapers.app.ui.viewmodel

import android.app.Application
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fluxawallpapers.app.data.BitmapUtils
import com.fluxawallpapers.app.data.model.SearchHistoryEntity
import com.fluxawallpapers.app.data.model.SlideshowTarget
import com.fluxawallpapers.app.data.network.Wallpaper
import com.fluxawallpapers.app.data.repository.PredictionType
import com.fluxawallpapers.app.data.repository.SearchPredictionItem
import com.fluxawallpapers.app.data.repository.WallpaperAction
import com.fluxawallpapers.app.data.repository.WallpaperRepository
import com.fluxawallpapers.app.di.AppInjector
import com.fluxawallpapers.app.util.FluxaLog
import com.fluxawallpapers.app.worker.SlideshowWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

sealed interface FeedUiState {
    object Loading : FeedUiState
    data class Success(
        val list: List<Wallpaper>,
        val page: Int,
        val isEnd: Boolean,
        val isAppending: Boolean = false,
        val notice: String? = null
    ) : FeedUiState
    data class Error(val message: String) : FeedUiState
}

sealed interface SearchUiState {
    object Idle : SearchUiState
    object Loading : SearchUiState
    data class Success(
        val list: List<Wallpaper>,
        val query: String,
        val page: Int,
        val isEnd: Boolean,
        val isAppending: Boolean = false,
        val notice: String? = null
    ) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

sealed interface SimilarUiState {
    object Idle : SimilarUiState
    object Loading : SimilarUiState
    data class Success(val list: List<Wallpaper>) : SimilarUiState
    data class Error(val message: String) : SimilarUiState
}

enum class ConnectionType { WIFI, MOBILE, OFFLINE }

class WallpaperViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application
    private val repository: WallpaperRepository = AppInjector.provideRepository(application)
    private val preAnalyzer = WallpaperPreAnalyzer(
        repository,
        AppInjector.provideAiAnalysisQueue(),
        viewModelScope
    )

    // 1. Core States
    private val _feedState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val feedState: StateFlow<FeedUiState> = _feedState.asStateFlow()

    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    // 4. Search Query with Debounced Predictions (Google-style autocomplete)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = repository.searchHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Curated wallpaper keyword categories for instant suggestions
    private val curatedKeywords = listOf(
        "Nature", "Abstract", "Minimal", "Cyberpunk", "Dark", "Neon",
        "Ocean", "Space", "Mountains", "Forest", "City", "Retro",
        "Vintage", "Anime", "Aesthetic", "Sunrise", "Night", "Architecture",
        "Car", "Animal", "Flower", "Beach", "Winter", "Rain", "Gradient",
        "Pattern", "Texture", "Galaxy", "Sunset", "River", "Desert",
        "Street", "Japan", "Futuristic", "Waterfall", "Aurora", "Clouds",
        "Bridge", "Cat", "Dog", "Bird", "Lake", "Skyline", "Fire",
        "Crystal", "Metal", "Wood", "Marble", "Pastel", "Monochrome",
        "Neon City", "Northern Lights", "Cherry Blossom", "Tropical",
        "Underwater", "Drone Shot", "Macro", "Bokeh", "Symmetry",
        "Golden Hour", "Black and White", "Oil Painting", "Digital Art",
        "3D Render", "Isometric", "Low Poly", "Vaporwave", "Synthwave",
        "Pixel Art", "Glitch", "Double Exposure", "Long Exposure"
    )

    @OptIn(FlowPreview::class)
    val searchPredictionItems: StateFlow<List<SearchPredictionItem>> = combine(
        _searchQuery
            .debounce(200) // Fast 200ms response for live predictive suggestions
            .distinctUntilChanged(),
        searchHistory
    ) { query, _ ->
        repository.fetchSearchPredictions(query)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchPredictions: StateFlow<List<String>> = searchPredictionItems
        .map { items -> items.map { it.query } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(FlowPreview::class)
    val searchPreviewThumbnails: StateFlow<List<Wallpaper>> = _searchQuery
        .debounce(350)
        .distinctUntilChanged()
        .map { query ->
            if (query.trim().length >= 2) {
                repository.fetchSearchPreviewThumbnails(query)
            } else {
                emptyList()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteSearchHistoryItem(query: String) {
        viewModelScope.launch {
            repository.deleteSearchHistoryQuery(query)
        }
    }

    fun clearAllSearchHistory() {
        viewModelScope.launch {
            repository.clearAllSearchHistory()
        }
    }

    private val _similarState = MutableStateFlow<SimilarUiState>(SimilarUiState.Idle)
    val similarState: StateFlow<SimilarUiState> = _similarState.asStateFlow()

    // 2. Local Lists (Pins and Cache)
    val pinnedWallpapers: StateFlow<List<Wallpaper>> = repository.pinnedWallpapers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cachedWallpapers: StateFlow<List<Wallpaper>> = repository.cachedWallpapers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 3. User Settings States
    private val _sourceUnsplash = MutableStateFlow(repository.getSourceToggle("unsplash"))
    val sourceUnsplash: StateFlow<Boolean> = _sourceUnsplash.asStateFlow()

    private val _sourcePexels = MutableStateFlow(repository.getSourceToggle("pexels"))
    val sourcePexels: StateFlow<Boolean> = _sourcePexels.asStateFlow()

    private val _sourcePixabay = MutableStateFlow(repository.getSourceToggle("pixabay"))
    val sourcePixabay: StateFlow<Boolean> = _sourcePixabay.asStateFlow()

    private val _sourcePinterest = MutableStateFlow(repository.getSourceToggle("pinterest"))
    val sourcePinterest: StateFlow<Boolean> = _sourcePinterest.asStateFlow()

    private val _sourceHealth = MutableStateFlow<Map<String, String>>(repository.getSourceHealth())
    val sourceHealth: StateFlow<Map<String, String>> = _sourceHealth.asStateFlow()

    private val _cacheLimit = MutableStateFlow(repository.getCacheSizeLimit())
    val cacheLimit: StateFlow<Int> = _cacheLimit.asStateFlow()

    private val _wifiOnly = MutableStateFlow(repository.getWifiOnlyToggle())
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _slideshowEnabled = MutableStateFlow(repository.getSlideshowEnabled())
    val slideshowEnabled: StateFlow<Boolean> = _slideshowEnabled.asStateFlow()

    private val _slideshowInterval = MutableStateFlow(repository.getSlideshowInterval())
    val slideshowInterval: StateFlow<String> = _slideshowInterval.asStateFlow()

    private val _slideshowTargets = MutableStateFlow(repository.getSlideshowTargets())
    val slideshowTargets: StateFlow<String> = _slideshowTargets.asStateFlow()

    private val _connectionType = MutableStateFlow(
        if (repository.isOnline()) ConnectionType.WIFI else ConnectionType.OFFLINE
    )
    val connectionType: StateFlow<ConnectionType> = _connectionType.asStateFlow()

    /** System dark mode flag — used for theme-aware recommendations. */
    val isDarkTheme: Boolean
        get() = (app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val _isOffline = MutableStateFlow(!repository.isOnline())
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private var currentFeedList = mutableListOf<Wallpaper>()
    private var currentFeedPage = 1
    private var isFetchingFeed = false
    private var currentSearchList = mutableListOf<Wallpaper>()
    private var currentSearchPage = 1
    private var currentSearchQuery = ""
    private var isFetchingSearch = false
    private var searchJob: Job? = null

    // Track active download jobs per wallpaper ID for cancellation support
    private val downloadJobs = ConcurrentHashMap<String, Job>()

    // Collections state (name -> list of wallpaper ids)
    private val _collectionsState = MutableStateFlow<Map<String, List<String>>>(repository.getCollections())
    val collectionsState: StateFlow<Map<String, List<String>>> = _collectionsState.asStateFlow()

    // Network callback for connectivity change detection
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOffline.value = false
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(network)
            _connectionType.value = classifyConnection(caps)
        }

        override fun onLost(network: Network) {
            _isOffline.value = true
            _connectionType.value = ConnectionType.OFFLINE
        }

        override fun onCapabilitiesChanged(
            network: Network,
            caps: NetworkCapabilities
        ) {
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            _isOffline.value = !hasInternet
            _connectionType.value = classifyConnection(caps)
        }
    }

    private fun classifyConnection(caps: NetworkCapabilities?): ConnectionType {
        if (caps == null) return ConnectionType.OFFLINE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return ConnectionType.OFFLINE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.MOBILE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.WIFI
            else -> ConnectionType.OFFLINE
        }
    }

    init {
        // Detect current connection type from the active network
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val activeCaps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null
        _connectionType.value = classifyConnection(activeCaps)
        _isOffline.value = _connectionType.value == ConnectionType.OFFLINE

        registerNetworkCallback()
        refreshFeed()

        // Firebase heartbeat for recommendation algorithm
        viewModelScope.launch {
            repository.updateHeartbeat()
        }
    }

    private fun registerNetworkCallback() {
        val cm = app
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, networkCallback)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            val cm = app
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            FluxaLog.e("Error unregistering network callback: ${e.message}", e)
        }
    }

    // Refresh and fetch next curated pages
    fun refreshFeed() {
        if (isFetchingFeed) return
        viewModelScope.launch {
            isFetchingFeed = true
            _feedState.value = FeedUiState.Loading
            currentFeedPage = 1
            currentFeedList.clear()
            try {
                val list = repository.getCuratedFeed(currentFeedPage, darkTheme = isDarkTheme, forceRefresh = true)
                currentFeedList.addAll(list)
                val notice = repository.getFeedStatusMessage()
                    ?: if (list.isNotEmpty() && list.all { it.id.startsWith("fallback_") }) {
                        "Demo wallpapers shown because no configured API source returned results."
                    } else {
                        null
                    }
                _feedState.value = FeedUiState.Success(
                    list = currentFeedList.toList(),
                    page = currentFeedPage,
                    isEnd = list.isEmpty(),
                    notice = notice
                )
                preAnalyzeWallpapers(list)
                FluxaLog.d("Feed refreshed: ${list.size} wallpapers, page $currentFeedPage")
            } catch (e: Exception) {
                _feedState.value = FeedUiState.Error(e.message ?: "An unknown error occurred")
                FluxaLog.e("Feed refresh failed: ${e.message}", e)
            } finally {
                isFetchingFeed = false
            }
        }
    }

    fun fetchNextFeedPage() {
        val currentState = _feedState.value
        if (currentState is FeedUiState.Success) {
            if (currentState.isEnd || currentState.isAppending || isFetchingFeed || currentState.notice?.startsWith("Offline") == true) return
            viewModelScope.launch {
                isFetchingFeed = true
                currentFeedPage++
                _feedState.value = currentState.copy(isAppending = true)
                try {
                    val list = repository.getCuratedFeed(currentFeedPage, darkTheme = isDarkTheme)
                    currentFeedList.addAll(list)
                    _feedState.value = FeedUiState.Success(
                        list = currentFeedList.toList(),
                        page = currentFeedPage,
                        isEnd = list.isEmpty(),
                        notice = repository.getFeedStatusMessage()
                    )
                    preAnalyzeWallpapers(list)
                    FluxaLog.d("Feed page $currentFeedPage loaded: ${list.size} wallpapers")
                } catch (e: Exception) {
                    currentFeedPage--
                    FluxaLog.e("Error loading page $currentFeedPage: ${e.message}", e)
                    _feedState.value = currentState.copy(isAppending = false)
                } finally {
                    isFetchingFeed = false
                }
            }
        }
    }

    // Search functionalities
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun executeSearch(query: String, saveToHistory: Boolean = true) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return

        if (saveToHistory) {
            viewModelScope.launch {
                repository.saveSearchHistory(trimmedQuery)
            }
        }
        
        // Save quota: if we already have successful results for this exact query, don't re-hit API
        val currentState = _searchState.value
        if (currentState is SearchUiState.Success && currentState.query.equals(trimmedQuery, ignoreCase = true)) {
            FluxaLog.d("Search '$trimmedQuery' already displayed, skipping redundant API call")
            return
        }

        // Cancel any pending search to free up quota and ensure the latest query wins
        searchJob?.cancel()
        
        searchJob = viewModelScope.launch {
            isFetchingSearch = true
            currentSearchQuery = trimmedQuery
            currentSearchPage = 1
            currentSearchList.clear()
            _searchState.value = SearchUiState.Loading
            try {
                val list = repository.searchWallpapers(currentSearchQuery, currentSearchPage, saveHistory = false)
                currentSearchList.addAll(list)
                _searchState.value = SearchUiState.Success(
                    list = currentSearchList.toList(),
                    query = currentSearchQuery,
                    page = currentSearchPage,
                    isEnd = list.isEmpty(),
                    notice = repository.getFeedStatusMessage()
                )
                preAnalyzeWallpapers(list)
                FluxaLog.d("Search '$currentSearchQuery' resolved: ${list.size} results")
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _searchState.value = SearchUiState.Error(e.message ?: "Search failed")
                    FluxaLog.e("Search failed: ${e.message}", e)
                }
            } finally {
                isFetchingSearch = false
            }
        }
    }

    fun preAnalyzeWallpapers(wallpapers: List<Wallpaper>) {
        preAnalyzer.preAnalyze(wallpapers)
    }

    /**
     * Fetch visually similar wallpapers using AI metadata if available, 
     * falling back to tag-based search.
     */
    fun fetchSimilar(wallpaper: Wallpaper) {
        viewModelScope.launch {
            _similarState.value = SimilarUiState.Loading
            try {
                val list = repository.getSimilarWallpapers(wallpaper)
                _similarState.value = SimilarUiState.Success(list)
                FluxaLog.d("Similar for ${wallpaper.id} resolved: ${list.size} results")
            } catch (e: Exception) {
                _similarState.value = SimilarUiState.Error(e.message ?: "Failed to load similar")
                FluxaLog.e("Similar fetch failed: ${e.message}", e)
            }
        }
    }

    /**
     * User feedback actions from the UI: "more like this" and "less like this".
     * These update local taste signals and (optionally) update the visible similar list.
     */
    fun moreLikeThis(wallpaper: Wallpaper) {
        viewModelScope.launch {
            try {
                // Treat as a positive signal — similar to SAVE
                repository.recordWallpaperAction(wallpaper, WallpaperAction.SAVE)
                // Optionally refresh recommendations in background
                fetchSimilar(wallpaper)
            } catch (e: Exception) {
                FluxaLog.e("moreLikeThis failed: ${e.message}", e)
            }
        }
    }

    fun lessLikeThis(wallpaper: Wallpaper) {
        viewModelScope.launch {
            try {
                // Negative signal — record SKIP and reduce tag weights
                repository.recordWallpaperAction(wallpaper, WallpaperAction.SKIP)
                // Remove the item from the currently exposed similar list where possible
                val current = _similarState.value
                if (current is SimilarUiState.Success) {
                    val filtered = current.list.filterNot { it.id == wallpaper.id }
                    _similarState.value = SimilarUiState.Success(filtered)
                }
            } catch (e: Exception) {
                FluxaLog.e("lessLikeThis failed: ${e.message}", e)
            }
        }
    }

    /**
     * Legacy tag-based search fallback (kept for compatibility or specific cases).
     */
    fun fetchSimilarByTags(tags: List<String>) {
        // Words that carry no visual/descriptive meaning for recommendations
        val providerNoise = setOf("unsplash", "pexels", "pixabay", "pinterest")
        val genericNoise = setOf(
            "photo", "photography", "wallpaper", "curated", "image",
            "background", "hd", "4k", "8k", "download", "free", "best",
            "top", "new", "popular", "trending", "awesome", "cool", "nice"
        )

        // Level 1+2: extract meaningful descriptors
        val meaningful = tags
            .map { it.lowercase().trim() }
            .filter { it.length >= 2 && it !in providerNoise && it !in genericNoise }
            .distinct()

        val query = when {
            meaningful.size >= 2 -> "${meaningful[0]} ${meaningful[1]}"
            meaningful.size == 1 -> meaningful[0]
            else -> "trending wallpaper"  // Level 3: ultimate fallback
        }

        viewModelScope.launch {
            _similarState.value = SimilarUiState.Loading
            try {
                val list = repository.searchWallpapers(query, 1)
                _similarState.value = SimilarUiState.Success(list)
                FluxaLog.d("Similar for '$query' (from tags: $tags → meaningful: $meaningful): ${list.size} results")
            } catch (e: Exception) {
                _similarState.value = SimilarUiState.Error(e.message ?: "Failed to load similar")
                FluxaLog.e("Similar fetch failed: ${e.message}", e)
            }
        }
    }

    fun fetchNextSearchPage() {
        val currentState = _searchState.value
        if (currentState !is SearchUiState.Success) return
        if (currentState.isEnd || currentState.isAppending || isFetchingSearch || currentState.notice?.startsWith("Offline") == true) return

        viewModelScope.launch {
            isFetchingSearch = true
            currentSearchPage++
            _searchState.value = currentState.copy(isAppending = true)
            try {
                val list = repository.searchWallpapers(currentSearchQuery, currentSearchPage)
                currentSearchList.addAll(list)
                _searchState.value = SearchUiState.Success(
                    list = currentSearchList.toList(),
                    query = currentSearchQuery,
                    page = currentSearchPage,
                    isEnd = list.isEmpty(),
                    notice = repository.getFeedStatusMessage()
                )
                preAnalyzeWallpapers(list)
                FluxaLog.d("Search page $currentSearchPage loaded: ${list.size} results")
            } catch (e: Exception) {
                currentSearchPage--
                FluxaLog.e("Search pagination failed: ${e.message}", e)
                _searchState.value = currentState.copy(isAppending = false)
            } finally {
                isFetchingSearch = false
            }
        }
    }

    // Extended set-as-wallpaper flow with progress, cancellation
    fun setAsDeviceWallpaper(
        wallpaper: Wallpaper,
        targetOverride: SlideshowTarget? = null,
        onProgress: ((Float) -> Unit)? = null,
        onComplete: (Boolean) -> Unit
    ) {
        // Capture the target NOW to avoid race conditions if _slideshowTargets changes before coroutine execution
        val target = targetOverride ?: SlideshowTarget.fromDisplayNameOrDefault(_slideshowTargets.value)
        
        val job = viewModelScope.launch {
            try {
                // Step 1: download the wallpaper if not already cached
                var path = repository.getCachedPath(wallpaper.id)
                if (path == null) {
                    onProgress?.invoke(0.1f)
                    path = repository.downloadWallpaperToCache(wallpaper) { progress ->
                        onProgress?.invoke(0.1f + progress * 0.7f)
                    }
                }
                if (path == null) {
                    withContext(Dispatchers.Main) { onComplete(false) }
                    return@launch
                }

                onProgress?.invoke(0.9f)

                // Step 2: decode bitmap with memory safety
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapUtils.safeDecodeBitmap(path)
                }
                if (bitmap == null) {
                    FluxaLog.e("Failed to decode bitmap from $path for wallpaper ${wallpaper.id}")
                    withContext(Dispatchers.Main) { onComplete(false) }
                    return@launch
                }

                // Step 3: apply to wallpaper manager
                val wallpaperManager = WallpaperManager.getInstance(app)

                when (target) {
                    SlideshowTarget.HOME_SCREEN -> {
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    }
                    SlideshowTarget.LOCK_SCREEN -> {
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                    }
                    SlideshowTarget.BOTH -> {
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                    }
                }

                // Record set as positive learning action
                repository.recordWallpaperAction(wallpaper, WallpaperAction.SET)

                onProgress?.invoke(1.0f)
                withContext(Dispatchers.Main) {
                    onComplete(true)
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                FluxaLog.d("Set wallpaper cancelled for ${wallpaper.id}")
                withContext(Dispatchers.Main) { onComplete(false) }
            } catch (e: Exception) {
                FluxaLog.e("Set wallpaper failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
        downloadJobs[wallpaper.id]?.cancel()
        downloadJobs[wallpaper.id] = job
    }

    /** Legacy overload for backward compatibility with UI passing target as string. */
    fun setAsDeviceWallpaper(
        wallpaper: Wallpaper,
        target: String,
        onComplete: (Boolean) -> Unit
    ) {
        val targetEnum = when (target) {
            "Home Screen" -> SlideshowTarget.HOME_SCREEN
            "Lock Screen" -> SlideshowTarget.LOCK_SCREEN
            else -> SlideshowTarget.BOTH
        }
        setAsDeviceWallpaper(wallpaper, targetEnum, null, onComplete)
    }

    // Toggle pin
    fun togglePin(wallpaper: Wallpaper, pin: Boolean) {
        viewModelScope.launch {
            repository.togglePin(wallpaper, pin)
        }
    }

    // Download to local public storage (Downloads/Fluxa directory)
    fun downloadToLocalStorage(
        wallpaper: Wallpaper,
        onComplete: (Boolean) -> Unit = {}
    ): Job {
        val job = viewModelScope.launch {
            try {
                val path = repository.downloadToLocalStorage(wallpaper)
                onComplete(path != null)
            } catch (_: kotlinx.coroutines.CancellationException) {
                FluxaLog.d("Download to local storage cancelled for ${wallpaper.id}")
                onComplete(false)
            }
        }
        downloadJobs[wallpaper.id] = job
        job.invokeOnCompletion {
            downloadJobs.remove(wallpaper.id)
        }
        return job
    }

    // Download to private cache with progress
    fun downloadToPrivateCache(
        wallpaper: Wallpaper,
        onComplete: (Boolean) -> Unit = {}
    ): Job {
        val job = viewModelScope.launch {
            try {
                val path = repository.downloadWallpaperToCache(wallpaper)
                onComplete(path != null)
            } catch (_: kotlinx.coroutines.CancellationException) {
                FluxaLog.d("Download cancelled for ${wallpaper.id}")
                onComplete(false)
            }
        }
        downloadJobs[wallpaper.id] = job
        job.invokeOnCompletion {
            downloadJobs.remove(wallpaper.id)
        }
        return job
    }

    // Check pinned status with callback
    fun isPinned(id: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val res = repository.isPinned(id)
            onResult(res)
        }
    }

    // Record view duration for taste learning
    fun recordViewDuration(wallpaper: Wallpaper, durationSeconds: Long) {
        viewModelScope.launch {
            val action = when {
                durationSeconds < 2 -> WallpaperAction.SKIP
                durationSeconds >= 10 -> WallpaperAction.VIEW_LONG
                else -> return@launch
            }
            FluxaLog.d("Learning pref: User viewed wallpaper ${wallpaper.id} for $durationSeconds sec -> recording action $action")
            repository.recordWallpaperAction(wallpaper, action)
        }
    }

    // Toggle sources & parameters
    fun toggleSource(source: String, enabled: Boolean) {
        repository.setSourceToggle(source, enabled)
        when (source) {
            "unsplash" -> _sourceUnsplash.value = enabled
            "pexels" -> _sourcePexels.value = enabled
            "pixabay" -> _sourcePixabay.value = enabled
            "pinterest" -> _sourcePinterest.value = enabled
        }
        // Update diagnostics and refresh feed
        _sourceHealth.value = repository.getSourceHealth()
        refreshFeed()
    }

    // Collection management wrappers
    fun createCollection(name: String) {
        repository.createCollection(name)
        _collectionsState.value = repository.getCollections()
    }

    fun addWallpaperToCollection(name: String, wallpaper: Wallpaper) {
        viewModelScope.launch {
            repository.addToCollection(name, wallpaper.id)
            _collectionsState.value = repository.getCollections()
        }
    }

    fun removeWallpaperFromCollection(name: String, wallpaperId: String) {
        viewModelScope.launch {
            repository.removeFromCollection(name, wallpaperId)
            _collectionsState.value = repository.getCollections()
        }
    }

    fun fetchCollectionWallpapers(name: String, onResult: (List<Wallpaper>) -> Unit) {
        viewModelScope.launch {
            val list = repository.getCollectionWallpapers(name)
            onResult(list)
        }
    }

    /**
     * Download a small pack of wallpapers for offline use (downloads into private cache and updates cached list).
     */
    fun downloadPackForOffline(count: Int = 10, onProgress: ((Int, Int) -> Unit)? = null, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val list = repository.getCuratedFeed(1, darkTheme = isDarkTheme).take(count)
                var completed = 0
                for (wp in list) {
                    repository.downloadWallpaperToCache(wp)
                    completed++
                    onProgress?.invoke(completed, list.size)
                }
                onComplete(true)
            } catch (e: Exception) {
                FluxaLog.e("downloadPackForOffline failed: ${e.message}", e)
                onComplete(false)
            }
        }
    }

    /**
     * Fetch a simple 'For You' recommendations list and return via callback.
     */
    fun fetchForYou(limit: Int = 12, onResult: (List<Wallpaper>) -> Unit) {
        viewModelScope.launch {
            try {
                val recs = repository.getRecommendations(limit)
                onResult(recs)
            } catch (e: Exception) {
                FluxaLog.e("fetchForYou failed: ${e.message}", e)
                onResult(emptyList())
            }
        }
    }

    fun setCacheLimit(limit: Int) {
        repository.setCacheSizeLimit(limit)
        _cacheLimit.value = limit
    }

    fun setWifiOnly(enabled: Boolean) {
        repository.setWifiOnlyToggle(enabled)
        _wifiOnly.value = enabled
    }

    private val _slideshowSource = MutableStateFlow(repository.getSlideshowSource())
    val slideshowSource: StateFlow<String> = _slideshowSource.asStateFlow()

    fun setSlideshowSource(source: String) {
        repository.setSlideshowSource(source)
        _slideshowSource.value = source
        if (_slideshowEnabled.value) {
            scheduleSlideshowRotation()
        }
    }

    fun setSlideshowTargets(target: String) {
        repository.setSlideshowTargets(target)
        _slideshowTargets.value = target
        if (_slideshowEnabled.value) {
            scheduleSlideshowRotation()
        }
    }

    fun setSlideshowInterval(interval: String) {
        repository.setSlideshowInterval(interval)
        _slideshowInterval.value = interval
        if (_slideshowEnabled.value) {
            scheduleSlideshowRotation()
        }
    }

    fun setSlideshowEnabled(enabled: Boolean) {
        repository.setSlideshowEnabled(enabled)
        _slideshowEnabled.value = enabled
        if (enabled) {
            // Ensure all pinned wallpapers are cached before starting rotation
            viewModelScope.launch {
                repository.ensureAllPinnedWallpapersAreCached()
            }
            // Trigger immediate execution on toggle so user gets instant rotation feedback
            forceRotateSlideshowOnce()
            scheduleSlideshowRotation()
        } else {
            SlideshowWorker.cancelSlideshow(app)
        }
    }

    private fun scheduleSlideshowRotation() {
        SlideshowWorker.scheduleSlideshow(app, _slideshowInterval.value)
    }

    fun forceRotateSlideshowOnce() {
        Toast.makeText(app, "Auto-rotating wallpaper...", Toast.LENGTH_SHORT).show()
        SlideshowWorker.executeOnce(app)
    }

}
