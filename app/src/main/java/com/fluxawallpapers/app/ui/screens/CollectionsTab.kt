package com.fluxawallpapers.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun CollectionsTab(viewModel: WallpaperViewModel) {
    val collections by viewModel.collectionsState.collectAsState()
    var newName by remember { mutableStateOf("") }
    var selectedCollection by remember { mutableStateOf<String?>(null) }
    var collectionItems by remember { mutableStateOf<List<Wallpaper>>(emptyList()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Collections", style = MaterialTheme.typography.titleLarge)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("New collection") }, modifier = Modifier.weight(1f))
            Button(onClick = {
                if (newName.isNotBlank()) {
                    viewModel.createCollection(newName.trim())
                    newName = ""
                }
            }) { Text("Create") }
        }

        if (collections.isEmpty()) {
            Text("No collections yet. Create one and add pinned wallpapers to it.", fontSize = 12.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                collections.forEach { (name, ids) ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(name, style = MaterialTheme.typography.titleMedium)
                                Text("${ids.size} items", fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { selectedCollection = name; viewModel.fetchCollectionWallpapers(name) { collectionItems = it } }) { Text("View") }
                            }
                        }
                    }
                }
            }
        }

        selectedCollection?.let { colName ->
            Spacer(Modifier.height(8.dp))
            Text("Contents of $colName", style = MaterialTheme.typography.titleSmall)
            if (collectionItems.isEmpty()) {
                Text("No items in this collection.")
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(collectionItems, key = { it.id }) { wp ->
                        AsyncImage(model = wp.thumbnailUrl, contentDescription = wp.author, modifier = Modifier.size(120.dp).clickable {
                            /* no-op for now */
                        })
                    }
                }
            }
        }
    }
}

