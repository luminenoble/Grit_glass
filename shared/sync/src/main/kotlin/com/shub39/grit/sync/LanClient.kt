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
import com.shub39.grit.core.sync.HostState
import com.shub39.grit.core.sync.LanPairing
import com.shub39.grit.core.sync.LanProtocol
import com.shub39.grit.core.sync.LanSync
import com.shub39.grit.core.sync.SnapshotMeta
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** A sync failure with a message meant for the person syncing. */
class LanSyncException(message: String) : IOException(message)

/**
 * The phone's side of LAN sync: plain HTTP to the desktop, trying each of its addresses in turn.
 * The PIN goes in the `Authorization` header.
 */
class LanClient(
    private val deviceName: String,
    private val connectTimeoutMs: Int = 4000,
    private val readTimeoutMs: Int = 60_000,
) {
    private fun hostPart(host: String) = if (':' in host && !host.startsWith("[")) "[$host]" else host

    private fun request(pairing: LanPairing, method: String, path: String, body: String? = null): String {
        require(pairing.isValid) { "Enter the address, port and 6-digit PIN shown on the desktop" }
        var unreachable: Exception? = null
        for (host in pairing.hosts) {
            val url = URI("http://${hostPart(host)}:${pairing.port}$path").toURL()
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = connectTimeoutMs
                connection.readTimeout = readTimeoutMs
                connection.useCaches = false
                connection.setRequestProperty("Authorization", "Bearer ${pairing.pin}")
                connection.setRequestProperty(LanProtocol.DeviceHeader, deviceName)
                connection.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                if (code in 200..299) return text
                throw LanSyncException(
                    when (code) {
                        401 -> "Wrong PIN. Check the PIN shown on the desktop"
                        403 -> text.ifBlank { "The desktop refused the request" }
                        else -> text.ifBlank { "The desktop answered $code" }
                    }
                )
            } catch (e: LanSyncException) {
                throw e
            } catch (e: ConnectException) {
                unreachable = e
            } catch (e: SocketTimeoutException) {
                unreachable = e
            } catch (e: NoRouteToHostException) {
                unreachable = e
            } catch (e: UnknownHostException) {
                unreachable = e
            } finally {
                connection.disconnect()
            }
        }
        throw LanSyncException(
            "Could not reach the desktop at ${pairing.hosts.joinToString(", ")}:${pairing.port}. " +
                "Check that both are on the same Wi-Fi, that LAN Sync is still on there, and that the firewall allows it" +
                (unreachable?.message?.let { " ($it)" } ?: "")
        )
    }

    suspend fun meta(pairing: LanPairing): SnapshotMeta =
        withContext(Dispatchers.IO) {
            LanProtocol.json.decodeFromString(request(pairing, "GET", LanProtocol.MetaPath))
        }

    suspend fun pull(pairing: LanPairing): TaskBackup =
        withContext(Dispatchers.IO) {
            TaskBackup.decode(request(pairing, "GET", LanProtocol.SnapshotPath))
        }

    suspend fun push(pairing: LanPairing, backup: TaskBackup): SnapshotMeta =
        withContext(Dispatchers.IO) {
            LanProtocol.json.decodeFromString(
                request(pairing, "POST", LanProtocol.SnapshotPath, TaskBackup.json.encodeToString(backup))
            )
        }
}

/** [LanSync] for a device that only connects to a desktop. */
class ClientLanSync(override val deviceName: String) : LanSync {
    private val client = LanClient(deviceName)
    override val canHost = false
    override val canConnect = true
    override val host: StateFlow<HostState> = MutableStateFlow(HostState.Stopped()).asStateFlow()

    override fun startHost() = throw UnsupportedOperationException("Only the desktop app can host LAN sync")

    override fun stopHost() {}

    override suspend fun fetchMeta(pairing: LanPairing) = client.meta(pairing)

    override suspend fun pull(pairing: LanPairing) = client.pull(pairing)

    override suspend fun push(pairing: LanPairing, backup: TaskBackup) = client.push(pairing, backup)
}
