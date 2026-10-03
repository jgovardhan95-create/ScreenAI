package com.example.automation

import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object TaskAutomationPlanner {

    fun planTask(rawCommand: String): AutomationTask {
        val trimmed = rawCommand.trim()
        val lower = trimmed.lowercase()

        val (actionType, payload, goal) = when {
            // YouTube / Video
            lower.contains("youtube") || lower.contains("play video") || lower.contains("watch") -> {
                val query = cleanQuery(trimmed, listOf("open youtube and search", "open youtube", "search on youtube", "play", "watch"))
                val url = if (query.isNotBlank()) {
                    "https://www.youtube.com/results?search_query=${encode(query)}"
                } else {
                    "https://www.youtube.com"
                }
                Triple(AutomationActionType.OPEN_YOUTUBE, url, "Open YouTube and search: \"${query.ifBlank { "Home" }}\"")
            }

            // Maps / Location / Navigation
            lower.contains("map") || lower.contains("directions") || lower.contains("navigate") ||
                lower.contains("nearby") || lower.contains("coffee") || lower.contains("restaurant") -> {
                val query = cleanQuery(trimmed, listOf("open maps and find", "open maps and search", "open maps", "find", "search"))
                val geoUri = if (query.isNotBlank()) "geo:0,0?q=${encode(query)}" else "geo:0,0?q=restaurants"
                Triple(AutomationActionType.OPEN_MAPS, geoUri, "Open Maps navigation for: \"${query.ifBlank { "nearby places" }}\"")
            }

            // Settings
            lower.contains("setting") || lower.contains("wifi") || lower.contains("bluetooth") ||
                lower.contains("battery") || lower.contains("display") -> {
                val settingType = when {
                    lower.contains("wifi") -> "android.settings.WIFI_SETTINGS"
                    lower.contains("bluetooth") -> "android.settings.BLUETOOTH_SETTINGS"
                    lower.contains("battery") -> "android.settings.BATTERY_SAVER_SETTINGS"
                    lower.contains("display") -> "android.settings.DISPLAY_SETTINGS"
                    else -> "android.settings.SETTINGS"
                }
                Triple(AutomationActionType.OPEN_SETTINGS, settingType, "Open Android System Settings (${settingType.substringAfterLast('.')})")
            }

            // Email / Compose
            lower.contains("email") || lower.contains("mail") || lower.contains("compose") -> {
                val body = cleanQuery(trimmed, listOf("compose email about", "send email to", "write email", "compose email"))
                val mailto = "mailto:?subject=${encode("ScreenAI Automated Task")}&body=${encode(body)}"
                Triple(AutomationActionType.COMPOSE_MESSAGE, mailto, "Compose new email with text: \"${body.take(40)}\"")
            }

            // Copy to clipboard / Calculation
            lower.startsWith("copy") || lower.startsWith("calc") -> {
                val text = cleanQuery(trimmed, listOf("copy", "calculate and copy", "calc"))
                Triple(AutomationActionType.COPY_TO_CLIPBOARD, text, "Copy text/calculation to Android clipboard")
            }

            // Web Search & Browser (e.g. "open browser and download movie")
            else -> {
                val query = cleanQuery(trimmed, listOf(
                    "open browser and download",
                    "open browser and search",
                    "open browser and",
                    "open browser",
                    "download movie",
                    "search for",
                    "search",
                    "google"
                ))

                val finalQuery = if (query.isNotBlank()) query else trimmed
                val url = if (finalQuery.startsWith("http://") || finalQuery.startsWith("https://")) {
                    finalQuery
                } else {
                    "https://www.google.com/search?q=${encode(finalQuery)}"
                }
                Triple(AutomationActionType.OPEN_BROWSER, url, "Open Web Browser to search: \"$finalQuery\"")
            }
        }

        val steps = listOf(
            AutomationStep(
                stepNumber = 1,
                title = "1. Parse User Intent",
                description = "Identified action type: ${actionType.displayName}",
                status = StepStatus.PENDING,
                detailPayload = goal
            ),
            AutomationStep(
                stepNumber = 2,
                title = "2. Build Action Plan & Payload",
                description = "Formulated background parameters: $payload",
                status = StepStatus.PENDING,
                detailPayload = payload
            ),
            AutomationStep(
                stepNumber = 3,
                title = "3. Dispatch System Intent",
                description = "Sending intent to Android OS in background",
                status = StepStatus.PENDING,
                detailPayload = actionType.name
            ),
            AutomationStep(
                stepNumber = 4,
                title = "4. Verify Flow & Provide Result",
                description = "Action dispatched successfully to target app",
                status = StepStatus.PENDING,
                detailPayload = "Ready"
            )
        )

        return AutomationTask(
            rawCommand = trimmed,
            parsedGoal = goal,
            actionType = actionType,
            targetPayload = payload,
            steps = steps,
            status = TaskStatus.IDLE
        )
    }

    private fun cleanQuery(text: String, prefixes: List<String>): String {
        var result = text.trim()
        for (prefix in prefixes) {
            if (result.startsWith(prefix, ignoreCase = true)) {
                result = result.substring(prefix.length).trim()
                if (result.startsWith("for", ignoreCase = true) || result.startsWith("to", ignoreCase = true) || result.startsWith("the", ignoreCase = true)) {
                    result = result.substring(3).trim()
                }
                break
            }
        }
        return result.ifBlank { text }
    }

    private fun encode(str: String): String {
        return try {
            URLEncoder.encode(str, StandardCharsets.UTF_8.toString())
        } catch (_: Exception) {
            Uri.encode(str)
        }
    }
}
