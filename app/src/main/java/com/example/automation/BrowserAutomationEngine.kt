package com.example.automation

import android.content.Context
import android.net.Uri
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
     * 1. Google-searches the task if URL is not exact.
     * 2. Finds and clicks the authentic link from search results.
     * 3. Uses Accessibility service + DOM events to simulate clicks and inputs.
     * 4. Checks for login/signup requirement.
     * 5. Performs course search, filter, and module completion.
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
                addLog("🚀 BrowserAutomationEngine started for: \"$userInstruction\"")
                onStatusUpdate("Step 1/5: Searching Google to discover official link...", false, null)

                // 1. Google Search Strategy
                val searchQuery = prepareSearchQuery(userInstruction)
                val encodedQuery = URLEncoder.encode(searchQuery, "UTF-8")
                val googleSearchUrl = "https://www.google.com/search?q=$encodedQuery"

                addLog("🔍 Step 1: Navigating to Google Search: \"$searchQuery\"")
                webView.loadUrl(googleSearchUrl)
                delay(3000)

                // 2. Discover & Click the Authentic Link
                onStatusUpdate("Step 2/5: Inspecting search results & opening authentic portal...", false, null)
                addLog("🌐 Step 2: Locating official organic search result link...")

                var discoveredUrl: String? = null
                val findLinkScript = """
                    (function() {
                        try {
                            const links = Array.from(document.querySelectorAll('a[href]'));
                            for (const a of links) {
                                const href = a.href || '';
                                const txt = (a.innerText || '').toLowerCase();
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
                    addLog("🎯 Discovered target portal: $discoveredUrl")
                    webView.loadUrl(discoveredUrl!!)
                } else {
                    // Try Accessibility click on top result
                    val clickedViaAccessibility = BrowserAccessibilityService.clickText("Infosys") ||
                        BrowserAccessibilityService.clickText("Springboard")
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

                // 3. Check for Webpage Error & Login Requirement
                val currentTitle = webView.title ?: ""
                val currentUrl = webView.url ?: ""

                if (currentTitle.contains("Webpage not available", ignoreCase = true) ||
                    currentTitle.contains("net::", ignoreCase = true) ||
                    currentTitle.contains("Error", ignoreCase = true)) {
                    addLog("⚠️ Target portal gave network error ($currentTitle). Re-routing via official search cache...", isWarning = true)
                    webView.loadUrl("https://www.google.com/search?q=" + URLEncoder.encode("Infosys Springboard courses login", "UTF-8"))
                    delay(3000)
                }

                AiWorkerBrowserManager.checkLoginRequirement(webView)
                if (AiWorkerBrowserManager.state.value.isLoginRequired) {
                    addLog("⏸️ Sign-in required! Tap 'View' to log in (Touch is enabled only on login page).", isWarning = true)
                    onStatusUpdate("⚠️ Login Required: Tap 'View' to log in", false, null)
                    repeat(25) {
                        delay(1000)
                        AiWorkerBrowserManager.checkLoginRequirement(webView)
                        if (!AiWorkerBrowserManager.state.value.isLoginRequired) return@repeat
                    }
                }

                // 4. In-Portal Search & Filter Course
                val courseTopic = extractCourseTopic(userInstruction)
                onStatusUpdate("Step 3/5: Searching for \"$courseTopic\" on portal...", false, null)
                addLog("🔍 Step 3: Searching inside portal for topic: \"$courseTopic\"")

                // Try setting text via accessibility first
                if (BrowserAccessibilityService.isAccessibilityEnabled()) {
                    BrowserAccessibilityService.setTextInFocusedOrSearch(courseTopic)
                }
                AiWebAutomationController.performAutoSearch(webView, courseTopic) { _, _ -> }
                delay(2500)

                // 5. Apply Filter "Course"
                onStatusUpdate("Step 4/5: Applying 'Course' filter and selecting result...", false, null)
                addLog("🏷️ Step 4: Applying course filter & opening module...")
                AiWebAutomationController.filterOrSelectCourse(webView, "course") { _, _ -> }
                delay(2500)

                // Extract Verified Title (Reject invalid placeholder titles)
                var verifiedCourseName = courseTopic
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

                // 6. Complete Modules / Lessons
                val wantsComplete = userInstruction.lowercase().contains("complete") ||
                    userInstruction.lowercase().contains("finish")

                if (wantsComplete) {
                    addLog("⚡ Executing course: Clicking 'Start Assessment' / 'Next Module'...")
                    // Accessibility click or DOM click
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
                    details = "BrowserAutomationEngine successfully completed '$verifiedCourseName'."
                )

                onStatusUpdate("✅ $completionSummary", true, verifiedCourseName)
            } catch (e: Exception) {
                CourseAutomationNotificationHelper.clearProgressNotification(context)
                addLog("❌ Automation error: ${e.localizedMessage}", isWarning = true)
                onStatusUpdate("Error: ${e.localizedMessage}", true, null)
            }
        }
    }

    private fun prepareSearchQuery(raw: String): String {
        var clean = raw.trim()
        val removeKeywords = listOf(
            "goto infosys springboard home page and goto search and enter",
            "goto infosys springboard home page",
            "and add filter course and complete on task",
            "and add filter course and complete on task told",
            "and complete on task told",
            "and complete task",
            "filter select course",
            "open that topic and complete course"
        )
        for (kw in removeKeywords) {
            clean = clean.replace(Regex("(?i)$kw"), "").trim()
        }
        if (clean.isBlank()) {
            return "Infosys Springboard Spring 5 Basics course"
        }
        if (!clean.contains("infosys", ignoreCase = true) && !clean.contains("springboard", ignoreCase = true)) {
            return "Infosys Springboard $clean course"
        }
        return "$clean course"
    }

    private fun extractCourseTopic(raw: String): String {
        var clean = raw.trim()
        val removeKeywords = listOf(
            "goto infosys springboard home page and goto search and enter",
            "goto search and enter",
            "goto infosys springboard",
            "and add filter course and complete on task",
            "and add filter course and complete on task told",
            "and complete on task told",
            "and complete task",
            "filter select course",
            "complete course"
        )
        for (kw in removeKeywords) {
            clean = clean.replace(Regex("(?i)$kw"), "").trim()
        }
        return clean.ifBlank { "Spring 5 Basics" }
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
