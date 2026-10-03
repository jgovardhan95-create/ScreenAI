package com.example.automation

import android.webkit.ValueCallback
import android.webkit.WebView
import org.json.JSONObject

object AiWebAutomationController {

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
                    // Find common search input selectors
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
                        if (el && el.offsetParent !== null) { // visible
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

                    // Try finding submit button or press Enter
                    const form = targetInput.form;
                    if (form) {
                        form.submit();
                        return JSON.stringify({ success: true, message: "Searched: " + q + " (submitted form)" });
                    }

                    // Try search button adjacent
                    const btnSelectors = ['button[type="submit"]', 'button[aria-label*="search" i]', '.search-btn', 'button'];
                    for (const bSel of btnSelectors) {
                        const btn = document.querySelector(bSel);
                        if (btn && btn.offsetParent !== null) {
                            btn.click();
                            return JSON.stringify({ success: true, message: "Searched: " + q + " (clicked search button)" });
                        }
                    }

                    // Keyboard enter event
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

                    // Try semantic course headings first
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
                        courseName = document.title || "Unknown Page / Course";
                    }

                    // Clean boilerplate
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
     * Injects JavaScript to click 'Next', 'Continue', or 'Start Assessment' buttons.
     */
    fun clickNextOrContinue(webView: WebView, onResult: (Boolean, String) -> Unit) {
        val js = """
            (function() {
                try {
                    const buttonTextKeywords = ['next', 'continue', 'proceed', 'start assessment', 'start', 'submit'];
                    const buttons = Array.from(document.querySelectorAll('button, a, input[type="button"], input[type="submit"]'));
                    
                    for (const btn of buttons) {
                        const txt = (btn.innerText || btn.value || '').toLowerCase().trim();
                        for (const kw of buttonTextKeywords) {
                            if (txt === kw || txt.includes(kw)) {
                                if (btn.offsetParent !== null) { // visible
                                    btn.click();
                                    return JSON.stringify({ success: true, message: "Clicked: " + (btn.innerText || kw) });
                                }
                            }
                        }
                    }
                    return JSON.stringify({ success: false, message: "No 'Next' or 'Continue' button detected on page." });
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
                    const items = Array.from(document.querySelectorAll('a, button, div[role="button"], .course-card, .card'));
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
