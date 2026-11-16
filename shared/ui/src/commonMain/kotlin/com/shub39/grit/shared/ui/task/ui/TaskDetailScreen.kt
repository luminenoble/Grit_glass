/*
 * Copyright (C) 2026  Shubham Gorai
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.shub39.grit.shared.ui.task.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.shub39.grit.shared.ui.components.glass.GlassAlertDialog
import com.shub39.grit.shared.ui.task.TaskAction
import com.shub39.grit.shared.ui.task.TaskState
import com.shub39.grit.shared.ui.task.ui.component.TaskUpsertSheet

/**
 * Full-screen editor for the stored task [taskId], shared by the Task page and the overview.
 * [onClose] leaves the screen: after saving, deleting, backing out, or when the task is gone.
 */
@Composable
fun TaskDetailScreen(
    state: TaskState,
    taskId: Long,
    onAction: (TaskAction) -> Unit,
    onClose: () -> Unit,
) {
    val task = state.allTasks.firstOrNull { it.id == taskId }
    if (task == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    TaskUpsertSheet(
        task = task,
        categories = state.tasks.keys.toList(),
        isEditSheet = true,
        is24Hr = state.is24Hour,
        // A fresh detail screen must not steal focus into the keyboard.
        fullScreen = true,
        autoFocusTitle = false,
        onDismissRequest = onClose,
        saving = state.saving,
        onUpsert = { updated -> onAction(TaskAction.UpsertTask(updated, onClose)) },
        onDelete = { onAction(TaskAction.DeleteTask(task, onClose)) },
    )
}

/** The failed-write dialog both task pages show for [TaskState.error]. */
@Composable
fun TaskErrorDialog(state: TaskState, onAction: (TaskAction) -> Unit) {
    val error = state.error ?: return
    GlassAlertDialog(
        onDismissRequest = { onAction(TaskAction.ClearError) },
        title = { Text("Something went wrong") },
        text = { Text(error) },
        confirmButton = {
            TextButton(onClick = { onAction(TaskAction.ClearError) }) { Text("Close") }
        },
    )
}
