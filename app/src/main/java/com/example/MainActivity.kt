package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.FloatingOverlayService
import com.example.service.OverlayStateController
import com.example.ui.AppDestination
import com.example.ui.MainViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TestScreenAiScreen
import com.example.ui.theme.ScreenAITheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Tracks whether the user tapped "Start Floating Assistant" so we can chain permission steps seamlessly
    private var startAssistantAfterPermission = false

    private val overlaySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = Settings.canDrawOverlays(this)
        if (granted) {
            if (startAssistantAfterPermission) {
                continueStartingAssistant()
            } else {
                viewModel.showTemporaryBanner("Overlay permission granted!")
            }
        } else {
            startAssistantAfterPermission = false
            viewModel.showTemporaryBanner(
                "Overlay permission denied. Please enable 'Display over other apps' to show the floating AI button."
            )
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        if (startAssistantAfterPermission) {
            requestScreenCaptureForAssistant()
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        startAssistantAfterPermission = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            FloatingOverlayService.startWithProjection(
                context = applicationContext,
                resultCode = result.resultCode,
                resultData = result.data!!,
                notifyControllerAfterInit = false
            )
            viewModel.showTemporaryBanner(
                "Floating AI Assistant started! Drag the glowing button or tap it to analyze any screen."
            )
        } else {
            viewModel.showTemporaryBanner(
                "Screen capture permission denied. ScreenAI requires this permission to analyze screen content."
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val overlayState by viewModel.overlayState.collectAsStateWithLifecycle()
            val hasProjection by viewModel.hasProjectionPermission.collectAsStateWithLifecycle()
            val recentHistory by viewModel.recentHistory.collectAsStateWithLifecycle()
            val destination by viewModel.currentDestination.collectAsStateWithLifecycle()
            val apiVerificationState by viewModel.apiVerificationState.collectAsStateWithLifecycle()
            val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

            var hasOverlayPerm by remember { mutableStateOf(Settings.canDrawOverlays(this)) }
            var hasNotifPerm by remember { mutableStateOf(checkNotificationPermission()) }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        hasOverlayPerm = Settings.canDrawOverlays(this@MainActivity)
                        hasNotifPerm = checkNotificationPermission()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            ScreenAITheme(themePreference = settings.themePreference) {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    when (destination) {
                        AppDestination.ONBOARDING -> {
                            OnboardingScreen(
                                hasOverlayPermission = hasOverlayPerm,
                                hasScreenCapturePermission = hasProjection,
                                hasNotificationPermission = hasNotifPerm,
                                onRequestOverlayPermission = {
                                    startAssistantAfterPermission = false
                                    openOverlayPermissionSettings()
                                },
                                onRequestScreenCapturePermission = {
                                    startAssistantAfterPermission = false
                                    requestScreenCaptureForAssistant()
                                },
                                onRequestNotificationPermission = {
                                    startAssistantAfterPermission = false
                                    requestNotificationPermissionIfNeeded()
                                },
                                onContinueToHome = {
                                    viewModel.acknowledgeOnboarding()
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }

                        AppDestination.HOME -> {
                            HomeScreen(
                                settings = settings,
                                overlayState = overlayState,
                                hasOverlayPermission = hasOverlayPerm,
                                hasScreenCapturePermission = hasProjection,
                                hasNotificationPermission = hasNotifPerm,
                                recentHistory = recentHistory,
                                statusBanner = statusBanner,
                                onStartFloatingAssistant = {
                                    handleStartFloatingAssistant()
                                },
                                onStopFloatingAssistant = {
                                    FloatingOverlayService.stopOverlay(applicationContext)
                                    viewModel.showTemporaryBanner("Floating AI Assistant stopped.")
                                },
                                onOpenFloatingPanelNow = {
                                    OverlayStateController.openPanel(applicationContext)
                                },
                                onRequestOverlayPermission = {
                                    startAssistantAfterPermission = false
                                    openOverlayPermissionSettings()
                                },
                                onRequestScreenCapturePermission = {
                                    startAssistantAfterPermission = false
                                    requestScreenCaptureForAssistant()
                                },
                                onRequestNotificationPermission = {
                                    startAssistantAfterPermission = false
                                    requestNotificationPermissionIfNeeded()
                                },
                                onOpenOnboardingGuide = {
                                    viewModel.navigateTo(AppDestination.ONBOARDING)
                                },
                                onOpenTestScreenAi = {
                                    viewModel.navigateTo(AppDestination.TEST_SCREEN_AI)
                                },
                                onOpenSettings = {
                                    viewModel.navigateTo(AppDestination.SETTINGS)
                                },
                                onDeleteHistoryItem = { id ->
                                    viewModel.deleteHistoryItem(id)
                                },
                                onShowBanner = { msg ->
                                    viewModel.showTemporaryBanner(msg)
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }

                        AppDestination.TEST_SCREEN_AI -> {
                            TestScreenAiScreen(
                                settings = settings,
                                overlayState = overlayState,
                                onNavigateBack = {
                                    viewModel.navigateTo(AppDestination.HOME)
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }

                        AppDestination.SETTINGS -> {
                            SettingsScreen(
                                settings = settings,
                                apiVerificationState = apiVerificationState,
                                statusBanner = statusBanner,
                                onSelectModel = { viewModel.updateModelOption(it) },
                                onSelectResponseLength = { viewModel.updateResponseLength(it) },
                                onSelectTheme = { viewModel.updateThemePreference(it) },
                                onUpdateButtonSize = { viewModel.updateFloatingButtonSize(it) },
                                onUpdateButtonOpacity = { viewModel.updateFloatingButtonOpacity(it) },
                                onUpdateVibration = { viewModel.updateVibrationEnabled(it) },
                                onVerifyApiConnection = { viewModel.verifyGeminiConnection() },
                                onClearTemporaryData = { viewModel.clearAllTemporaryData() },
                                onNavigateBack = { viewModel.navigateTo(AppDestination.HOME) },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleStartFloatingAssistant() {
        startAssistantAfterPermission = true
        if (!Settings.canDrawOverlays(this)) {
            viewModel.showTemporaryBanner("Step 1: Grant 'Display over other apps' permission.")
            openOverlayPermissionSettings()
            return
        }
        continueStartingAssistant()
    }

    private fun continueStartingAssistant() {
        if (!checkNotificationPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        requestScreenCaptureForAssistant()
    }

    private fun requestScreenCaptureForAssistant() {
        if (com.example.capture.ScreenCaptureManager.hasActiveProjection()) {
            FloatingOverlayService.startOverlayOnly(applicationContext)
            OverlayStateController.openPanel(applicationContext)
            startAssistantAfterPermission = false
            viewModel.showTemporaryBanner("Floating AI Assistant is active!")
            return
        }

        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mpManager != null) {
            screenCaptureLauncher.launch(mpManager.createScreenCaptureIntent())
        } else {
            startAssistantAfterPermission = false
            viewModel.showTemporaryBanner("MediaProjection service is not available on this device.")
        }
    }

    private fun openOverlayPermissionSettings() {
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlaySettingsLauncher.launch(intent)
        }.onFailure {
            val fallbackIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            overlaySettingsLauncher.launch(fallbackIntent)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
