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
package com.shub39.grit.core.settings.backup

import com.shub39.grit.core.tasks.*
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class TaskBackup(
    val format: String,
    val formatVersion: Int,
    val exportedAt: Instant,
    val categories: List<Category>,
    val tasks: List<Task>,
) {
    fun validated(): TaskBackup {
        require(format == FORMAT && formatVersion == VERSION) { "Only Grit Glass backup format version $VERSION is supported" }
        require(
            categories.all { it.id > 0 } &&
                categories.map { it.id }.distinct().size == categories.size
        ) { "Category IDs must be positive and unique" }
        require(tasks.all { it.id > 0 } && tasks.map { it.id }.distinct().size == tasks.size) {
            "Task IDs must be positive and unique"
        }
        fun order(indices: List<Int>) =
            require(indices.sorted() == indices.indices.toList()) { "Order within a parent must be contiguous" }
        order(categories.map { it.orderIndex })
        categories.forEach {
            require(it.name.isNotBlank()) { "A category has no name" }
            validateTitleColor(it.titleColor)
            require(it.updatedAt >= it.createdAt) { "Category “${it.name}” was changed before it was created" }
        }
        val categoryIds = categories.map { it.id }.toSet()
        tasks.forEach { task ->
            require(task.categoryId in categoryIds) { "Task “${task.title}” belongs to a missing category" }
            require(task.title.isNotBlank()) { "A task has no title" }
            validateTitleColor(task.titleColor)
            require(task.updatedAt >= task.createdAt) { "Task “${task.title}” was changed before it was created" }
            require(task.isCompleted == (task.completedAt != null)) { "Completion time does not match the completed state" }
            order(task.sections.map { it.orderIndex })
        }
        tasks.groupBy { it.categoryId }.values.forEach { order(it.map { task -> task.orderIndex }) }
        val sections = tasks.flatMap { it.sections }
        require(sections.all { it.id > 0 && it.updatedAt >= it.createdAt }) {
            "Section IDs must be positive, and no Section changed before it was created"
        }
        require(sections.map { it.id }.distinct().size == sections.size) { "Section IDs must be unique" }
        return copy(
            tasks =
                tasks.map { task ->
                    task.copy(
                        sections = task.sections.map { it.normalized().copy(taskId = task.id) }
                    )
                }
        )
    }

    companion object {
        const val FORMAT = "grit_glass"
        const val VERSION = 2

        /** The format shared with Grhinet up to Grit Glass 1.0; still imported, never written. */
        const val LEGACY_FORMAT = "grit_rhine"
        const val LEGACY_VERSION = 1

        val json = Json {
            encodeDefaults = true
            prettyPrint = true
        }

        private fun required(o: JsonObject, vararg keys: String) {
            require(keys.all { it in o }) { "The backup is missing required fields" }
        }

        /**
         * A v1 `grit_rhine` backup rewritten as v2: Sections keep only whether they are done,
         * `hideCompleted` is dropped, and the new Task fields take what the database migration
         * gives them (last touched and completed at the last update, normal priority).
         */
        private fun upgradeLegacy(root: JsonObject): JsonObject {
            fun JsonObject.edit(block: MutableMap<String, JsonElement>.() -> Unit) =
                JsonObject(toMutableMap().apply(block))
            val categories =
                root.getValue("categories").jsonArray.map {
                    it.jsonObject.edit { remove("hideCompleted") }
                }
            val tasks =
                root.getValue("tasks").jsonArray.map { value ->
                    val task = value.jsonObject
                    required(task, "updatedAt", "sections")
                    val updatedAt = task.getValue("updatedAt")
                    val completed = task["isCompleted"]?.jsonPrimitive?.booleanOrNull == true
                    task.edit {
                        put("lastTouchedAt", updatedAt)
                        put("completedAt", if (completed) updatedAt else JsonNull)
                        put(
                            "sections",
                            JsonArray(
                                task.getValue("sections").jsonArray.map { section ->
                                    required(section.jsonObject, "completedOn")
                                    section.jsonObject.edit {
                                        put("isCompleted", JsonPrimitive(remove("completedOn") != JsonNull))
                                    }
                                }
                            ),
                        )
                    }
                }
            return root.edit {
                put("format", JsonPrimitive(FORMAT))
                put("formatVersion", JsonPrimitive(VERSION))
                put("categories", JsonArray(categories))
                put("tasks", JsonArray(tasks))
            }
        }

        fun decode(text: String): TaskBackup {
            var root = json.parseToJsonElement(text).jsonObject
            required(root, "format", "formatVersion", "exportedAt", "categories", "tasks")
            val format = root.getValue("format").jsonPrimitive.contentOrNull
            val version = root.getValue("formatVersion").jsonPrimitive.intOrNull
            if (format == LEGACY_FORMAT && version == LEGACY_VERSION) root = upgradeLegacy(root)
            else
                require(format == FORMAT && version == VERSION) {
                    "Only Grit Glass backups (version $VERSION) and older Grit Glass / Grhinet backups (grit_rhine version 1) are supported"
                }
            root.getValue("categories").jsonArray.forEach {
                required(it.jsonObject, "id", "name", "orderIndex", "createdAt", "updatedAt")
            }
            root.getValue("tasks").jsonArray.forEach { value ->
                val task = value.jsonObject
                required(
                    task,
                    "id",
                    "categoryId",
                    "title",
                    "orderIndex",
                    "createdAt",
                    "updatedAt",
                    "lastTouchedAt",
                    "sections",
                )
                task.getValue("sections").jsonArray.forEach {
                    required(it.jsonObject, "id", "orderIndex", "isCompleted", "createdAt", "updatedAt")
                }
            }
            return json.decodeFromJsonElement<TaskBackup>(root).validated()
        }
    }
}
