package com.fluxawallpapers.app.data.network

import com.fluxawallpapers.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface UnsplashApi {
    @GET("photos")
    suspend fun getCurated(
        @Header("Authorization") auth: String,
        @Header("Accept-Version") version: String = "v1",
        @Query("page") page: Int,
        @Query("per_page") perPage: Int
    ): List<UnsplashPhoto>

    @GET("search/photos")
    suspend fun search(
        @Header("Authorization") auth: String,
        @Header("Accept-Version") version: String = "v1",
        @Query("query") query: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int
    ): UnsplashSearchResponse
}

interface PexelsApi {
    @GET("v1/curated")
    suspend fun getCurated(
        @Header("Authorization") auth: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int
    ): PexelsSearchResponse

    @GET("v1/search")
    suspend fun search(
        @Header("Authorization") auth: String,
        @Query("query") query: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int
    ): PexelsSearchResponse
}

interface PixabayApi {
    @GET("api/")
    suspend fun getPopular(
        @Query("key") key: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("image_type") imageType: String = "photo"
    ): PixabayResponse

    @GET("api/")
    suspend fun search(
        @Query("key") key: String,
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("image_type") imageType: String = "photo"
    ): PixabayResponse
}

object RetrofitClient {
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val unsplashApi: UnsplashApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.unsplash.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(UnsplashApi::class.java)
    }

    val pexelsApi: PexelsApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.pexels.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(PexelsApi::class.java)
    }

    val pixabayApi: PixabayApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://pixabay.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(PixabayApi::class.java)
    }

    val nvidiaApi: NvidiaApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://integrate.api.nvidia.com/v1/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(NvidiaApi::class.java)
    }

}
