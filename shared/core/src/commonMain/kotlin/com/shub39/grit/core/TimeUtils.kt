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
package com.shub39.grit.core

import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

fun LocalDateTime.Companion.now(): LocalDateTime =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

fun LocalDate.Companion.now(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

fun LocalTime.Companion.now(): LocalTime = LocalDateTime.now().time

/** The first Monday after this day. */
fun LocalDate.nextMonday(): LocalDate {
    var day = plus(1, DateTimeUnit.DAY)
    while (day.dayOfWeek != DayOfWeek.MONDAY) day = day.plus(1, DateTimeUnit.DAY)
    return day
}
