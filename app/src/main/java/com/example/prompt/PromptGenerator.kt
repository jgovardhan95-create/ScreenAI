package com.example.prompt

import com.example.data.AiMode
import com.example.data.ResponseLength

object PromptGenerator {

    fun buildSystemInstruction(responseLength: ResponseLength): String {
        return """
You are ScreenAI Assistant, an intelligent vision-powered Android screen assistant.
Your job is to inspect the user's captured mobile screen and provide immediate, high-value assistance.

CORE SCREEN UNDERSTANDING RULES:
1. Ignore irrelevant Android system UI chrome (status bar time, battery percentage, Wi-Fi icons, navigation gesture bar, or the ScreenAI overlay itself).
2. Automatically detect the primary subject on screen:
   - Programming Code / Output Question: Trace the execution carefully. State the exact output first (e.g., "Output: 25"), then provide a clear, concise explanation of how the code produces that result.
   - Multiple Choice Question (MCQ): Identify the question and options, state the correct option clearly at the very top, then explain why it is correct.
   - Mathematical / Science Problem: State the final solution first, followed by the key steps.
   - Technical Diagram / Chart: Identify what the diagram represents, its key components, and how data or control flows through it.
   - Article / Webpage / Paragraph: Focus on the main body text and core message.
3. Formatting: Use clean Markdown-friendly structure (bold headings, bullet points, and short paragraphs) optimized for reading inside a compact floating mobile panel.
4. Length Preference: ${responseLength.promptInstruction}
        """.trimIndent()
    }

    fun buildModePrompt(
        mode: AiMode,
        customInstruction: String? = null,
        responseLength: ResponseLength = ResponseLength.MEDIUM
    ): String {
        val lengthNote = "Response Length Target: ${responseLength.label} (${responseLength.promptInstruction})"
        return when (mode) {
            AiMode.ANSWER -> """
MODE: ✨ ANSWER
Analyze the captured screen and detect the primary question, coding challenge, math problem, or MCQ visible.
1. Give the direct, exact answer on the very first line (for example: "Output: 25" or "Answer: B — Binary Search").
2. Follow with a short, clear explanation of how that answer is derived.
3. If multiple questions are visible, answer the most prominent or central one first, then briefly address the others.
$lengthNote
            """.trimIndent()

            AiMode.EXPLAIN -> """
MODE: 📖 EXPLAIN
Explain the primary visible content on this screen in simple, approachable language.
- If it is code, explain what the code does line-by-line or block-by-block and clarify any tricky concepts.
- If it is a diagram or chart, explain the components and relationships.
- If it is technical or academic text, break down the core concepts with an intuitive analogy if helpful.
$lengthNote
            """.trimIndent()

            AiMode.SUMMARIZE -> """
MODE: 📝 SUMMARIZE
Summarize the most important information visible on this screen.
- Start with a 1-sentence high-level takeaway ("TL;DR").
- Provide 3 to 5 crisp bullet points capturing the key facts, numbers, or conclusions.
- Omit boilerplate UI menus, ads, or navigation labels.
$lengthNote
            """.trimIndent()

            AiMode.READ_SCREEN -> """
MODE: 🔍 READ SCREEN
Extract and structure all important visible text, code snippets, equations, or table data from this screen.
- Preserve code indentation and structure accurately.
- Group extracted text logically (e.g., Heading, Main Content, Code/Data).
- Ignore status bar icons and unrelated chrome.
            """.trimIndent()

            AiMode.ASK_AI -> {
                val userQuery = customInstruction?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: "Analyze the most important content on this screen and help me understand it."
                """
MODE: 💡 ASK AI (Custom User Instruction)
User's specific request about the current screen:
"$userQuery"

Fulfill the user's instruction directly and accurately based on the visual and textual content shown in the screenshot.
$lengthNote
                """.trimIndent()
            }

            AiMode.WORK_IN_ONE_TIME -> {
                val task = customInstruction?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: "Analyze the task on screen, plan the background actions, and state the execution flow."
                """
MODE: ⚡ WORK IN ONE TIME (Autonomous Action Flow)
User's background task instruction:
"$task"

1. Identify the requested action (e.g. search web, open browser, download, navigate, open settings).
2. Detail the exact sequential steps to fulfill this task.
3. Provide the direct links or commands needed to complete it.
                """.trimIndent()
            }
        }
    }

    val quickAskSuggestions = listOf(
        "Explain this like I'm a beginner",
        "Solve this step by step",
        "Translate this to English",
        "Find the error in this code"
    )
}
