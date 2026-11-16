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

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/*
 * The task wall: open tasks laid out as cards whose size is urgency (days to the deadline) and
 * whose order is priority. Pure functions; the UI only draws what [layoutWall] returns.
 */

/** Card sizes, most urgent first, as spans on the wall grid. */
enum class WallTier(val columns: Int, val rows: Int) {
    XL(4, 2),
    L(2, 2),
    M(2, 1),
    S(1, 1),
}

/** Days left up to which a task is XL ([a]), L ([b]) and M ([c]); beyond [c] it is S. */
data class WallThresholds(val a: Int = 1, val b: Int = 7, val c: Int = 30) {
    val isValid: Boolean
        get() = a in 1..<b && b < c
}

/** Days from [today] to the deadline, negative once it has passed; null without a deadline. */
fun Task.daysLeft(today: LocalDate): Int? = deadline?.let { today.daysUntil(it) }

fun Task.isOverdue(today: LocalDate): Boolean = deadline != null && deadline < today

/** Whole days since the task was last worked on. */
fun Task.idleDays(today: LocalDate, zone: TimeZone = TimeZone.currentSystemDefault()): Int =
    lastTouchedAt.toLocalDateTime(zone).date.daysUntil(today).coerceAtLeast(0)

/** Idle days after which an undated task of this priority has sunk; null if it never does. */
val Priority.sinksAfterDays: Int?
    get() =
        when (this) {
            Priority.LOW -> 30
            Priority.NORMAL -> 60
            Priority.HIGH -> null
        }

/** An undated task left alone for too long: the wall asks what to do with it. */
fun Task.hasSunk(today: LocalDate, zone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
    val limit = priority.sinksAfterDays ?: return false
    return deadline == null && idleDays(today, zone) > limit
}

/** Size by absolute days left, so it only changes when the day does. Today tasks are at least L. */
fun Task.wallTier(today: LocalDate, thresholds: WallThresholds = WallThresholds()): WallTier {
    val left = daysLeft(today)
    val tier =
        when {
            left == null -> WallTier.S
            left <= thresholds.a -> WallTier.XL
            left <= thresholds.b -> WallTier.L
            left <= thresholds.c -> WallTier.M
            else -> WallTier.S
        }
    return if (isToday && tier > WallTier.L) WallTier.L else tier
}

/** A card placed on the wall grid; [row] counts from the top of its band. */
data class WallCard(
    val task: Task,
    val tier: WallTier,
    val column: Int,
    val row: Int,
    val columns: Int,
    val rows: Int,
)

/** A horizontal slice of the wall that no card crosses, so slices can be laid out lazily. */
sealed interface WallBand {
    data class Cards(val rows: Int, val cards: List<WallCard>) : WallBand

    /** The label row above the S cards that have no deadline. */
    data object NoDeadline : WallBand
}

/** Reading order of the wall: tier, dated before undated, priority, then deadline or recency. */
fun wallOrder(
    tasks: List<Task>,
    today: LocalDate,
    thresholds: WallThresholds = WallThresholds(),
): List<Pair<Task, WallTier>> =
    tasks
        .filter { it.isActive(today) }
        .map { it to it.wallTier(today, thresholds) }
        .sortedWith(
            compareBy<Pair<Task, WallTier>> { it.second }
                .thenBy { it.first.deadline == null }
                .thenByDescending { it.first.priority }
                .then { x, y ->
                    val first = x.first.deadline
                    val second = y.first.deadline
                    if (first != null && second != null) first.compareTo(second)
                    else y.first.lastTouchedAt.compareTo(x.first.lastTouchedAt)
                }
                .thenBy { it.first.id }
        )

/** How far back in the order a card may be from the card before a gap and still fill it. */
private const val BackfillReach = 4

/**
 * Places [cards] in order on a grid [columns] wide: each goes to the first place it fits, reading
 * left to right and top to bottom, like CSS `grid-auto-flow: row dense`. A card only drops back
 * into a gap left behind when it is within [BackfillReach] places of the card before that gap;
 * otherwise the gap stays, so nothing jumps far ahead of its place in the order. Rows count from
 * the top of the run.
 */
fun packWall(cards: List<Pair<Task, WallTier>>, columns: Int): List<WallCard> {
    val taken = ArrayList<BooleanArray>()
    // For an empty cell the flow has passed: index of the card placed just before the gap opened.
    val gapAfter = ArrayList<IntArray>()
    fun grow(row: Int) {
        while (taken.size <= row) {
            taken += BooleanArray(columns)
            gapAfter += IntArray(columns) { -1 }
        }
    }
    fun fits(cell: Int, w: Int, h: Int): Boolean {
        val row = cell / columns
        val column = cell % columns
        if (column + w > columns) return false
        grow(row + h - 1)
        for (r in row until row + h) for (c in column until column + w) if (taken[r][c]) return false
        return true
    }
    val placed = ArrayList<WallCard>(cards.size)
    var flow = 0
    cards.forEachIndexed { index, (task, tier) ->
        val w = minOf(tier.columns, columns)
        val h = tier.rows
        fun reachable(cell: Int): Boolean {
            val row = cell / columns
            val column = cell % columns
            for (r in row until row + h) for (c in column until column + w) {
                if (r * columns + c >= flow) continue
                if (index - gapAfter[r][c] > BackfillReach) return false
            }
            return true
        }
        var spot = (0 until flow).firstOrNull { fits(it, w, h) && reachable(it) } ?: -1
        if (spot < 0) {
            spot = flow
            while (!fits(spot, w, h)) spot++
            for (cell in flow until spot) {
                val row = cell / columns
                val column = cell % columns
                if (!taken[row][column]) gapAfter[row][column] = index - 1
            }
            flow = spot + w
        }
        val row = spot / columns
        val column = spot % columns
        for (r in row until row + h) for (c in column until column + w) taken[r][c] = true
        placed += WallCard(task, tier, column, row, w, h)
    }
    return placed
}

/** Cuts a packed run into bands at every row boundary no card crosses. */
private fun bands(cards: List<WallCard>): List<WallBand.Cards> {
    if (cards.isEmpty()) return emptyList()
    val rows = cards.maxOf { it.row + it.rows }
    val crossed = BooleanArray(rows)
    for (card in cards) for (r in card.row + 1 until card.row + card.rows) crossed[r] = true
    val result = ArrayList<WallBand.Cards>()
    var start = 0
    for (end in 1..rows) {
        if (end < rows && crossed[end]) continue
        result +=
            WallBand.Cards(
                rows = end - start,
                cards =
                    cards
                        .filter { it.row in start until end }
                        .map { it.copy(row = it.row - start) },
            )
        start = end
    }
    return result
}

/**
 * The wall for [tasks] on [today]: active tasks sorted by [wallOrder] and packed on a grid
 * [columns] wide. S cards without a deadline follow a [WallBand.NoDeadline] row and are packed on
 * their own, so nothing drifts across that line.
 */
fun layoutWall(
    tasks: List<Task>,
    today: LocalDate,
    thresholds: WallThresholds = WallThresholds(),
    columns: Int = 4,
): List<WallBand> {
    val ordered = wallOrder(tasks, today, thresholds)
    val split =
        ordered
            .indexOfFirst { (task, tier) -> tier == WallTier.S && task.deadline == null }
            .let { if (it < 0) ordered.size else it }
    val dated = bands(packWall(ordered.subList(0, split), columns))
    val undated = bands(packWall(ordered.subList(split, ordered.size), columns))
    return if (undated.isEmpty()) dated else dated + WallBand.NoDeadline + undated
}
