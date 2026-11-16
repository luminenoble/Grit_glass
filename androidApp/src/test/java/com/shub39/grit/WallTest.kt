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
    fun tierFollowsDaysLeftAndTodayLiftsToL() {
        fun tier(due: Int?, isToday: Boolean = false) =
            task(1, due, isToday = isToday).wallTier(today)
        assertEquals(WallTier.XL, tier(-3))
        assertEquals(WallTier.XL, tier(0))
        assertEquals(WallTier.XL, tier(1))
        assertEquals(WallTier.L, tier(2))
        assertEquals(WallTier.L, tier(7))
        assertEquals(WallTier.M, tier(8))
        assertEquals(WallTier.M, tier(30))
        assertEquals(WallTier.S, tier(31))
        assertEquals(WallTier.S, tier(null))
        assertEquals(WallTier.L, tier(null, isToday = true))
        assertEquals(WallTier.L, tier(31, isToday = true))
        assertEquals(WallTier.L, tier(20, isToday = true))
        assertEquals(WallTier.XL, tier(1, isToday = true))
        // Thresholds are the user's.
        val wide = WallThresholds(3, 10, 60)
        assertEquals(WallTier.XL, task(1, 3).wallTier(today, wide))
        assertEquals(WallTier.L, task(1, 10).wallTier(today, wide))
        assertEquals(WallTier.M, task(1, 60).wallTier(today, wide))
        assertEquals(WallTier.S, task(1, 61).wallTier(today, wide))
        assertTrue(WallThresholds().isValid)
        for (bad in listOf(WallThresholds(0, 7, 30), WallThresholds(7, 7, 30), WallThresholds(1, 30, 7)))
            assertFalse(bad.isValid)
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
    fun orderIsTierThenDatedThenPriorityThenDeadlineOrRecency() {
        val tasks =
            listOf(
                task(1, idle = 5),
                task(2, idle = 1),
                task(3, priority = Priority.HIGH, idle = 90),
                task(4, due = 40, priority = Priority.LOW),
                task(5, due = 50),
                task(6, due = 45),
                task(7, due = 20, priority = Priority.HIGH),
                task(8, due = 10, priority = Priority.LOW),
                task(9, due = 5),
                task(10, due = 3, priority = Priority.LOW),
                task(11, due = -2),
                task(12, isToday = true, idle = 3),
                task(13, idle = 1),
            )
        assertEquals(
            // XL; L dated by priority then date, the undated Today task after them; M with HIGH
            // first; dated S; undated S by priority, then most recently touched, then id.
            listOf(11L, 9, 10, 12, 7, 8, 6, 5, 4, 3, 2, 13, 1),
            wallOrder(tasks.shuffled(Random(7)), today).map { it.first.id },
        )
    }

    @Test
    fun archivedAndShelvedTasksStayOffTheWall() {
        val tasks =
            listOf(
                task(1),
                task(2).copy(isCompleted = true),
                task(3).copy(isSomeday = true),
                task(4).copy(hiddenUntil = today.plus(1, DateTimeUnit.DAY)),
                // Back on the day it was suspended until.
                task(5).copy(hiddenUntil = today),
            )
        assertEquals(listOf(1L, 5L), wallOrder(tasks, today).map { it.first.id }.sorted())
        assertEquals(LocalDate(2026, 11, 23), today.nextMonday())
        assertEquals(LocalDate(2026, 11, 23), LocalDate(2026, 11, 22).nextMonday())
    }

    /** Cards of a band as "id@column,row", in placement order. */
    private fun WallBand.spots() =
        (this as WallBand.Cards).cards.joinToString(" ") { "${it.task.id}@${it.column},${it.row}" }

    @Test
    fun cardsPackDenselyAndSplitIntoBandsAndTheUndatedLine() {
        val tasks =
            listOf(task(1, due = 0)) +
                (2L..4L).map { task(it, due = 3) } +
                (5L..7L).map { task(it, due = 15) } +
                (8L..10L).map { task(it, due = 60) } +
                (11L..12L).map { task(it, idle = it.toInt()) }
        val wall = layoutWall(tasks, today)
        assertEquals(
            listOf(
                "1@0,0",
                "2@0,0 3@2,0",
                // The odd L leaves a 2×2 square that the next two M fill.
                "4@0,0 5@2,0 6@2,1",
                "7@0,0 8@2,0 9@3,0",
                "10@0,0",
                null,
                "11@0,0 12@1,0",
            ),
            wall.map { if (it is WallBand.Cards) it.spots() else null },
        )
        assertEquals(listOf(2, 2, 2, 1, 1), wall.filterIsInstance<WallBand.Cards>().take(5).map { it.rows })
        assertSame(WallBand.NoDeadline, wall[5])
        // With nothing undated there is no line; with nothing dated the line comes first.
        assertTrue(layoutWall(tasks.take(7), today).none { it === WallBand.NoDeadline })
        assertSame(WallBand.NoDeadline, layoutWall(tasks.takeLast(2), today).first())
        assertTrue(layoutWall(emptyList(), today).isEmpty())
    }

    @Test
    fun wideWallsUseEightColumns() {
        val tasks = listOf(task(1, due = 0), task(2, due = 3), task(3, due = 15), task(4, due = 15))
        val band = layoutWall(tasks, today, columns = 8).single()
        // XL takes the left half; the L and the two M stack beside it.
        assertEquals("1@0,0 2@4,0 3@6,0 4@6,1", band.spots())
    }

    @Test
    fun everyActiveTaskIsPlacedOnceWithoutOverlap() {
        val random = Random(42)
        repeat(60) { round ->
            val tasks =
                (1L..random.nextLong(1, 60)).map { id ->
                    task(
                        id,
                        due = if (random.nextInt(3) == 0) null else random.nextInt(-5, 80),
                        priority = Priority.entries.random(random),
                        idle = random.nextInt(0, 100),
                        isToday = random.nextInt(8) == 0,
                    )
                }
            for (columns in listOf(4, 8)) {
                val bands = layoutWall(tasks, today, columns = columns)
                val placed = bands.filterIsInstance<WallBand.Cards>().flatMap { it.cards }
                assertEquals("round $round", tasks.map { it.id }.sorted(), placed.map { it.task.id }.sorted())
                for (band in bands.filterIsInstance<WallBand.Cards>()) {
                    val cells = HashSet<Pair<Int, Int>>()
                    for (card in band.cards) {
                        assertTrue(card.column >= 0 && card.column + card.columns <= columns)
                        assertTrue(card.row >= 0 && card.row + card.rows <= band.rows)
                        for (r in card.row until card.row + card.rows) for (c in
                            card.column until card.column + card.columns) assertTrue(cells.add(c to r))
                    }
                }
                assertTrue(bands.count { it === WallBand.NoDeadline } <= 1)
            }
        }
    }

    @Test
    fun gapsAreOnlyFilledByCardsCloseInOrder() {
        fun spots(tiers: List<WallTier>) =
            packWall(tiers.mapIndexed { i, tier -> task(i.toLong()) to tier }, 4).joinToString(" ") {
                "${it.task.id}@${it.column},${it.row}"
            }
        // The XL cannot sit beside the first S, which leaves three cells; cards up to four places
        // after that S drop back into them.
        assertEquals(
            "0@0,0 1@0,1 2@1,0 3@2,0 4@3,0 5@0,3",
            spots(listOf(WallTier.S, WallTier.XL, WallTier.S, WallTier.S, WallTier.S, WallTier.S)),
        )
        // Five places on, the gap is left alone and the card goes to the end.
        assertEquals(
            "0@0,0 1@0,1 2@0,3 3@0,5 4@0,7 5@0,9",
            spots(listOf(WallTier.S, WallTier.XL, WallTier.XL, WallTier.XL, WallTier.XL, WallTier.S)),
        )
    }
}
