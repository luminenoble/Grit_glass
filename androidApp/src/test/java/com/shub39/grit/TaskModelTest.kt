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

import com.shub39.grit.core.now
import com.shub39.grit.core.settings.backup.TaskBackup
import com.shub39.grit.core.tasks.*
import kotlin.time.Instant
import kotlinx.datetime.*
import org.junit.Assert.*
import org.junit.Test

class TaskModelTest {
    private val time = Instant.parse("2026-01-01T00:00:00Z")

    private fun backup(
        sections: List<Section> =
            listOf(
                Section(id = 3, title = "工作", isCompleted = true, createdAt = time)
            )
    ) =
        TaskBackup(
            TaskBackup.FORMAT,
            TaskBackup.VERSION,
            time,
            listOf(Category(id = 1, name = "学习", createdAt = time)),
            listOf(
                Task(
                    id = 2,
                    categoryId = 1,
                    title = "项目",
                    titleColor = "#435B64",
                    reminder = LocalDateTime(2027, 1, 1, 8, 0),
                    createdAt = time,
                    sections = sections,
                )
            ),
        )

    @Test
    fun sectionCompletionIsAFlagAndDoesNotCompleteTask() {
        val completed = Section().toggleCompleted()
        assertTrue(completed.isCompleted)
        assertFalse(completed.toggleCompleted().isCompleted)
        assertFalse(Task(categoryId = 1, title = "长期事项", sections = listOf(completed)).isCompleted)
        assertEquals("unname", Section(title = "  ").normalized().title)
    }

    @Test
    fun backupRoundTripPreservesEveryFieldAndIdentity() {
        val plain = backup()
        val original =
            plain.copy(
                tasks =
                    plain.tasks +
                        plain.tasks
                            .single()
                            .copy(
                                id = 9,
                                orderIndex = 1,
                                sections = emptyList(),
                                isCompleted = true,
                                completedAt = time,
                                deadline = LocalDate(2026, 3, 1),
                                priority = Priority.HIGH,
                                hiddenUntil = LocalDate(2026, 2, 1),
                                isSomeday = true,
                            )
            )
        val encoded = TaskBackup.json.encodeToString(original)
        val decoded = TaskBackup.decode(encoded)
        assertTrue(encoded.contains("\"format\": \"grit_glass\""))
        assertTrue(encoded.contains("\"formatVersion\": 2"))
        assertEquals(original.tasks.last(), decoded.tasks.last())
        assertEquals(original.tasks.first().titleColor, decoded.tasks.first().titleColor)
        assertEquals(original.tasks.first().reminder, decoded.tasks.first().reminder)
        assertTrue(decoded.tasks.first().sections.single().isCompleted)
        assertEquals(2L, decoded.tasks.first().sections.single().taskId)
        assertFalse(encoded.contains("taskId"))
        assertEquals(original.categories, decoded.categories)
    }

    @Test
    fun titleColoursAreOneToFiveStops() {
        assertEquals(listOf(0x336699), titleColorStops("#336699"))
        assertEquals(
            listOf(0xFF0000, 0x00FF00, 0x0000FF, 0x123456, 0xABCDEF),
            titleColorStops("#FF0000,#00FF00,#0000FF,#123456,#abcdef"),
        )
        for (bad in listOf("", "#12345", "336699", "#FF0000,", "#FF0000, #00FF00")) {
            assertNull(bad, titleColorStops(bad))
            assertThrows(IllegalArgumentException::class.java) { validateTitleColor(bad) }
        }
        // Six stops are one too many.
        assertNull(titleColorStops(List(6) { "#000000" }.joinToString(",")))
        validateTitleColor(null)
    }

    @Test
    fun sectionColoursRoundTripAndOldBackupsStillImport() {
        val coloured =
            backup(
                listOf(
                    Section(id = 3, title = "工作", createdAt = time, titleColor = "#C0392B,#1E6FD9")
                )
            )
        val text = TaskBackup.json.encodeToString(coloured)
        assertEquals(
            "#C0392B,#1E6FD9",
            TaskBackup.decode(text).tasks.single().sections.single().titleColor,
        )
        // Backups written before Sections had a colour carry no such field.
        val old = text.replace(Regex(",\\s*\"titleColor\": \"#C0392B,#1E6FD9\""), "")
        assertFalse(old.contains("#C0392B"))
        assertNull(TaskBackup.decode(old).tasks.single().sections.single().titleColor)
        assertThrows(IllegalArgumentException::class.java) {
            TaskBackup.decode(text.replace("#C0392B,#1E6FD9", "red"))
        }
    }

    @Test
    fun duplicateSectionsAcrossTasksAndOrphanCategoriesAreRejected() {
        val b = backup()
        assertThrows(IllegalArgumentException::class.java) {
            b.copy(tasks = b.tasks + b.tasks.single().copy(id = 4, orderIndex = 1)).validated()
        }
        assertThrows(IllegalArgumentException::class.java) {
            b.copy(categories = emptyList()).validated()
        }
        assertThrows(IllegalArgumentException::class.java) {
            b.copy(tasks = listOf(b.tasks.single().copy(orderIndex = 5))).validated()
        }
        assertThrows(IllegalArgumentException::class.java) { b.copy(formatVersion = 3).validated() }
        // A completion time and the completed flag go together.
        assertThrows(IllegalArgumentException::class.java) {
            b.copy(tasks = listOf(b.tasks.single().copy(isCompleted = true))).validated()
        }
        assertThrows(IllegalArgumentException::class.java) {
            b.copy(tasks = listOf(b.tasks.single().copy(completedAt = time))).validated()
        }
    }

    @Test
    fun duplicateSectionTitlesAreAllowed() {
        val b =
            backup(
                listOf(
                    Section(id = 3, title = "重复", isCompleted = true, createdAt = time),
                    Section(
                        id = 4,
                        title = "重复",
                        orderIndex = 1,
                        isCompleted = true,
                        createdAt = time,
                    ),
                )
            )
        assertEquals(2, b.validated().tasks.single().sections.size)
    }

    @Test
    fun legacyFormatAndMissingRequiredFieldsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TaskBackup.decode("{\"tasks\":[],\"categories\":[],\"habits\":[]}")
        }
        val text = TaskBackup.json.encodeToString(backup())
        assertThrows(Exception::class.java) {
            TaskBackup.decode(text.replace("\"formatVersion\": 2", "\"formatVersion\": 9"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaskBackup.decode(text.replace(Regex("\"isCompleted\": true,?"), ""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaskBackup.decode(text.replace(Regex("\"lastTouchedAt\": \"[^\"]*\",?"), ""))
        }
    }

    /** A file as Grit Glass 1.0.x and Grhinet write it: `grit_rhine`, version 1. */
    private val legacy =
        """
        {
          "format": "grit_rhine",
          "formatVersion": 1,
          "exportedAt": "2026-01-05T00:00:00Z",
          "categories": [
            {"id": 1, "name": "学习", "description": "", "orderIndex": 0, "titleColor": null,
             "hideCompleted": true, "createdAt": "2026-01-01T00:00:00Z", "updatedAt": "2026-01-01T00:00:00Z"}
          ],
          "tasks": [
            {"id": 2, "categoryId": 1, "title": "开着的", "orderIndex": 0, "isCompleted": false,
             "reminder": null, "description": "", "isToday": true, "titleColor": "#435B64",
             "createdAt": "2026-01-01T00:00:00Z", "updatedAt": "2026-01-03T00:00:00Z",
             "sections": [
               {"id": 3, "title": "做完的", "content": "", "orderIndex": 0, "completedOn": "2026-01-02",
                "createdAt": "2026-01-01T00:00:00Z", "updatedAt": "2026-01-02T00:00:00Z", "titleColor": null},
               {"id": 4, "title": "没做的", "content": "", "orderIndex": 1, "completedOn": null,
                "createdAt": "2026-01-01T00:00:00Z", "updatedAt": "2026-01-01T00:00:00Z"}
             ]},
            {"id": 5, "categoryId": 1, "title": "做完的", "orderIndex": 1, "isCompleted": true,
             "createdAt": "2026-01-01T00:00:00Z", "updatedAt": "2026-01-04T00:00:00Z", "sections": []}
          ]
        }
        """
            .trimIndent()

    @Test
    fun legacyBackupsAreUpgradedLikeTheDatabaseMigration() {
        val decoded = TaskBackup.decode(legacy)
        assertEquals(TaskBackup.FORMAT, decoded.format)
        assertEquals(TaskBackup.VERSION, decoded.formatVersion)
        assertEquals("学习", decoded.categories.single().name)
        val (open, done) = decoded.tasks
        assertEquals(listOf(true, false), open.sections.map { it.isCompleted })
        assertEquals(Priority.NORMAL, open.priority)
        assertEquals(Instant.parse("2026-01-03T00:00:00Z"), open.lastTouchedAt)
        assertNull(open.completedAt)
        assertNull(open.deadline)
        assertTrue(open.isToday)
        assertEquals("#435B64", open.titleColor)
        assertEquals(Instant.parse("2026-01-04T00:00:00Z"), done.completedAt)
        assertEquals(done.updatedAt, done.lastTouchedAt)
        // What was imported exports as the current format and reads back the same.
        assertEquals(decoded, TaskBackup.decode(TaskBackup.json.encodeToString(decoded)))
        // A v1 Section must still say whether it was completed.
        assertThrows(IllegalArgumentException::class.java) {
            TaskBackup.decode(legacy.replace("\"completedOn\": null,", ""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TaskBackup.decode(legacy.replace("\"formatVersion\": 1", "\"formatVersion\": 2"))
        }
    }

    @Test
    fun onlyWorkOnATaskTouchesIt() {
        val task =
            Task(
                id = 1,
                categoryId = 1,
                title = "任务",
                createdAt = time,
                sections = listOf(Section(id = 1, title = "一"), Section(id = 2, title = "二")),
            )
        assertTrue(touches(null, task))
        assertFalse(touches(task, task.copy()))
        // Opening, reordering among tasks, recolouring, completing, shelving: not work.
        for (same in
            listOf(
                task.copy(orderIndex = 3),
                task.copy(titleColor = "#336699"),
                task.copy(isCompleted = true),
                task.copy(hiddenUntil = LocalDate(2027, 1, 1)),
                task.copy(isSomeday = true),
                task.copy(categoryId = 2),
                task.copy(isToday = true).let { it.copy(isToday = false) },
                task.copy(reminder = LocalDateTime(2027, 1, 1, 8, 0)),
            )) assertFalse(same.toString(), touches(task, same))
        for (worked in
            listOf(
                task.copy(title = "改了"),
                task.copy(description = "写了点"),
                task.copy(deadline = LocalDate(2027, 1, 1)),
                task.copy(priority = Priority.HIGH),
                task.copy(isToday = true),
                task.copy(sections = task.sections.map { it.toggleCompleted() }),
                task.copy(sections = task.sections.reversed()),
                task.copy(sections = task.sections + Section(title = "三")),
                task.copy(sections = task.sections.dropLast(1)),
                task.copy(sections = task.sections.map { it.copy(content = "正文") }),
            )) assertTrue(worked.toString(), touches(task, worked))
        // Leaving Today is not work, joining it is.
        assertFalse(touches(task.copy(isToday = true), task))
    }

    @Test
    fun movingAnItemInsertsAndShiftsIntermediateItems() {
        val ids = (1L..8L).toList()
        assertEquals(listOf(1L, 8L, 2L, 3L, 4L, 5L, 6L, 7L), movedIds(ids, 8L, 1))
        assertEquals(listOf(1L, 3L, 4L, 5L, 6L, 7L, 8L, 2L), movedIds(ids, 2L, 7))
        assertEquals(ids, movedIds(ids, 1L, 0))
        assertThrows(IllegalArgumentException::class.java) { movedIds(ids, 8L, 8) }
        assertThrows(IllegalArgumentException::class.java) { validateReorder(ids, listOf(1L, 1L)) }
    }
}
