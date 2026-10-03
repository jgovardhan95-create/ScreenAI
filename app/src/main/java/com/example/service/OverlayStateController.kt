package com.example.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.automation.TaskAutomationManager
import com.example.capture.CaptureOutcome
import com.example.capture.ScreenCaptureManager
import com.example.data.AiMode
import com.example.data.AppSettings
import com.example.data.HistoryRepository
import com.example.data.SettingsRepository
import com.example.imaging.ImageProcessor
import com.example.imaging.ProcessedScreenImage
import com.example.network.GeminiApiClient
import com.example.network.GeminiErrorType
import com.example.network.GeminiResult
import com.example.prompt.PromptGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OverlayDisplayState {
    BUBBLE,
    MINIMIZED_PILL,
    EXPANDED_PANEL
}

enum class CapturePhase {
    IDLE,
    CAPTURING_SCREEN,
    ANALYZING_WITH_GEMINI,
    RESULT_READY,
    ERROR
}

data class OverlayUiState(
    val isServiceRunning: Boolean = false,
    val displayState: OverlayDisplayState = OverlayDisplayState.BUBBLE,
    val activeMode: AiMode = AiMode.ANSWER,
    val customAskText: String = "",
    val isAskInputExpanded: Boolean = false,
    val capturePhase: CapturePhase = CapturePhase.IDLE,
    val aiResponse: String = "",
    val errorTitle: String? = null,
    val errorMessage: String? = null,
    val needsApiKeyInput: Boolean = false,
    val inlineApiKeyDraft: String = "",
    val lastCapturedPreview: Bitmap? = null,
    val lastBase64Image: String? = null,
    val modelBadge: String = "",
    val feedbackBanner: String? = null,
    val isOverlayHiddenForCapture: Boolean = false
)

object OverlayStateController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeAnalysisJob: Job? = null

    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    // Pending mode to execute immediately after ScreenCaptureAcquireActivity grants MediaProjection
    @Volatile
    private var pendingModeAfterPermission: AiMode? = null

    @Volatile
    private var pendingCustomQueryAfterPermission: String? = null

    var onRequestWindowFocusable: ((Boolean) -> Unit)? = null

    fun setServiceRunning(running: Boolean) {
        _uiState.update {
            it.copy(
                isServiceRunning = running,
                displayState = if (running) it.displayState else OverlayDisplayState.BUBBLE
            )
        }
    }

    fun openPanel(context: Context) {
        triggerHapticIfEnabled(context)
        _uiState.update {
            it.copy(
                displayState = OverlayDisplayState.EXPANDED_PANEL,
                feedbackBanner = null
            )
        }
    }

    fun minimizePanel(context: Context) {
        triggerHapticIfEnabled(context)
        onRequestWindowFocusable?.invoke(false)
        _uiState.update {
            it.copy(
                displayState = OverlayDisplayState.MINIMIZED_PILL,
                isAskInputExpanded = false
            )
        }
    }

    fun collapseToBubble(context: Context) {
        triggerHapticIfEnabled(context)
        onRequestWindowFocusable?.invoke(false)
        _uiState.update {
            it.copy(
                displayState = OverlayDisplayState.BUBBLE,
                isAskInputExpanded = false,
                feedbackBanner = null
            )
        }
    }

    fun updateCustomAskText(text: String) {
        _uiState.update { it.copy(customAskText = text) }
    }

    fun updateInlineApiKeyDraft(text: String) {
        _uiState.update { it.copy(inlineApiKeyDraft = text) }
    }

    fun saveInlineApiKeyAndRetry(context: Context, apiKey: String) {
        val cleaned = apiKey.trim()
        if (cleaned.isEmpty()) return
        val appContext = context.applicationContext
        scope.launch {
            SettingsRepository.getInstance(appContext).setCustomApiKey(cleaned)
            onRequestWindowFocusable?.invoke(false)
            _uiState.update {
                it.copy(
                    needsApiKeyInput = false,
                    inlineApiKeyDraft = "",
                    errorTitle = null,
                    errorMessage = null
                )
            }
            regenerateLastAction(appContext, recaptureScreen = false)
        }
    }

    fun toggleAskAiMode(context: Context) {
        triggerHapticIfEnabled(context)
        val shouldExpand = !(_uiState.value.activeMode == AiMode.ASK_AI && _uiState.value.isAskInputExpanded)
        onRequestWindowFocusable?.invoke(shouldExpand)
        _uiState.update {
            it.copy(
                activeMode = AiMode.ASK_AI,
                isAskInputExpanded = shouldExpand,
                errorTitle = null,
                errorMessage = null
            )
        }
    }

    fun onPermissionAcquiredFromActivity(context: Context, granted: Boolean) {
        val pendingMode = pendingModeAfterPermission
        val pendingQuery = pendingCustomQueryAfterPermission
        pendingModeAfterPermission = null
        pendingCustomQueryAfterPermission = null

        if (granted) {
            if (pendingMode != null) {
                executeAiAction(
                    context = context,
                    mode = pendingMode,
                    customQuery = pendingQuery,
                    explicitBitmap = null,
                    reuseLastImage = false
                )
            } else {
                showFeedbackBanner("Screen capture permission ready. Tap any AI mode to analyze.")
            }
        } else {
            _uiState.update {
                it.copy(
                    displayState = OverlayDisplayState.EXPANDED_PANEL,
                    capturePhase = CapturePhase.ERROR,
                    errorTitle = "Screen Capture Permission Denied",
                    errorMessage = "ScreenAI needs Android's screen capture permission to view and analyze your screen content."
                )
            }
        }
    }

    fun executeAiAction(
        context: Context,
        mode: AiMode,
        customQuery: String? = null,
        explicitBitmap: Bitmap? = null,
        reuseLastImage: Boolean = false
    ) {
        val appContext = context.applicationContext
        triggerHapticIfEnabled(appContext)
        onRequestWindowFocusable?.invoke(false)

        activeAnalysisJob?.cancel()
        activeAnalysisJob = scope.launch {
            val settings: AppSettings = SettingsRepository.getInstance(appContext).settingsFlow.first()

            _uiState.update {
                it.copy(
                    displayState = OverlayDisplayState.EXPANDED_PANEL,
                    activeMode = mode,
                    isAskInputExpanded = (mode == AiMode.ASK_AI || mode == AiMode.WORK_IN_ONE_TIME) && customQuery.isNullOrBlank() && it.customAskText.isBlank(),
                    errorTitle = null,
                    errorMessage = null,
                    feedbackBanner = null,
                    modelBadge = settings.modelOption.displayName
                )
            }

            // If user tapped Ask AI or Work in One Time without providing a query yet, let them type first
            val resolvedQuery = (customQuery ?: _uiState.value.customAskText).trim()
            if ((mode == AiMode.ASK_AI || mode == AiMode.WORK_IN_ONE_TIME) && resolvedQuery.isEmpty()) {
                onRequestWindowFocusable?.invoke(true)
                _uiState.update {
                    it.copy(
                        isAskInputExpanded = true,
                        capturePhase = CapturePhase.IDLE
                    )
                }
                return@launch
            }

            if (mode == AiMode.WORK_IN_ONE_TIME && resolvedQuery.isNotEmpty()) {
                TaskAutomationManager.runTask(appContext, resolvedQuery)
            }

            // Step 1: Acquire or reuse screen image
            val existingBase64 = _uiState.value.lastBase64Image
            val existingPreview = _uiState.value.lastCapturedPreview

            val base64ToSend: String
            val previewToKeep: Bitmap?

            if (reuseLastImage && !existingBase64.isNullOrEmpty() && existingPreview != null) {
                base64ToSend = existingBase64
                previewToKeep = existingPreview
            } else {
                // Verify we have either an explicit bitmap or an active MediaProjection
                if (explicitBitmap == null && !ScreenCaptureManager.hasActiveProjection()) {
                    pendingModeAfterPermission = mode
                    pendingCustomQueryAfterPermission = resolvedQuery
                    ScreenCaptureAcquireActivity.launch(appContext)
                    return@launch
                }

                // Temporarily hide overlay so we capture the clean underlying app screen
                _uiState.update {
                    it.copy(
                        capturePhase = CapturePhase.CAPTURING_SCREEN,
                        isOverlayHiddenForCapture = explicitBitmap == null
                    )
                }

                if (explicitBitmap == null) {
                    delay(180)
                }

                val captureResult = if (explicitBitmap != null) {
                    CaptureOutcome.Success(explicitBitmap)
                } else {
                    ScreenCaptureManager.captureScreenOnDemand()
                }

                // Immediately restore overlay visibility after grabbing frame
                _uiState.update { it.copy(isOverlayHiddenForCapture = false) }

                val rawBitmap = when (captureResult) {
                    is CaptureOutcome.Success -> captureResult.bitmap
                    is CaptureOutcome.PermissionRequired -> {
                        pendingModeAfterPermission = mode
                        pendingCustomQueryAfterPermission = resolvedQuery
                        ScreenCaptureAcquireActivity.launch(appContext)
                        return@launch
                    }
                    is CaptureOutcome.Failure -> {
                        _uiState.update {
                            it.copy(
                                capturePhase = CapturePhase.ERROR,
                                errorTitle = "Screenshot Capture Failed",
                                errorMessage = captureResult.message
                            )
                        }
                        return@launch
                    }
                }

                when (val processed = ImageProcessor.processCapturedBitmap(rawBitmap)) {
                    is ProcessedScreenImage.Error -> {
                        _uiState.update {
                            it.copy(
                                capturePhase = CapturePhase.ERROR,
                                errorTitle = if (processed.isBlankScreen) "Empty or Protected Screen" else "Image Processing Error",
                                errorMessage = processed.reason
                            )
                        }
                        return@launch
                    }
                    is ProcessedScreenImage.Success -> {
                        base64ToSend = processed.base64Jpeg
                        previewToKeep = processed.previewBitmap
                    }
                }
            }

            // Step 2: Send to vision-capable Gemini model
            _uiState.update {
                it.copy(
                    capturePhase = CapturePhase.ANALYZING_WITH_GEMINI,
                    lastCapturedPreview = previewToKeep,
                    lastBase64Image = base64ToSend,
                    aiResponse = ""
                )
            }

            val systemPrompt = PromptGenerator.buildSystemInstruction(settings.responseLength)
            val modePrompt = PromptGenerator.buildModePrompt(
                mode = mode,
                customInstruction = resolvedQuery,
                responseLength = settings.responseLength
            )

            val geminiResult = GeminiApiClient.analyzeScreenWithGemini(
                context = appContext,
                modelId = settings.modelOption.modelId,
                systemInstructionText = systemPrompt,
                promptText = modePrompt,
                base64JpegImage = base64ToSend
            )

            when (geminiResult) {
                is GeminiResult.Success -> {
                    triggerHapticIfEnabled(appContext)
                    _uiState.update {
                        it.copy(
                            capturePhase = CapturePhase.RESULT_READY,
                            aiResponse = geminiResult.text,
                            errorTitle = null,
                            errorMessage = null
                        )
                    }
                    HistoryRepository.getInstance(appContext).recordAnalysis(
                        mode = mode,
                        customQuery = resolvedQuery.takeIf { it.isNotEmpty() },
                        response = geminiResult.text,
                        modelId = geminiResult.modelUsed
                    )
                }
                is GeminiResult.Error -> {
                    val isKeyError = geminiResult.errorType == GeminiErrorType.INVALID_API_KEY
                    if (isKeyError) {
                        onRequestWindowFocusable?.invoke(true)
                    }
                    _uiState.update {
                        it.copy(
                            capturePhase = CapturePhase.ERROR,
                            errorTitle = geminiResult.title,
                            errorMessage = geminiResult.message,
                            needsApiKeyInput = isKeyError
                        )
                    }
                }
            }
        }
    }

    fun regenerateLastAction(context: Context, recaptureScreen: Boolean = false) {
        val state = _uiState.value
        executeAiAction(
            context = context,
            mode = state.activeMode,
            customQuery = state.customAskText,
            explicitBitmap = null,
            reuseLastImage = !recaptureScreen && state.lastBase64Image != null
        )
    }

    fun copyResponseToClipboard(context: Context) {
        val text = _uiState.value.aiResponse
        if (text.isBlank()) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("ScreenAI Answer", text))
        triggerHapticIfEnabled(context)
        showFeedbackBanner("Copied response to clipboard")
    }

    fun saveCapturedFrameExplicitly(context: Context) {
        val preview = _uiState.value.lastCapturedPreview ?: return
        scope.launch {
            val result = ImageProcessor.saveScreenshotExplicitly(context.applicationContext, preview)
            val message = result.getOrElse { "Could not save screenshot: ${it.localizedMessage}" }
            showFeedbackBanner(message)
        }
    }

    fun clearSessionState() {
        activeAnalysisJob?.cancel()
        ScreenCaptureManager.clearTemporaryMemory()
        _uiState.update {
            it.copy(
                capturePhase = CapturePhase.IDLE,
                aiResponse = "",
                errorTitle = null,
                errorMessage = null,
                lastCapturedPreview = null,
                lastBase64Image = null,
                feedbackBanner = "Cleared temporary screen data"
            )
        }
    }

    private fun showFeedbackBanner(message: String) {
        scope.launch {
            _uiState.update { it.copy(feedbackBanner = message) }
            delay(2600)
            _uiState.update { state ->
                if (state.feedbackBanner == message) state.copy(feedbackBanner = null) else state
            }
        }
    }

    fun triggerHapticIfEnabled(context: Context) {
        scope.launch {
            val enabled = runCatching {
                SettingsRepository.getInstance(context.applicationContext).settingsFlow.first().vibrationEnabled
            }.getOrDefault(true)
            if (!enabled) return@launch

            runCatching {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(18L)
                }
            }
        }
    }
}
