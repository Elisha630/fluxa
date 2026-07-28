package com.fluxawallpapers.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluxawallpapers.app.ui.viewmodel.ConnectionType

@Composable
fun AppHeader(connectionType: ConnectionType) {
    val (icon, label, color) = when (connectionType) {
        ConnectionType.WIFI -> Triple(
            Icons.Filled.Wifi,
            "WiFi",
            Color(0xFF4CAF50)
        )
        ConnectionType.MOBILE -> Triple(
            Icons.Filled.SignalCellularAlt,
            "Mobile",
            Color(0xFF2196F3)
        )
        ConnectionType.OFFLINE -> Triple(
            Icons.Filled.WifiOff,
            "Offline",
            Color(0xFFFF3D00)
        )
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FLUXA",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.15f))
                    .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = color,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        label,
                        color = color,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (connectionType == ConnectionType.OFFLINE) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFF3D00).copy(alpha = 0.1f))
                    .padding(vertical = 4.dp, horizontal = 24.dp)
            ) {
                Text(
                    text = "Offline Mode active \u2022 Wallpapers are rotating from local cache.",
                    color = Color(0xFFFF8A80),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
