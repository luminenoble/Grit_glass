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

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/*
 * The task wall. On a phone it is a list ordered by deadline; on a wide window it is a desk of
 * paper cards that can be moved freely, whose size is importance and whose paper deepens as the
 * deadline nears. Everything here is a pure function; the UI only draws what it returns.
 */

/** Days left up to which a deadline is due ([a]), soon ([b]) and upcoming ([c]). */
data class WallThresholds(val a: Int = 1, val b: Int = 7, val c: Int = 30) {
    val isValid: Boolean
        get() = a in 1..<b && b < c
}

/** How close a deadline is, from none to due (or passed). Decides how deep a card's paper is. */
enum class Urgency {
    NONE,
    AHEAD,
    UPCOMING,
    SOON,
    DUE,
}

/** Days from [today] to the deadline, negative once it has passed; null without a deadline. */
fun Task.daysLeft(today: LocalDate): Int? = deadline?.let { today.daysUntil(it) }

fun Task.isOverdue(today: LocalDate): Boolean = deadline != null && deadline < today

/** By absolute days left, so it only changes when the day does. */
fun Task.urgency(today: LocalDate, thresholds: WallThresholds = WallThresholds()): Urgency {
    val left = daysLeft(today) ?: return Urgency.NONE
    return when {
        left <= thresholds.a -> Urgency.DUE
        left <= thresholds.b -> Urgency.SOON
        left <= thresholds.c -> Urgency.UPCOMING
        else -> Urgency.AHEAD
    }
}

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

/**
 * Open tasks with a deadline, soonest first: passed deadlines lead, then by date, priority and
 * id. The phone's "Due" list.
 */
fun dueOrder(tasks: List<Task>, today: LocalDate): List<Task> =
    tasks
        .filter { it.isActive(today) && it.deadline != null }
        .sortedWith(
            compareBy<Task> { it.deadline }.thenByDescending { it.priority }.thenBy { it.id }
        )

/** Card sizes on the desk in dp: importance is area. */
enum class CardSize(val width: Float, val height: Float) {
    LARGE(300f, 150f),
    MEDIUM(230f, 112f),
    SMALL(180f, 92f),
}

/** High priority or a passed deadline is large; normal, or on Today, at least medium. */
fun Task.cardSize(today: LocalDate): CardSize =
    when {
        priority == Priority.HIGH || isOverdue(today) -> CardSize.LARGE
        priority == Priority.NORMAL || isToday -> CardSize.MEDIUM
        else -> CardSize.SMALL
    }

/**
 * Where a card lies on the desk: its centre in dp from the desk's origin, and its tilt in degrees.
 * Kept on the device only, keyed by task id; never part of a backup.
 */
@Serializable data class CardSpot(val x: Float, val y: Float, val angle: Float)

/** The order cards are placed in, nearest the centre first: urgency, priority, date, recency. */
fun wallRank(today: LocalDate, thresholds: WallThresholds = WallThresholds()): Comparator<Task> =
    compareByDescending<Task> { it.urgency(today, thresholds) }
        .thenByDescending { it.priority }
        .then { x, y ->
            val first = x.deadline
            val second = y.deadline
            if (first != null && second != null) first.compareTo(second)
            else y.lastTouchedAt.compareTo(x.lastTouchedAt)
        }
        .thenBy { it.id }

/** A natural tilt of 1.5°–2.5° either way, the same for a task every time unless [random] varies it. */
fun naturalTilt(id: Long, random: Random? = null): Float {
    val r = random ?: Random(id * 7919 + 17)
    val size = 1.5f + r.nextFloat()
    return if (r.nextBoolean()) size else -size
}

private data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun overlaps(other: Box, gap: Float) =
        left < other.right + gap &&
            other.left < right + gap &&
            top < other.bottom + gap &&
            other.top < bottom + gap
}

private fun box(spot: CardSpot, size: CardSize) =
    Box(
        spot.x - size.width / 2,
        spot.y - size.height / 2,
        spot.x + size.width / 2,
        spot.y + size.height / 2,
    )

/** The golden angle: successive points of the spiral never line up, so it fills evenly. */
private val GoldenAngle = PI * (3 - sqrt(5.0))

/**
 * The first free centre for a card of [size] on a sunflower spiral out from ([x], [y]), keeping
 * [gap] dp from every card in [taken]. The spiral is stretched sideways, like a desk.
 */
private fun spiralSpot(size: CardSize, taken: List<Box>, x: Float, y: Float, gap: Float): Pair<Float, Float> {
    var i = 0
    while (true) {
        val r = 26f * sqrt(i.toFloat())
        val a = i * GoldenAngle
        val cx = x + (r * 1.6f * cos(a)).toFloat()
        val cy = y + (r * sin(a)).toFloat()
        val candidate =
            Box(cx - size.width / 2, cy - size.height / 2, cx + size.width / 2, cy + size.height / 2)
        if (taken.none { it.overlaps(candidate, gap) }) return cx to cy
        i++
    }
}

/**
 * Spots for every task in [tasks]: those in [spots] stay where they are, the rest are laid on the
 * nearest free place around the centre in [wallRank] order, each with its natural tilt.
 */
fun placeMissing(
    tasks: List<Task>,
    spots: Map<Long, CardSpot>,
    today: LocalDate,
    thresholds: WallThresholds = WallThresholds(),
): Map<Long, CardSpot> {
    val result = LinkedHashMap<Long, CardSpot>()
    val taken = ArrayList<Box>()
    tasks.forEach { task ->
        spots[task.id]?.let {
            result[task.id] = it
            taken += box(it, task.cardSize(today))
        }
    }
    tasks
        .filter { it.id !in result }
        .sortedWith(wallRank(today, thresholds))
        .forEach { task ->
            val size = task.cardSize(today)
            val (x, y) = spiralSpot(size, taken, 0f, 0f, 20f)
            val spot = CardSpot(x, y, naturalTilt(task.id))
            result[task.id] = spot
            taken += box(spot, size)
        }
    return result
}

/** Rearranges every card from scratch around the centre, most pressing in the middle. */
fun pullUrgentToCentre(
    tasks: List<Task>,
    today: LocalDate,
    thresholds: WallThresholds = WallThresholds(),
): Map<Long, CardSpot> = placeMissing(tasks, emptyMap(), today, thresholds)

/**
 * A loose, natural spread: the same centre-out order, but with more room, each card nudged off
 * its spot and tilted at random. [seed] makes it repeatable.
 */
fun scatter(
    tasks: List<Task>,
    today: LocalDate,
    thresholds: WallThresholds = WallThresholds(),
    seed: Int = 0,
): Map<Long, CardSpot> {
    val random = Random(seed)
    val result = LinkedHashMap<Long, CardSpot>()
    val taken = ArrayList<Box>()
    tasks.sortedWith(wallRank(today, thresholds)).forEach { task ->
        val size = task.cardSize(today)
        val (x, y) = spiralSpot(size, taken, 0f, 0f, 44f)
        val spot =
            CardSpot(
                x + random.nextFloat() * 24f - 12f,
                y + random.nextFloat() * 24f - 12f,
                naturalTilt(task.id, random),
            )
        result[task.id] = spot
        taken += box(spot, size)
    }
    return result
}

/**
 * One cluster per category, in [categoryOrder], each laid out centre-out by rank; clusters run
 * left to right in rows about [rowWidth] dp wide, centred on the origin.
 */
fun clusterByCategory(
    tasks: List<Task>,
    categoryOrder: List<Long>,
    today: LocalDate,
    thresholds: WallThresholds = WallThresholds(),
    rowWidth: Float = 1800f,
): Map<Long, CardSpot> {
    val gap = 96f
    val groups =
        tasks.groupBy { it.categoryId }.entries.sortedBy { (id, _) ->
            categoryOrder.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it }
        }
    // Each cluster around its own origin, then measured.
    val clusters =
        groups.map { (_, members) ->
            val local = placeMissing(members, emptyMap(), today, thresholds)
            val boxes = members.map { box(local.getValue(it.id), it.cardSize(today)) }
            Triple(local, boxes.minOf { it.left } to boxes.minOf { it.top }, boxes.maxOf { it.right } to boxes.maxOf { it.bottom })
        }
    val result = LinkedHashMap<Long, CardSpot>()
    var cursorX = 0f
    var cursorY = 0f
    var rowHeight = 0f
    clusters.forEach { (local, min, max) ->
        val w = max.first - min.first
        val h = max.second - min.second
        if (cursorX > 0f && cursorX + w > rowWidth) {
            cursorX = 0f
            cursorY += rowHeight + gap
            rowHeight = 0f
        }
        local.forEach { (id, spot) ->
            result[id] = spot.copy(x = spot.x - min.first + cursorX, y = spot.y - min.second + cursorY)
        }
        cursorX += w + gap
        rowHeight = maxOf(rowHeight, h)
    }
    if (result.isEmpty()) return result
    // Centre the whole arrangement on the origin.
    val boxes = tasks.map { box(result.getValue(it.id), it.cardSize(today)) }
    val dx = (boxes.minOf { it.left } + boxes.maxOf { it.right }) / 2
    val dy = (boxes.minOf { it.top } + boxes.maxOf { it.bottom }) / 2
    return result.mapValuesTo(LinkedHashMap()) { (_, s) -> s.copy(x = s.x - dx, y = s.y - dy) }
}
