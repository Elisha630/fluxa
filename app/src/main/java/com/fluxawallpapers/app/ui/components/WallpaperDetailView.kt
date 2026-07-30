package com.fluxawallpapers.app.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fluxawallpapers.app.data.network.Wallpaper
import com.fluxawallpapers.app.ui.theme.DarkSurface
import com.fluxawallpapers.app.ui.theme.NeonBorder
import com.fluxawallpapers.app.ui.viewmodel.WallpaperViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WallpaperDetailView(
    initialIndex: Int,
    wallpapers: List<Wallpaper>,
    viewModel: WallpaperViewModel,
    onBack: () -> Unit
) {
    val wallpaperList = remember { androidx.compose.runtime.mutableStateListOf<Wallpaper>().also { it.addAll(wallpapers) } }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, wallpaperList.size - 1),
        pageCount = { wallpaperList.size }
    )
    val context = LocalContext.current

    var overlayMode by remember { mutableStateOf("None") }
    var showPreviewControls by remember { mutableStateOf(false) }
    var isPinnedState by remember { mutableStateOf(false) }
    var isSettingWallpaper by remember { mutableStateOf(false) }
    var showSetDialog by remember { mutableStateOf(false) }

    val currentWallpaper = wallpaperList.getOrNull(pagerState.currentPage)

    // Capture the wallpaper being viewed and its entry time locally within a single effect,
    // keyed on the page. Each time the page changes, a fresh onDispose closure is created that
    // captures THIS page's wallpaper and entry timestamp — so the previous page's duration is
    // computed correctly, instead of racing against a shared mutable `startTime` that a second
    // effect could update before the outgoing page's dispose callback reads it.
    DisposableEffect(pagerState.currentPage) {
        val enteredWallpaper = currentWallpaper
        val enteredAt = System.currentTimeMillis()
        onDispose {
            enteredWallpaper?.let { wp ->
                val duration = (System.currentTimeMillis() - enteredAt) / 1000L
                viewModel.recordViewDuration(wp, duration)
            }
        }
    }

    LaunchedEffect(currentWallpaper) {
        currentWallpaper?.let { wp ->
            viewModel.isPinned(wp.id) { pinned ->
                isPinnedState = pinned
            }
        }
    }

    val similarState by viewModel.similarState.collectAsState()
    LaunchedEffect(currentWallpaper) {
        currentWallpaper?.let { wp ->
            viewModel.fetchSimilar(wp)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("wallpaper_detail_container")
    ) {
        if (currentWallpaper == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Wallpaper load failed", color = Color.White)
            }
        } else {
            if (overlayMode in listOf("Lock Screen", "Home Screen", "Clean")) {
                FullScreenPreviewMode(
                    currentWallpaper = currentWallpaper,
                    overlayMode = overlayMode,
                    showControls = showPreviewControls,
                    onControlsToggle = { showPreviewControls = !showPreviewControls },
                    onModeChange = { newMode -> overlayMode = newMode },
                    onBack = {
                        overlayMode = "None"
                        showPreviewControls = false
                    },
                    onSetWallpaper = { showSetDialog = true },
                    viewModel = viewModel
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.82f)
                        .align(Alignment.TopCenter)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val wallpaper = wallpaperList[page]
                        var scale by remember { mutableStateOf(1f) }
                        var offsetX by remember { mutableStateOf(0f) }
                        var offsetY by remember { mutableStateOf(0f) }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                                        scale = newScale
                                        if (newScale <= 1f) {
                                            // Back at baseline zoom — recenter so a leftover pan
                                            // offset can't leave the image visibly off-center.
                                            offsetX = 0f
                                            offsetY = 0f
                                        } else {
                                            offsetX += pan.x
                                            offsetY += pan.y
                                        }
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = wallpaper.imageUrl,
                                contentDescription = "Full-screen wallpaper by ${wallpaper.author}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offsetX,
                                        translationY = offsetY
                                    )
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.35f)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                        )
                                    )
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = overlayMode != "None",
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (overlayMode) {
                            "Lock Screen" -> {
                                LockScreenOverlay()
                            }
                            "Home Screen" -> {
                                HomeScreenOverlay()
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .testTag("back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to list",
                                tint = Color.White
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val guideModes = listOf("None", "Lock", "Home")
                            guideModes.forEach { mode ->
                                val label = when (mode) {
                                    "None" -> "Full"
                                    "Lock" -> "Lock"
                                    "Home" -> "Home"
                                    else -> mode
                                }
                                val isSelected = when (mode) {
                                    "None" -> overlayMode == "None"
                                    "Lock" -> overlayMode == "Lock Screen"
                                    "Home" -> overlayMode == "Home Screen"
                                    else -> false
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable {
                                            overlayMode = when (mode) {
                                                "Lock" -> "Lock Screen"
                                                "Home" -> "Home Screen"
                                                else -> "None"
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 24.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, NeonBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Photo by ${currentWallpaper.author}",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = currentWallpaper.source.uppercase(),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    isPinnedState = !isPinnedState
                                    viewModel.togglePin(currentWallpaper, isPinnedState)
                                    val detailsMsg = if (isPinnedState) "Pinned & saved!" else "Unpinned"
                                    Toast.makeText(context, detailsMsg, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                    .testTag("pin_button")
                            ) {
                                Icon(
                                    imageVector = if (isPinnedState) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = "Pin wallpaper",
                                    tint = if (isPinnedState) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    viewModel.downloadToLocalStorage(currentWallpaper) { success ->
                                        val msg = if (success) "Downloaded!" else "Download failed"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                                    .testTag("download_cache_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = "Save copy",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Button(
                                onClick = { showSetDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(30.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .testTag("apply_wallpaper_button"),
                                enabled = !isSettingWallpaper
                            ) {
                                if (isSettingWallpaper) {
                                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                                } else {
                                    Icon(Icons.Filled.Wallpaper, "Set")
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Set as Wallpaper",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }

                when (val sim = similarState) {
                    is com.fluxawallpapers.app.ui.viewmodel.SimilarUiState.Loading -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                        ) {
                            SimilarWallpapersSkeleton()
                        }
                    }
                    is com.fluxawallpapers.app.ui.viewmodel.SimilarUiState.Success -> {
                        val coroutineScope = rememberCoroutineScope()
                        val similarItems = sim.list.filter { it.id != currentWallpaper.id }
                        if (similarItems.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                            ) {
                                SimilarWallpapersRow(
                                    wallpapers = similarItems,
                                    onSelect = { selected: Wallpaper ->
                                        val targetIndex = wallpaperList.indexOfFirst { it.id == selected.id }
                                        if (targetIndex >= 0) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(targetIndex)
                                            }
                                        } else {
                                            wallpaperList.add(selected)
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(wallpaperList.size - 1)
                                            }
                                        }
                                    },
                                    onMore = { sel: Wallpaper -> viewModel.moreLikeThis(sel) },
                                    onLess = { sel: Wallpaper -> viewModel.lessLikeThis(sel) }
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }
        }

        if (showSetDialog && currentWallpaper != null) {
            AlertDialog(
                onDismissRequest = { showSetDialog = false },
                title = { Text("Set Wallpaper Target", fontWeight = FontWeight.Bold) },
                text = { Text("Choose where you want to apply this wallpaper.") },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val targets = listOf("Home Screen", "Lock Screen", "Home & Lock Screen")
                        targets.forEach { target ->
                            Button(
                                onClick = {
                                    showSetDialog = false
                                    isSettingWallpaper = true
                                    viewModel.setAsDeviceWallpaper(currentWallpaper, target) { success ->
                                        isSettingWallpaper = false
                                        val m = if (success) "Successfully set to $target" else "Failed to apply wallpaper."
                                        Toast.makeText(context, m, Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("set_target_${target.replace(" ", "_").lowercase()}"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (target == "Home & Lock Screen") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (target == "Home & Lock Screen") Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(target, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSetDialog = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.primary)
                    }
                },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }

}

@Composable
private fun FullScreenPreviewMode(
    currentWallpaper: Wallpaper,
    overlayMode: String,
    showControls: Boolean,
    onControlsToggle: () -> Unit,
    onModeChange: (String) -> Unit,
    onBack: () -> Unit,
    onSetWallpaper: () -> Unit,
    @Suppress("UNUSED_PARAMETER") viewModel: WallpaperViewModel
) {
    var offsetY by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }

    // Live clock update
    var currentTime by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            kotlinx.coroutines.delay(1000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Layer 1: Wallpaper image with pinch/pan and swipe gesture detection
        AsyncImage(
            model = currentWallpaper.imageUrl,
            contentDescription = "Fullscreen preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 4f)
                        scale = newScale
                        if (newScale <= 1f) {
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        if (scale == 1f) {
                            if (overlayMode == "Lock Screen" && dragAmount.y < -100) {
                                onModeChange("Home Screen")
                            }
                        }
                    }
                }
        )

        // Layer 2: Tap-to-toggle area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onControlsToggle() }
        )

        // Layer 3: Device overlay elements
        when (overlayMode) {
            "Lock Screen" -> FullScreenLockOverlay(currentTime)
            "Home Screen" -> FullScreenHomeOverlay(currentTime)
        }

        // Layer 4: Top Header with Back button and Interactive Mode Selector Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .testTag("preview_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }

            // Interactive Mode Pill inside preview
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val modes = listOf("Lock Screen", "Home Screen", "Clean")
                modes.forEach { mode ->
                    val label = when (mode) {
                        "Lock Screen" -> "Lock"
                        "Home Screen" -> "Home"
                        else -> "Clean"
                    }
                    val isSelected = overlayMode == mode

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { onModeChange(mode) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.size(40.dp))
        }

        // Layer 5: Set as Wallpaper floating control bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, NeonBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onSetWallpaper() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("preview_set_wallpaper_button")
                    ) {
                        Icon(Icons.Filled.Wallpaper, "Set", modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Apply Wallpaper", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

/**
 * Realistic lock screen overlay for fullscreen preview:
 * lock icon, big clock, date, and swipe-up hint.
 */
@Composable
private fun FullScreenLockOverlay(currentTime: Date) {
    val locale = LocalConfiguration.current.locales[0]
    val date = SimpleDateFormat("EEEE, MMMM d", locale).format(currentTime)
    val time = SimpleDateFormat("HH:mm", locale).format(currentTime)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "Lock icon",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = date,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Text(
            text = time,
            color = Color.White,
            fontSize = 80.sp,
            fontWeight = FontWeight.Thin,
            textAlign = TextAlign.Center,
            letterSpacing = (-2).sp
        )
        Spacer(Modifier.weight(1f))
        // Notification hint area
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No notifications",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(24.dp))
        // Bottom row: flashlight and camera icons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.FlashlightOn, "Flashlight", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CameraAlt, "Camera", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        // Home indicator bar
        Box(
            modifier = Modifier
                .padding(bottom = 8.dp)
                .width(134.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.5f))
        )
    }
}

/**
 * Realistic home screen overlay for fullscreen preview:
 * status bar clock, weather widget, app grid, and dock.
 */
@Composable
private fun FullScreenHomeOverlay(currentTime: Date) {
    val locale = LocalConfiguration.current.locales[0]
    val time = SimpleDateFormat("HH:mm", locale).format(currentTime)

    val mockApps = listOf(
        Pair("Search", Icons.Filled.Search),
        Pair("Gallery", Icons.Filled.PhotoLibrary),
        Pair("Settings", Icons.Filled.Settings),
        Pair("Camera", Icons.Filled.CameraAlt),
        Pair("Music", Icons.Filled.MusicNote),
        Pair("Mail", Icons.Filled.Mail),
        Pair("Browser", Icons.Filled.Language),
        Pair("Files", Icons.Filled.Folder)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Status bar area with clock
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = time,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.SignalCellularAlt, "Signal", tint = Color.White, modifier = Modifier.size(14.dp))
                Icon(Icons.Filled.Wifi, "WiFi", tint = Color.White, modifier = Modifier.size(14.dp))
                Icon(Icons.Filled.BatteryFull, "Battery", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }

        // Weather/info widget card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.WbSunny,
                    "Weather info",
                    tint = Color(0xFFFFEB3B),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Today's Wallpaper Preview", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("24°C • Clean Design Setup", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        }

        // App grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (row in 0..1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0..3) {
                            val index = row * 4 + col
                            val app = mockApps.getOrNull(index)
                            if (app != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(60.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = app.second,
                                            contentDescription = app.first,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = app.first,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dock bar
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val dockApps = listOf(Icons.Filled.Phone, Icons.AutoMirrored.Filled.Message, Icons.Filled.Language, Icons.Filled.PlayArrow)
                dockApps.forEach { icon ->
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, "Dock item", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SimilarWallpapersSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(top = 8.dp, bottom = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .width(120.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.1f))
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            userScrollEnabled = false
        ) {
            items(5) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            RoundedCornerShape(12.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun SimilarWallpapersRow(
    wallpapers: List<Wallpaper>,
    onSelect: (Wallpaper) -> Unit,
    onMore: (Wallpaper) -> Unit = {},
    onLess: (Wallpaper) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(top = 8.dp, bottom = 12.dp)
    ) {
        Text(
            text = "You Might Also Like",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(wallpapers, key = { it.id }) { wallpaper ->
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    AsyncImage(
                        model = wallpaper.thumbnailUrl,
                        contentDescription = "Similar: ${wallpaper.author}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onSelect(wallpaper) }
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.15f))
                    )

                    // Quick feedback actions
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { onLess(wallpaper) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.Clear, "Less like this", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { onMore(wallpaper) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.ThumbUp, "More like this", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

}

@Composable
fun LockScreenOverlay() {
    var currentTime by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            kotlinx.coroutines.delay(1000L)
        }
    }
    val locale = LocalConfiguration.current.locales[0]
    val date = SimpleDateFormat("EEEE, MMMM d", locale).format(currentTime)
    val time = SimpleDateFormat("HH:mm", locale).format(currentTime)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "Swipe lock simulation",
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = date,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Text(
            text = time,
            color = Color.White,
            fontSize = 72.sp,
            fontWeight = FontWeight.Light,
            textAlign = TextAlign.Center,
            letterSpacing = (-2).sp
        )

        Surface(
            color = Color.White.copy(alpha = 0.18f),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.WbSunny, "Weather", tint = Color(0xFFFFD54F), modifier = Modifier.size(16.dp))
                Text("24°C Sunny", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            text = "Swipe up to unlock",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 100.dp)
        )
    }
}

@Composable
fun HomeScreenOverlay() {
    var currentTime by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            kotlinx.coroutines.delay(1000L)
        }
    }
    val locale = LocalConfiguration.current.locales[0]
    val time = SimpleDateFormat("HH:mm", locale).format(currentTime)

    val mockApps = listOf(
        Pair("Photos", Icons.Filled.PhotoLibrary),
        Pair("Settings", Icons.Filled.Settings),
        Pair("Camera", Icons.Filled.CameraAlt),
        Pair("Music", Icons.Filled.MusicNote),
        Pair("Mail", Icons.Filled.Mail),
        Pair("Browser", Icons.Filled.Language),
        Pair("Files", Icons.Filled.Folder),
        Pair("Store", Icons.Filled.ShoppingCart)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(time, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.SignalCellularAlt, "Signal", tint = Color.White, modifier = Modifier.size(13.dp))
                Icon(Icons.Filled.Wifi, "WiFi", tint = Color.White, modifier = Modifier.size(13.dp))
                Icon(Icons.Filled.BatteryFull, "Battery", tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }

        Surface(
            color = Color.Black.copy(alpha = 0.45f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.WbSunny, "Weather", tint = Color(0xFFFFEB3B), modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Today's Setup Preview", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("24°C • High Contrast Setup", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (row in 0..1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0..3) {
                            val index = row * 4 + col
                            val app = mockApps.getOrNull(index)
                            if (app != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(56.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.22f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(app.second, app.first, tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(app.first, color = Color.White, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(
            color = Color.White.copy(alpha = 0.22f),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val dockApps = listOf(Icons.Filled.Phone, Icons.AutoMirrored.Filled.Message, Icons.Filled.Language, Icons.Filled.CameraAlt)
                dockApps.forEach { icon ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, "Dock item", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

