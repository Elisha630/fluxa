package com.fluxawallpapers.app.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class NvidiaChatRequest(
    @field:Json(name = "model") val model: String = "meta/llama-3.2-90b-vision-instruct",
    @field:Json(name = "messages") val messages: List<NvidiaMessage>
)

@JsonClass(generateAdapter = true)
data class NvidiaMessage(
    @field:Json(name = "role") val role: String,
    // Was previously typed `Any` to "support both String and List<NvidiaContent>" — but Moshi's
    // generated adapter resolves an Any-typed field to its built-in Object adapter, which only
    // knows how to serialize Map/List/String/Number/Boolean/null. A List<NvidiaContent> (a custom
    // data class) doesn't fall into any of those, so every request body build threw
    // IllegalArgumentException, meaning analyzeImage() ALWAYS failed and AI-based similar-wallpaper
    // lookup was permanently broken. The only value ever constructed here is a
    // List<NvidiaContent>, so just declare that type directly — Moshi's codegen adapter for it
    // works fine.
    @field:Json(name = "content") val content: List<NvidiaContent>
)

@JsonClass(generateAdapter = true)
data class NvidiaContent(
    @field:Json(name = "type") val type: String,
    @field:Json(name = "text") val text: String? = null,
    @field:Json(name = "image_url") val imageUrl: NvidiaImageUrl? = null
)

@JsonClass(generateAdapter = true)
data class NvidiaImageUrl(
    @field:Json(name = "url") val url: String
)

@JsonClass(generateAdapter = true)
data class NvidiaChatResponse(
    @field:Json(name = "choices") val choices: List<NvidiaChoice>
)

@JsonClass(generateAdapter = true)
data class NvidiaChoice(
    @field:Json(name = "message") val message: NvidiaResponseMessage
)

@JsonClass(generateAdapter = true)
data class NvidiaResponseMessage(
    @field:Json(name = "content") val content: String
)

interface NvidiaApi {
    @POST("chat/completions")
    suspend fun analyzeImage(
        @Header("Authorization") auth: String,
        @Body request: NvidiaChatRequest
    ): NvidiaChatResponse
}
