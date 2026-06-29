package com.example.service

import com.example.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class Content(val parts: List<Part>)

@Serializable
data class Part(val text: String)

@Serializable
data class GenerateContentRequest(val contents: List<Content>)

@Serializable
data class GenerateContentResponse(val candidates: List<Candidate>)

@Serializable
data class Candidate(val content: Content)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiService {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"
    
    val service: GeminiApiService by lazy {
        val json = Json { ignoreUnknownKeys = true }
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(OkHttpClient.Builder().connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiApiService::class.java)
    }

    suspend fun analyzeBleTraffic(traffic: List<String>): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val prompt = "Analyze the following BLE traffic packets from a scooter and identify any meaningful patterns, errors, or commands:\n" + traffic.joinToString("\n")
        
        val request = GenerateContentRequest(listOf(Content(listOf(Part(prompt)))))
        
        try {
            val response = service.generateContent(apiKey, request)
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No analysis available"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
