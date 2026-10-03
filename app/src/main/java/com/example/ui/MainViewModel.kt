package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.automation.AutomationTask
import com.example.automation.TaskAutomationManager
import com.example.capture.ScreenCaptureManager
import com.example.data.AnalysisHistoryItem
import com.example.data.AppSettings
import com.example.data.GeminiModelOption
import com.example.data.HistoryRepository
import com.example.data.ResponseLength
import com.example.data.SettingsRepository
import com.example.data.ThemePreference
import com.example.network.GeminiApiClient
import com.example.network.GeminiResult
import com.example.service.OverlayStateController
import com.example.service.OverlayUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppDestination {
    ONBOARDING,
    HOME,
    WORK_IN_ONE_TIME,
    AI_BROWSER,
    TEST_SCREEN_AI,
    SETTINGS
}

sealed class ApiVerificationState {
    data object Idle : ApiVerificationState()
    data object Verifying : ApiVerificationState()
    data class Success(val message: String) : ApiVerificationState()
    data class Error(val title: String, val message: String) : ApiVerificationState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository.getInstance(application)
    private val historyRepo = HistoryRepository.getInstance(application)

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    val recentHistory: StateFlow<List<AnalysisHistoryItem>> = historyRepo.recentHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val overlayState: StateFlow<OverlayUiState> = OverlayStateController.uiState
    val hasProjectionPermission: StateFlow<Boolean> = ScreenCaptureManager.hasProjectionPermission

    val currentAutomationTask: StateFlow<AutomationTask?> = TaskAutomationManager.currentTask
    val automationHistory: StateFlow<List<AutomationTask>> = TaskAutomationManager.taskHistory

    private val _currentDestination = MutableStateFlow(AppDestination.HOME)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    private val _apiVerificationState = MutableStateFlow<ApiVerificationState>(ApiVerificationState.Idle)
    val apiVerificationState: StateFlow<ApiVerificationState> = _apiVerificationState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _browserInitialUrl = MutableStateFlow("https://infy.onwingspan.com")
    val browserInitialUrl: StateFlow<String> = _browserInitialUrl.asStateFlow()

    fun openAiBrowser(url: String = "https://infy.onwingspan.com") {
        _browserInitialUrl.value = url
        _currentDestination.value = AppDestination.AI_BROWSER
    }

    fun navigateTo(destination: AppDestination) {
        _currentDestination.value = destination
    }

    fun navigateBack(): Boolean {
        return if (_currentDestination.value != AppDestination.HOME) {
            _currentDestination.value = AppDestination.HOME
            true
        } else {
            false
        }
    }

    fun acknowledgeOnboarding() {
        viewModelScope.launch {
            settingsRepo.setOnboardingAcknowledged(true)
            _currentDestination.value = AppDestination.HOME
        }
    }

    fun updateModelOption(option: GeminiModelOption) {
        viewModelScope.launch {
            settingsRepo.setModelOption(option)
        }
    }

    fun updateResponseLength(length: ResponseLength) {
        viewModelScope.launch {
            settingsRepo.setResponseLength(length)
        }
    }

    fun updateThemePreference(theme: ThemePreference) {
        viewModelScope.launch {
            settingsRepo.setThemePreference(theme)
        }
    }

    fun updateFloatingButtonSize(sizeDp: Int) {
        viewModelScope.launch {
            settingsRepo.setFloatingButtonSizeDp(sizeDp)
        }
    }

    fun updateFloatingButtonOpacity(opacity: Float) {
        viewModelScope.launch {
            settingsRepo.setFloatingButtonOpacity(opacity)
        }
    }

    fun updateVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setVibrationEnabled(enabled)
        }
    }

    fun saveCustomApiKey(apiKey: String) {
        viewModelScope.launch {
            settingsRepo.setCustomApiKey(apiKey)
            if (apiKey.trim().isNotEmpty()) {
                showTemporaryBanner("Gemini API key saved! Testing connection...")
                verifyGeminiConnection()
            } else {
                showTemporaryBanner("Cleared custom API key.")
                _apiVerificationState.value = ApiVerificationState.Idle
            }
        }
    }

    fun runAutomationTask(command: String) {
        TaskAutomationManager.runTask(getApplication(), command)
    }

    fun reRunAutomationTask(task: AutomationTask) {
        TaskAutomationManager.reRunTask(getApplication(), task)
    }

    fun clearCurrentAutomationTask() {
        TaskAutomationManager.clearCurrentTask()
    }

    fun clearAutomationHistory() {
        TaskAutomationManager.clearHistory()
    }

    fun verifyGeminiConnection() {
        viewModelScope.launch {
            _apiVerificationState.value = ApiVerificationState.Verifying
            val modelId = settings.value.modelOption.modelId
            when (val res = GeminiApiClient.testConnection(getApplication(), modelId)) {
                is GeminiResult.Success -> {
                    _apiVerificationState.value = ApiVerificationState.Success(
                        "Connected to ${res.modelUsed}: \"${res.text}\""
                    )
                }
                is GeminiResult.Error -> {
                    _apiVerificationState.value = ApiVerificationState.Error(
                        title = res.title,
                        message = res.message
                    )
                }
            }
        }
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            historyRepo.deleteItem(id)
        }
    }

    fun clearAllTemporaryData() {
        viewModelScope.launch {
            val deletedFiles = historyRepo.clearAllTemporaryData()
            OverlayStateController.clearSessionState()
            showTemporaryBanner(
                "Cleared temporary screen captures ($deletedFiles files) and session history."
            )
        }
    }

    fun showTemporaryBanner(message: String) {
        viewModelScope.launch {
            _statusBanner.value = message
            delay(3200)
            if (_statusBanner.value == message) {
                _statusBanner.value = null
            }
        }
    }
}
