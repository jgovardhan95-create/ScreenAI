package com.example.automation

enum class AutomationActionType(val displayName: String, val iconEmoji: String) {
    OPEN_BROWSER(displayName = "Open Web Browser", iconEmoji = "🌐"),
    WEB_SEARCH(displayName = "Web Search", iconEmoji = "🔍"),
    OPEN_YOUTUBE(displayName = "Open YouTube", iconEmoji = "🎬"),
    OPEN_MAPS(displayName = "Open Maps Navigation", iconEmoji = "🗺️"),
    OPEN_SETTINGS(displayName = "System Settings", iconEmoji = "⚙️"),
    COMPOSE_MESSAGE(displayName = "Compose Message / Email", iconEmoji = "✉️"),
    COPY_TO_CLIPBOARD(displayName = "Copy Result to Clipboard", iconEmoji = "📋"),
    GENERIC_INTENT(displayName = "Custom Action", iconEmoji = "🚀")
}

enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

data class AutomationStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val status: StepStatus = StepStatus.PENDING,
    val detailPayload: String? = null
)

enum class TaskStatus {
    IDLE,
    PLANNING,
    EXECUTING,
    COMPLETED,
    FAILED
}

data class AutomationTask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val rawCommand: String,
    val parsedGoal: String,
    val actionType: AutomationActionType,
    val targetPayload: String,
    val steps: List<AutomationStep>,
    val status: TaskStatus = TaskStatus.IDLE,
    val summaryResult: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
