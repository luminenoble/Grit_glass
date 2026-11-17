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
package com.shub39.grit.web_demo.stubs

import com.shub39.grit.core.interfaces.*
import com.shub39.grit.core.settings.*
import com.shub39.grit.core.settings.backup.TaskBackup
import com.shub39.grit.core.sync.HostState
import com.shub39.grit.core.sync.LanPairing
import com.shub39.grit.core.sync.LanSync
import com.shub39.grit.core.sync.RollbackPoint
import com.shub39.grit.core.sync.RollbackStore
import com.shub39.grit.core.sync.SnapshotMeta
import com.shub39.grit.core.sync.WallLayoutStore
import com.shub39.grit.core.tasks.*
import com.shub39.grit.core.theme.Fonts
import kotlin.time.Clock
import kotlinx.coroutines.flow.*
import kotlinx.datetime.LocalDate
import org.koin.core.annotation.Single

@Single(binds = [ThemeDatastore::class])
class ThemeDatastoreStub : ThemeDatastore {
    private val font = MutableStateFlow(Fonts.FIGTREE)

    override fun getFontPrefFlow() = font.asStateFlow()

    override suspend fun setFontPref(font: Fonts) {
        this.font.value = font
    }

    private val reduceTransparency = MutableStateFlow(false)

    override fun getReduceTransparencyFlow() = reduceTransparency.asStateFlow()

    override suspend fun setReduceTransparency(pref: Boolean) {
        reduceTransparency.value = pref
    }
}

@Single(binds = [SettingsDatastore::class])
class SettingsDatastoreStub : SettingsDatastore {
    private val page = MutableStateFlow(Sections.Tasks)
    private val hours = MutableStateFlow(false)
    private val notifications = MutableStateFlow(false)
    private val biometric = MutableStateFlow(false)
    private val wall = MutableStateFlow(WallSettings())
    private var lastOpenedOn: LocalDate? = null
    private val changelog = MutableStateFlow("")
    private val widget = MutableStateFlow(WidgetTextSize.MEDIUM)

    override fun getStartingSectionPref() = page.asStateFlow()

    override suspend fun setStartingPage(page: Sections) {
        this.page.value = page
    }

    override fun getIs24Hr() = hours.asStateFlow()

    override suspend fun setIs24Hr(pref: Boolean) {
        hours.value = pref
    }

    override fun getNotificationsFlow() = notifications.asStateFlow()

    override suspend fun setNotifications(pref: Boolean) {
        notifications.value = pref
    }

    override fun getBiometricLockPref() = biometric.asStateFlow()

    override suspend fun setBiometricPref(pref: Boolean) {
        biometric.value = pref
    }

    override fun getLastChangelogShown() = changelog.asStateFlow()

    override suspend fun updateLastChangelogShown(version: String) {
        changelog.value = version
    }

    override fun getWidgetTextSizeFlow() = widget.asStateFlow()

    override suspend fun setWidgetTextSize(size: WidgetTextSize) {
        widget.value = size
    }

    override fun getWallSettingsFlow() = wall.asStateFlow()

    override suspend fun setWallSettings(settings: WallSettings) {
        wall.value = settings
    }

    override suspend fun getLastOpenedOn() = lastOpenedOn

    override suspend fun setLastOpenedOn(date: LocalDate) {
        lastOpenedOn = date
    }
}

@Single(binds = [TaskRepo::class])
class TaskRepoStub :
    TaskRepo by SnapshotTaskRepo(
        TaskBackup(
            TaskBackup.FORMAT,
            TaskBackup.VERSION,
            Clock.System.now(),
            listOf(Category(id = 1, name = "Work")),
            listOf(Task(id = 1, categoryId = 1, title = "Welcome to Grit Glass")),
        )
    )

/** The demo neither hosts nor joins LAN sync. */
@Single(binds = [LanSync::class])
class LanSyncStub : LanSync {
    override val canHost = false
    override val canConnect = false
    override val deviceName = "Demo"
    override val host: StateFlow<HostState> = MutableStateFlow(HostState.Stopped())

    override fun startHost() {}

    override fun stopHost() {}

    override suspend fun fetchMeta(pairing: LanPairing): SnapshotMeta = error("Not in the demo")

    override suspend fun pull(pairing: LanPairing): TaskBackup = error("Not in the demo")

    override suspend fun push(pairing: LanPairing, backup: TaskBackup): SnapshotMeta =
        error("Not in the demo")
}

@Single(binds = [RollbackStore::class])
class RollbackStoreStub : RollbackStore {
    override suspend fun save(backup: TaskBackup, reason: String) {}

    override suspend fun list(): List<RollbackPoint> = emptyList()

    override suspend fun load(id: String): TaskBackup = error("No restore points in the demo")
}

@Single(binds = [WallLayoutStore::class])
class WallLayoutStoreStub : WallLayoutStore {
    private var spots: Map<Long, CardSpot> = emptyMap()

    override suspend fun load() = spots

    override suspend fun save(spots: Map<Long, CardSpot>) {
        this.spots = spots
    }
}
