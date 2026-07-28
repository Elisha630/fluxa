package com.fluxawallpapers.app.data.model

/**
 * Wallpaper source identifiers replacing raw strings "unsplash"/"pexels"/"pixabay".
 * Used in Wallpaper.source, source toggle preferences, and API routing.
 */
enum class WallpaperSource(val key: String) {
    UNSPLASH("unsplash"),
    PEXELS("pexels"),
    PIXABAY("pixabay"),
    PINTEREST("pinterest");

    companion object {
        fun fromKey(key: String): WallpaperSource? =
            entries.find { it.key == key }

        fun fromKeyOrDefault(key: String): WallpaperSource =
            fromKey(key) ?: UNSPLASH
    }
}

/**
 * Slideshow wallpaper targets replacing raw strings "Home Screen"/"Lock Screen"/"Both".
 */
enum class SlideshowTarget(val displayName: String) {
    HOME_SCREEN("Home Screen"),
    LOCK_SCREEN("Lock Screen"),
    BOTH("Both");

    companion object {
        fun fromDisplayName(name: String): SlideshowTarget? =
            entries.find { it.displayName == name }

        fun fromDisplayNameOrDefault(name: String): SlideshowTarget =
            fromDisplayName(name) ?: BOTH
    }
}

/**
 * Slideshow rotation intervals replacing raw strings "15 min"/"1 hour" etc.
 * Each entry maps a display label to a duration in minutes.
 */
enum class SlideshowInterval(val displayName: String, val durationMinutes: Long) {
    FIVE_MIN("5 min", 5L),
    FIFTEEN_MIN("15 min", 15L),
    THIRTY_MIN("30 min", 30L),
    ONE_HOUR("1 hour", 60L),
    SIX_HOURS("6 hours", 360L),
    TWELVE_HOURS("12 hours", 720L),
    DAILY("Daily", 1440L);

    companion object {
        fun fromDisplayName(name: String): SlideshowInterval? =
            entries.find { it.displayName == name }

        fun fromDisplayNameOrDefault(name: String): SlideshowInterval =
            fromDisplayName(name) ?: ONE_HOUR
    }
}
