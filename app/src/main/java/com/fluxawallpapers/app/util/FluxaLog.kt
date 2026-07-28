package com.fluxawallpapers.app.util

import android.util.Log
import com.fluxawallpapers.app.BuildConfig
import com.fluxawallpapers.app.BuildConfig.DEBUG

/**
 * Release-safe logger that strips log calls in production builds.
 * Use instead of android.util.Log to avoid leaking internal data via logcat.
 *
 * In debug builds: full logging.
 * In release builds: only errors are logged; info/debug/verbose are suppressed.
 */
object FluxaLog {
    private const val TAG = "Fluxa"

    fun d(message: String) {
        if (DEBUG) Log.d(TAG, message)
    }

    fun d(tag: String, message: String) {
        if (DEBUG) Log.d(tag, message)
    }

    fun i(message: String) {
        if (DEBUG) Log.i(TAG, message)
    }

    fun i(tag: String, message: String) {
        if (DEBUG) Log.i(tag, message)
    }

    fun w(message: String) {
        Log.w(TAG, message) // warnings are always useful
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }

    /** Verbose logs — always stripped in release */
    fun v(message: String) {
        if (DEBUG) Log.v(TAG, message)
    }

    fun v(tag: String, message: String) {
        if (DEBUG) Log.v(tag, message)
    }
}
