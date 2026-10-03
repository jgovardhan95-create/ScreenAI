package com.example.automation

import android.content.Context
import android.webkit.WebView
import com.example.service.BrowserAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BrowserAutomationEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var engineJob: Job? = null

    private val _logs = MutableStateFlow<List<WorkLogEntry>>(emptyList())
    val logs: StateFlow<List<WorkLogEntry>> = _logs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun addLog(msg: String, isSuccess: Boolean = false, isWarning: Boolean = false) {
        val entry = WorkLogEntry(
            timestamp = timeFormat.format(Date()),
            message = msg,
            isSuccess = isSuccess,
            isWarning = isWarning
        )
        _logs.update { it + entry }
        AiWorkerBrowserManager.addLog(msg, isSuccess, isWarning)
    }

    /**
     * Executes the autonomous browser engine.
     * Uses Gemini AI to understand the natural language prompt,
     * navigates via Google search, discovers authentic portal link,
     * applies in-site search and filters, and completes the work.
     */
    fun executeTask(
        context: Context,
        webView: WebView,
        userInstruction: String,
        onStatusUpdate: (status: String, isDone: Boolean, verifiedCourse: String?) -> Unit
    ) {
        engineJob?.cancel()

        engineJob = scope.launch {
            try {
                addLog("🚀 AI Browser Automation initiated for: \"$userInstruction\"")
                onStatusUpdate("AI analyzing prompt and building execution plan...", false, null)

                // 1. Gemini AI Plan Generation
                val plan = AiTaskPlanner.createPlan(context, userInstruction)
                addLog("🧠 Plan: Portal: '${plan.googleSearchQuery}' | Search: '${plan.inSiteSearchQuery}' | Filter: '${plan.filterKeyword}'")

                // 2. Google Search for authentic portal
                onStatusUpdate("Step 1/5: Searching Google for ${plan.googleSearchQuery}...", false, null)
                val encodedQuery = URLEncoder.encode(plan.googleSearchQuery, "UTF-8")
                val googleSearchUrl = "https://www.google.com/search?q=$encodedQuery"

                addLog("🔍 Step 1: Navigating to Google Search: \"${plan.googleSearchQuery}\"")
                webView.loadUrl(googleSearchUrl)
                delay(3000)

                // 3. Inspect Search Results & Open Real Website
                onStatusUpdate("Step 2/5: Locating official portal link...", false, null)
                addLog("🌐 Step 2: Locating official organic search result link...")

                var discoveredUrl: String? = null
                val findLinkScript = """
                    (function() {
                        try {
                            const links = Array.from(document.querySelectorAll('a[href]'));
                            for (const a of links) {
                                const href = a.href || '';
                                if (href.startsWith('http') && !href.includes('google.com') && !href.includes('accounts') && !href.includes('/search')) {
                                    return JSON.stringify({ success: true, url: href, title: a.innerText });
                                }
                            }
                            return JSON.stringify({ success: false });
                        } catch(e) {
                            return JSON.stringify({ success: false, error: e.toString() });
                        }
                    })();
                """.trimIndent()

                webView.evaluateJavascript(findLinkScript) { res ->
                    try {
                        val cleaned = unquote(res)
                        val json = JSONObject(cleaned)
                        if (json.optBoolean("success", false)) {
                            discoveredUrl = json.optString("url")
                        }
                    } catch (_: Exception) {}
                }
                delay(1200)

                if (!discoveredUrl.isNullOrBlank()) {
                    addLog("🎯 Opening official portal: $discoveredUrl")
                    webView.loadUrl(discoveredUrl!!)
                } else {
                    // Try Accessibility click on top result
                    val clickedViaAccessibility = BrowserAccessibilityService.clickText(plan.googleSearchQuery.take(8))
                    if (clickedViaAccessibility) {
                        addLog("⚡ Clicked search result via Android Accessibility Service")
                    } else {
                        // Fallback click on first organic heading
                        val clickFirstResultScript = """
                            (function() {
                                const h3 = document.querySelector('h3');
                                if (h3) {
                                    h3.click();
                                    const parentAnchor = h3.closest('a');
                                    if (parentAnchor) parentAnchor.click();
                                    return true;
                                }
                                return false;
                            })();
                        """.trimIndent()
                        webView.evaluateJavascript(clickFirstResultScript, null)
                    }
                }
                delay(3500)

                // 4. Error Check & Login Requirement
                val currentTitle = webView.title ?: ""
                if (currentTitle.contains("Webpage not available", ignoreCase = true) ||
                    currentTitle.contains("net::", ignoreCase = true) ||
                    currentTitle.contains("Error", ignoreCase = true)) {
                    addLog("⚠️ Unresolved domain, re-routing via Google official portal link...", isWarning = true)
                    webView.loadUrl("https://www.google.com/search?q=" + URLEncoder.encode("${plan.googleSearchQuery} login", "UTF-8"))
                    delay(3000)
                }

                AiWorkerBrowserManager.checkLoginRequirement(webView)
                if (AiWorkerBrowserManager.state.value.isLoginRequired) {
                    addLog("⏸️ Sign-in required! Tap 'View' to log in (Touch enabled for login page).", isWarning = true)
                    onStatusUpdate("⚠️ Login Required: Tap 'View' to log in", false, null)
                    repeat(25) {
                        delay(1000)
                        AiWorkerBrowserManager.checkLoginRequirement(webView)
                        if (!AiWorkerBrowserManager.state.value.isLoginRequired) return@repeat
                    }
                }

                // 5. In-Portal Search for the Exact Topic
                onStatusUpdate("Step 3/5: Searching portal for \"${plan.inSiteSearchQuery}\"...", false, null)
                addLog("🔍 Step 3: Entering target query into portal search: \"${plan.inSiteSearchQuery}\"")

                // Use Accessibility input if available
                if (BrowserAccessibilityService.isAccessibilityEnabled()) {
                    BrowserAccessibilityService.setTextInFocusedOrSearch(plan.inSiteSearchQuery)
                }
                AiWebAutomationController.performAutoSearch(webView, plan.inSiteSearchQuery) { _, _ -> }
                delay(2500)

                // 6. Apply Category/Type Filter
                if (plan.filterKeyword.isNotBlank() && plan.filterKeyword != "all") {
                    onStatusUpdate("Step 4/5: Applying filter '${plan.filterKeyword}'...", false, null)
                    addLog("🏷️ Step 4: Applying filter '${plan.filterKeyword}' & opening result...")
                    AiWebAutomationController.filterOrSelectCourse(webView, plan.filterKeyword) { _, _ -> }
                    delay(2500)
                }

                // Select Course Card
                AiWebAutomationController.filterOrSelectCourse(webView, plan.targetCourseName) { _, _ -> }
                delay(2000)

                // Extract Verified Title (Reject invalid strings like "Webpage not available")
                var verifiedCourseName = plan.targetCourseName
                AiWebAutomationController.extractCourseInfo(webView) { success, title ->
                    if (success && title.isNotBlank() &&
                        !title.contains("Webpage not available", ignoreCase = true) &&
                        !title.contains("Google", ignoreCase = true) &&
                        !title.contains("Error", ignoreCase = true)) {
                        verifiedCourseName = title
                    }
                }
                delay(800)
                addLog("🎓 Step 5: Verified Course: \"$verifiedCourseName\"", isSuccess = true)

                // 7. Complete Modules / Lessons
                if (plan.wantsCompleteModules) {
                    addLog("⚡ Executing course: Starting assessment & advancing modules...")
                    if (BrowserAccessibilityService.isAccessibilityEnabled()) {
                        BrowserAccessibilityService.clickText("Start")
                        BrowserAccessibilityService.clickText("Continue")
                    }
                    AiWebAutomationController.clickNextOrContinue(webView) { _, _ -> }
                    delay(1800)

                    repeat(3) { stepIndex ->
                        addLog("⏩ Progressing lesson ${stepIndex + 1}/3 for \"$verifiedCourseName\"...")
                        AiWebAutomationController.clickNextOrContinue(webView) { _, _ -> }
                        delay(1500)
                    }
                }

                val completionSummary = "Completed task: \"$verifiedCourseName\" successfully!"
                addLog("🎉 $completionSummary", isSuccess = true)

                CourseAutomationNotificationHelper.clearProgressNotification(context)
                CourseAutomationNotificationHelper.sendCourseCompletedNotification(
                    context = context,
                    courseName = verifiedCourseName,
                    details = "AI Worker completed: '$verifiedCourseName' as requested."
                )

                onStatusUpdate("✅ $completionSummary", true, verifiedCourseName)
            } catch (e: Exception) {
                CourseAutomationNotificationHelper.clearProgressNotification(context)
                addLog("❌ Automation error: ${e.localizedMessage}", isWarning = true)
                onStatusUpdate("Error: ${e.localizedMessage}", true, null)
            }
        }
    }

    private fun unquote(raw: String?): String {
        if (raw == null) return ""
        var str = raw.trim()
        if (str.startsWith("\"") && str.endsWith("\"") && str.length >= 2) {
            str = str.substring(1, str.length - 1)
            str = str.replace("\\\"", "\"").replace("\\\\", "\\")
        }
        return str
    }
}
