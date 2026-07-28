package com.fluxawallpapers.app

import android.app.Application
import com.fluxawallpapers.app.di.AppInjector
import com.fluxawallpapers.app.worker.HeartbeatWorker
import com.fluxawallpapers.app.worker.SlideshowWorker
import com.google.firebase.analytics.FirebaseAnalytics

class FluxaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase Analytics
        FirebaseAnalytics.getInstance(this)
        
        HeartbeatWorker.schedule(this)

        val repository = AppInjector.provideRepository(this)
        if (repository.getSlideshowEnabled()) {
            SlideshowWorker.scheduleSlideshow(this, repository.getSlideshowIntervalEnum())
        }
    }
}
