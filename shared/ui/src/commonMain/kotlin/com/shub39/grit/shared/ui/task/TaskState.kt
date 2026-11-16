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
package com.shub39.grit.shared.ui.task

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.shub39.grit.core.now
import com.shub39.grit.core.settings.WallSettings
import com.shub39.grit.core.tasks.Category
import com.shub39.grit.core.tasks.Task
import kotlinx.datetime.LocalDate

@Stable
@Immutable
data class TaskState(
    val saving: Boolean = false,
    val error: String? = null,
    /** Every stored task by category, archived and shelved ones included. */
    val tasks: Map<Category, List<Task>> = emptyMap(),
    val currentCategory: Category? = null,
    val is24Hour: Boolean = false,
    /** Virtual "Today" category is selected: show tasks flagged [Task.isToday] instead. */
    val isTodayView: Boolean = false,
    /** The day everything date-based is measured from; refreshed whenever the app is shown. */
    val today: LocalDate = LocalDate.now(),
    val wall: WallSettings = WallSettings(),
    /** The task that was just completed and archived, while its undo is on offer. */
    val archived: Task? = null,
) {
    val allTasks: List<Task>
        get() = tasks.values.flatten()

    /** What the Task page lists for [category]: open tasks that are not shelved. */
    fun openTasks(category: Category?): List<Task> =
        tasks[category].orEmpty().filter { it.isActive(today) }

    val todayTasks: List<Task>
        get() = allTasks.filter { it.isToday && it.isActive(today) }

    /** The archive, newest first. */
    val completedTasks: List<Task>
        get() = allTasks.filter { it.isCompleted }.sortedByDescending { it.completedAt }

    /** Parked without a date, most recently worked on first. */
    val somedayTasks: List<Task>
        get() =
            allTasks
                .filter { !it.isCompleted && it.isSomeday }
                .sortedByDescending { it.lastTouchedAt }

    /** Suspended until a day still ahead, soonest back first. */
    val suspendedTasks: List<Task>
        get() =
            allTasks
                .filter { !it.isCompleted && !it.isSomeday && it.isSuspended(today) }
                .sortedBy { it.hiddenUntil }
}
