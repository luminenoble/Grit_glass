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
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Section(
    val id: Long = 0,
    @Transient val taskId: Long = 0,
    val title: String = "unname",
    val content: String = "",
    val orderIndex: Int = 0,
    val isCompleted: Boolean = false,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = createdAt,
    /** Same format as a Task's title colour; null is the default black. */
    val titleColor: String? = null,
) {
    fun toggleCompleted(): Section = copy(isCompleted = !isCompleted)

    fun normalized(): Section {
        validateTitleColor(titleColor)
        return copy(title = title.trim().ifBlank { "unname" })
    }
}
