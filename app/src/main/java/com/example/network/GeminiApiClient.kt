package com.example.network

import android.content.Context
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import com.example.BuildConfig
import com.example.data.SettingsRepository
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
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
            .certificatePinner(CertificatePinner.DEFAULT)
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

    fun isFirebaseAiConfigured(context: Context): Boolean {
        return runCatching {
            FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
        }.getOrDefault(false)
    }

    fun isBuildConfigApiKeyValid(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY.trim()
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY" && key != "null"
    }

    fun resolveActiveApiKey(customApiKey: String = ""): String {
        val trimmedCustom = customApiKey.trim()
        if (trimmedCustom.isNotEmpty()) return trimmedCustom
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        return if (isBuildConfigApiKeyValid()) buildKey else ""
    }

    fun isApiKeyConfigured(customApiKey: String = ""): Boolean {
        return resolveActiveApiKey(customApiKey).isNotEmpty()
    }

    fun getMaskedApiKeyStatus(customApiKey: String = ""): String {
        val activeKey = resolveActiveApiKey(customApiKey)
        val sourceLabel = if (customApiKey.trim().isNotEmpty()) "App Settings" else "BuildConfig"
        return if (activeKey.isEmpty()) {
            "Not configured — Paste your Gemini API key below or in the floating panel"
        } else if (activeKey.length > 8) {
            "Active via $sourceLabel (${activeKey.take(4)}••••${activeKey.takeLast(4)})"
        } else {
            "Active via $sourceLabel"
        }
    }

    fun isInternetAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun buildModelFallbackList(preferredModelId: String): List<String> {
        return listOf(
            preferredModelId,
            "gemini-flash-latest",
            "gemini-2.5-flash",
            "gemini-3.5-flash",
            "gemini-3.1-flash-lite-preview",
            "gemini-2.0-flash"
        ).distinct()
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

        val savedCustomKey = runCatching {
            SettingsRepository.getInstance(context.applicationContext).settingsFlow.first().customApiKey
        }.getOrDefault("")

        val apiKey = resolveActiveApiKey(savedCustomKey)

        // Priority 1: If no API key is entered, try Firebase AI Logic if configured
        if (apiKey.isEmpty() && isFirebaseAiConfigured(context)) {
            val firebaseAttempt = runCatching {
                val imageBytes = Base64.decode(base64JpegImage, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: throw IllegalStateException("Could not decode screen bitmap for Firebase AI")

                val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
                    .generativeModel(
                        modelName = modelId,
                        generationConfig = generationConfig {
                            temperature = 0.25f
                            topP = 0.9f
                            maxOutputTokens = 1536
                        },
                        systemInstruction = content { text(systemInstructionText) }
                    )

                val response = generativeModel.generateContent(
                    content {
                        text(promptText)
                        image(bitmap)
                    }
                )
                response.text?.trim()
            }

            val fbText = firebaseAttempt.getOrNull()
            if (!fbText.isNullOrEmpty()) {
                return@withContext GeminiResult.Success(
                    text = fbText,
                    modelUsed = "$modelId (Firebase AI)"
                )
            }
        }

        // Priority 2: Direct REST API using saved custom key or BuildConfig key
        if (apiKey.isEmpty()) {
            return@withContext GeminiResult.Error(
                title = "API Key Required",
                message = "Paste your Gemini API key below (or in ScreenAI Settings) to start analyzing your screen immediately.",
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

        val candidateModels = buildModelFallbackList(modelId)
        var lastException: Exception? = null

        for (candidateModel in candidateModels) {
            try {
                val response = service.generateContent(
                    model = candidateModel,
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

                if (!answerText.isNullOrEmpty()) {
                    return@withContext GeminiResult.Success(
                        text = answerText,
                        modelUsed = candidateModel
                    )
                } else {
                    return@withContext GeminiResult.Error(
                        title = "Empty AI Response",
                        message = "Gemini analyzed the screenshot but returned no text. Tap Regenerate to try again.",
                        errorType = GeminiErrorType.EMPTY_RESPONSE
                    )
                }
            } catch (e: HttpException) {
                lastException = e
                // If 404 (Model not found in this region/tier), automatically try the next fallback model
                if (e.code() == 404) {
                    continue
                }
                break
            } catch (e: Exception) {
                lastException = e
                break
            }
        }

        return@withContext mapExceptionToGeminiError(lastException, apiKey)
    }

    private fun mapExceptionToGeminiError(e: Exception?, usedKey: String): GeminiResult.Error {
        return when (e) {
            is SocketTimeoutException -> GeminiResult.Error(
                title = "AI Response Timeout",
                message = "The request took too long to complete. Check your connection speed and tap Retry.",
                errorType = GeminiErrorType.TIMEOUT
            )
            is UnknownHostException -> GeminiResult.Error(
                title = "Network Unreachable",
                message = "Could not reach Google Gemini servers. Please check your internet connection.",
                errorType = GeminiErrorType.NO_INTERNET
            )
            is HttpException -> {
                val code = e.code()
                val errorBody = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
                when {
                    code == 400 && errorBody.contains("API_KEY_INVALID", ignoreCase = true) ||
                        code == 401 || code == 403 -> {
                        val formatHint = if (!usedKey.startsWith("AIza")) {
                            " Note: Standard Google AI Studio Gemini API keys start with 'AIza...' (from aistudio.google.com/app/apikey)."
                        } else {
                            ""
                        }
                        GeminiResult.Error(
                            title = "Invalid Gemini API Key ($code)",
                            message = "The API key was rejected by Google Gemini servers.$formatHint Please paste a valid Gemini API key below.",
                            errorType = GeminiErrorType.INVALID_API_KEY
                        )
                    }
                    code == 429 -> GeminiResult.Error(
                        title = "Rate Limit Reached (429)",
                        message = "Quota or rate limit reached on this API key. Wait a few seconds and tap Retry.",
                        errorType = GeminiErrorType.API_FAILURE
                    )
                    else -> GeminiResult.Error(
                        title = "Gemini API Error ($code)",
                        message = "The AI service returned HTTP $code. Please try again or switch models in Settings.",
                        errorType = GeminiErrorType.API_FAILURE
                    )
                }
            }
            is IOException -> GeminiResult.Error(
                title = "Network Connection Error",
                message = "A network error interrupted the screen analysis (${e.localizedMessage ?: "IO error"}).",
                errorType = GeminiErrorType.NO_INTERNET
            )
            else -> GeminiResult.Error(
                title = "AI Processing Failed",
                message = e?.localizedMessage ?: "An unexpected error occurred while communicating with Gemini.",
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

            val savedCustomKey = runCatching {
                SettingsRepository.getInstance(context.applicationContext).settingsFlow.first().customApiKey
            }.getOrDefault("")
            val apiKey = resolveActiveApiKey(savedCustomKey)

            if (apiKey.isEmpty() && isFirebaseAiConfigured(context)) {
                val fbResult = runCatching {
                    val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                        .generativeModel(modelName = modelId)
                    model.generateContent("Reply with: ScreenAI Firebase Secure Connected.").text?.trim()
                }.getOrNull()
                if (!fbResult.isNullOrEmpty()) {
                    return@withContext GeminiResult.Success(
                        text = fbResult,
                        modelUsed = "$modelId (Firebase AI)"
                    )
                }
            }

            if (apiKey.isEmpty()) {
                return@withContext GeminiResult.Error(
                    title = "API Key Not Set",
                    message = "Paste your Gemini API key into the field above and tap Save Key first.",
                    errorType = GeminiErrorType.INVALID_API_KEY
                )
            }

            val request = GenerateContentRequest(
                contents = listOf(
                    Content(
                        role = "user",
                        parts = listOf(Part(text = "Reply with: ScreenAI Vision Connected."))
                    )
                ),
                generationConfig = GenerationConfig(maxOutputTokens = 32)
            )

            var lastException: Exception? = null
            for (candidateModel in buildModelFallbackList(modelId)) {
                try {
                    val response = service.generateContent(
                        model = candidateModel,
                        apiKey = apiKey,
                        request = request
                    )
                    val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                    if (!text.isNullOrEmpty()) {
                        return@withContext GeminiResult.Success(text = text, modelUsed = candidateModel)
                    }
                } catch (e: HttpException) {
                    lastException = e
                    if (e.code() == 404) continue
                    break
                } catch (e: Exception) {
                    lastException = e
                    break
                }
            }

            mapExceptionToGeminiError(lastException, apiKey)
        }

    suspend fun generateText(
        context: Context,
        prompt: String,
        systemInstruction: String = ""
    ): GeminiResult = withContext(Dispatchers.IO) {
        if (!isInternetAvailable(context)) {
            return@withContext GeminiResult.Error(
                title = "Internet Unavailable",
                message = "No active internet connection.",
                errorType = GeminiErrorType.NO_INTERNET
            )
        }

        val savedCustomKey = runCatching {
            SettingsRepository.getInstance(context.applicationContext).settingsFlow.first().customApiKey
        }.getOrDefault("")
        val apiKey = resolveActiveApiKey(savedCustomKey)

        val modelsToTry = listOf("gemini-2.5-flash", "gemini-flash-latest", "gemini-3.5-flash")
        for (modelId in modelsToTry) {
            try {
                if (apiKey.isEmpty() && isFirebaseAiConfigured(context)) {
                    val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                        .generativeModel(
                            modelName = modelId,
                            systemInstruction = if (systemInstruction.isNotBlank()) content { text(systemInstruction) } else null
                        )
                    val resp = model.generateContent(prompt).text
                    if (!resp.isNullOrBlank()) {
                        return@withContext GeminiResult.Success(resp, modelId)
                    }
                }

                if (apiKey.isNotEmpty()) {
                    val request = GenerateContentRequest(
                        contents = listOf(
                            Content(
                                role = "user",
                                parts = listOf(Part(text = prompt))
                            )
                        ),
                        systemInstruction = if (systemInstruction.isNotBlank()) Content(
                            parts = listOf(Part(text = systemInstruction))
                        ) else null,
                        generationConfig = GenerationConfig(temperature = 0.2f, maxOutputTokens = 1024)
                    )
                    val response = service.generateContent(modelId, apiKey, request)
                    val reply = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!reply.isNullOrBlank()) {
                        return@withContext GeminiResult.Success(reply.trim(), modelId)
                    }
                }
            } catch (_: Exception) {
                // Try next model
            }
        }

        return@withContext GeminiResult.Error(
            "Generation Failed",
            "Could not generate text with Gemini.",
            GeminiErrorType.API_FAILURE
        )
    }
}
