package com.fluxawallpapers.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fluxawallpapers.app.data.network.Wallpaper
import com.fluxawallpapers.app.ui.viewmodel.WallpaperViewModel

@Composable
fun ForYouFeed(viewModel: WallpaperViewModel) {
    var items by remember { mutableStateOf<List<Wallpaper>>(emptyList()) }
    LaunchedEffect(Unit) {
        viewModel.fetchForYou(12) { list -> items = list }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("For You", style = MaterialTheme.typography.titleLarge)
        }

        item {
            if (items.isEmpty()) {
                Text("We couldn't find personalized recommendations yet. Interact with wallpapers to improve suggestions.", fontSize = 12.sp)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id }) { wp ->
                        AsyncImage(model = wp.thumbnailUrl, contentDescription = wp.author, modifier = Modifier.size(140.dp))
                    }
                }
            }
        }
    }
}

