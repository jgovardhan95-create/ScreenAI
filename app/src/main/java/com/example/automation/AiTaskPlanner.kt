package com.example.automation

import android.content.Context
import com.example.network.GeminiApiClient
import com.example.network.GeminiResult
import org.json.JSONObject

data class AutonomousExecutionPlan(
    val googleSearchQuery: String,
    val inSiteSearchQuery: String,
    val filterKeyword: String,
    val targetCourseName: String,
    val wantsCompleteModules: Boolean,
    val summary: String
)

object AiTaskPlanner {

    suspend fun createPlan(context: Context, rawPrompt: String): AutonomousExecutionPlan {
        val trimmed = rawPrompt.trim()
        val systemPrompt = """
            You are an autonomous web automation planner. 
            The user wants to accomplish a task on the web described in natural language.
            Parse their prompt into an exact execution plan JSON:
            {
               "googleSearchQuery": "<short query to find the authentic official website on Google, e.g. 'Infosys Springboard'>",
               "inSiteSearchQuery": "<exact search term to type into the website's search box, e.g. 'Spring 5 Basics'>",
               "filterKeyword": "<category or filter to click if mentioned, e.g. 'course' or 'assessment' or 'all'>",
               "targetCourseName": "<name of the course or topic, e.g. 'Spring 5 Basics'>",
               "wantsCompleteModules": <true if prompt says complete, finish, pass, or solve, else false>,
               "summary": "<1-line description of the plan>"
            }
            Return ONLY raw JSON, no markdown backticks, no comments.
        """.trimIndent()

        val promptToSend = "User instruction: \"$trimmed\"\nOutput JSON:"

        try {
            val result = GeminiApiClient.generateText(
                context = context,
                prompt = promptToSend,
                systemInstruction = systemPrompt
            )

            if (result is GeminiResult.Success) {
                var jsonStr = result.text.trim()
                if (jsonStr.startsWith("```json")) {
                    jsonStr = jsonStr.removePrefix("```json").trim()
                }
                if (jsonStr.startsWith("```")) {
                    jsonStr = jsonStr.removePrefix("```").trim()
                }
                if (jsonStr.endsWith("```")) {
                    jsonStr = jsonStr.removeSuffix("```").trim()
                }

                val obj = JSONObject(jsonStr)
                return AutonomousExecutionPlan(
                    googleSearchQuery = obj.optString("googleSearchQuery").ifBlank { "Infosys Springboard" },
                    inSiteSearchQuery = obj.optString("inSiteSearchQuery").ifBlank { "Spring 5 Basics" },
                    filterKeyword = obj.optString("filterKeyword", "course"),
                    targetCourseName = obj.optString("targetCourseName", "Spring 5 Basics"),
                    wantsCompleteModules = obj.optBoolean("wantsCompleteModules", true),
                    summary = obj.optString("summary", "Automating task for $trimmed")
                )
            }
        } catch (_: Exception) {
            // Fallback to local heuristic parser
        }

        return fallbackLocalPlan(trimmed)
    }

    private fun fallbackLocalPlan(prompt: String): AutonomousExecutionPlan {
        val lower = prompt.lowercase()

        val googleQuery = if (lower.contains("infosys") || lower.contains("springboard") || lower.contains("wingspan")) {
            "Infosys Springboard"
        } else if (lower.contains("coursera")) {
            "Coursera"
        } else if (lower.contains("udemy")) {
            "Udemy"
        } else {
            "Infosys Springboard"
        }

        // Extract topic: remove instructions
        var topic = prompt
        val noise = listOf(
            "goto infosys springboard home page and goto search and enter",
            "goto infosys springboard home page and goto search",
            "goto infosys springboard home page",
            "goto search and enter",
            "and add filter course and complete on task",
            "and add filter course and complete on task told",
            "and complete on task told",
            "and complete task",
            "filter select course",
            "open that topic and complete course",
            "this input and",
            "this topic and",
            "complete course",
            "complete on task",
            "goto",
            "search for",
            "search"
        )
        for (n in noise) {
            topic = topic.replace(Regex("(?i)$n"), "").trim()
        }
        val cleanTopic = topic.ifBlank { "Spring 5 Basics" }

        val filter = if (lower.contains("course")) "course" else if (lower.contains("assessment")) "assessment" else "course"
        val complete = lower.contains("complete") || lower.contains("finish") || lower.contains("pass")

        return AutonomousExecutionPlan(
            googleSearchQuery = googleQuery,
            inSiteSearchQuery = cleanTopic,
            filterKeyword = filter,
            targetCourseName = cleanTopic,
            wantsCompleteModules = complete,
            summary = "Navigating to $googleQuery, searching '$cleanTopic', and completing task."
        )
    }
}
