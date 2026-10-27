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

@Dao
interface SectionDao {
    @Query("SELECT * FROM sections WHERE taskId = :taskId ORDER BY orderIndex")
    suspend fun getInTask(taskId: Long): List<SectionEntity>

    @Query("SELECT * FROM sections WHERE id = :id") suspend fun getById(id: Long): SectionEntity?

    @Upsert suspend fun upsert(section: SectionEntity): Long

    @Query("DELETE FROM sections WHERE id = :id") suspend fun delete(id: Long)

    @Query("UPDATE sections SET orderIndex = :position, updatedAt = :time WHERE id = :id")
    suspend fun setOrder(id: Long, position: Int, time: Instant)
}
