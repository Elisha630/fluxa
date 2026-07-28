package com.fluxawallpapers.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
//import androidx.compose.animation.fadeIn
//import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import com.fluxawallpapers.app.ui.theme.NeonBorder
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import com.fluxawallpapers.app.data.repository.PredictionType
import com.fluxawallpapers.app.data.repository.SearchPredictionItem
import androidx.compose.material3.*
import androidx.compose.runtime.*
//import kotlinx.coroutines.flow.*
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
//import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
//import androidx.compose.ui.text.input.KeyboardType
//import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluxawallpapers.app.data.network.Wallpaper
import com.fluxawallpapers.app.ui.components.EmptyStatePlaceholder
import com.fluxawallpapers.app.ui.components.FeedRefreshRow
import com.fluxawallpapers.app.ui.components.NoticeBanner
import com.fluxawallpapers.app.ui.components.WallpaperGrid
import com.fluxawallpapers.app.ui.viewmodel.FeedUiState
import com.fluxawallpapers.app.ui.viewmodel.SearchUiState
import com.fluxawallpapers.app.ui.viewmodel.WallpaperViewModel
import com.fluxawallpapers.app.ui.theme.DarkSurface
import com.fluxawallpapers.app.ui.theme.DarkSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTabContent(
    viewModel: WallpaperViewModel,
    onWallpaperClick: (Int, List<Wallpaper>) -> Unit
) {
    val feedState by viewModel.feedState.collectAsState()
    val searchState by viewModel.searchState.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchPredictionItems by viewModel.searchPredictionItems.collectAsState()
    val previewThumbnails by viewModel.searchPreviewThumbnails.collectAsState()

    var isSearchFocused by remember { mutableStateOf(false) }
    var isSearchingActive by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    // System back: from search results → back to feed
    BackHandler(enabled = isSearchingActive) {
        isSearchingActive = false
        viewModel.updateSearchQuery("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Search Bar Layer
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                viewModel.updateSearchQuery(it)
                if (it.isEmpty()) {
                    isSearchingActive = false
                }
            },
            placeholder = {
                Text(
                    "Search wallpapers (e.g., Cyberpunk, OLED)…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    softWrap = false
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .height(56.dp)
                .onFocusChanged { focusState -> isSearchFocused = focusState.isFocused }
                .testTag("search_bar_input"),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = DarkSurfaceVariant,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            singleLine = true,
            maxLines = 1,
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery(""); isSearchingActive = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color.White)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (searchQuery.isNotBlank()) {
                        viewModel.executeSearch(searchQuery.trim(), saveToHistory = true)
                        isSearchingActive = true
                        focusManager.clearFocus()
                    }
                }
            )
        )

        // Search Predictions Dropdown Overlay (Google & Pinterest predictive autocomplete)
        AnimatedVisibility(
            visible = searchPredictionItems.isNotEmpty() && isSearchFocused && !isSearchingActive
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Dropdown Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "EXPLORE & RECENT SEARCHES" else "PREDICTIVE SUGGESTIONS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )

                        if (searchPredictionItems.any { it.type == PredictionType.RECENT }) {
                            Text(
                                text = "Clear History",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier.clickable { viewModel.clearAllSearchHistory() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp)
                    ) {
                        items(searchPredictionItems) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.updateSearchQuery(item.query)
                                        viewModel.executeSearch(item.query, saveToHistory = true)
                                        isSearchingActive = true
                                        focusManager.clearFocus()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val (icon, tint) = when (item.type) {
                                    PredictionType.RECENT -> Icons.Filled.History to Color(0xFFFFB74D)
                                    PredictionType.LIVE_SUGGESTION -> Icons.AutoMirrored.Filled.TrendingUp to MaterialTheme.colorScheme.primary
                                    PredictionType.TRENDING_TAG -> Icons.Filled.Whatshot to Color(0xFFFF5252)
                                    PredictionType.CATEGORY -> Icons.Filled.GridView to Color(0xFF64B5F6)
                                }

                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(18.dp)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.query,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    item.subtitle?.let { sub ->
                                        Text(
                                            text = sub,
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                if (item.type == PredictionType.RECENT) {
                                    IconButton(
                                        onClick = { viewModel.deleteSearchHistoryItem(item.query) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Delete from history",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color.Gray.copy(alpha = 0.5f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Instant Visual Thumbnail Preview Strip
                    if (previewThumbnails.isNotEmpty()) {
                        HorizontalDivider(
                            color = DarkSurfaceVariant,
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "INSTANT PREVIEWS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "${previewThumbnails.size} suggestions",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            previewThumbnails.take(6).forEachIndexed { idx, wp ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, NeonBorder.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .size(width = 72.dp, height = 96.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            onWallpaperClick(idx, previewThumbnails)
                                        }
                                        .testTag("preview_thumbnail_$idx")
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = wp.thumbnailUrl,
                                            contentDescription = "Preview wallpaper by ${wp.author}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(28.dp)
                                                .align(Alignment.BottomCenter)
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                                    )
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Main content area: either search results or curated feed
        if (isSearchingActive) {
            Box(modifier = Modifier.weight(1f)) {
                when (val result = searchState) {
                    is SearchUiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is SearchUiState.Success -> {
                        Column(Modifier.fillMaxSize()) {
                            result.notice?.let { NoticeBanner(it) }
                            if (result.list.isEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    EmptyStatePlaceholder("No wallpapers found for \"${result.query}\".")
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Popular searches to try:",
                                        fontSize = 13.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("Cyberpunk", "OLED Dark", "Minimalist", "4K Nature", "Anime", "Space").forEach { suggestion ->
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.updateSearchQuery(suggestion)
                                                    viewModel.executeSearch(suggestion)
                                                },
                                                shape = RoundedCornerShape(14.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Text("#$suggestion", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            } else {
                                WallpaperGrid(
                                    list = result.list,
                                    onWallpaperClick = { index -> onWallpaperClick(index, result.list) },
                                    onEndReached = { viewModel.fetchNextSearchPage() },
                                    isLoadingMore = result.isAppending,
                                    onPinClick = { wp -> viewModel.togglePin(wp, !wp.isPinned) }
                                )
                            }
                        }
                    }
                    is SearchUiState.Error -> {
                        EmptyStatePlaceholder("Search failed: ${result.message}\nPlease check your network connection or API settings.")
                    }
                    else -> {}
                }
            }
        } else {
            // Curated Feeds Grid with pull-to-refresh
            Box(modifier = Modifier.weight(1f)) {
                var isRefreshing by remember { mutableStateOf(false) }
                var hasLoadedOnce by remember { mutableStateOf(false) }
                var cachedList by remember { mutableStateOf<List<Wallpaper>>(emptyList()) }
                val gridState = rememberLazyStaggeredGridState()

                LaunchedEffect(feedState) {
                    when (feedState) {
                        is FeedUiState.Success -> {
                            hasLoadedOnce = true
                            isRefreshing = false
                            cachedList = (feedState as FeedUiState.Success).list
                        }
                        is FeedUiState.Error -> {
                            hasLoadedOnce = true
                            isRefreshing = false
                        }
                        else -> {}
                    }
                }

                val displayList = when (feedState) {
                    is FeedUiState.Success -> (feedState as FeedUiState.Success).list
                    else -> cachedList
                }
                val displayNotice = (feedState as? FeedUiState.Success)?.notice
                val displayIsAppending = (feedState as? FeedUiState.Success)?.isAppending ?: false

                if (!hasLoadedOnce && feedState is FeedUiState.Loading) {
                    // Initial full-screen loading
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (feedState is FeedUiState.Error) {
                    Column(Modifier.fillMaxSize()) {
                        FeedRefreshRow(onRefresh = { viewModel.refreshFeed() })
                        EmptyStatePlaceholder("Could not load wallpapers: ${(feedState as FeedUiState.Error).message}")
                    }
                } else if (displayList.isEmpty() && !isRefreshing) {
                    Column(Modifier.fillMaxSize()) {
                        displayNotice?.let { NoticeBanner(it) }
                        EmptyStatePlaceholder("No wallpapers are available yet. Connect to the internet, enable an API source, or cache wallpapers for offline use.")
                    }
                } else {
                    val pullToRefreshState = rememberPullToRefreshState()
                    val density = LocalDensity.current

                    // Shared animation for icon rotation
                    val infiniteTransition = rememberInfiniteTransition(label = "refreshRotation")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "rotation"
                    )

                    val maxTranslationPx = with(density) { 80.dp.toPx() }
                    val animatedTranslationY by animateFloatAsState(
                        targetValue = if (isRefreshing) 0f else pullToRefreshState.distanceFraction * maxTranslationPx,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "translationY"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pullToRefresh(
                                state = pullToRefreshState,
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    isRefreshing = true
                                    viewModel.refreshFeed()
                                }
                            )
                    ) {
                        // Content translates with pull gesture
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationY = animatedTranslationY }
                        ) {
                            Column(Modifier.fillMaxSize()) {
                                displayNotice?.let { NoticeBanner(it) }
                                WallpaperGrid(
                                    list = displayList,
                                    onWallpaperClick = { index -> onWallpaperClick(index, displayList) },
                                    onEndReached = { viewModel.fetchNextFeedPage() },
                                    isLoadingMore = displayIsAppending,
                                    gridState = gridState,
                                    onPinClick = { wp -> viewModel.togglePin(wp, !wp.isPinned) }
                                )
                            }

                            // Refresh icon is only shown when pulling or actively refreshing
                            val showRefreshIcon = isRefreshing || pullToRefreshState.distanceFraction > 0f

                            if (showRefreshIcon) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 12.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    color = DarkSurface.copy(alpha = 0.9f),
                                    shadowElevation = 6.dp
                                ) {
                                    Icon(
                                        Icons.Filled.Refresh,
                                        contentDescription = "Refresh feed",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .size(20.dp)
                                            .graphicsLayer {
                                                if (isRefreshing) {
                                                    rotationZ = rotation
                                                } else {
                                                    rotationZ = pullToRefreshState.distanceFraction * 360f
                                                }
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
