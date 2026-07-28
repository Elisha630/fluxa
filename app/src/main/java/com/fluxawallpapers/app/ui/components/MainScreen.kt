package com.fluxawallpapers.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.fluxawallpapers.app.data.network.Wallpaper
import com.fluxawallpapers.app.ui.screens.*
import com.fluxawallpapers.app.ui.viewmodel.WallpaperViewModel

/**
 * Root composable that orchestrates tab-based navigation and the detail overlay.
 *
 * Tab contents are split into dedicated screen files:
 *   - [HomeTabContent]      – Feed + Search
 *   - [DiscoverTabContent]  – Category grid
 *   - [CategoryResultsContent] – Category search results
 *   - [FavoritesTabContent] – Pinned wallpapers
 *   - [SettingsTabContent]  – App settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: WallpaperViewModel) {
    var activeTab by remember { mutableStateOf("home") }
    var detailWallpaperIndex by remember { mutableStateOf<Int?>(null) }
    var detailList by remember { mutableStateOf<List<Wallpaper>>(emptyList()) }

    val connectionType by viewModel.connectionType.collectAsState()

    // Manage separate nested screen inside Discover: "Categories Index" vs "Category Results"
    var activeCategory by remember { mutableStateOf<String?>(null) }

    // Intercept system back press: detail → list, category results → categories,
    // non-home tabs → home, search results → feed
    BackHandler(enabled = detailWallpaperIndex != null) {
        detailWallpaperIndex = null
    }

    BackHandler(enabled = activeCategory != null && detailWallpaperIndex == null) {
        activeCategory = null
    }

    BackHandler(enabled = activeTab != "home" && activeCategory == null && detailWallpaperIndex == null) {
        activeTab = "home"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Immersive Header Banner
            AppHeader(connectionType = connectionType)

            // Dynamic Content Pane based on active tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Main Switcher
                when (activeTab) {
                    "home" -> {
                        HomeTabContent(
                            viewModel = viewModel,
                            onWallpaperClick = { index, list ->
                                detailWallpaperIndex = index
                                detailList = list
                            }
                        )
                    }
                    "discover" -> {
                        if (activeCategory == null) {
                            DiscoverTabContent(
                                onCategoryClick = { category ->
                                    activeCategory = category
                                    viewModel.executeSearch(category)
                                }
                            )
                        } else {
                            CategoryResultsContent(
                                category = activeCategory!!,
                                viewModel = viewModel,
                                onBack = { activeCategory = null },
                                onWallpaperClick = { index, list ->
                                    detailWallpaperIndex = index
                                    detailList = list
                                }
                            )
                        }
                    }
                    "favorites" -> {
                        FavoritesTabContent(
                            viewModel = viewModel,
                            onWallpaperClick = { index, list ->
                                detailWallpaperIndex = index
                                detailList = list
                            }
                        )
                    }
                    "settings" -> {
                        SettingsTabContent(viewModel = viewModel)
                    }
                }
            }

            // Centralized Bottom Navigation Bar
            FluxaNavigationBar(
                activeTab = activeTab,
                onTabSelect = { tab ->
                    activeTab = tab
                    // reset category query state when leaving discover
                    if (tab != "discover") {
                        activeCategory = null
                    }
                }
            )
        }

        // Full-screen Slide show Detail Overlay
        AnimatedVisibility(
            visible = detailWallpaperIndex != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            if (detailWallpaperIndex != null) {
                WallpaperDetailView(
                    initialIndex = detailWallpaperIndex!!,
                    wallpapers = detailList,
                    viewModel = viewModel,
                    onBack = {
                        detailWallpaperIndex = null
                    }
                )
            }
        }
    }
}
