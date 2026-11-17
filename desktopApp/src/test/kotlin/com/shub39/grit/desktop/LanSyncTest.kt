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
import com.shub39.grit.core.sync.LanPairing
import com.shub39.grit.core.sync.LanProtocol
import com.shub39.grit.core.sync.WallLayoutStore
import com.shub39.grit.core.tasks.CardSpot
import com.shub39.grit.core.tasks.Category
import com.shub39.grit.core.tasks.Section
import com.shub39.grit.core.tasks.SnapshotTaskRepo
import com.shub39.grit.core.tasks.Task
import com.shub39.grit.sync.FileRollbackStore
import com.shub39.grit.sync.LanClient
import com.shub39.grit.sync.LanSyncException
import java.io.File
import java.nio.file.Files
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LanSyncTest {
    private val dir: File = Files.createTempDirectory("grit-lan").toFile()

    @After fun cleanUp() = dir.deleteRecursively().let {}

    private fun library(vararg titles: String): TaskBackup {
        val now = Clock.System.now()
        return TaskBackup(
            TaskBackup.FORMAT,
            TaskBackup.VERSION,
            now,
            listOf(Category(id = 1, name = "Work", createdAt = now)),
            titles.mapIndexed { i, t ->
                Task(
                    id = i + 1L,
                    categoryId = 1,
                    title = t,
                    orderIndex = i,
                    createdAt = now,
                    sections = listOf(Section(id = i + 1L, title = "step", createdAt = now)),
                )
            },
        )
    }

    private fun desktop(vararg titles: String): Pair<SnapshotTaskRepo, DesktopLanSync> {
        val repo = SnapshotTaskRepo(library(*titles))
        return repo to
            DesktopLanSync(repo, FileRollbackStore(File(dir, "rollback")), "Desk", { listOf("127.0.0.1") })
    }

    private fun DesktopLanSync.pairing() = (host.value as HostState.Running).pairing

    @Test
    fun phonePullsAndPushesWithThePin() = runBlocking {
        val (repo, sync) = desktop("Plan the week", "Write report")
        sync.startHost()
        try {
            val pairing = sync.pairing()
            assertEquals(listOf("127.0.0.1"), pairing.hosts)
            assertTrue(pairing.pin.matches(Regex("[0-9]{6}")))
            assertEquals(pairing, LanPairing.parse(pairing.toUri()))
            val phone = LanClient("Pixel")

            val meta = phone.meta(pairing)
            assertEquals("Desk", meta.device)
            assertEquals(2, meta.tasks)
            assertEquals(2, meta.sections)

            val pulled = phone.pull(pairing)
            assertEquals(listOf("Plan the week", "Write report"), pulled.tasks.map { it.title })

            val answer = phone.push(pairing, library("Only this"))
            assertEquals(1, answer.tasks)
            assertEquals(listOf("Only this"), repo.getTasks().map { it.title })
            // The desktop kept what it had before the push.
            val points = FileRollbackStore(File(dir, "rollback")).list()
            assertEquals(1, points.size)
            assertEquals("Before Pixel pushed", points.single().reason)
            assertEquals(2, points.single().tasks)
            val events = (sync.host.value as HostState.Running).events
            assertTrue(events[0].contains("Pixel pulled"))
            assertTrue(events[1].contains("Pixel pushed"))
        } finally {
            sync.stopHost()
        }
    }

    @Test
    fun brokenUploadsChangeNothing() = runBlocking {
        val (repo, sync) = desktop("Keep me")
        sync.startHost()
        try {
            val bad = library("x").let { it.copy(tasks = it.tasks.map { t -> t.copy(categoryId = 9) }) }
            try {
                LanClient("Pixel").push(sync.pairing(), bad)
                fail("A backup with a missing category must be refused")
            } catch (e: LanSyncException) {
                assertNotNull(e.message)
            }
            assertEquals(listOf("Keep me"), repo.getTasks().map { it.title })
            assertTrue(FileRollbackStore(File(dir, "rollback")).list().isEmpty())
        } finally {
            sync.stopHost()
        }
    }

    @Test
    fun wrongPinsTurnTheServiceOff() = runBlocking {
        val (_, sync) = desktop("Secret")
        sync.startHost()
        val good = sync.pairing()
        val wrong = good.copy(pin = if (good.pin == "000000") "111111" else "000000")
        val phone = LanClient("Guess")
        repeat(LanProtocol.MaxWrongPins) { i ->
            try {
                phone.meta(wrong)
                fail("A wrong PIN must be refused")
            } catch (e: LanSyncException) {
                if (i < LanProtocol.MaxWrongPins - 1) assertEquals("Wrong PIN. Check the PIN shown on the desktop", e.message)
            }
        }
        Thread.sleep(300)
        val stopped = sync.host.value
        assertTrue(stopped is HostState.Stopped && stopped.reason!!.contains("wrong PINs"))
        try {
            phone.meta(good)
            fail("The service is off")
        } catch (e: LanSyncException) {
            assertTrue(e.message!!.startsWith("Could not reach"))
        }
    }

    @Test
    fun idleServiceStopsByItself() {
        val repo = SnapshotTaskRepo(library("a"))
        val sync = DesktopLanSync(repo, FileRollbackStore(dir), "Desk", { listOf("127.0.0.1") }, idleMillis = 1)
        sync.startHost()
        Thread.sleep(6500)
        val state = sync.host.value
        assertTrue(state is HostState.Stopped && state.reason!!.contains("without use"))
    }

    @Test
    fun noNetworkMeansNoService() {
        val sync = DesktopLanSync(SnapshotTaskRepo(), FileRollbackStore(dir), "Desk", { emptyList() })
        sync.startHost()
        assertTrue((sync.host.value as HostState.Stopped).reason!!.startsWith("No local network"))
    }

    @Test
    fun pairingLinksParseOnlyWhenComplete() {
        val link = LanPairing(listOf("192.168.1.5", "10.0.0.2"), 51234, "012345")
        assertEquals(link, LanPairing.parse(link.toUri()))
        assertEquals("gritglass://sync?host=192.168.1.5,10.0.0.2&port=51234&pin=012345", link.toUri())
        assertNull(LanPairing.parse("gritglass://sync?host=192.168.1.5&port=51234&pin=12"))
        assertNull(LanPairing.parse("gritglass://sync?host=&port=51234&pin=123456"))
        assertNull(LanPairing.parse("https://example.com/?host=1&port=2&pin=123456"))
        assertNull(LanPairing.parse("gritglass://sync?host=1.2.3.4&port=99999&pin=123456"))
    }

    @Test
    fun restorePointsKeepTheNewestFive() = runBlocking {
        val store = FileRollbackStore(File(dir, "points"))
        repeat(7) { store.save(library(*Array(it + 1) { i -> "t$i" }), "save $it") }
        val points = store.list()
        assertEquals(5, points.size)
        assertEquals(listOf("save 6", "save 5", "save 4", "save 3", "save 2"), points.map { it.reason })
        assertEquals(7, store.load(points.first().id).tasks.size)
        // A copy that no longer passes the import checks is still listed; restoring it says why.
        val odd = library("late").let { it.copy(tasks = it.tasks.map { t -> t.copy(updatedAt = t.createdAt - kotlin.time.Duration.parse("1d")) }) }
        store.save(odd, "odd")
        assertEquals("odd", store.list().first().reason)
        assertEquals(1, store.list().first().tasks)
        try {
            store.load(store.list().first().id)
            fail("An invalid copy cannot be restored")
        } catch (_: IllegalArgumentException) {}
        try {
            store.load("../tasks.json")
            fail("Only restore point files can be read")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun layoutSurvivesARoundTrip() {
        val spots = mapOf(3L to CardSpot(1.5f, -20f, 2f), 10L to CardSpot(0f, 0f, -1.75f))
        assertEquals(spots, WallLayoutStore.decode(WallLayoutStore.encode(spots)))
    }

    @Test
    fun aFailedSaveLeavesTheLibraryAsItWas() = runBlocking {
        var fail = false
        val saved = ArrayList<TaskBackup>()
        val repo =
            SnapshotTaskRepo(library("one")) {
                if (fail) error("disk full")
                saved += it
            }
        val id = repo.upsertTask(Task(categoryId = 1, title = "two"))
        assertEquals(2L, id)
        assertEquals(listOf("one", "two"), saved.last().tasks.map { it.title })
        fail = true
        try {
            repo.upsertTask(Task(categoryId = 1, title = "three"))
            fail("The save failed")
        } catch (_: IllegalStateException) {}
        assertEquals(listOf("one", "two"), repo.getTasks().map { it.title })
        fail = false
        // The id given out by the failed save is not skipped.
        assertEquals(3L, repo.upsertTask(Task(categoryId = 1, title = "three")))
        assertFalse(repo.getTasks().any { it.isCompleted })
        // Completing archives with a time; reopening clears it.
        val done = repo.upsertTask(repo.getTaskById(3)!!.copy(isCompleted = true))
        assertNotNull(repo.getTaskById(done)!!.completedAt)
        repo.upsertTask(repo.getTaskById(3)!!.copy(isCompleted = false))
        assertNull(repo.getTaskById(3)!!.completedAt)
        // What is saved is always a valid backup.
        TaskBackup.decode(TaskBackup.json.encodeToString(saved.last()))
        Unit
    }
}
