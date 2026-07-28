package com.fluxawallpapers.app.worker

import android.content.Context
import androidx.work.*
import com.fluxawallpapers.app.di.AppInjector
import com.fluxawallpapers.app.util.FluxaLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class HeartbeatWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        FluxaLog.d("HeartbeatWorker: Updating active status...")
        try {
            val repository = AppInjector.provideRepository(applicationContext)
            repository.updateHeartbeat()
            Result.success()
        } catch (e: Exception) {
            FluxaLog.e("HeartbeatWorker: Failed to update status: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_TAG = "fluxa_heartbeat_work"

        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val heartbeatRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(
                6, TimeUnit.HOURS,
                15, TimeUnit.MINUTES
            )
                .addTag(WORK_TAG)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "fluxa_bg_heartbeat",
                ExistingPeriodicWorkPolicy.KEEP,
                heartbeatRequest
            )
        }
    }
}
