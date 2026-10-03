package com.example.automation

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object TaskAutomationManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _currentTask = MutableStateFlow<AutomationTask?>(null)
    val currentTask: StateFlow<AutomationTask?> = _currentTask.asStateFlow()

    private val _taskHistory = MutableStateFlow<List<AutomationTask>>(emptyList())
    val taskHistory: StateFlow<List<AutomationTask>> = _taskHistory.asStateFlow()

    fun runTask(context: Context, rawCommand: String) {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) return

        val plannedTask = TaskAutomationPlanner.planTask(trimmed)
        _currentTask.value = plannedTask

        scope.launch {
            TaskAutomationExecutor.executeTaskFlow(context, plannedTask).collect { updatedTask ->
                _currentTask.value = updatedTask
                if (updatedTask.status == TaskStatus.COMPLETED || updatedTask.status == TaskStatus.FAILED) {
                    _taskHistory.update { history ->
                        listOf(updatedTask) + history.filter { it.id != updatedTask.id }.take(14)
                    }
                }
            }
        }
    }

    fun reRunTask(context: Context, task: AutomationTask) {
        runTask(context, task.rawCommand)
    }

    fun clearCurrentTask() {
        _currentTask.value = null
    }

    fun clearHistory() {
        _taskHistory.value = emptyList()
    }
}
