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
package com.shub39.grit.shared.ui.overview.ui.component

import com.shub39.grit.core.tasks.Priority
import com.shub39.grit.core.tasks.Task
import com.shub39.grit.core.tasks.daysLeft
import com.shub39.grit.core.tasks.idleDays
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/** "11月23日", with the year once it is not this one. */
fun LocalDate.label(today: LocalDate): String =
    if (year == today.year) "${month.number}月${day}日" else "${year}年${month.number}月${day}日"

/**
 * What a dated wall card says about time: the days left, or just the date once it has passed. A
 * passed deadline is never called late.
 */
fun Task.deadlineLabel(today: LocalDate): String? {
    val left = daysLeft(today) ?: return null
    return when {
        left < 0 -> deadline?.label(today)
        left == 0 -> "今天"
        left == 1 -> "明天"
        else -> "$left 天"
    }
}

/** What an undated wall card says about time: days since it was last worked on. */
fun Task.idleLabel(today: LocalDate): String =
    idleDays(today).let { if (it == 0) "刚动过" else "$it 天" }

fun Priority.label(): String =
    when (this) {
        Priority.LOW -> "低"
        Priority.NORMAL -> "普通"
        Priority.HIGH -> "高"
    }

/** The day the task was completed, in the device's time zone. */
fun Task.completedDay(): LocalDate? =
    completedAt?.toLocalDateTime(TimeZone.currentSystemDefault())?.date
