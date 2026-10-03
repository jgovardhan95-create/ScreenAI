package com.example.data

enum class AiMode(
    val id: String,
    val emoji: String,
    val title: String,
    val shortDescription: String
) {
    ANSWER(
        id = "answer",
        emoji = "✨",
        title = "Answer",
        shortDescription = "Detect question & give direct answer + explanation"
    ),
    EXPLAIN(
        id = "explain",
        emoji = "📖",
        title = "Explain",
        shortDescription = "Break down visible concepts in simple language"
    ),
    SUMMARIZE(
        id = "summarize",
        emoji = "📝",
        title = "Summarize",
        shortDescription = "Distill key takeaways from screen content"
    ),
    READ_SCREEN(
        id = "read_screen",
        emoji = "🔍",
        title = "Read Screen",
        shortDescription = "Extract & structure visible text, code, or tables"
    ),
    ASK_AI(
        id = "ask_ai",
        emoji = "💡",
        title = "Ask AI",
        shortDescription = "Type a custom instruction about the screen"
    ),
    WORK_IN_ONE_TIME(
        id = "work_in_one_time",
        emoji = "⚡",
        title = "Work 1-Time",
        shortDescription = "Autonomous action runner (open browser, search, apps)"
    )
}

enum class GeminiModelOption(
    val modelId: String,
    val displayName: String,
    val badge: String,
    val description: String
) {
    GEMINI_3_5_FLASH(
        modelId = "gemini-3.5-flash",
        displayName = "Gemini 3.5 Flash",
        badge = "Default • Fast Vision",
        description = "Ideal balance of speed and multimodal screen comprehension."
    ),
    GEMINI_3_1_PRO(
        modelId = "gemini-3.1-pro-preview",
        displayName = "Gemini 3.1 Pro Preview",
        badge = "Deep Reasoning & Code",
        description = "Best for complex algorithms, math proofs, and intricate diagrams."
    ),
    GEMINI_FLASH_LATEST(
        modelId = "gemini-flash-latest",
        displayName = "Gemini Flash Latest",
        badge = "Balanced Multimodal",
        description = "General-purpose vision and text analysis across apps."
    ),
    GEMINI_3_1_FLASH_LITE(
        modelId = "gemini-3.1-flash-lite-preview",
        displayName = "Gemini 3.1 Flash Lite",
        badge = "Ultra Low Latency",
        description = "Fastest responses for quick text extraction and summaries."
    );

    companion object {
        fun fromId(id: String): GeminiModelOption =
            entries.find { it.modelId == id } ?: GEMINI_3_5_FLASH
    }
}

enum class ResponseLength(val label: String, val promptInstruction: String) {
    SHORT(
        label = "Short",
        promptInstruction = "Keep the response concise and ultra-direct (2–4 sentences or bullet points maximum)."
    ),
    MEDIUM(
        label = "Medium",
        promptInstruction = "Provide a clear, well-structured response with a direct answer followed by a brief 1-paragraph explanation."
    ),
    DETAILED(
        label = "Detailed",
        promptInstruction = "Provide a comprehensive, step-by-step breakdown covering all relevant nuances visible on the screen."
    )
}

enum class ThemePreference(val label: String) {
    DARK("Dark Mode"),
    LIGHT("Light Mode"),
    SYSTEM("System Default")
}

data class AppSettings(
    val modelOption: GeminiModelOption = GeminiModelOption.GEMINI_3_5_FLASH,
    val responseLength: ResponseLength = ResponseLength.MEDIUM,
    val themePreference: ThemePreference = ThemePreference.DARK,
    val floatingButtonSizeDp: Int = 58,
    val floatingButtonOpacity: Float = 0.92f,
    val vibrationEnabled: Boolean = true,
    val onboardingAcknowledged: Boolean = false,
    val customApiKey: String = ""
)
