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
package com.shub39.grit.desktop

import com.shub39.grit.core.settings.backup.TaskBackup
import com.shub39.grit.core.tasks.Category
import com.shub39.grit.core.tasks.SnapshotTaskRepo
import com.shub39.grit.core.tasks.Task
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayRolloverTest {
    @Test
    fun clearingTodayKeepsTasksPutThereSinceTheDayBegan() = runBlocking {
        val now = Clock.System.now()
        val dayStart = now - 2.hours
        fun task(id: Long, touched: Instant, today: Boolean) =
            Task(
                id = id,
                categoryId = 1,
                title = "t$id",
                orderIndex = id.toInt() - 1,
                isToday = today,
                createdAt = touched,
                updatedAt = touched,
                lastTouchedAt = touched,
            )
        val repo =
            SnapshotTaskRepo(
                TaskBackup(
                    TaskBackup.FORMAT,
                    TaskBackup.VERSION,
                    now,
                    listOf(Category(id = 1, name = "Work", createdAt = now - 48.hours)),
                    listOf(
                        task(1, now - 30.hours, today = true), // yesterday's pick
                        task(2, now - 1.hours, today = true), // picked today
                        task(3, now - 30.hours, today = false),
                    ),
                )
            )

        repo.clearToday(dayStart)
        // A second run on the same day (interrupted start, synced-in library) changes nothing.
        repo.clearToday(dayStart)

        val tasks = repo.getTasks().associateBy { it.id }
        assertFalse(tasks.getValue(1).isToday)
        assertTrue(tasks.getValue(2).isToday)
        assertFalse(tasks.getValue(3).isToday)
        // Leaving Today is not work on the task.
        assertEquals(now - 30.hours, tasks.getValue(1).lastTouchedAt)
    }
}
