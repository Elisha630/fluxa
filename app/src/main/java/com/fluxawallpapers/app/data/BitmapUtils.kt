package com.fluxawallpapers.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.fluxawallpapers.app.util.FluxaLog
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Shared bitmap utility functions for safe, memory-aware image decoding.
 * Used by WallpaperViewModel and SlideshowWorker to avoid OOM on large images.
 */
object BitmapUtils {

    /**
     * Decode a bitmap from a file safely by first checking dimensions to avoid OOM.
     * Down-samples large images to at most [maxDimension] pixels on the longest side.
     */
    fun safeDecodeBitmap(path: String, maxDimension: Int = 4096): Bitmap? {
        return try {
            // First pass: decode bounds only
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, boundsOptions)

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

            val sampleSize = calculateInSampleSize(
                boundsOptions.outWidth, boundsOptions.outHeight,
                maxDimension, maxDimension
            )

            // Second pass: decode with sample size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                // ARGB_8888 for best quality on wallpapers; use RGB_565 for memory-constrained thumbnails
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(path, decodeOptions)
        } catch (e: Exception) {
            FluxaLog.e("Failed to safely decode bitmap file: ${e.message}", e)
            null
        }
    }

    /**
     * Decode a bitmap from an InputStream safely. Since streams can't be rewound for
     * two-pass decode, write to a temp file first, then use dimension-checked file decode.
     */
    fun safeDecodeStream(
        inputStream: InputStream,
        maxDimension: Int = 4096
    ): Bitmap? {
        val tempFile = try {
            File.createTempFile("wallpaper_", ".tmp").also { it.deleteOnExit() }
        } catch (e: Exception) {
            FluxaLog.e("Failed to create temp file for stream decode: ${e.message}", e)
            return null
        }
        return try {
            BufferedInputStream(inputStream).use { buffered ->
                FileOutputStream(tempFile).use { output ->
                    buffered.copyTo(output)
                }
            }
            safeDecodeBitmap(tempFile.absolutePath, maxDimension)
        } catch (e: Exception) {
            FluxaLog.e("Failed to safely decode stream: ${e.message}", e)
            null
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Calculate a sample size that reduces the image to at most [reqWidth]x[reqHeight].
     * Always a power of 2 for efficient decode.
     */
    fun calculateInSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var sampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while (halfHeight / sampleSize >= reqHeight && halfWidth / sampleSize >= reqWidth) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }
}
