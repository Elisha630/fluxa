package com.fluxawallpapers.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fluxawallpapers.app.data.network.Wallpaper
import com.fluxawallpapers.app.ui.theme.DarkSurface
import com.fluxawallpapers.app.ui.theme.DarkSurfaceVariant

// ---- Grid ----

@Composable
fun WallpaperGrid(
    list: List<Wallpaper>,
    onWallpaperClick: (Int) -> Unit,
    onEndReached: () -> Unit,
    isLoadingMore: Boolean = false,
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    onPinClick: (Wallpaper) -> Unit = {},
    // Authoritative set of pinned wallpaper IDs (e.g. sourced from viewModel.pinnedWallpapers).
    // Feed/search items come straight from the network APIs and always have isPinned = false,
    // so without this the pin icon would silently revert to "unpinned" on every refresh or
    // pagination even for wallpapers the user already pinned. When null, falls back to each
    // item's own isPinned flag (correct for lists already sourced from the DB, e.g. Favorites).
    pinnedIds: Set<String>? = null
) {
    // Local optimistic overrides for instant UI feedback on tap, before the DB write round-trips
    // back through pinnedIds.
    val optimisticOverrides = remember { mutableStateMapOf<String, Boolean>() }
    // Once the authoritative source confirms an override's value, drop the override so it
    // doesn't linger and mask genuine future external changes.
    LaunchedEffect(pinnedIds) {
        if (pinnedIds != null) {
            val confirmed = optimisticOverrides.keys.filter { id -> optimisticOverrides[id] == (id in pinnedIds) }
            confirmed.forEach { optimisticOverrides.remove(it) }
        }
    }
    val staggeredGridState = gridState

    // Detect scroll to end for pagination
    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = staggeredGridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && (lastVisibleItem >= totalItems - 5)
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isLoadingMore) {
            onEndReached()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
            modifier = Modifier.fillMaxSize(),
            state = staggeredGridState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp
        ) {
            items(list.size) { index ->
                val wallpaper = list[index]
                val basePinned = pinnedIds?.contains(wallpaper.id) ?: wallpaper.isPinned
                val isPinnedLocal = optimisticOverrides[wallpaper.id] ?: basePinned

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(19.dp))
                        .clickable { onWallpaperClick(index) }
                        .testTag("wallpaper_card_$index")
                ) {
                    AsyncImage(
                        model = wallpaper.thumbnailUrl,
                        contentDescription = "by ${wallpaper.author}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Pin toggle icon — top-right corner
                    IconButton(
                        onClick = {
                            optimisticOverrides[wallpaper.id] = !isPinnedLocal
                            onPinClick(wallpaper)
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(30.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPinnedLocal) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (isPinnedLocal) "Unpin" else "Pin wallpaper",
                            tint = if (isPinnedLocal) Color(0xFFFFD700) else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                                )
                            )
                    )

                    // Source badge + pin indicator overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SourceBadge(wallpaper.source)
                            if (isPinnedLocal) {
                                SourceBadge("PINNED")
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isLoadingMore,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
        ) {
            Surface(
                color = DarkSurface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, DarkSurfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Text("Loading more", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---- Notice Banner ----

@Composable
fun NoticeBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

// ---- Feed Refresh Row ----

@Composable
fun FeedRefreshRow(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onRefresh,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Refresh", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---- Source Badge ----

@Composable
fun SourceBadge(source: String) {
    StatusBadge(source.uppercase(), Icons.Filled.Image)
}

// ---- Status Badge ----

@Composable
fun StatusBadge(label: String, icon: ImageVector) {
    Surface(
        color = Color.Black.copy(alpha = 0.58f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---- Empty State Placeholder ----

@Composable
fun EmptyStatePlaceholder(msg: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.ImageNotSupported,
            contentDescription = "No images",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            modifier = Modifier.size(54.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = msg,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
