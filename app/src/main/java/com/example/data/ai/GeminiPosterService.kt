package com.example.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GenerationConfig(
    val imageConfig: ImageConfig? = null,
    val responseModalities: List<String>? = null
)

@Serializable
data class ImageConfig(
    val aspectRatio: String,
    val imageSize: String
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@Serializable
data class Candidate(
    val content: Content? = null
)

interface GeminiImageApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateImageContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

enum class PosterAiModel(val modelId: String, val displayLabelEn: String, val displayLabelBn: String) {
    STUDIO_PRO(
        modelId = "gemini-3-pro-image-preview",
        displayLabelEn = "Studio Quality (Gemini 3 Pro Image)",
        displayLabelBn = "স্টুডিও কোয়ালিটি (Gemini 3 Pro Image)"
    ),
    NANO_BANANA(
        modelId = "gemini-nano-banana-2.1",
        displayLabelEn = "Fast High-Res (Nano Banana 2.1)",
        displayLabelBn = "দ্রুত হাই-রেজোলিউশন (Nano Banana 2.1)"
    )
}

val SUPPORTED_IMAGE_SIZES = listOf("1K", "2K", "4K")
val SUPPORTED_ASPECT_RATIOS = listOf("1:1", "2:3", "3:2", "3:4", "4:3", "9:16", "16:9", "21:9")

data class GeneratedPosterResult(
    val bitmap: Bitmap?,
    val captionText: String,
    val errorMessage: String? = null
)

object GeminiPosterClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val service: GeminiImageApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiImageApiService::class.java)
    }

    suspend fun generateEmergencyPoster(
        prompt: String,
        model: PosterAiModel,
        imageSize: String,
        aspectRatio: String
    ): GeneratedPosterResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext GeneratedPosterResult(
                bitmap = null,
                captionText = "",
                errorMessage = "Please configure GEMINI_API_KEY in the AI Studio Secrets panel to generate live AI images."
            )
        }

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(text = prompt))
                )
            ),
            generationConfig = GenerationConfig(
                imageConfig = ImageConfig(
                    aspectRatio = aspectRatio,
                    imageSize = imageSize
                ),
                responseModalities = listOf("TEXT", "IMAGE")
            )
        )

        try {
            val response = service.generateImageContent(
                model = model.modelId,
                apiKey = apiKey,
                request = request
            )
            val parts = response.candidates?.firstOrNull()?.content?.parts.orEmpty()
            val textPart = parts.firstOrNull { !it.text.isNullOrBlank() }?.text.orEmpty()
            val inlineImage = parts.firstOrNull { it.inlineData != null }?.inlineData

            if (inlineImage != null && inlineImage.data.isNotBlank()) {
                val decodedBytes = Base64.decode(inlineImage.data, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                GeneratedPosterResult(
                    bitmap = bitmap,
                    captionText = textPart.ifBlank { "Rokkhon AI Emergency Poster ($imageSize • $aspectRatio)" }
                )
            } else {
                GeneratedPosterResult(
                    bitmap = null,
                    captionText = textPart,
                    errorMessage = "Model returned no image payload. Try refining your poster description."
                )
            }
        } catch (e: Exception) {
            GeneratedPosterResult(
                bitmap = null,
                captionText = "",
                errorMessage = "Poster generation failed: ${e.localizedMessage ?: "Network error"}"
            )
        }
    }
}
