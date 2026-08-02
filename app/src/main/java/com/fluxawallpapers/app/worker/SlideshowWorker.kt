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

        // Cap decoded bitmaps well below the raw 4096 default. In "Both" (home + lock) mode we
        // can hold two full wallpaper bitmaps in memory at once; at the old 4096 cap that's two
        // roughly 64 MB ARGB_8888 bitmaps. 2048px on the long side is still well above most phone
        // screen resolutions, at a quarter of the memory.
        val maxSlideshowDimension = 2048

        // Helper to load a wallpaper bitmap from cache or download
        suspend fun loadWallpaperBitmap(wallpaper: Wallpaper): Bitmap? {
            return try {
                val cachedPath = repository.getCachedPath(wallpaper.id)
                if (cachedPath != null && File(cachedPath).exists()) {
                    BitmapUtils.safeDecodeBitmap(cachedPath, maxSlideshowDimension)
                } else {
                    val downloadedPath = repository.downloadWallpaperToCache(wallpaper)
                    if (downloadedPath != null) {
                        BitmapUtils.safeDecodeBitmap(downloadedPath, maxSlideshowDimension)
                    } else {
                        downloadBitmap(wallpaper.imageUrl, maxSlideshowDimension)
                    }
                }
            } catch (e: OutOfMemoryError) {
                FluxaLog.e("OOM loading slideshow bitmap: ${e.message}", e)
                null
            } catch (e: Exception) {
                FluxaLog.e("Failed to load slideshow bitmap: ${e.message}", e)
                null
            }
        }

        // Use a try/catch/finally that covers all paths so that rescheduling always happens
        // (except when slideshow is explicitly disabled). This is more robust than PeriodicWorkRequest
        // which could be suppressed by Doze mode / battery optimization on various Android devices.
        //
        // Catch OutOfMemoryError explicitly below so failures during decode/apply still return a
        // WorkManager result and still reach the rescheduling block.
        var result: Result
        var homeBitmap: Bitmap? = null
        var lockBitmap: Bitmap? = null
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
            homeBitmap = loadWallpaperBitmap(homeWallpaper)
            val loadedHomeBitmap = homeBitmap
            if (loadedHomeBitmap == null) {
                FluxaLog.e("SlideshowWorker: Home bitmap is null. Auto-rotation aborted.")
                return@withContext Result.failure()
            }

            lockBitmap = if (lockWallpaper != null) {
                loadWallpaperBitmap(lockWallpaper)
            } else {
                null // reuse the home bitmap at apply-time without holding a second reference
            }
            val loadedLockBitmap = lockBitmap

            // 4. Apply the wallpaper(s)
            val wallpaperManager = WallpaperManager.getInstance(applicationContext)

            when (target) {
                SlideshowTarget.HOME_SCREEN -> {
                    wallpaperManager.setBitmap(loadedHomeBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    FluxaLog.d("SlideshowWorker: Completed setting home screen wallpaper.")
                    repository.recordWallpaperAction(homeWallpaper, WallpaperAction.SET)
                }
                SlideshowTarget.LOCK_SCREEN -> {
                    wallpaperManager.setBitmap(loadedHomeBitmap, null, true, WallpaperManager.FLAG_LOCK)
                    FluxaLog.d("SlideshowWorker: Completed setting lock screen wallpaper.")
                    repository.recordWallpaperAction(homeWallpaper, WallpaperAction.SET)
                }
                SlideshowTarget.BOTH -> {
                    // Set different wallpapers for home and lock screens
                    wallpaperManager.setBitmap(loadedHomeBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    repository.recordWallpaperAction(homeWallpaper, WallpaperAction.SET)

                    if (loadedLockBitmap != null) {
                        wallpaperManager.setBitmap(loadedLockBitmap, null, true, WallpaperManager.FLAG_LOCK)
                        if (lockWallpaper != null && lockWallpaper.id != homeWallpaper.id) {
                            repository.recordWallpaperAction(lockWallpaper, WallpaperAction.SET)
                        }
                        FluxaLog.d("SlideshowWorker: Completed setting different home and lock screen wallpapers.")
                    } else {
                        // Fallback: same wallpaper for both
                        wallpaperManager.setBitmap(loadedHomeBitmap, null, true, WallpaperManager.FLAG_LOCK)
                        FluxaLog.d("SlideshowWorker: Completed setting home and lock screen wallpaper (same image fallback).")
                    }
                }
            }

            result = Result.success()
        } catch (e: OutOfMemoryError) {
            FluxaLog.e("SlideshowWorker: Out of memory applying wallpaper: ${e.message}", e)
            result = Result.failure()
        } catch (e: Exception) {
            FluxaLog.e("SlideshowWorker: Exception applying wallpaper: ${e.message}", e)
            result = Result.failure()
        } finally {
            // Free the bitmaps promptly rather than waiting on GC; this worker may run again soon.
            try {
                if (homeBitmap?.isRecycled == false) homeBitmap.recycle()
                if (lockBitmap?.isRecycled == false && lockBitmap !== homeBitmap) lockBitmap.recycle()
            } catch (e: Exception) {
                FluxaLog.e("SlideshowWorker: Error recycling bitmaps: ${e.message}", e)
            }

            // Always reschedule the next run after each execution (even on failure and retry) so the
            // chain never breaks. Keep this isolated so a transient scheduling/prefs failure does
            // not escape doWork() and permanently stop the self-rescheduling chain.
            try {
                if (repository.getSlideshowEnabled()) {
                    val nextInterval = repository.getSlideshowIntervalEnum()
                    scheduleOneShot(applicationContext, nextInterval)
                }
            } catch (e: Throwable) {
                FluxaLog.e("SlideshowWorker: Failed to reschedule next rotation, falling back to default interval: ${e.message}", e)
                try {
                    scheduleOneShot(applicationContext, SlideshowInterval.ONE_HOUR)
                } catch (e2: Throwable) {
                    FluxaLog.e("SlideshowWorker: Fallback reschedule also failed: ${e2.message}", e2)
                }
            }
        }

        return@withContext result
    }

    private fun downloadBitmap(urlString: String, maxDimension: Int = 4096): Bitmap? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection()
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()
            val inputStream = connection.getInputStream()
            BitmapUtils.safeDecodeStream(inputStream, maxDimension)
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
