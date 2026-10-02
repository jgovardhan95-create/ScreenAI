package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val role: String? = null,
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float? = 0.3f,
    val topP: Float? = 0.9f,
    val maxOutputTokens: Int? = 1536
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null,
    val promptFeedback: PromptFeedback? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class PromptFeedback(
    val blockReason: String? = null
)

interface GeminiRestService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

sealed class GeminiResult {
    data class Success(val text: String, val modelUsed: String) : GeminiResult()
    data class Error(
        val title: String,
        val message: String,
        val errorType: GeminiErrorType
    ) : GeminiResult()
}

enum class GeminiErrorType {
    NO_INTERNET,
    INVALID_API_KEY,
    TIMEOUT,
    API_FAILURE,
    EMPTY_RESPONSE
}

object GeminiApiClient {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val service: GeminiRestService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiRestService::class.java)
    }

    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY.trim()
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY" && key != "null"
    }

    fun getMaskedApiKeyStatus(): String {
        val key = BuildConfig.GEMINI_API_KEY.trim()
        return if (!isApiKeyConfigured()) {
            "Not configured (Add GEMINI_API_KEY in AI Studio Secrets panel)"
        } else if (key.length > 8) {
            "Active (${key.take(4)}••••${key.takeLast(4)})"
        } else {
            "Active (Configured via BuildConfig)"
        }
    }

    fun isInternetAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun analyzeScreenWithGemini(
        context: Context,
        modelId: String,
        systemInstructionText: String,
        promptText: String,
        base64JpegImage: String
    ): GeminiResult = withContext(Dispatchers.IO) {
        if (!isInternetAvailable(context)) {
            return@withContext GeminiResult.Error(
                title = "Internet Unavailable",
                message = "No active network connection detected. Please connect to Wi-Fi or mobile data and try again.",
                errorType = GeminiErrorType.NO_INTERNET
            )
        }

        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (!isApiKeyConfigured()) {
            return@withContext GeminiResult.Error(
                title = "Invalid or Missing API Key",
                message = "GEMINI_API_KEY is not set. Please open the Secrets panel in AI Studio and add your Gemini API key.",
                errorType = GeminiErrorType.INVALID_API_KEY
            )
        }

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    role = "user",
                    parts = listOf(
                        Part(text = promptText),
                        Part(
                            inlineData = InlineData(
                                mimeType = "image/jpeg",
                                data = base64JpegImage
                            )
                        )
                    )
                )
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = systemInstructionText))
            ),
            generationConfig = GenerationConfig(
                temperature = 0.25f,
                topP = 0.9f,
                maxOutputTokens = 1536
            )
        )

        try {
            val response = service.generateContent(
                model = modelId,
                apiKey = apiKey,
                request = request
            )

            val blockReason = response.promptFeedback?.blockReason
            if (!blockReason.isNullOrBlank()) {
                return@withContext GeminiResult.Error(
                    title = "Content Blocked",
                    message = "Gemini could not process this screen (Reason: $blockReason).",
                    errorType = GeminiErrorType.API_FAILURE
                )
            }

            val answerText = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.mapNotNull { it.text }
                ?.joinToString("\n")
                ?.trim()

            if (answerText.isNullOrEmpty()) {
                GeminiResult.Error(
                    title = "Empty AI Response",
                    message = "Gemini analyzed the screenshot but returned no text. Try selecting another mode or tapping Regenerate.",
                    errorType = GeminiErrorType.EMPTY_RESPONSE
                )
            } else {
                GeminiResult.Success(text = answerText, modelUsed = modelId)
            }
        } catch (e: SocketTimeoutException) {
            GeminiResult.Error(
                title = "AI Response Timeout",
                message = "The request took too long to complete. Try switching to Gemini 3.5 Flash or check your connection speed.",
                errorType = GeminiErrorType.TIMEOUT
            )
        } catch (e: UnknownHostException) {
            GeminiResult.Error(
                title = "Network Unreachable",
                message = "Could not reach Google Gemini servers. Please check your internet connection.",
                errorType = GeminiErrorType.NO_INTERNET
            )
        } catch (e: HttpException) {
            val code = e.code()
            val errorBody = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
            when {
                code == 400 && errorBody.contains("API_KEY_INVALID", ignoreCase = true) ||
                    code == 401 || code == 403 -> {
                    GeminiResult.Error(
                        title = "Invalid API Key ($code)",
                        message = "Your Gemini API key was rejected by the server. Verify GEMINI_API_KEY in the AI Studio Secrets panel.",
                        errorType = GeminiErrorType.INVALID_API_KEY
                    )
                }
                code == 429 -> {
                    GeminiResult.Error(
                        title = "Rate Limit Reached (429)",
                        message = "Too many requests sent to Gemini API in a short window. Please wait a few seconds and tap Regenerate.",
                        errorType = GeminiErrorType.API_FAILURE
                    )
                }
                else -> {
                    GeminiResult.Error(
                        title = "Gemini API Error ($code)",
                        message = "The AI service encountered an issue (HTTP $code). Please try regenerating or switching models in Settings.",
                        errorType = GeminiErrorType.API_FAILURE
                    )
                }
            }
        } catch (e: IOException) {
            GeminiResult.Error(
                title = "Network Connection Error",
                message = "A network error interrupted the screen analysis (${e.localizedMessage ?: "IO error"}).",
                errorType = GeminiErrorType.NO_INTERNET
            )
        } catch (e: Exception) {
            GeminiResult.Error(
                title = "AI Processing Failed",
                message = e.localizedMessage ?: "An unexpected error occurred while communicating with Gemini.",
                errorType = GeminiErrorType.API_FAILURE
            )
        }
    }

    suspend fun testConnection(context: Context, modelId: String): GeminiResult =
        withContext(Dispatchers.IO) {
            if (!isInternetAvailable(context)) {
                return@withContext GeminiResult.Error(
                    title = "Internet Unavailable",
                    message = "Cannot verify Gemini API without an active internet connection.",
                    errorType = GeminiErrorType.NO_INTERNET
                )
            }
            if (!isApiKeyConfigured()) {
                return@withContext GeminiResult.Error(
                    title = "API Key Not Configured",
                    message = "Add your GEMINI_API_KEY via the Secrets panel in AI Studio to enable live responses.",
                    errorType = GeminiErrorType.INVALID_API_KEY
                )
            }
            try {
                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            role = "user",
                            parts = listOf(Part(text = "Reply with: ScreenAI Vision Connected."))
                        )
                    ),
                    generationConfig = GenerationConfig(maxOutputTokens = 32)
                )
                val response = service.generateContent(
                    model = modelId,
                    apiKey = BuildConfig.GEMINI_API_KEY.trim(),
                    request = request
                )
                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                if (!text.isNullOrEmpty()) {
                    GeminiResult.Success(text = text, modelUsed = modelId)
                } else {
                    GeminiResult.Error(
                        title = "Empty Verification Response",
                        message = "Connected to server, but received an empty response.",
                        errorType = GeminiErrorType.EMPTY_RESPONSE
                    )
                }
            } catch (e: Exception) {
                GeminiResult.Error(
                    title = "Connection Test Failed",
                    message = e.localizedMessage ?: "Unable to verify Gemini API connection.",
                    errorType = GeminiErrorType.API_FAILURE
                )
            }
        }
}
