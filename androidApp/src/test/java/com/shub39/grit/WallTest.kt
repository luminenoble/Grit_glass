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
package com.shub39.grit

import com.shub39.grit.core.nextMonday
import com.shub39.grit.core.tasks.*
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Instant
import kotlinx.datetime.*
import org.junit.Assert.*
import org.junit.Test

class WallTest {
    private val today = LocalDate(2026, 11, 16)
    private val zone = TimeZone.UTC
    private val noon = today.atTime(12, 0).toInstant(zone)

    private fun task(
        id: Long,
        due: Int? = null,
        priority: Priority = Priority.NORMAL,
        idle: Int = 0,
        isToday: Boolean = false,
    ) =
        Task(
            id = id,
            categoryId = 1,
            title = "$id",
            deadline = due?.let { today.plus(it, DateTimeUnit.DAY) },
            priority = priority,
            isToday = isToday,
            lastTouchedAt = noon.minus(idle, DateTimeUnit.DAY, zone),
        )

    @Test
    fun urgencyFollowsDaysLeftAndThresholds() {
        fun urgency(due: Int?) = task(1, due).urgency(today)
        assertEquals(Urgency.DUE, urgency(-3))
        assertEquals(Urgency.DUE, urgency(0))
        assertEquals(Urgency.DUE, urgency(1))
        assertEquals(Urgency.SOON, urgency(2))
        assertEquals(Urgency.SOON, urgency(7))
        assertEquals(Urgency.UPCOMING, urgency(8))
        assertEquals(Urgency.UPCOMING, urgency(30))
        assertEquals(Urgency.AHEAD, urgency(31))
        assertEquals(Urgency.NONE, urgency(null))
        // Thresholds are the user's.
        val wide = WallThresholds(3, 10, 60)
        assertEquals(Urgency.DUE, task(1, 3).urgency(today, wide))
        assertEquals(Urgency.SOON, task(1, 10).urgency(today, wide))
        assertEquals(Urgency.UPCOMING, task(1, 60).urgency(today, wide))
        assertEquals(Urgency.AHEAD, task(1, 61).urgency(today, wide))
        assertTrue(WallThresholds().isValid)
        for (bad in listOf(WallThresholds(0, 7, 30), WallThresholds(7, 7, 30), WallThresholds(1, 30, 7)))
            assertFalse(bad.isValid)
    }

    @Test
    fun sizeIsImportanceWithPassedDeadlinesAndTodayLifted() {
        assertEquals(CardSize.LARGE, task(1, priority = Priority.HIGH).cardSize(today))
        assertEquals(CardSize.LARGE, task(1, due = -1, priority = Priority.LOW).cardSize(today))
        assertEquals(CardSize.MEDIUM, task(1).cardSize(today))
        assertEquals(CardSize.MEDIUM, task(1, priority = Priority.LOW, isToday = true).cardSize(today))
        assertEquals(CardSize.SMALL, task(1, due = 0, priority = Priority.LOW).cardSize(today))
        assertEquals(CardSize.SMALL, task(1, priority = Priority.LOW).cardSize(today))
    }

    @Test
    fun undatedTasksSinkWithIdleDaysUnlessHigh() {
        assertEquals(12, task(1, idle = 12).idleDays(today, zone))
        assertFalse(task(1, priority = Priority.LOW, idle = 30).hasSunk(today, zone))
        assertTrue(task(1, priority = Priority.LOW, idle = 31).hasSunk(today, zone))
        assertFalse(task(1, idle = 60).hasSunk(today, zone))
        assertTrue(task(1, idle = 61).hasSunk(today, zone))
        assertFalse(task(1, priority = Priority.HIGH, idle = 400).hasSunk(today, zone))
        // A deadline is its own limit: dated tasks never sink.
        assertFalse(task(1, due = 90, priority = Priority.LOW, idle = 400).hasSunk(today, zone))
        assertTrue(task(1, due = -1).isOverdue(today))
        assertFalse(task(1, due = 0).isOverdue(today))
        assertFalse(task(1).isOverdue(today))
    }

    @Test
    fun dueListIsDatedOpenTasksSoonestFirst() {
        val tasks =
            listOf(
                task(1, due = 5),
                task(2),
                task(3, due = -2),
                task(4, due = 5, priority = Priority.HIGH),
                task(5, due = 1).copy(isCompleted = true),
                task(6, due = 3).copy(isSomeday = true),
                task(7, due = 3).copy(hiddenUntil = today.plus(1, DateTimeUnit.DAY)),
                // Back on the day it was suspended until.
                task(8, due = 9).copy(hiddenUntil = today),
            )
        assertEquals(listOf(3L, 4, 1, 8), dueOrder(tasks.shuffled(Random(3)), today).map { it.id })
        assertEquals(LocalDate(2026, 11, 23), today.nextMonday())
        assertEquals(LocalDate(2026, 11, 23), LocalDate(2026, 11, 22).nextMonday())
    }

    @Test
    fun rankPutsUrgentThenImportantFirst() {
        val tasks =
            listOf(
                task(1, idle = 5),
                task(2, idle = 1),
                task(3, priority = Priority.HIGH),
                task(4, due = 40),
                task(5, due = 3, priority = Priority.LOW),
                task(6, due = -2),
                task(7, due = 1, priority = Priority.HIGH),
                task(8, due = 20),
            )
        assertEquals(
            listOf(7L, 6, 5, 8, 4, 3, 2, 1),
            tasks.shuffled(Random(5)).sortedWith(wallRank(today)).map { it.id },
        )
    }

    /** Desk boxes of [spots] in dp, for overlap checks. */
    private fun boxes(tasks: List<Task>, spots: Map<Long, CardSpot>) =
        tasks.map { t ->
            val s = spots.getValue(t.id)
            val c = t.cardSize(today)
            floatArrayOf(s.x - c.width / 2, s.y - c.height / 2, s.x + c.width / 2, s.y + c.height / 2)
        }

    private fun assertNoOverlap(tasks: List<Task>, spots: Map<Long, CardSpot>) {
        val b = boxes(tasks, spots)
        for (i in b.indices) for (j in i + 1 until b.size) {
            val overlap = b[i][0] < b[j][2] && b[j][0] < b[i][2] && b[i][1] < b[j][3] && b[j][1] < b[i][3]
            assertFalse("${tasks[i].id} overlaps ${tasks[j].id}", overlap)
        }
    }

    private val many =
        (1L..40L).map { id ->
            task(
                id,
                due = if (id % 3 == 0L) null else (id % 11).toInt() - 2,
                priority = Priority.entries[(id % 3).toInt()],
                idle = id.toInt(),
            ).copy(categoryId = id % 4 + 1)
        }

    @Test
    fun newCardsFillFreeSpotsAndKeepPlacedOnes() {
        val placed = placeMissing(many, emptyMap(), today)
        assertEquals(many.map { it.id }.toSet(), placed.keys)
        assertNoOverlap(many, placed)
        placed.values.forEach { assertTrue(abs(it.angle) in 1.5f..2.5f) }
        // The most pressing card is laid in the middle.
        val first = many.sortedWith(wallRank(today)).first()
        assertEquals(0f, placed.getValue(first.id).x, 0.01f)
        // Cards with a spot stay put; a new one goes where nothing lies.
        val moved = placed + (1L to CardSpot(5000f, 5000f, 0f))
        val extra = task(99, priority = Priority.HIGH)
        val next = placeMissing(many + extra, moved, today)
        assertEquals(CardSpot(5000f, 5000f, 0f), next.getValue(1L))
        many.forEach { if (it.id != 1L) assertEquals(placed.getValue(it.id), next.getValue(it.id)) }
        assertNoOverlap(many + extra, next)
        // Tilt is stable per task.
        assertEquals(naturalTilt(42), naturalTilt(42))
    }

    @Test
    fun tidyingNeverStacksCards() {
        assertNoOverlap(many, pullUrgentToCentre(many, today))
        val scattered = scatter(many, today, seed = 3)
        assertNoOverlap(many, scattered)
        assertEquals(scattered, scatter(many, today, seed = 3))
        val clustered = clusterByCategory(many, listOf(4L, 3L, 2L, 1L), today)
        assertNoOverlap(many, clustered)
        assertEquals(many.size, clustered.size)
        // Categories sit apart: each category's cards span a range of x no other category enters
        // within the same row of clusters.
        val byCategory = many.groupBy { it.categoryId }.mapValues { (_, ts) -> boxes(ts, clustered) }
        val ranges = byCategory.mapValues { (_, b) -> Triple(b.minOf { it[0] }, b.maxOf { it[2] }, b.minOf { it[1] }) }
        for ((a, ra) in ranges) for ((b, rb) in ranges) {
            if (a >= b || abs(ra.third - rb.third) > 1f) continue
            assertTrue(ra.second < rb.first || rb.second < ra.first)
        }
        assertTrue(placeMissing(emptyList(), emptyMap(), today).isEmpty())
        assertTrue(clusterByCategory(emptyList(), emptyList(), today).isEmpty())
    }
}
