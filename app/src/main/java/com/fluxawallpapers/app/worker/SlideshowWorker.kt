package com.fluxawallpapers.app.worker

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import androidx.work.*
import com.fluxawallpapers.app.data.BitmapUtils
import com.fluxawallpapers.app.di.AppInjector
import com.fluxawallpapers.app.data.model.SlideshowInterval
import com.fluxawallpapers.app.data.model.SlideshowTarget
import com.fluxawallpapers.app.data.network.Wallpaper
import com.fluxawallpapers.app.data.repository.WallpaperAction
import com.fluxawallpapers.app.util.FluxaLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit

class SlideshowWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @Suppress("DEPRECATION")
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        FluxaLog.d("SlideshowWorker: Executing wallpaper auto-rotation...")

        val repository = AppInjector.provideRepository(applicationContext)

        // Helper to load a wallpaper bitmap from cache or download
        suspend fun loadWallpaperBitmap(wallpaper: Wallpaper): Bitmap? {
            return try {
                val cachedPath = repository.getCachedPath(wallpaper.id)
                if (cachedPath != null && File(cachedPath).exists()) {
                    BitmapUtils.safeDecodeBitmap(cachedPath)
                } else {
                    val downloadedPath = repository.downloadWallpaperToCache(wallpaper)
                    if (downloadedPath != null) {
                        BitmapUtils.safeDecodeBitmap(downloadedPath)
                    } else {
                        downloadBitmap(wallpaper.imageUrl)
                    }
                }
            } catch (e: Exception) {
                FluxaLog.e("Failed to load slideshow bitmap: ${e.message}", e)
                null
            }
        }

        // Use a try/catch/finally that covers all paths so that rescheduling always happens
        // (except when slideshow is explicitly disabled). This is more robust than PeriodicWorkRequest
        // which could be suppressed by Doze mode / battery optimization on various Android devices.
        var result: Result
        try {
            // 1. Check if slideshow is enabled
            if (!repository.getSlideshowEnabled()) {
                FluxaLog.d("Slideshow is disabled, ending worker success.")
                return@withContext Result.success()
            }

            // 2. Wi-Fi check
            if (repository.getWifiOnlyToggle() && !repository.isWifiOnlyAvailable()) {
                FluxaLog.d("SlideshowWorker: Blocked - WiFi only option enabled, but not on WiFi.")
                return@withContext Result.retry()
            }

            val target = repository.getSlideshowTargetEnum()

            // Retrieve first wallpaper (home screen)
            val homeWallpaper = repository.getNextSlideshowWallpaper()
            if (homeWallpaper == null) {
                FluxaLog.e("SlideshowWorker: No available wallpaper found for slideshow.")
                return@withContext Result.success()
            }
            FluxaLog.d("SlideshowWorker: Selected home wallpaper ${homeWallpaper.id} (Author: ${homeWallpaper.author})")

            // For BOTH target, retrieve a second wallpaper for lock screen
            val lockWallpaper: Wallpaper?
            if (target == SlideshowTarget.BOTH) {
                lockWallpaper = repository.getNextSlideshowWallpaper()
                if (lockWallpaper != null) {
                    FluxaLog.d("SlideshowWorker: Selected lock wallpaper ${lockWallpaper.id} (Author: ${lockWallpaper.author})")
                } else {
                    FluxaLog.d("SlideshowWorker: No separate lock wallpaper found, reusing home wallpaper.")
                }
            } else {
                lockWallpaper = null
            }

            // 3. Load bitmaps
            val homeBitmap = loadWallpaperBitmap(homeWallpaper)
            if (homeBitmap == null) {
                FluxaLog.e("SlideshowWorker: Home bitmap is null. Auto-rotation aborted.")
                return@withContext Result.failure()
            }

            val lockBitmap: Bitmap? = if (lockWallpaper != null) {
                loadWallpaperBitmap(lockWallpaper)
            } else {
                homeBitmap // reuse same bitmap for lock if no separate one
            }

            // 4. Apply the wallpaper(s)
            val wallpaperManager = WallpaperManager.getInstance(applicationContext)

            when (target) {
                SlideshowTarget.HOME_SCREEN -> {
                    wallpaperManager.setBitmap(homeBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    FluxaLog.d("SlideshowWorker: Completed setting home screen wallpaper.")
                    repository.recordWallpaperAction(homeWallpaper, WallpaperAction.SET)
                }
                SlideshowTarget.LOCK_SCREEN -> {
                    wallpaperManager.setBitmap(homeBitmap, null, true, WallpaperManager.FLAG_LOCK)
                    FluxaLog.d("SlideshowWorker: Completed setting lock screen wallpaper.")
                    repository.recordWallpaperAction(homeWallpaper, WallpaperAction.SET)
                }
                SlideshowTarget.BOTH -> {
                    // Set different wallpapers for home and lock screens
                    wallpaperManager.setBitmap(homeBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    repository.recordWallpaperAction(homeWallpaper, WallpaperAction.SET)

                    if (lockBitmap != null) {
                        wallpaperManager.setBitmap(lockBitmap, null, true, WallpaperManager.FLAG_LOCK)
                        if (lockWallpaper != null && lockWallpaper.id != homeWallpaper.id) {
                            repository.recordWallpaperAction(lockWallpaper, WallpaperAction.SET)
                        }
                        FluxaLog.d("SlideshowWorker: Completed setting different home and lock screen wallpapers.")
                    } else {
                        // Fallback: same wallpaper for both
                        wallpaperManager.setBitmap(homeBitmap, null, true, WallpaperManager.FLAG_LOCK)
                        FluxaLog.d("SlideshowWorker: Completed setting home and lock screen wallpaper (same image fallback).")
                    }
                }
            }

            result = Result.success()
        } catch (e: Exception) {
            FluxaLog.e("SlideshowWorker: Exception applying wallpaper: ${e.message}", e)
            result = Result.failure()
        } finally {
            // Always reschedule the next run after each execution (even on failure and retry) so the
            // chain never breaks.
            if (repository.getSlideshowEnabled()) {
                val nextInterval = repository.getSlideshowIntervalEnum()
                scheduleOneShot(applicationContext, nextInterval)
            }
        }

        return@withContext result
    }

    private fun downloadBitmap(urlString: String): Bitmap? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection()
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()
            val inputStream = connection.getInputStream()
            BitmapUtils.safeDecodeStream(inputStream)
        } catch (e: Exception) {
            FluxaLog.e("Failed to download bitmap from url $urlString: ${e.message}", e)
            null
        }
    }

    companion object {
        private const val WORK_TAG = "fluxa_slideshow_work"
        private const val UNIQUE_WORK_NAME = "fluxa_bg_slideshow"

        /**
         * Schedule the slideshow to start after the given interval.
         * Uses a OneTimeWorkRequest with initial delay, which is more reliable
         * than PeriodicWorkRequest across different Android versions and OEMs.
         * After the first execution, the worker self-reschedules for the next interval.
         */
        fun scheduleSlideshow(context: Context, interval: SlideshowInterval) {
            val workManager = WorkManager.getInstance(context)

            // Cancel existing updates
            workManager.cancelAllWorkByTag(WORK_TAG)

            FluxaLog.d("Scheduling background auto-rotation every ${interval.displayName} (${interval.durationMinutes} minutes).")

            scheduleOneShot(context, interval)
        }

        /**
         * Schedule a single one-shot execution with the given delay.
         * Not private so it can be called from [doWork] instance method.
         */
        fun scheduleOneShot(context: Context, interval: SlideshowInterval) {
            val workManager = WorkManager.getInstance(context)

            @Suppress("DEPRECATION")
            val request = OneTimeWorkRequestBuilder<SlideshowWorker>()
                .addTag(WORK_TAG)
                .setInitialDelay(interval.durationMinutes, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        /** Legacy overload for compatibility with existing ViewModel callers using string intervals. */
        fun scheduleSlideshow(context: Context, intervalString: String) {
            scheduleSlideshow(context, SlideshowInterval.fromDisplayNameOrDefault(intervalString))
        }

        fun executeOnce(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val buildRequest = OneTimeWorkRequestBuilder<SlideshowWorker>().build()
            workManager.enqueue(buildRequest)
        }

        fun cancelSlideshow(context: Context) {
            FluxaLog.d("Cancelling all background slideshow auto-rotation tasks.")
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
        }
    }
}
