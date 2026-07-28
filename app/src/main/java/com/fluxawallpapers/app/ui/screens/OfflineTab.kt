package com.fluxawallpapers.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fluxawallpapers.app.data.network.Wallpaper
import com.fluxawallpapers.app.ui.viewmodel.WallpaperViewModel

@Composable
fun OfflineTab(viewModel: WallpaperViewModel) {
    val cached by viewModel.cachedWallpapers.collectAsState()
    var progressText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Offline Library", style = MaterialTheme.typography.titleLarge)

        Text("Cached wallpapers on this device: ${cached.size}", fontSize = 12.sp)

        Button(onClick = {
            progressText = "Preparing download..."
            viewModel.downloadPackForOffline(10, onProgress = { done, total -> progressText = "Downloaded $done/$total" }) { success ->
                progressText = if (success) "Pack downloaded" else "Download failed"
            }
        }) {
            Text("Download pack for offline (10)")
        }

        if (progressText.isNotEmpty()) Text(progressText, fontSize = 12.sp)

        Spacer(Modifier.height(8.dp))

        if (cached.isEmpty()) {
            Text("No cached wallpapers yet. Pin wallpapers or run 'Download pack' to populate.")
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cached, key = { it.id }) { wp ->
                    AsyncImage(model = wp.thumbnailUrl, contentDescription = wp.author, modifier = Modifier.size(120.dp))
                }
            }
        }
    }
}

