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

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.shub39.grit.core.settings.WallSettings
import com.shub39.grit.core.sync.HostState
import com.shub39.grit.core.sync.LanPairing
import com.shub39.grit.core.sync.RollbackPoint
import com.shub39.grit.core.sync.SnapshotMeta
import com.shub39.grit.core.sync.WallLayoutStore
import com.shub39.grit.core.tasks.CardSpot
import com.shub39.grit.core.tasks.Category
import com.shub39.grit.core.tasks.Priority
import com.shub39.grit.core.tasks.Section
import com.shub39.grit.core.tasks.Task
import com.shub39.grit.shared.ui.LocalWindowSizeClass
import com.shub39.grit.shared.ui.overview.ui.OverviewPage
import com.shub39.grit.shared.ui.setting.ui.section.LanSyncPage
import com.shub39.grit.shared.ui.task.TaskState
import com.shub39.grit.shared.ui.theme.GritTheme
import com.shub39.grit.shared.ui.viewmodel.LanSyncState
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.skia.EncodedImageFormat
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Renders the desk, the phone's Due list and LAN Sync to PNG files for a visual check. Runs only
 * when `GRIT_SCREENSHOTS` names an output directory.
 */
class Screenshots {
    private val out = System.getenv("GRIT_SCREENSHOTS")?.let(::File)

    @After fun stop() = stopKoin()

    private val today = LocalDate(2026, 11, 17)
    private val now = Clock.System.now()

    private val categories =
        listOf(
            Category(id = 1, name = "Work", titleColor = "#1E6FD9", orderIndex = 0),
            Category(id = 2, name = "Home", titleColor = "#C0392B,#E67E22", orderIndex = 1),
            Category(id = 3, name = "Study", titleColor = "#66704F", orderIndex = 2),
        )

    private fun task(
        id: Long,
        category: Long,
        title: String,
        due: Int? = null,
        priority: Priority = Priority.NORMAL,
        idle: Int = 0,
        color: String? = null,
        description: String = "",
        today: Boolean = false,
        sections: Int = 0,
    ) =
        Task(
            id = id,
            categoryId = category,
            title = title,
            deadline = due?.let { this.today.plus(it, DateTimeUnit.DAY) },
            priority = priority,
            lastTouchedAt = now - idle.days,
            titleColor = color,
            description = description,
            isToday = today,
            sections = (1..sections).map { Section(id = id * 10 + it, title = "s$it", isCompleted = it == 1) },
        )

    private val tasks =
        listOf(
            task(1, 1, "Ship the quarterly report to finance", due = -2, priority = Priority.HIGH, sections = 4,
                description = "Numbers from the October close; ask Lin about the travel line."),
            task(2, 1, "Prepare slides for Monday", due = 1, priority = Priority.HIGH, today = true),
            task(3, 1, "Reply to the vendor about the renewal terms", due = 5),
            task(4, 2, "Book the dentist", due = 12, priority = Priority.LOW),
            task(5, 2, "Fix the kitchen tap", priority = Priority.NORMAL, idle = 3, color = "#9A3B30"),
            task(6, 3, "Read chapter 4 of the compiler book", due = 40, priority = Priority.LOW),
            task(7, 3, "Finish the Kotlin coroutines course", due = 20, sections = 6, color = "#1E6FD9,#8E44AD"),
            task(8, 2, "Sort out old photos", priority = Priority.LOW, idle = 45),
            task(9, 1, "Plan the team offsite", priority = Priority.HIGH, description = "Venue, dates, budget"),
            task(10, 3, "Write notes on the paper about CRDTs", due = 3, priority = Priority.LOW),
            task(11, 2, "Renew the passport", due = 0),
        )

    private val state =
        TaskState(
            tasks = categories.associateWith { c -> tasks.filter { it.categoryId == c.id } },
            today = today,
            wall = WallSettings(),
        )

    private fun shoot(name: String, width: Int, height: Int, content: @Composable () -> Unit) {
        val dir = out ?: return
        dir.mkdirs()
        val edt = { block: () -> Unit -> javax.swing.SwingUtilities.invokeAndWait(block) }
        lateinit var scene: ImageComposeScene
        edt {
            scene =
                ImageComposeScene(width, height, Density(1.25f)) {
                    CompositionLocalProvider(
                        LocalWindowSizeClass provides
                            WindowSizeClass.calculateFromSize(DpSize((width / 1.25f).dp, (height / 1.25f).dp))
                    ) {
                        GritTheme { content() }
                    }
                }
        }
        var t = 0L
        repeat(40) {
            edt { scene.render(t) }
            t += 50_000_000L
            Thread.sleep(40)
        }
        edt {
            val image = scene.render(t)
            File(dir, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
    }

    private fun koin(spots: Map<Long, CardSpot> = emptyMap()) {
        startKoin {
            modules(
                module {
                    single<WallLayoutStore> {
                        object : WallLayoutStore {
                            var saved = spots

                            override suspend fun load() = saved

                            override suspend fun save(spots: Map<Long, CardSpot>) {
                                saved = spots
                            }
                        }
                    }
                }
            )
        }
    }

    @Test
    fun desk() {
        assumeTrue(out != null)
        koin()
        shoot("desk", 1700, 1060) { OverviewPage(state) {} }
    }

    @Test
    fun phoneDueList() {
        assumeTrue(out != null)
        koin()
        shoot("phone-due", 450, 960) { OverviewPage(state) {} }
    }

    @Test
    fun lanSyncDesktop() {
        assumeTrue(out != null)
        koin()
        val pairing = LanPairing(listOf("192.168.1.23"), 51734, "482913")
        val meta = SnapshotMeta("DESKTOP-01", now, now - 1.days, 3, 11, 10)
        shoot("lan-desktop", 1300, 1000) {
            LanSyncPage(
                LanSyncState(
                    canHost = true,
                    deviceName = "DESKTOP-01",
                    host =
                        HostState.Running(
                            pairing,
                            DesktopLanSync.qrOf(pairing.toUri()),
                            listOf("14:02  Pixel 9 pulled 3 categories and 11 tasks"),
                        ),
                    local = meta,
                    restorePoints = listOf(RollbackPoint("1.json", now - 2.days, "Before Pixel 9 pushed", 3, 9)),
                ),
                {},
                {},
            )
        }
    }

    @Test
    fun lanSyncPhone() {
        assumeTrue(out != null)
        koin()
        shoot("lan-phone", 450, 960) {
            LanSyncPage(
                LanSyncState(
                    canConnect = true,
                    deviceName = "Pixel 9",
                    address = "192.168.1.23",
                    port = "51734",
                    pin = "482913",
                    local = SnapshotMeta("Pixel 9", now, now - 3.days, 3, 9, 8),
                    remote = SnapshotMeta("DESKTOP-01", now, now - 1.days, 3, 11, 10),
                ),
                {},
                {},
            )
        }
    }
}
