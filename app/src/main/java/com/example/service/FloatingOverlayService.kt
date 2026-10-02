package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.capture.ScreenCaptureManager
import com.example.data.AppSettings
import com.example.data.SettingsRepository
import com.example.ui.overlay.FloatingAssistantOverlayRoot
import com.example.ui.theme.ScreenAITheme
import kotlin.math.roundToInt

class FloatingOverlayService :
    Service(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var composeOverlayView: ComposeView? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        createNotificationChannel()
        OverlayStateController.setServiceRunning(true)
        OverlayStateController.onRequestWindowFocusable = { focusable ->
            updateWindowFocusable(focusable)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_OVERLAY) {
            stopSelf()
            return START_NOT_STICKY
        }

        val hasProjectionExtra = intent?.getBooleanExtra(EXTRA_HAS_PROJECTION, false) == true
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        // Start foreground with appropriate type based on whether MediaProjection token is supplied
        promoteToForeground(withMediaProjectionType = hasProjectionExtra && resultData != null)

        if (hasProjectionExtra && resultData != null) {
            val initialized = ScreenCaptureManager.initializeMediaProjection(
                context = applicationContext,
                resultCode = resultCode,
                resultData = resultData
            )
            if (intent?.getBooleanExtra(EXTRA_NOTIFY_CONTROLLER, false) == true) {
                OverlayStateController.onPermissionAcquiredFromActivity(
                    context = applicationContext,
                    granted = initialized
                )
            }
        }

        if (Settings.canDrawOverlays(this)) {
            ensureOverlayAttached()
        }

        return START_STICKY
    }

    private fun promoteToForeground(withMediaProjectionType: Boolean) {
        val notification = buildForegroundNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val fgType = if (withMediaProjectionType || ScreenCaptureManager.hasActiveProjection()) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
                startForeground(NOTIFICATION_ID, notification, fgType)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (withMediaProjectionType || ScreenCaptureManager.hasActiveProjection()) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Fallback to basic startForeground if OS restricts specific FGS type
            runCatching { startForeground(NOTIFICATION_ID, notification) }
        }
    }

    private fun ensureOverlayAttached() {
        if (composeOverlayView != null) return

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 32
            y = 320
        }
        windowLayoutParams = params

        val settingsRepo = SettingsRepository.getInstance(applicationContext)
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeViewModelStoreOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)

            setContent {
                val uiState by OverlayStateController.uiState.collectAsState()
                val settings by settingsRepo.settingsFlow.collectAsState(initial = AppSettings())

                ScreenAITheme(themePreference = settings.themePreference) {
                    FloatingAssistantOverlayRoot(
                        uiState = uiState,
                        settings = settings,
                        onDragBy = { dx, dy -> moveOverlayBy(dx, dy) },
                        onTapBubble = { OverlayStateController.openPanel(applicationContext) },
                        onMinimizePanel = { OverlayStateController.minimizePanel(applicationContext) },
                        onClosePanelToBubble = { OverlayStateController.collapseToBubble(applicationContext) },
                        onSelectMode = { mode ->
                            if (mode == com.example.data.AiMode.ASK_AI) {
                                OverlayStateController.toggleAskAiMode(applicationContext)
                            } else {
                                OverlayStateController.executeAiAction(
                                    context = applicationContext,
                                    mode = mode
                                )
                            }
                        },
                        onCustomQueryChange = { OverlayStateController.updateCustomAskText(it) },
                        onSubmitCustomQuery = { query ->
                            OverlayStateController.executeAiAction(
                                context = applicationContext,
                                mode = com.example.data.AiMode.ASK_AI,
                                customQuery = query
                            )
                        },
                        onCopyResponse = {
                            OverlayStateController.copyResponseToClipboard(applicationContext)
                        },
                        onRegenerate = { recapture ->
                            OverlayStateController.regenerateLastAction(
                                context = applicationContext,
                                recaptureScreen = recapture
                            )
                        },
                        onSaveScreenshotExplicitly = {
                            OverlayStateController.saveCapturedFrameExplicitly(applicationContext)
                        }
                    )
                }
            }
        }

        runCatching {
            wm.addView(view, params)
            composeOverlayView = view
        }
    }

    private fun moveOverlayBy(dx: Float, dy: Float) {
        val view = composeOverlayView ?: return
        val params = windowLayoutParams ?: return
        val wm = windowManager ?: return

        val metrics = resources.displayMetrics
        params.x = (params.x + dx.roundToInt()).coerceIn(0, (metrics.widthPixels - 120).coerceAtLeast(100))
        params.y = (params.y + dy.roundToInt()).coerceIn(48, (metrics.heightPixels - 160).coerceAtLeast(200))

        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun updateWindowFocusable(focusable: Boolean) {
        val view = composeOverlayView ?: return
        val params = windowLayoutParams ?: return
        val wm = windowManager ?: return

        params.flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { wm.updateViewLayout(view, params) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the ScreenAI floating button active over other apps"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, FloatingOverlayService::class.java).apply {
            action = ACTION_STOP_OVERLAY
        }
        val pendingStop = PendingIntent.getService(
            this,
            101,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Assistant",
                pendingStop
            )
            .build()
    }

    override fun onDestroy() {
        OverlayStateController.onRequestWindowFocusable = null
        OverlayStateController.setServiceRunning(false)
        composeOverlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        composeOverlayView = null
        ScreenCaptureManager.stopProjection()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "screenai_overlay_channel"
        private const val NOTIFICATION_ID = 4201

        private const val ACTION_START_OVERLAY = "com.example.action.START_OVERLAY"
        private const val ACTION_STOP_OVERLAY = "com.example.action.STOP_OVERLAY"

        private const val EXTRA_HAS_PROJECTION = "extra_has_projection"
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val EXTRA_NOTIFY_CONTROLLER = "extra_notify_controller"

        fun startOverlayOnly(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_START_OVERLAY
                putExtra(EXTRA_HAS_PROJECTION, false)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startWithProjection(
            context: Context,
            resultCode: Int,
            resultData: Intent,
            notifyControllerAfterInit: Boolean = false
        ) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_START_OVERLAY
                putExtra(EXTRA_HAS_PROJECTION, true)
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
                putExtra(EXTRA_NOTIFY_CONTROLLER, notifyControllerAfterInit)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopOverlay(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_STOP_OVERLAY
            }
            context.stopService(intent)
        }
    }
}
