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

private val Months =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** "Nov 23", with the year once it is not this one. */
fun LocalDate.label(today: LocalDate): String =
    "${Months[month.number - 1]} $day".let { if (year == today.year) it else "$it, $year" }

/** "1 day", "12 days". */
fun days(count: Int): String = if (count == 1) "1 day" else "$count days"

/**
 * What a dated wall card says about time: the days left, or just the date once it has passed. A
 * passed deadline is never called late.
 */
fun Task.deadlineLabel(today: LocalDate): String? {
    val left = daysLeft(today) ?: return null
    return when {
        left < 0 -> deadline?.label(today)
        left == 0 -> "Today"
        left == 1 -> "Tomorrow"
        else -> days(left)
    }
}

/** What an undated wall card says about time: days since it was last worked on. */
fun Task.idleLabel(today: LocalDate): String =
    idleDays(today).let { if (it == 0) "Today" else days(it) }

fun Priority.label(): String =
    when (this) {
        Priority.LOW -> "Low"
        Priority.NORMAL -> "Normal"
        Priority.HIGH -> "High"
    }

/** The day the task was completed, in the device's time zone. */
fun Task.completedDay(): LocalDate? =
    completedAt?.toLocalDateTime(TimeZone.currentSystemDefault())?.date
