package com.fluxawallpapers.app.di

import android.content.Context
import com.fluxawallpapers.app.data.database.AppDatabase
import com.fluxawallpapers.app.data.database.WallpaperDao
import com.fluxawallpapers.app.data.recommendation.FirebaseManager
import com.fluxawallpapers.app.data.repository.AiAnalysisQueue
import com.fluxawallpapers.app.data.repository.WallpaperRepository

/**
 * Manual dependency injection container for the Fluxa app.
 *
 * Avoids Hilt/Koin/Dagger dependency — just a plain singleton that wires
 * Room and Repository once per process. Use [AppInjector.init] early
 * (e.g. in Application.onCreate or the ViewModel provider).
 */
object AppInjector {

    @Volatile
    private var database: AppDatabase? = null

    @Volatile
    private var aiAnalysisQueue: AiAnalysisQueue? = null

    @Volatile
    private var firebaseManager: FirebaseManager? = null

    @Volatile
    private var repository: WallpaperRepository? = null

    fun init(context: Context) {
        if (database == null) {
            synchronized(this) {
                if (database == null) {
                    database = AppDatabase.getDatabase(context.applicationContext)
                }
            }
        }
    }

    fun provideDatabase(context: Context): AppDatabase {
        init(context)
        return database!!
    }

    fun provideWallpaperDao(context: Context): WallpaperDao {
        return provideDatabase(context).wallpaperDao()
    }

    fun provideAiAnalysisQueue(): AiAnalysisQueue {
        if (aiAnalysisQueue == null) {
            synchronized(this) {
                if (aiAnalysisQueue == null) {
                    aiAnalysisQueue = AiAnalysisQueue()
                }
            }
        }
        return aiAnalysisQueue!!
    }

    fun provideFirebaseManager(context: Context): FirebaseManager {
        if (firebaseManager == null) {
            synchronized(this) {
                if (firebaseManager == null) {
                    firebaseManager = FirebaseManager(context.applicationContext)
                }
            }
        }
        return firebaseManager!!
    }

    /** Override the repository instance (for testing). */
    fun overrideRepository(repo: WallpaperRepository) {
        repository = repo
    }

    fun provideRepository(context: Context): WallpaperRepository {
        if (repository == null) {
            synchronized(this) {
                if (repository == null) {
                    repository = WallpaperRepository(
                        context.applicationContext,
                        provideWallpaperDao(context),
                        provideAiAnalysisQueue(),
                        provideFirebaseManager(context)
                    )
                }
            }
        }
        return repository!!
    }
}
