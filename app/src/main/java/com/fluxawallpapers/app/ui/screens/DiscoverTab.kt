package com.fluxawallpapers.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fluxawallpapers.app.data.network.Wallpaper
import com.fluxawallpapers.app.ui.components.EmptyStatePlaceholder
import com.fluxawallpapers.app.ui.components.NoticeBanner
import com.fluxawallpapers.app.ui.components.WallpaperGrid
import com.fluxawallpapers.app.ui.viewmodel.WallpaperViewModel

@Composable
fun DiscoverTabContent(onCategoryClick: (String) -> Unit) {
    val categories = listOf(
        Pair("Dark", "https://images.unsplash.com/photo-1502134249126-9f3755a50d78?q=80&w=400"),
        Pair("Nature", "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?q=80&w=400"),
        Pair("Minimal", "https://images.unsplash.com/photo-1541701494587-cb58502866ab?q=80&w=400"),
        Pair("Abstract", "https://images.unsplash.com/photo-1579783902614-a3fb3927b6a5?q=80&w=400"),
        Pair("Cyberpunk", "https://images.unsplash.com/photo-1535223289827-42f1e9919769?q=80&w=400"),
        Pair("Sci-Fi", "https://images.unsplash.com/photo-1518770660439-4636190af475?q=80&w=400"),
        Pair("Architecture", "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?q=80&w=400"),
        Pair("Space", "https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=400"),
        Pair("Textures", "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?q=80&w=400"),
        Pair("Gradient", "https://images.unsplash.com/photo-1558591710-4b4a1ae0f04d?q=80&w=400")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            "EXPLORE VERTICALS",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(categories) { category ->
                Box(
                    modifier = Modifier
                        .height(130.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onCategoryClick(category.first) }
                        .testTag("category_card_${category.first.lowercase()}")
                ) {
                    AsyncImage(
                        model = category.second,
                        contentDescription = "Category ${category.first}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay card gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                )
                            )
                    )

                    Text(
                        text = category.first,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryResultsContent(
    category: String,
    viewModel: WallpaperViewModel,
    onBack: () -> Unit,
    onWallpaperClick: (Int, List<Wallpaper>) -> Unit
) {
    val searchState by viewModel.searchState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("category_back")) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$category Wallpapers",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (val state = searchState) {
                is com.fluxawallpapers.app.ui.viewmodel.SearchUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is com.fluxawallpapers.app.ui.viewmodel.SearchUiState.Success -> {
                    Column(Modifier.fillMaxSize()) {
                        state.notice?.let { NoticeBanner(it) }
                        if (state.list.isEmpty()) {
                            EmptyStatePlaceholder("No $category wallpapers found.")
                        } else {
                            WallpaperGrid(
                                list = state.list,
                                onWallpaperClick = { index -> onWallpaperClick(index, state.list) },
                                onEndReached = { viewModel.fetchNextSearchPage() },
                                isLoadingMore = state.isAppending,
                                onPinClick = { wp -> viewModel.togglePin(wp, !wp.isPinned) }
                            )
                        }
                    }
                }
                is com.fluxawallpapers.app.ui.viewmodel.SearchUiState.Error -> {
                    EmptyStatePlaceholder("Could not load $category wallpapers: ${state.message}")
                }
                else -> {}
            }
        }
    }
}
