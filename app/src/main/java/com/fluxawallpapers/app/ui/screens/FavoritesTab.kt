package com.fluxawallpapers.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluxawallpapers.app.data.network.Wallpaper
import com.fluxawallpapers.app.ui.components.WallpaperGrid
import com.fluxawallpapers.app.ui.viewmodel.WallpaperViewModel

@Composable
fun FavoritesTabContent(
    viewModel: WallpaperViewModel,
    onWallpaperClick: (Int, List<Wallpaper>) -> Unit
) {
    val pinnedState by viewModel.pinnedWallpapers.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "PINNED WALLPAPERS",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (pinnedState.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.BookmarkBorder,
                    contentDescription = "No pins",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "No pinned wallpapers yet",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Browse wallpapers, open the details view, and tap the bookmark icon to secure them in local caching sandbox securely.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        } else {
            WallpaperGrid(
                list = pinnedState,
                onWallpaperClick = { index -> onWallpaperClick(index, pinnedState) },
                onEndReached = {},
                onPinClick = { wp -> viewModel.togglePin(wp, !wp.isPinned) }
            )
        }
    }
}
