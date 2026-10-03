package com.example.automation

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WorkLogEntry(
    val timestamp: String,
    val message: String,
    val isSuccess: Boolean = false,
    val isWarning: Boolean = false
)

enum class WorkerPhase {
    IDLE,
    PLANNING,
    NAVIGATING,
    SEARCHING,
    FILTERING,
    EXECUTING_MODULES,
    LOGIN_REQUIRED,
    COMPLETED,
    ERROR
}

data class WorkerState(
    val isRunning: Boolean = false,
    val phase: WorkerPhase = WorkerPhase.IDLE,
    val currentInstruction: String = "goto infosys springboard home page and goto search and enter Spring 5 Basics and add filter course and complete on task",
    val activeUrl: String = "https://www.google.com",
    val pageTitle: String = "Google Search",
    val isLoginRequired: Boolean = false,
    val isFloatingWindowOpen: Boolean = false, // Controlled by 👁️ View Button on Home Screen
    val isLiveViewVisible: Boolean = true,
    val isTextInputVisible: Boolean = true, // Controlled by 💬 Text Button on Home Screen
    val detectedCourseName: String? = null,
    val currentStepStatus: String = "Ready for autonomous task.",
    val logs: List<WorkLogEntry> = emptyList()
)

object AiWorkerBrowserManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var workerJob: Job? = null

    private val _state = MutableStateFlow(WorkerState())
    val state: StateFlow<WorkerState> = _state.asStateFlow()

    @SuppressLint("StaticFieldLeak")
    private var internalWebView: WebView? = null

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun getOrCreateWebView(context: Context): WebView {
        if (internalWebView == null) {
            val appCtx = context.applicationContext
            internalWebView = WebView(appCtx).apply {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    userAgentString = userAgentString.replace("; wv", "")
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        url?.let {
                            _state.update { s -> s.copy(activeUrl = it) }
                        }
                        view?.title?.let {
                            if (!it.contains("Webpage not available", ignoreCase = true)) {
                                _state.update { s -> s.copy(pageTitle = it) }
                            }
                        }
                        view?.let { checkLoginRequirement(it) }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            addLog("⚠️ Unresolved domain: ${request.url}. Falling back to Google Search...", isWarning = true)
                            view?.loadUrl("https://www.google.com")
                        }
                    }

                    @Suppress("DEPRECATION")
                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        super.onReceivedError(view, errorCode, description, failingUrl)
                        addLog("⚠️ Unresolved domain: '$failingUrl'. Falling back to Google Search...", isWarning = true)
                        view?.loadUrl("https://www.google.com")
                    }
                }
                loadUrl("https://www.google.com")
            }
        }
        return internalWebView!!
    }

    fun updateInstruction(text: String) {
        _state.update { it.copy(currentInstruction = text) }
    }

    fun toggleTextInput() {
        _state.update { it.copy(isTextInputVisible = !it.isTextInputVisible) }
    }

    fun openFloatingWindow() {
        _state.update { it.copy(isFloatingWindowOpen = true) }
    }

    fun closeFloatingWindow() {
        _state.update { it.copy(isFloatingWindowOpen = false) }
    }

    fun toggleFloatingWindow() {
        _state.update { it.copy(isFloatingWindowOpen = !it.isFloatingWindowOpen) }
    }

    fun toggleLiveWorkView() {
        _state.update { it.copy(isLiveViewVisible = !it.isLiveViewVisible) }
    }

    fun setPageDetails(url: String, title: String) {
        if (!title.contains("Webpage not available", ignoreCase = true)) {
            _state.update {
                it.copy(
                    activeUrl = url,
                    pageTitle = title
                )
            }
        }
    }

    fun checkLoginRequirement(webView: WebView) {
        val js = """
            (function() {
                try {
                    const hasPasswordField = document.querySelector('input[type="password"]') !== null;
                    const url = window.location.href.toLowerCase();
                    const isAuthUrl = url.includes('login') || url.includes('signin') || url.includes('auth') || url.includes('saml');
                    return JSON.stringify({ isLogin: hasPasswordField || isAuthUrl });
                } catch(e) {
                    return JSON.stringify({ isLogin: false });
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { raw ->
            val isLogin = raw?.contains("\"isLogin\":true") == true
            if (isLogin && !_state.value.isLoginRequired) {
                addLog("⚠️ Login or Sign-up detected! Please tap 'View' to sign in once.", isWarning = true)
                _state.update { it.copy(isLoginRequired = true, phase = WorkerPhase.LOGIN_REQUIRED) }
            } else if (!isLogin && _state.value.isLoginRequired) {
                addLog("✅ User authenticated successfully! Resuming AI task...", isSuccess = true)
                _state.update { it.copy(isLoginRequired = false) }
            }
        }
    }

    fun addLog(message: String, isSuccess: Boolean = false, isWarning: Boolean = false) {
        val entry = WorkLogEntry(
            timestamp = timeFormat.format(Date()),
            message = message,
            isSuccess = isSuccess,
            isWarning = isWarning
        )
        _state.update { it.copy(logs = it.logs + entry) }
    }

    /**
     * Executes the autonomous AI Worker task using BrowserAutomationEngine.
     */
    fun runAutonomousTask(context: Context, customInstruction: String? = null, providedWebView: WebView? = null) {
        val instruction = customInstruction ?: _state.value.currentInstruction
        if (instruction.isBlank()) return

        val webView = providedWebView ?: getOrCreateWebView(context)

        _state.update {
            it.copy(
                isRunning = true,
                currentInstruction = instruction,
                phase = WorkerPhase.PLANNING,
                currentStepStatus = "Starting BrowserAutomationEngine with Google Search..."
            )
        }

        BrowserAutomationEngine.executeTask(
            context = context,
            webView = webView,
            userInstruction = instruction
        ) { status, isDone, verifiedCourse ->
            _state.update { s ->
                s.copy(
                    isRunning = !isDone,
                    phase = if (isDone) WorkerPhase.COMPLETED else WorkerPhase.EXECUTING_MODULES,
                    currentStepStatus = status,
                    detectedCourseName = verifiedCourse ?: s.detectedCourseName
                )
            }
        }
    }
}
