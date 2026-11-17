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
package com.shub39.grit.desktop

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.shub39.grit.core.settings.backup.TaskBackup
import com.shub39.grit.core.sync.HostState
import com.shub39.grit.core.sync.LanPairing
import com.shub39.grit.core.sync.LanProtocol
import com.shub39.grit.core.sync.LanSync
import com.shub39.grit.core.sync.QrMatrix
import com.shub39.grit.core.sync.RollbackStore
import com.shub39.grit.core.sync.SnapshotMeta
import com.shub39.grit.core.sync.meta
import com.shub39.grit.core.tasks.TaskRepo
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking

/**
 * The desktop's side of LAN sync: a small HTTP server from the JDK on a random port, answering
 * only to the PIN of this session. It stops when asked, after [LanProtocol.IdleMinutes] minutes
 * without a request, or after [LanProtocol.MaxWrongPins] wrong PINs. Before a phone's push
 * replaces the library, the library is saved to [rollback].
 */
class DesktopLanSync(
    private val repo: TaskRepo,
    private val rollback: RollbackStore,
    override val deviceName: String = defaultDeviceName(),
    private val addresses: () -> List<String> = ::lanAddresses,
    private val idleMillis: Long = LanProtocol.IdleMinutes * 60_000L,
) : LanSync {
    override val canHost = true
    override val canConnect = false

    private val state = MutableStateFlow<HostState>(HostState.Stopped())
    override val host: StateFlow<HostState> = state.asStateFlow()

    private val lock = Any()
    private var server: HttpServer? = null
    private var watchdog: ScheduledExecutorService? = null
    @Volatile private var lastUse = 0L
    @Volatile private var wrongPins = 0
    private val random = SecureRandom()

    override fun startHost() {
        synchronized(lock) {
            if (server != null) return
            val hosts = addresses()
            if (hosts.isEmpty()) {
                state.value =
                    HostState.Stopped("No local network found. Connect this computer to Wi-Fi or Ethernet and try again.")
                return
            }
            val pin = (0 until 6).joinToString("") { random.nextInt(10).toString() }
            val http = HttpServer.create(InetSocketAddress(0), 8)
            http.executor =
                Executors.newSingleThreadExecutor { Thread(it, "grit-lan-sync").apply { isDaemon = true } }
            http.createContext("/") { exchange -> handle(exchange, pin) }
            http.start()
            server = http
            wrongPins = 0
            lastUse = System.currentTimeMillis()
            val pairing = LanPairing(hosts, http.address.port, pin)
            state.value = HostState.Running(pairing, qrOf(pairing.toUri()))
            watchdog =
                Executors.newSingleThreadScheduledExecutor { Thread(it, "grit-lan-idle").apply { isDaemon = true } }
                    .also {
                        it.scheduleAtFixedRate(
                            {
                                if (System.currentTimeMillis() - lastUse > idleMillis)
                                    stop("Turned off after ${idleMillis / 60_000} minutes without use.")
                            },
                            5,
                            5,
                            TimeUnit.SECONDS,
                        )
                    }
        }
    }

    override fun stopHost() = stop(null)

    private fun hostOnly(): Nothing = throw UnsupportedOperationException("The desktop app hosts LAN sync")

    override suspend fun fetchMeta(pairing: LanPairing): SnapshotMeta = hostOnly()

    override suspend fun pull(pairing: LanPairing): TaskBackup = hostOnly()

    override suspend fun push(pairing: LanPairing, backup: TaskBackup): SnapshotMeta = hostOnly()

    private fun stop(reason: String?) {
        val (http, dog) =
            synchronized(lock) {
                val pair = server to watchdog
                server = null
                watchdog = null
                if (pair.first != null || reason != null) state.value = HostState.Stopped(reason)
                pair
            }
        dog?.shutdownNow()
        // Stopping waits for the exchange in progress; never from inside the server's own thread.
        Thread {
                http?.stop(0)
                (http?.executor as? java.util.concurrent.ExecutorService)?.shutdown()
            }
            .apply { isDaemon = true }
            .start()
    }

    private fun event(text: String) {
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        state.update { s -> if (s is HostState.Running) s.copy(events = s.events + "$time  $text") else s }
    }

    private fun HttpExchange.reply(code: Int, body: String, type: String = "text/plain; charset=utf-8") {
        val bytes = body.toByteArray(Charsets.UTF_8)
        responseHeaders.add("Content-Type", type)
        responseHeaders.add("Cache-Control", "no-store")
        sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) responseBody.use { it.write(bytes) }
        close()
    }

    private fun HttpExchange.readBody(): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        requestBody.use { input ->
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                require(out.size() <= LanProtocol.MaxBodyBytes) { "The data is too large" }
            }
        }
        return out.toString(Charsets.UTF_8)
    }

    private fun localOnly(address: InetAddress?): Boolean =
        address != null &&
            (address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress)

    private fun handle(exchange: HttpExchange, pin: String) {
        try {
            if (!localOnly(exchange.remoteAddress?.address)) {
                exchange.reply(403, "Only devices on the local network can sync")
                return
            }
            val given = exchange.requestHeaders.getFirst("Authorization")?.removePrefix("Bearer ")?.trim().orEmpty()
            if (!MessageDigest.isEqual(given.toByteArray(), pin.toByteArray())) {
                wrongPins++
                if (wrongPins >= LanProtocol.MaxWrongPins) {
                    exchange.reply(403, "Too many wrong PINs. LAN Sync was turned off on the desktop")
                    stop("Turned off after ${LanProtocol.MaxWrongPins} wrong PINs. Turn it on again for a new PIN.")
                } else exchange.reply(401, "Wrong PIN")
                return
            }
            lastUse = System.currentTimeMillis()
            val device =
                exchange.requestHeaders.getFirst(LanProtocol.DeviceHeader)?.take(60)?.ifBlank { null } ?: "A phone"
            val json = "application/json; charset=utf-8"
            when (exchange.requestMethod to exchange.requestURI.path) {
                "GET" to LanProtocol.MetaPath -> {
                    val meta = runBlocking { repo.exportSnapshot() }.meta(deviceName)
                    exchange.reply(200, LanProtocol.json.encodeToString(SnapshotMeta.serializer(), meta), json)
                }
                "GET" to LanProtocol.SnapshotPath -> {
                    val backup = runBlocking { repo.exportSnapshot() }
                    exchange.reply(200, TaskBackup.json.encodeToString(backup), json)
                    event("$device pulled ${backup.categories.size} categories and ${backup.tasks.size} tasks")
                }
                "POST" to LanProtocol.SnapshotPath -> {
                    val incoming = TaskBackup.decode(exchange.readBody())
                    val after =
                        runBlocking {
                            rollback.save(repo.exportSnapshot(), "Before $device pushed")
                            repo.replaceData(incoming)
                            repo.exportSnapshot()
                        }
                    exchange.reply(
                        200,
                        LanProtocol.json.encodeToString(SnapshotMeta.serializer(), after.meta(deviceName)),
                        json,
                    )
                    event(
                        "$device pushed ${incoming.categories.size} categories and ${incoming.tasks.size} tasks; the previous data is under Restore points"
                    )
                }
                else -> exchange.reply(404, "Not found")
            }
        } catch (e: IllegalArgumentException) {
            runCatching { exchange.reply(400, e.message ?: "The data is not a valid Grit Glass backup") }
        } catch (e: kotlinx.serialization.SerializationException) {
            runCatching { exchange.reply(400, "The data is not a valid Grit Glass backup") }
        } catch (e: Exception) {
            runCatching { exchange.reply(500, "The desktop could not finish: ${e.message}") }
        }
    }

    companion object {
        fun defaultDeviceName(): String =
            System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
                ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: "Desktop"

        private val virtual = Regex("(?i)vethernet|virtual|vmware|vbox|hyper-v|wsl|docker|loopback|tailscale|zerotier")

        /** Private IPv4 addresses of real adapters that are up, home networks (192.168.x) first. */
        fun lanAddresses(): List<String> =
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .filter {
                    runCatching { it.isUp && !it.isLoopback && !it.isVirtual }.getOrDefault(false) &&
                        !virtual.containsMatchIn(it.name + " " + it.displayName)
                }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
                .map { it.hostAddress }
                .distinct()
                .sortedBy {
                    when {
                        it.startsWith("192.168.") -> 0
                        it.startsWith("10.") -> 1
                        else -> 2
                    }
                }

        fun qrOf(text: String): QrMatrix {
            val bits =
                QRCodeWriter()
                    .encode(
                        text,
                        BarcodeFormat.QR_CODE,
                        0,
                        0,
                        mapOf(
                            EncodeHintType.MARGIN to 0,
                            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                        ),
                    )
            val size = bits.width
            return QrMatrix(size, BooleanArray(size * size) { bits.get(it % size, it / size) })
        }
    }
}
