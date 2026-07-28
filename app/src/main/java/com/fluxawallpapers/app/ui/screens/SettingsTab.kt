package com.fluxawallpapers.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluxawallpapers.app.ui.viewmodel.WallpaperViewModel
import com.fluxawallpapers.app.ui.theme.DarkSurface
import com.fluxawallpapers.app.ui.theme.DarkSurfaceVariant

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTabContent(viewModel: WallpaperViewModel) {
    // Collect from ViewModel StateFlows
    val unsplashEnabled by viewModel.sourceUnsplash.collectAsState()
    val pexelsEnabled by viewModel.sourcePexels.collectAsState()
    val pixabayEnabled by viewModel.sourcePixabay.collectAsState()
    val pinterestEnabled by viewModel.sourcePinterest.collectAsState()
    val sourceHealth by viewModel.sourceHealth.collectAsState()
    val currentLimit by viewModel.cacheLimit.collectAsState()
    val isWifiOnlyEnabled by viewModel.wifiOnly.collectAsState()

    val slideshowEnabled by viewModel.slideshowEnabled.collectAsState()
    val slideshowInterval by viewModel.slideshowInterval.collectAsState()
    val slideshowTargets by viewModel.slideshowTargets.collectAsState()
    val slideshowSource by viewModel.slideshowSource.collectAsState()
    val localCacheSize by viewModel.cachedWallpapers.collectAsState()

    var showIntervalDropdown by remember { mutableStateOf(false) }
    var showTargetDropdown by remember { mutableStateOf(false) }
    var showSourceDropdown by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Slideshow Auto-Rotation (WorkManager)
        item {
            Text(
                "SLIDESHOW AUTO-ROTATION (ANDROID)",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DarkSurfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Toggle Slideshow
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Rotation Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Automatically rotate wallpaper in background", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = slideshowEnabled,
                            onCheckedChange = { viewModel.setSlideshowEnabled(it) },
                            modifier = Modifier.testTag("slideshow_toggle_switch")
                        )
                    }

                    if (slideshowEnabled) {
                        Spacer(Modifier.height(16.dp))

                        // Target Selector: Home Screen, Lock Screen or Both
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Target Screens", fontWeight = FontWeight.Medium, fontSize = 13.sp)

                            Box {
                                OutlinedButton(
                                    onClick = { showTargetDropdown = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, DarkSurfaceVariant)
                                ) {
                                    Text(slideshowTargets, fontWeight = FontWeight.Bold)
                                }

                                DropdownMenu(
                                    expanded = showTargetDropdown,
                                    onDismissRequest = { showTargetDropdown = false }
                                ) {
                                    listOf("Home Screen", "Lock Screen", "Both").forEach { target ->
                                        DropdownMenuItem(
                                            text = { Text(target, color = Color.White) },
                                            onClick = {
                                                viewModel.setSlideshowTargets(target)
                                                showTargetDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Interval Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Set Rotation Every", fontWeight = FontWeight.Medium, fontSize = 13.sp)

                            Box {
                                OutlinedButton(
                                    onClick = { showIntervalDropdown = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, DarkSurfaceVariant)
                                ) {
                                    Text(slideshowInterval, fontWeight = FontWeight.Bold)
                                }

                                DropdownMenu(
                                    expanded = showIntervalDropdown,
                                    onDismissRequest = { showIntervalDropdown = false }
                                ) {
                                    listOf("5 min", "15 min", "30 min", "1 hour", "6 hours", "12 hours", "Daily").forEach { interval ->
                                        DropdownMenuItem(
                                            text = { Text(interval, color = Color.White) },
                                            onClick = {
                                                viewModel.setSlideshowInterval(interval)
                                                showIntervalDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Rotation Pool Source Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Rotation Pool Source", fontWeight = FontWeight.Medium, fontSize = 13.sp)

                            Box {
                                OutlinedButton(
                                    onClick = { showSourceDropdown = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, DarkSurfaceVariant)
                                ) {
                                    Text(slideshowSource, fontWeight = FontWeight.Bold)
                                }

                                DropdownMenu(
                                    expanded = showSourceDropdown,
                                    onDismissRequest = { showSourceDropdown = false }
                                ) {
                                    listOf("Mixed (All Sources)", "Favorites & Collections", "Curated Online Feed").forEach { source ->
                                        DropdownMenuItem(
                                            text = { Text(source, color = Color.White) },
                                            onClick = {
                                                viewModel.setSlideshowSource(source)
                                                showSourceDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Rotate Now Action Button
                        Button(
                            onClick = { viewModel.forceRotateSlideshowOnce() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("rotate_now_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Rotate Wallpaper Now", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                    }
                }
            }
        }

        // Section: In-App Private Sandbox Cache
        item {
            Text(
                "IN-APP PRIVATE SANDBOX CACHE",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DarkSurfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Cache Status Summary
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Current Sandboxed Cache", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Badge(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black) {
                            Text("${localCacheSize.size} wallpapers", fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                        }
                    }

                    Text(
                        "All wallpapers stay inside Fluxa's secure folder and never clutter your central image system gallery unless pinned or set.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                    )

                    HorizontalDivider(color = DarkSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

                    // Wi-Fi Only toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wi-Fi Only Loading", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Only download wallpapers when connected to Wi-Fi", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isWifiOnlyEnabled,
                            onCheckedChange = { viewModel.setWifiOnly(it) },
                            modifier = Modifier.testTag("wifi_only_toggle_switch")
                        )
                    }

                    HorizontalDivider(color = DarkSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

                    // LRU Cache Slider Limit
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Active LRU Cache Limit", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("$currentLimit items", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = currentLimit.toFloat(),
                            onValueChange = { viewModel.setCacheLimit(it.toInt()) },
                            valueRange = 50f..100f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("cache_limit_slider")
                        )
                        Text(
                            "Evicts oldest unpinned cached wallpapers dynamically once the limit has been reached.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section: Wallpaper API Sources
        item {
            Text(
                "WALLPAPER API INTEGRATIONS",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DarkSurfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // API Source Toggle: Unsplash
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Unsplash API", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            val status = sourceHealth["Unsplash"] ?: "unknown"
                            Text(status.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = when (status) {
                                "ready" -> Color(0xFF4CAF50)
                                "missing key" -> Color(0xFFFFA000)
                                "disabled & missing key" -> Color(0xFFF44336)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            })
                        }
                        Switch(
                            checked = unsplashEnabled,
                            onCheckedChange = { viewModel.toggleSource("unsplash", it) },
                            modifier = Modifier.testTag("toggle_unsplash_source")
                        )
                    }

                    // API Source Toggle: Pexels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Pexels API", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            val status = sourceHealth["Pexels"] ?: "unknown"
                            Text(status.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = when (status) {
                                "ready" -> Color(0xFF4CAF50)
                                "missing key" -> Color(0xFFFFA000)
                                "disabled & missing key" -> Color(0xFFF44336)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            })
                        }
                        Switch(
                            checked = pexelsEnabled,
                            onCheckedChange = { viewModel.toggleSource("pexels", it) },
                            modifier = Modifier.testTag("toggle_pexels_source")
                        )
                    }

                    // API Source Toggle: Pixabay
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Pixabay API", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            val status = sourceHealth["Pixabay"] ?: "unknown"
                            Text(status.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = when (status) {
                                "ready" -> Color(0xFF4CAF50)
                                "missing key" -> Color(0xFFFFA000)
                                "disabled & missing key" -> Color(0xFFF44336)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            })
                        }
                        Switch(
                            checked = pixabayEnabled,
                            onCheckedChange = { viewModel.toggleSource("pixabay", it) },
                            modifier = Modifier.testTag("toggle_pixabay_source")
                        )
                    }

                    // API Source Toggle: Pinterest
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Pinterest RSS", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            val status = sourceHealth["Pinterest"] ?: "unknown"
                            Text(status.replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = when (status) {
                                "ready" -> Color(0xFF4CAF50)
                                "missing key" -> Color(0xFFFFA000)
                                "disabled & missing key" -> Color(0xFFF44336)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            })
                        }
                        Switch(
                            checked = pinterestEnabled,
                            onCheckedChange = { viewModel.toggleSource("pinterest", it) },
                            modifier = Modifier.testTag("toggle_pinterest_source")
                        )
                    }
                }
            }
        }
    }
}
