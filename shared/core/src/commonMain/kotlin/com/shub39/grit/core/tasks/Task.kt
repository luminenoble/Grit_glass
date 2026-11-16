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
package com.shub39.grit.core.tasks

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/** How much a task matters; stored as its ordinal (0 / 1 / 2). Shown on the wall as fading. */
@Serializable
enum class Priority {
    LOW,
    NORMAL,
    HIGH,
}

@Serializable
data class Task(
    val id: Long = 0,
    val categoryId: Long,
    val title: String,
    val orderIndex: Int = 0,
    val isCompleted: Boolean = false,
    val reminder: LocalDateTime? = null,
    val description: String = "",
    val isToday: Boolean = false,
    val titleColor: String? = null,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = createdAt,
    val sections: List<Section> = emptyList(),
    /** Due day; null when the task has none. Decides the card size on the wall. */
    val deadline: LocalDate? = null,
    val priority: Priority = Priority.NORMAL,
    /** Last time the task was worked on, see [touches]. Set by the repository. */
    val lastTouchedAt: Instant = updatedAt,
    /** When the task was completed and archived; null while open. Set by the repository. */
    val completedAt: Instant? = null,
    /** Suspended until this day: off the Task page and the wall before it. */
    val hiddenUntil: LocalDate? = null,
    /** Parked without a date; only listed under "以后再说". */
    val isSomeday: Boolean = false,
) {
    fun isSuspended(today: LocalDate): Boolean = hiddenUntil != null && hiddenUntil > today

    /** Open and in play: listed on the Task page and placed on the wall. */
    fun isActive(today: LocalDate): Boolean = !isCompleted && !isSomeday && !isSuspended(today)
}

/**
 * Whether saving [next] over [previous] counts as working on the task: its title, description,
 * Sections, deadline or priority changed, or it was added to Today. Opening it, reordering,
 * completing, suspending and recolouring do not.
 */
fun touches(previous: Task?, next: Task): Boolean {
    if (previous == null) return true
    fun List<Section>.work() = map { listOf(it.id, it.title, it.content, it.isCompleted) }
    return previous.title != next.title ||
        previous.description != next.description ||
        previous.deadline != next.deadline ||
        previous.priority != next.priority ||
        (next.isToday && !previous.isToday) ||
        previous.sections.work() != next.sections.work()
}
