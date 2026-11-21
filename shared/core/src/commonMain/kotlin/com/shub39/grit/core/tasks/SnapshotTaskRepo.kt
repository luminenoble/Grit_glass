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

import com.shub39.grit.core.settings.backup.TaskBackup
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A [TaskRepo] that holds the whole library in memory as one [TaskBackup] and hands every new
 * version to [persist] before it becomes visible, so a failed save leaves nothing half done. The
 * desktop app persists to a JSON file; the web demo keeps it in memory. Rules match the Android
 * repository: contiguous order, tail inserts, [touches], archiving on completion.
 */
class SnapshotTaskRepo(
    initial: TaskBackup? = null,
    private val persist: suspend (TaskBackup) -> Unit = {},
) : TaskRepo {
    private data class Data(val categories: List<Category>, val tasks: List<Task>)

    private val data =
        MutableStateFlow(
            initial?.validated()?.let { Data(it.categories, it.tasks) } ?: Data(emptyList(), emptyList())
        )
    private val mutex = Mutex()
    private var nextCategory = 1L
    private var nextTask = 1L
    private var nextSection = 1L

    init {
        bumpIds(data.value)
    }

    private fun bumpIds(d: Data) {
        nextCategory = maxOf(nextCategory, (d.categories.maxOfOrNull { it.id } ?: 0) + 1)
        nextTask = maxOf(nextTask, (d.tasks.maxOfOrNull { it.id } ?: 0) + 1)
        nextSection =
            maxOf(nextSection, (d.tasks.flatMap { it.sections }.maxOfOrNull { it.id } ?: 0) + 1)
    }

    private fun snapshot(d: Data) =
        TaskBackup(
            TaskBackup.FORMAT,
            TaskBackup.VERSION,
            Clock.System.now(),
            d.categories.sortedBy { it.orderIndex },
            d.tasks.sortedWith(compareBy({ it.categoryId }, { it.orderIndex })),
        )

    private suspend fun <T> write(block: (Data) -> Pair<Data, T>): T =
        mutex.withLock {
            val ids = Triple(nextCategory, nextTask, nextSection)
            try {
                val (next, result) = block(data.value)
                persist(snapshot(next))
                data.value = next
                result
            } catch (e: Throwable) {
                nextCategory = ids.first
                nextTask = ids.second
                nextSection = ids.third
                throw e
            }
        }

    private fun normalize(ts: List<Task>) =
        ts.groupBy { it.categoryId }
            .values
            .flatMap { group ->
                group.sortedBy { it.orderIndex }.mapIndexed { i, t -> t.copy(orderIndex = i) }
            }

    override fun getCategoriesFlow(): Flow<List<Category>> =
        data.map { it.categories.sortedBy { c -> c.orderIndex } }

    override fun getTasksFlow(): Flow<Map<Category, List<Task>>> =
        data.map { d ->
            d.categories
                .sortedBy { it.orderIndex }
                .associateWith { c ->
                    d.tasks.filter { it.categoryId == c.id }.sortedBy { it.orderIndex }
                }
        }

    override fun getCompletedTasksFlow() = data.map { it.tasks.filter { t -> t.isCompleted } }

    override suspend fun getTasks() = data.value.tasks

    override suspend fun getCategories() = data.value.categories.sortedBy { it.orderIndex }

    override suspend fun getTaskById(id: Long) = data.value.tasks.firstOrNull { it.id == id }

    override suspend fun upsertCategory(category: Category): Long = write { d ->
        require(category.name.isNotBlank())
        validateTitleColor(category.titleColor)
        val old = d.categories.firstOrNull { it.id == category.id }
        require(category.id == 0L || old != null) { "The category was deleted" }
        val now = Clock.System.now()
        val saved =
            category.copy(
                id = old?.id ?: nextCategory++,
                orderIndex = old?.orderIndex ?: d.categories.size,
                name = category.name.trim(),
                createdAt = old?.createdAt ?: now,
                updatedAt = now,
            )
        d.copy(categories = d.categories.filterNot { it.id == saved.id } + saved) to saved.id
    }

    override suspend fun upsertTask(task: Task): Long = write { d ->
        require(task.title.isNotBlank())
        validateTitleColor(task.titleColor)
        require(d.categories.any { it.id == task.categoryId }) { "The category was deleted" }
        val old = d.tasks.firstOrNull { it.id == task.id }
        require(task.id == 0L || old != null) { "The task was deleted" }
        val id = old?.id ?: nextTask++
        val known = old?.sections?.associateBy { it.id } ?: emptyMap()
        val ids = task.sections.filter { it.id > 0 }.map { it.id }
        require(ids.distinct().size == ids.size && ids.all { it in known })
        val now = Clock.System.now()
        val prepared = task.sections.map { it.normalized() }
        val sections =
            prepared.mapIndexed { i, s ->
                val before = known[s.id]
                val candidate =
                    s.copy(
                        id = if (s.id == 0L) nextSection++ else s.id,
                        taskId = id,
                        orderIndex = i,
                        createdAt = before?.createdAt ?: now,
                    )
                candidate.copy(
                    updatedAt =
                        if (before != null && candidate == before.copy(taskId = id)) before.updatedAt
                        else now
                )
            }
        val draft = task.copy(title = task.title.trim(), sections = prepared)
        val moved = old != null && old.categoryId != task.categoryId
        val saved =
            draft.copy(
                id = id,
                orderIndex =
                    if (old == null || moved) d.tasks.count { it.categoryId == task.categoryId }
                    else old.orderIndex,
                createdAt = old?.createdAt ?: now,
                updatedAt = now,
                lastTouchedAt = if (touches(old, draft)) now else old!!.lastTouchedAt,
                completedAt =
                    when {
                        !task.isCompleted -> null
                        old?.isCompleted == true -> old.completedAt ?: now
                        else -> now
                    },
                sections = sections,
            )
        d.copy(tasks = normalize(d.tasks.filterNot { it.id == id } + saved)) to id
    }

    override suspend fun deleteTask(task: Task) {
        write { d -> d.copy(tasks = normalize(d.tasks.filterNot { it.id == task.id })) to Unit }
    }

    override suspend fun deleteTasks(ids: List<Long>) {
        write { d -> d.copy(tasks = normalize(d.tasks.filterNot { it.id in ids })) to Unit }
    }

    override suspend fun deleteAllTasks() {
        write { d -> d.copy(tasks = emptyList()) to Unit }
    }

    override suspend fun deleteCategory(category: Category) {
        write { d ->
            d.copy(
                categories =
                    d.categories
                        .filterNot { it.id == category.id }
                        .sortedBy { it.orderIndex }
                        .mapIndexed { i, c -> c.copy(orderIndex = i) },
                tasks = d.tasks.filterNot { it.categoryId == category.id },
            ) to Unit
        }
    }

    override suspend fun deleteAllCategories() {
        write { Data(emptyList(), emptyList()) to Unit }
    }

    override suspend fun clearToday(before: Instant) {
        write { d ->
            d.copy(
                tasks =
                    d.tasks.map {
                        if (it.isToday && it.lastTouchedAt < before) it.copy(isToday = false) else it
                    }
            ) to Unit
        }
    }

    override suspend fun reorderTasks(categoryId: Long, ids: List<Long>) {
        write { d ->
            validateReorder(d.tasks.filter { it.categoryId == categoryId }.map { it.id }, ids)
            val now = Clock.System.now()
            d.copy(
                tasks =
                    d.tasks.map {
                        if (it.categoryId == categoryId)
                            it.copy(orderIndex = ids.indexOf(it.id), updatedAt = now)
                        else it
                    }
            ) to Unit
        }
    }

    override suspend fun reorderCategories(ids: List<Long>) {
        write { d ->
            validateReorder(d.categories.map { it.id }, ids)
            val now = Clock.System.now()
            d.copy(
                categories =
                    d.categories.map { it.copy(orderIndex = ids.indexOf(it.id), updatedAt = now) }
            ) to Unit
        }
    }

    override suspend fun upsertSection(section: Section): Long = write { d ->
        val task = requireNotNull(d.tasks.firstOrNull { it.id == section.taskId })
        val old = task.sections.firstOrNull { it.id == section.id }
        require(section.id == 0L || old != null)
        val now = Clock.System.now()
        val saved =
            section
                .normalized()
                .copy(
                    id = old?.id ?: nextSection++,
                    orderIndex = old?.orderIndex ?: task.sections.size,
                    createdAt = old?.createdAt ?: now,
                    updatedAt = now,
                )
        val updated =
            task.copy(
                sections =
                    (task.sections.filterNot { it.id == saved.id } + saved).sortedBy {
                        it.orderIndex
                    },
                updatedAt = now,
                lastTouchedAt = now,
            )
        d.copy(tasks = d.tasks.map { if (it.id == task.id) updated else it }) to saved.id
    }

    override suspend fun deleteSection(id: Long) {
        write { d ->
            val now = Clock.System.now()
            d.copy(
                tasks =
                    d.tasks.map { t ->
                        if (t.sections.none { it.id == id }) t
                        else
                            t.copy(
                                sections =
                                    t.sections
                                        .filterNot { it.id == id }
                                        .mapIndexed { i, s -> s.copy(orderIndex = i) },
                                updatedAt = now,
                                lastTouchedAt = now,
                            )
                    }
            ) to Unit
        }
    }

    override suspend fun exportSnapshot(): TaskBackup = mutex.withLock { snapshot(data.value) }

    override suspend fun replaceData(backup: TaskBackup) {
        val valid = backup.validated()
        write {
            val next = Data(valid.categories, valid.tasks)
            bumpIds(next)
            next to Unit
        }
    }
}
