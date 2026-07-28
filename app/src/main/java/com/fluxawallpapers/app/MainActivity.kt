package com.fluxawallpapers.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.fluxawallpapers.app.ui.components.MainScreen
import com.fluxawallpapers.app.ui.theme.FluxaTheme
import com.fluxawallpapers.app.ui.viewmodel.WallpaperViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup edge-to-edge layout safely
        enableEdgeToEdge()
        
        // Initialize ViewModel manually via provider
        val viewModel = ViewModelProvider(this)[WallpaperViewModel::class.java]

        setContent {
            FluxaTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
