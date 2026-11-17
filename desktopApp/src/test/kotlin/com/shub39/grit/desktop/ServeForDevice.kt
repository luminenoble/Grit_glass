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

import com.shub39.grit.core.settings.backup.TaskBackup
import com.shub39.grit.core.sync.HostState
import com.shub39.grit.core.tasks.SnapshotTaskRepo
import com.shub39.grit.sync.FileRollbackStore
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Manual check with a phone or emulator: serves the library in `GRIT_LAN_LIBRARY` for
 * `GRIT_LAN_SERVE` seconds, advertising `GRIT_LAN_HOST` (10.0.2.2 for the emulator), and writes
 * the pairing link and then the session's events to `GRIT_LAN_OUT`.
 */
class ServeForDevice {
    @Test
    fun serve() {
        val seconds = System.getenv("GRIT_LAN_SERVE")?.toLongOrNull()
        assumeTrue(seconds != null)
        val out = File(System.getenv("GRIT_LAN_OUT"))
        val repo = SnapshotTaskRepo(TaskBackup.decode(File(System.getenv("GRIT_LAN_LIBRARY")).readText()))
        val sync =
            DesktopLanSync(
                repo,
                FileRollbackStore(File(out.parentFile, "rollback")),
                "WSL-DESK",
                { listOf(System.getenv("GRIT_LAN_HOST")) },
            )
        sync.startHost()
        val running = sync.host.value as HostState.Running
        out.writeText(running.pairing.toUri() + "\n")
        Thread.sleep(seconds!! * 1000)
        val end = sync.host.value
        out.appendText(
            when (end) {
                is HostState.Running -> end.events.joinToString("\n")
                is HostState.Stopped -> "stopped: ${end.reason}"
            } + "\n"
        )
        out.appendText(kotlinx.coroutines.runBlocking { repo.getTasks() }.joinToString("\n") { it.title } + "\n")
        sync.stopHost()
    }
}
