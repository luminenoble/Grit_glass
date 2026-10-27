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
package com.shub39.grit.tasks.data.database

import androidx.room3.*
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface TasksDao {
    @Query("SELECT id FROM tasks WHERE categoryId = :parentId ORDER BY orderIndex")
    suspend fun orderedIds(parentId: Long): List<Long>

    @Transaction
    @Query("SELECT * FROM tasks ORDER BY categoryId, orderIndex")
    fun getTasksFlow(): Flow<List<TaskWithSections>>

    @Transaction
    @Query("SELECT * FROM tasks ORDER BY categoryId, orderIndex")
    suspend fun getTasks(): List<TaskWithSections>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskWithSections?

    @Query("SELECT * FROM tasks WHERE categoryId = :categoryId ORDER BY orderIndex")
    suspend fun getInCategory(categoryId: Long): List<TaskEntity>

    @Upsert suspend fun upsert(task: TaskEntity): Long

    @Query("UPDATE tasks SET orderIndex = :position, updatedAt = :time WHERE id = :id")
    suspend fun setOrder(id: Long, position: Int, time: Instant)

    @Query("UPDATE tasks SET updatedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Instant)

    @Query("DELETE FROM tasks WHERE id = :id") suspend fun delete(id: Long)

    @Query("DELETE FROM tasks") suspend fun deleteAll()
}
