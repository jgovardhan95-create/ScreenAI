package com.example.automation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object TaskAutomationExecutor {

    fun executeTaskFlow(context: Context, initialTask: AutomationTask): Flow<AutomationTask> = flow {
        val appContext = context.applicationContext
        var currentTask = initialTask.copy(status = TaskStatus.PLANNING)
        emit(currentTask)

        // Step 1: Parsing
        currentTask = updateStep(currentTask, 1, StepStatus.IN_PROGRESS)
        emit(currentTask)
        delay(400)
        currentTask = updateStep(currentTask, 1, StepStatus.COMPLETED)
        emit(currentTask)

        // Step 2: Formulating Action Plan
        currentTask = updateStep(currentTask, 2, StepStatus.IN_PROGRESS)
        emit(currentTask)
        delay(450)
        currentTask = updateStep(currentTask, 2, StepStatus.COMPLETED)
        emit(currentTask)

        // Step 3: Dispatching System Intent
        currentTask = currentTask.copy(status = TaskStatus.EXECUTING)
        currentTask = updateStep(currentTask, 3, StepStatus.IN_PROGRESS)
        emit(currentTask)
        delay(350)

        val dispatchResult = runCatching {
            dispatchAction(appContext, currentTask.actionType, currentTask.targetPayload)
        }

        if (dispatchResult.isSuccess) {
            currentTask = updateStep(
                currentTask,
                3,
                StepStatus.COMPLETED,
                "Intent dispatched: ${currentTask.actionType.displayName}"
            )
            emit(currentTask)

            // Step 4: Confirmation & Result Ready
            currentTask = updateStep(currentTask, 4, StepStatus.IN_PROGRESS)
            emit(currentTask)
            delay(300)

            val summary = when (currentTask.actionType) {
                AutomationActionType.OPEN_BROWSER -> "Web browser launched with target query/URL in background."
                AutomationActionType.OPEN_YOUTUBE -> "YouTube opened with search query in background."
                AutomationActionType.OPEN_MAPS -> "Maps navigation opened for location in background."
                AutomationActionType.OPEN_SETTINGS -> "System settings screen opened in background."
                AutomationActionType.COMPOSE_MESSAGE -> "Email/messaging composer launched in background."
                AutomationActionType.COPY_TO_CLIPBOARD -> "Text successfully copied to system clipboard."
                else -> "Action executed successfully."
            }

            currentTask = updateStep(currentTask, 4, StepStatus.COMPLETED, summary).copy(
                status = TaskStatus.COMPLETED,
                summaryResult = summary
            )
            emit(currentTask)
        } else {
            val errorMsg = dispatchResult.exceptionOrNull()?.localizedMessage ?: "Failed to dispatch intent"
            currentTask = updateStep(currentTask, 3, StepStatus.FAILED, errorMsg).copy(
                status = TaskStatus.FAILED,
                summaryResult = "Error: $errorMsg"
            )
            emit(currentTask)
        }
    }

    fun dispatchAction(context: Context, actionType: AutomationActionType, payload: String) {
        when (actionType) {
            AutomationActionType.OPEN_BROWSER,
            AutomationActionType.OPEN_YOUTUBE -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(payload)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }

            AutomationActionType.WEB_SEARCH -> {
                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra("query", payload)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }

            AutomationActionType.OPEN_MAPS -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(payload)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }

            AutomationActionType.OPEN_SETTINGS -> {
                val action = if (payload.startsWith("android.settings")) payload else Settings.ACTION_SETTINGS
                val intent = Intent(action).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }

            AutomationActionType.COMPOSE_MESSAGE -> {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(payload)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }

            AutomationActionType.COPY_TO_CLIPBOARD -> {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("ScreenAI Task Result", payload)
                clipboard?.setPrimaryClip(clip)
            }

            AutomationActionType.GENERIC_INTENT -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(payload)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    private fun updateStep(
        task: AutomationTask,
        stepNumber: Int,
        newStatus: StepStatus,
        newDetail: String? = null
    ): AutomationTask {
        val updatedSteps = task.steps.map { step ->
            if (step.stepNumber == stepNumber) {
                step.copy(
                    status = newStatus,
                    detailPayload = newDetail ?: step.detailPayload
                )
            } else {
                step
            }
        }
        return task.copy(steps = updatedSteps)
    }
}
