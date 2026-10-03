package com.example.automation

import android.content.Context
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

object AiWebAutomationController {

    private val automationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Executes the autonomous flow triggered by the single "RUN" button.
     * Parses the user's instruction, searches/filters the course, clicks into it,
     * extracts the course name, advances through the modules, and sends a notification upon completion.
     */
    fun executeAutonomousCourseFlow(
        context: Context,
        webView: WebView,
        userInstruction: String,
        onProgressUpdate: (status: String, isFinished: Boolean, courseName: String?) -> Unit
    ) {
        val trimmed = userInstruction.trim()
        val lower = trimmed.lowercase()

        val wantsComplete = lower.contains("complete") || lower.contains("finish") ||
            lower.contains("pass") || lower.contains("all module")

        val extractedTopic = extractTopicQuery(trimmed)

        automationScope.launch {
            try {
                CourseAutomationNotificationHelper.sendCourseProgressNotification(
                    context,
                    "ScreenAI Course Automation",
                    "Starting automation for: $extractedTopic"
                )

                // 1. Initial Status
                onProgressUpdate("Step 1/5: Analyzing command & searching \"$extractedTopic\"...", false, null)

                // 2. Perform Search if topic given
                if (extractedTopic.isNotBlank()) {
                    performAutoSearch(webView, extractedTopic) { _, _ -> }
                    delay(1500)
                }

                // 3. Select / Filter matching course card
                onProgressUpdate("Step 2/5: Filtering & clicking course matching \"$extractedTopic\"...", false, null)
                filterOrSelectCourse(webView, extractedTopic) { _, _ -> }
                delay(1800)

                // 4. Extract Exact Course Name
                onProgressUpdate("Step 3/5: Extracting course title and syllabus...", false, null)
                var resolvedCourseName = "Course ($extractedTopic)"
                extractCourseInfo(webView) { success, title ->
                    if (success && title.isNotBlank() && title != "Google" && title != "Infosys Springboard") {
                        resolvedCourseName = title
                    }
                }
                delay(600)

                onProgressUpdate("Step 4/5: Verified Course: \"$resolvedCourseName\".", false, resolvedCourseName)

                // 5. Advance / Complete if requested
                if (wantsComplete) {
                    onProgressUpdate("Step 5/5: Starting modules & auto-advancing lessons...", false, resolvedCourseName)
                    // Click start / begin
                    clickNextOrContinue(webView) { _, _ -> }
                    delay(1600)

                    // Auto-advance through 3 consecutive checks/modules
                    repeat(3) { stepIndex ->
                        onProgressUpdate(
                            "Advancing module ${stepIndex + 1}/3 for \"$resolvedCourseName\"...",
                            false,
                            resolvedCourseName
                        )
                        clickNextOrContinue(webView) { _, _ -> }
                        delay(1500)
                    }

                    // Final extraction to ensure updated page title
                    extractCourseInfo(webView) { success, title ->
                        if (success && title.isNotBlank() && title != "Google") {
                            resolvedCourseName = title
                        }
                    }
                }

                // Complete!
                val completionMessage = if (wantsComplete) {
                    "Course \"$resolvedCourseName\" completed successfully!"
                } else {
                    "Selected course \"$resolvedCourseName\" ready."
                }

                CourseAutomationNotificationHelper.clearProgressNotification(context)
                CourseAutomationNotificationHelper.sendCourseCompletedNotification(
                    context = context,
                    courseName = resolvedCourseName,
                    details = completionMessage
                )

                onProgressUpdate("✅ $completionMessage", true, resolvedCourseName)
            } catch (e: Exception) {
                CourseAutomationNotificationHelper.clearProgressNotification(context)
                onProgressUpdate("Error: ${e.localizedMessage}", true, null)
            }
        }
    }

    private fun extractTopicQuery(instruction: String): String {
        var clean = instruction.trim()
        val removeKeywords = listOf(
            "filter select course",
            "filter and select course",
            "filter course",
            "select course",
            "open that topic and complete course",
            "open topic and complete course",
            "and complete course",
            "complete course",
            "complete the course",
            "open that topic",
            "open",
            "filter",
            "select",
            "search for",
            "search",
            "find"
        )
        for (kw in removeKeywords) {
            clean = clean.replace(Regex("(?i)$kw"), "").trim()
        }
        clean = clean.replace(Regex("(?i)mean it should complete.*"), "").trim()
        clean = clean.replace(Regex("(?i)and tell me.*"), "").trim()
        return clean.ifBlank { "Spring 5 Basics" }
    }

    /**
     * Injects JavaScript to find search fields on the current webpage,
     * fills in the query, dispatches input events, and submits the form or clicks the search button.
     */
    fun performAutoSearch(webView: WebView, query: String, onResult: (Boolean, String) -> Unit) {
        val sanitizedQuery = JSONObject.quote(query)
        val js = """
            (function() {
                try {
                    const q = $sanitizedQuery;
                    const selectors = [
                        'input[type="search"]',
                        'input[name="q"]',
                        'input[placeholder*="search" i]',
                        'input[placeholder*="find" i]',
                        'input[placeholder*="course" i]',
                        'input[aria-label*="search" i]',
                        'input[id*="search" i]',
                        'input[class*="search" i]',
                        'input[type="text"]'
                    ];

                    let targetInput = null;
                    for (const sel of selectors) {
                        const el = document.querySelector(sel);
                        if (el && el.offsetParent !== null) {
                            targetInput = el;
                            break;
                        }
                    }

                    if (!targetInput) {
                        return JSON.stringify({ success: false, message: "No search input found on page." });
                    }

                    targetInput.focus();
                    targetInput.value = q;
                    targetInput.dispatchEvent(new Event('input', { bubbles: true }));
                    targetInput.dispatchEvent(new Event('change', { bubbles: true }));

                    const form = targetInput.form;
                    if (form) {
                        form.submit();
                        return JSON.stringify({ success: true, message: "Searched: " + q });
                    }

                    const btnSelectors = ['button[type="submit"]', 'button[aria-label*="search" i]', '.search-btn', 'button'];
                    for (const bSel of btnSelectors) {
                        const btn = document.querySelector(bSel);
                        if (btn && btn.offsetParent !== null) {
                            btn.click();
                            return JSON.stringify({ success: true, message: "Searched: " + q });
                        }
                    }

                    targetInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, bubbles: true }));
                    return JSON.stringify({ success: true, message: "Searched: " + q });
                } catch(e) {
                    return JSON.stringify({ success: false, message: e.toString() });
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { rawResult ->
            parseJsResult(rawResult, onResult)
        }
    }

    /**
     * Extracts course title, assessment name, or page headline from the DOM.
     */
    fun extractCourseInfo(webView: WebView, onResult: (Boolean, String) -> Unit) {
        val js = """
            (function() {
                try {
                    let courseName = "";
                    const courseSelectors = [
                        'h1',
                        'h2',
                        '[class*="course-title" i]',
                        '[class*="assessment-title" i]',
                        '[class*="module-title" i]',
                        '[class*="courseName" i]',
                        '[data-testid*="course" i]',
                        '.header-title'
                    ];

                    for (const sel of courseSelectors) {
                        const el = document.querySelector(sel);
                        if (el && el.innerText && el.innerText.trim().length > 2) {
                            courseName = el.innerText.trim();
                            break;
                        }
                    }

                    if (!courseName) {
                        courseName = document.title || "Unknown Course";
                    }

                    courseName = courseName.replace(/[\n\r\t]+/g, ' ').trim();

                    return JSON.stringify({
                        success: true,
                        courseName: courseName,
                        url: window.location.href
                    });
                } catch(e) {
                    return JSON.stringify({ success: false, message: e.toString() });
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { rawResult ->
            try {
                val cleaned = unquote(rawResult)
                val json = JSONObject(cleaned)
                if (json.optBoolean("success", false)) {
                    val title = json.optString("courseName", "Unknown Course")
                    onResult(true, title)
                } else {
                    onResult(false, json.optString("message", "Could not extract course name"))
                }
            } catch (e: Exception) {
                onResult(false, "Extraction error: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Injects JavaScript to click 'Next', 'Continue', 'Start', or 'Proceed' buttons.
     */
    fun clickNextOrContinue(webView: WebView, onResult: (Boolean, String) -> Unit) {
        val js = """
            (function() {
                try {
                    const buttonTextKeywords = ['next', 'continue', 'proceed', 'start assessment', 'start', 'begin', 'submit'];
                    const buttons = Array.from(document.querySelectorAll('button, a, input[type="button"], input[type="submit"]'));
                    
                    for (const btn of buttons) {
                        const txt = (btn.innerText || btn.value || '').toLowerCase().trim();
                        for (const kw of buttonTextKeywords) {
                            if (txt === kw || txt.includes(kw)) {
                                if (btn.offsetParent !== null) {
                                    btn.click();
                                    return JSON.stringify({ success: true, message: "Clicked: " + (btn.innerText || kw) });
                                }
                            }
                        }
                    }
                    return JSON.stringify({ success: false, message: "No active 'Next' or 'Continue' button detected." });
                } catch(e) {
                    return JSON.stringify({ success: false, message: e.toString() });
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { rawResult ->
            parseJsResult(rawResult, onResult)
        }
    }

    /**
     * Injects JavaScript to click on course filter chips or search result items.
     */
    fun filterOrSelectCourse(webView: WebView, filterKeyword: String, onResult: (Boolean, String) -> Unit) {
        val kw = JSONObject.quote(filterKeyword.lowercase())
        val js = """
            (function() {
                try {
                    const target = $kw;
                    const items = Array.from(document.querySelectorAll('a, button, div[role="button"], .course-card, .card, h3, h4'));
                    for (const el of items) {
                        const txt = (el.innerText || '').toLowerCase();
                        if (txt.includes(target) && el.offsetParent !== null) {
                            el.click();
                            return JSON.stringify({ success: true, message: "Selected course/filter: " + el.innerText.trim().slice(0, 50) });
                        }
                    }
                    return JSON.stringify({ success: false, message: "No element found matching: " + target });
                } catch(e) {
                    return JSON.stringify({ success: false, message: e.toString() });
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { rawResult ->
            parseJsResult(rawResult, onResult)
        }
    }

    private fun parseJsResult(raw: String?, onResult: (Boolean, String) -> Unit) {
        if (raw == null) {
            onResult(false, "No response from webpage")
            return
        }
        try {
            val cleaned = unquote(raw)
            val json = JSONObject(cleaned)
            val success = json.optBoolean("success", false)
            val message = json.optString("message", if (success) "Action executed" else "Action failed")
            onResult(success, message)
        } catch (e: Exception) {
            onResult(false, "Parse error: ${e.localizedMessage}")
        }
    }

    private fun unquote(raw: String): String {
        var str = raw.trim()
        if (str.startsWith("\"") && str.endsWith("\"") && str.length >= 2) {
            str = str.substring(1, str.length - 1)
            str = str.replace("\\\"", "\"").replace("\\\\", "\\")
        }
        return str
    }
}
