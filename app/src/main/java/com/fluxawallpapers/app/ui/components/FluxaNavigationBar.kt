package com.fluxawallpapers.app.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluxawallpapers.app.ui.theme.DarkSurface

@Composable
fun FluxaNavigationBar(
    activeTab: String,
    onTabSelect: (String) -> Unit
) {
    NavigationBar(
        containerColor = DarkSurface,
        tonalElevation = 8.dp,
        windowInsets = WindowInsets.navigationBars,
        modifier = Modifier.testTag("fluxa_bottom_navigation")
    ) {
        val items = listOf(
            Triple("home", "Home", Icons.Filled.Home),
            Triple("discover", "Discover", Icons.Filled.Explore),
            Triple("favorites", "Pinned", Icons.Filled.Bookmark),
            Triple("settings", "Settings", Icons.Filled.Settings)
        )

        items.forEach { item ->
            val isSelected = activeTab == item.first
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelect(item.first) },
                icon = {
                    Icon(
                        imageVector = item.third,
                        contentDescription = item.second,
                        tint = if (isSelected) Color.Black else Color.White
                    )
                },
                label = {
                    Text(
                        text = item.second,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("nav_item_${item.first}")
            )
        }
    }
}
