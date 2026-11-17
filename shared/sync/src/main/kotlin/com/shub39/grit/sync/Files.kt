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
package com.shub39.grit.sync

import com.shub39.grit.core.settings.backup.TaskBackup
import com.shub39.grit.core.sync.RollbackPoint
import com.shub39.grit.core.sync.RollbackStore
import com.shub39.grit.core.sync.WallLayoutStore
import com.shub39.grit.core.tasks.CardSpot
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Writes [text] beside [file] first and moves it into place, so a crash never leaves half a file. */
fun writeAtomically(file: File, text: String) {
    file.absoluteFile.parentFile?.mkdirs()
    val temp = File(file.absoluteFile.parentFile, "${file.name}.tmp")
    temp.writeText(text, Charsets.UTF_8)
    try {
        Files.move(
            temp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

@Serializable private class RollbackFile(val reason: String, val backup: JsonElement)

/**
 * Restore points as JSON files in [dir], named by the time they were saved. Each holds the reason
 * and a full v2 backup; only the newest [RollbackStore.Keep] are kept.
 */
class FileRollbackStore(private val dir: File, private val clock: Clock = Clock.System) :
    RollbackStore {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val name = Regex("([0-9]{13})\\.json")

    private fun files(): List<File> =
        dir.listFiles { f -> name.matches(f.name) }.orEmpty().sortedByDescending { it.name }

    override suspend fun save(backup: TaskBackup, reason: String) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                var millis = clock.now().toEpochMilliseconds()
                // Two saves in one millisecond must not overwrite each other.
                while (File(dir, "$millis.json").exists()) millis++
                val text =
                    json.encodeToString(
                        RollbackFile(reason, TaskBackup.json.encodeToJsonElement(TaskBackup.serializer(), backup))
                    )
                writeAtomically(File(dir, "$millis.json"), text)
                files().drop(RollbackStore.Keep).forEach { it.delete() }
            }
        }

    override suspend fun list(): List<RollbackPoint> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                // Listed without validating, so a point never disappears; a restore validates.
                files().mapNotNull { file ->
                    runCatching {
                        val saved = json.decodeFromString<RollbackFile>(file.readText())
                        val backup = saved.backup.jsonObject
                        RollbackPoint(
                            id = file.name,
                            savedAt =
                                Instant.fromEpochMilliseconds(file.name.removeSuffix(".json").toLong()),
                            reason = saved.reason,
                            categories = backup["categories"]?.jsonArray?.size ?: 0,
                            tasks = backup["tasks"]?.jsonArray?.size ?: 0,
                        )
                    }.getOrNull()
                }
            }
        }

    override suspend fun load(id: String): TaskBackup =
        withContext(Dispatchers.IO) {
            require(name.matches(id)) { "Unknown restore point" }
            mutex.withLock {
                val saved = json.decodeFromString<RollbackFile>(File(dir, id).readText())
                TaskBackup.decode(saved.backup.toString())
            }
        }
}

/** Card spots in one JSON file; a missing or unreadable file is an empty desk. */
class FileWallLayoutStore(private val file: File) : WallLayoutStore {
    private val mutex = Mutex()

    override suspend fun load(): Map<Long, CardSpot> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!file.exists()) emptyMap()
                else runCatching { WallLayoutStore.decode(file.readText()) }.getOrDefault(emptyMap())
            }
        }

    override suspend fun save(spots: Map<Long, CardSpot>) =
        withContext(Dispatchers.IO) {
            mutex.withLock { writeAtomically(file, WallLayoutStore.encode(spots)) }
        }
}
