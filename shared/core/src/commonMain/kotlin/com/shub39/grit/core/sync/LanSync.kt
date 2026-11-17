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
package com.shub39.grit.core.sync

import com.shub39.grit.core.settings.backup.TaskBackup
import com.shub39.grit.core.tasks.CardSpot
import kotlin.time.Instant
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * LAN sync: the desktop app serves its library over HTTP inside the local network for a short
 * while, guarded by a one-time PIN; the phone connects and either pulls the desktop's library or
 * pushes its own. Either way one side's whole library replaces the other's, as a backup import
 * does, after the side being replaced saves a restore point.
 *
 *   GET  /snapshot/meta   SnapshotMeta of the desktop
 *   GET  /snapshot        TaskBackup v2 JSON
 *   POST /snapshot        TaskBackup v2 JSON; the desktop replaces its data and answers its new meta
 *
 * Every request carries `Authorization: Bearer <PIN>` and the caller's name in [DeviceHeader].
 */
object LanProtocol {
    const val Scheme = "gritglass"
    const val Host = "sync"
    const val MetaPath = "/snapshot/meta"
    const val SnapshotPath = "/snapshot"
    const val DeviceHeader = "X-Grit-Device"
    /** Wrong PINs after which the desktop stops serving and a new PIN is needed. */
    const val MaxWrongPins = 5
    /** Minutes without a request after which the desktop stops serving. */
    const val IdleMinutes = 10
    /** Largest body the desktop accepts. */
    const val MaxBodyBytes = 32 * 1024 * 1024

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}

/** What one side holds, compared before choosing a direction. */
@Serializable
data class SnapshotMeta(
    val device: String,
    val exportedAt: Instant,
    /** Latest change to any category, task or Section; null for an empty library. */
    val lastChange: Instant?,
    val categories: Int,
    val tasks: Int,
    val sections: Int,
)

/** Latest edit in the library. Deletions leave no trace, so this is the newest surviving edit. */
fun TaskBackup.lastChange(): Instant? =
    (categories.map { it.updatedAt } +
            tasks.flatMap { t -> listOf(t.updatedAt) + t.sections.map { it.updatedAt } })
        .maxOrNull()

fun TaskBackup.meta(device: String) =
    SnapshotMeta(
        device = device,
        exportedAt = exportedAt,
        lastChange = lastChange(),
        categories = categories.size,
        tasks = tasks.size,
        sections = tasks.sumOf { it.sections.size },
    )

/**
 * How the phone reaches the desktop: every LAN address the desktop has (tried in order), its port
 * and the PIN. Shown as a QR code holding [toUri], or typed in by hand.
 */
data class LanPairing(val hosts: List<String>, val port: Int, val pin: String) {
    val isValid: Boolean
        get() = hosts.isNotEmpty() && hosts.all { it.isNotBlank() } && port in 1..65535 && pin.matches(PinPattern)

    fun toUri(): String =
        "${LanProtocol.Scheme}://${LanProtocol.Host}?host=${hosts.joinToString(",")}&port=$port&pin=$pin"

    companion object {
        val PinPattern = Regex("[0-9]{6}")

        /** Reads a `gritglass://sync?host=…&port=…&pin=…` link; null if it is not one. */
        fun parse(uri: String): LanPairing? {
            val prefix = "${LanProtocol.Scheme}://${LanProtocol.Host}?"
            if (!uri.trim().startsWith(prefix, ignoreCase = true)) return null
            val query =
                uri.trim().substring(prefix.length).split('&').mapNotNull {
                    val parts = it.split('=', limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }.toMap()
            val pairing =
                LanPairing(
                    hosts = query["host"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                    port = query["port"]?.toIntOrNull() ?: return null,
                    pin = query["pin"].orEmpty(),
                )
            return pairing.takeIf { it.isValid }
        }
    }
}

/** A QR code as rows of dark (true) and light modules. */
class QrMatrix(val size: Int, private val dark: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]
}

sealed interface HostState {
    /** Not serving; [reason] says why it stopped, if it was not asked to. */
    data class Stopped(val reason: String? = null) : HostState

    data class Running(
        val pairing: LanPairing,
        val qr: QrMatrix?,
        /** What phones have done since the service started, newest last. */
        val events: List<String> = emptyList(),
    ) : HostState
}

/**
 * The platform's side of LAN sync. The desktop can [startHost]; the phone can connect with
 * [fetchMeta], [pull] and [push]. A platform that can do neither reports both flags false.
 */
interface LanSync {
    val canHost: Boolean
    val canConnect: Boolean
    /** This device's name, shown to the other side. */
    val deviceName: String

    val host: StateFlow<HostState>

    fun startHost()

    fun stopHost()

    suspend fun fetchMeta(pairing: LanPairing): SnapshotMeta

    suspend fun pull(pairing: LanPairing): TaskBackup

    /** Sends [backup]; the desktop answers with what it holds afterwards. */
    suspend fun push(pairing: LanPairing, backup: TaskBackup): SnapshotMeta
}

/** A copy of the library saved just before something replaced it. */
data class RollbackPoint(
    val id: String,
    val savedAt: Instant,
    val reason: String,
    val categories: Int,
    val tasks: Int,
)

/** Restore points kept on this device, newest first; only the last [Keep] are kept. */
interface RollbackStore {
    suspend fun save(backup: TaskBackup, reason: String)

    suspend fun list(): List<RollbackPoint>

    suspend fun load(id: String): TaskBackup

    companion object {
        const val Keep = 5
    }
}

/** Where the cards lie on this device's desk; local only, never synced or backed up. */
interface WallLayoutStore {
    suspend fun load(): Map<Long, CardSpot>

    suspend fun save(spots: Map<Long, CardSpot>)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(spots: Map<Long, CardSpot>): String =
            json.encodeToString(spots.mapKeys { it.key.toString() })

        fun decode(text: String): Map<Long, CardSpot> =
            json.decodeFromString<Map<String, CardSpot>>(text).mapNotNull { (k, v) ->
                k.toLongOrNull()?.let { it to v }
            }.toMap()
    }
}
