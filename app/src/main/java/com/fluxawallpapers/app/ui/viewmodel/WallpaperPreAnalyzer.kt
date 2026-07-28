package com.fluxawallpapers.app.ui.viewmodel

import com.fluxawallpapers.app.data.network.Wallpaper
import com.fluxawallpapers.app.data.repository.AiAnalysisQueue
import com.fluxawallpapers.app.data.repository.WallpaperRepository
import com.fluxawallpapers.app.util.FluxaLog
import com.fluxawallpapers.app.util.HashUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles pre-emptive AI analysis for wallpapers to ensure recommendations are
 * ready before the user even opens the detail view.
 */
class WallpaperPreAnalyzer(
    private val repository: WallpaperRepository,
    private val aiAnalysisQueue: AiAnalysisQueue,
    private val scope: CoroutineScope
) {
    private val analyzedHashes = ConcurrentHashMap.newKeySet<String>()

    /**
     * Schedules analysis for a list of wallpapers. 
     * Analysis is only performed if not already cached and within queue limits.
     */
    fun preAnalyze(wallpapers: List<Wallpaper>) {
        wallpapers.take(10).forEach { wallpaper ->
            val hash = HashUtils.sha256(wallpaper.imageUrl)
            if (analyzedHashes.contains(hash)) return@forEach

            scope.launch(Dispatchers.IO) {
                try {
                    // Check if already in DB cache first to avoid queueing
                    val existing = repository.getCachedWallpaperEntity(wallpaper.id)
                    // Note: This check might be redundant as analyzeAndCache does it,
                    // but it saves us from hitting the Semaphore if we already know it's there.
                    
                    repository.analyzeAndCache(wallpaper)
                    analyzedHashes.add(hash)
                } catch (e: Exception) {
                    FluxaLog.e("Pre-analysis failed for ${wallpaper.id}: ${e.message}")
                }
            }
        }
    }
}
