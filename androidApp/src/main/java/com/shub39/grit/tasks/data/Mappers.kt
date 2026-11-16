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
package com.shub39.grit.tasks.data

import com.shub39.grit.core.tasks.*
import com.shub39.grit.tasks.data.database.*

fun Category.toCategoryEntity() =
    CategoryEntity(id, name, description, orderIndex, titleColor, createdAt, updatedAt)

fun CategoryEntity.toCategory() =
    Category(id, name, description, orderIndex, titleColor, createdAt, updatedAt)

fun Task.toTaskEntity() =
    TaskEntity(
        id = id,
        categoryId = categoryId,
        title = title,
        description = description,
        orderIndex = orderIndex,
        titleColor = titleColor,
        isCompleted = isCompleted,
        isToday = isToday,
        reminder = reminder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deadline = deadline,
        priority = priority.ordinal,
        lastTouchedAt = lastTouchedAt,
        completedAt = completedAt,
        hiddenUntil = hiddenUntil,
        isSomeday = isSomeday,
    )

fun TaskWithSections.toTask() =
    task.let {
        Task(
            id = it.id,
            categoryId = it.categoryId,
            title = it.title,
            orderIndex = it.orderIndex,
            isCompleted = it.isCompleted,
            reminder = it.reminder,
            description = it.description,
            isToday = it.isToday,
            titleColor = it.titleColor,
            createdAt = it.createdAt,
            updatedAt = it.updatedAt,
            sections = sections.sortedBy { s -> s.orderIndex }.map { s -> s.toSection() },
            deadline = it.deadline,
            priority = Priority.entries.getOrElse(it.priority) { Priority.NORMAL },
            lastTouchedAt = it.lastTouchedAt,
            completedAt = it.completedAt,
            hiddenUntil = it.hiddenUntil,
            isSomeday = it.isSomeday,
        )
    }

fun Section.toSectionEntity() =
    SectionEntity(
        id,
        taskId,
        title,
        content,
        orderIndex,
        isCompleted,
        createdAt,
        updatedAt,
        titleColor,
    )

fun SectionEntity.toSection() =
    Section(id, taskId, title, content, orderIndex, isCompleted, createdAt, updatedAt, titleColor)
