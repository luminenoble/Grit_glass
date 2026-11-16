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
        require(format == FORMAT && formatVersion == VERSION) { "只支持 Grit Glass 备份格式版本 $VERSION" }
        require(
            categories.all { it.id > 0 } &&
                categories.map { it.id }.distinct().size == categories.size
        )
        require(tasks.all { it.id > 0 } && tasks.map { it.id }.distinct().size == tasks.size)
        fun order(indices: List<Int>) =
            require(indices.sorted() == indices.indices.toList()) { "同级排序必须连续" }
        order(categories.map { it.orderIndex })
        categories.forEach {
            require(it.name.isNotBlank())
            validateTitleColor(it.titleColor)
            require(it.updatedAt >= it.createdAt)
        }
        val categoryIds = categories.map { it.id }.toSet()
        tasks.forEach { task ->
            require(task.categoryId in categoryIds && task.title.isNotBlank())
            validateTitleColor(task.titleColor)
            require(task.updatedAt >= task.createdAt)
            require(task.isCompleted == (task.completedAt != null)) { "完成时间与完成状态不一致" }
            order(task.sections.map { it.orderIndex })
        }
        tasks.groupBy { it.categoryId }.values.forEach { order(it.map { task -> task.orderIndex }) }
        val sections = tasks.flatMap { it.sections }
        require(sections.all { it.id > 0 && it.updatedAt >= it.createdAt })
        require(sections.map { it.id }.distinct().size == sections.size)
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
            require(keys.all { it in o }) { "备份缺少必要字段" }
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
                    "只支持 Grit Glass 备份（版本 $VERSION），以及 Grit Glass / Grhinet 的旧版备份（grit_rhine 版本 1）"
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
