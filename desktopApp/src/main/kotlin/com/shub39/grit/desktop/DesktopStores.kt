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

import com.shub39.grit.core.interfaces.SettingsDatastore
import com.shub39.grit.core.interfaces.ThemeDatastore
import com.shub39.grit.core.settings.ImportanceDisplay
import com.shub39.grit.core.settings.Sections
import com.shub39.grit.core.settings.WallSettings
import com.shub39.grit.core.settings.WallTextSize
import com.shub39.grit.core.settings.WidgetTextSize
import com.shub39.grit.core.settings.backup.TaskBackup
import com.shub39.grit.core.tasks.SnapshotTaskRepo
import com.shub39.grit.core.tasks.TaskRepo
import com.shub39.grit.core.tasks.WallThresholds
import com.shub39.grit.core.theme.Fonts
import com.shub39.grit.sync.writeAtomically
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Opens the library file. An unreadable file is set aside, never overwritten. */
fun openTaskRepo(paths: DesktopPaths): TaskRepo {
    val file = paths.tasks
    val initial =
        if (!file.exists()) null
        else
            try {
                TaskBackup.decode(file.readText())
            } catch (e: Exception) {
                val aside = File(file.parentFile, "tasks.unreadable-${System.currentTimeMillis()}.json")
                file.renameTo(aside)
                System.err.println("Grit Glass: ${file} could not be read (${e.message}); moved to ${aside.name}")
                null
            }
    return SnapshotTaskRepo(initial) { backup ->
        withContext(Dispatchers.IO) { writeAtomically(file, TaskBackup.json.encodeToString(backup)) }
    }
}

@Serializable
data class WindowPrefs(
    val width: Float,
    val height: Float,
    val x: Float? = null,
    val y: Float? = null,
    val maximized: Boolean = false,
)

/** Everything this computer remembers that is not the library. Never synced. */
@Serializable
data class DesktopPrefs(
    val startingPage: String = Sections.Overview.name,
    val is24Hr: Boolean = true,
    val notifications: Boolean = false,
    val lastChangelog: String = "",
    val widgetTextSize: String = WidgetTextSize.MEDIUM.name,
    val wallA: Int = 1,
    val wallB: Int = 7,
    val wallC: Int = 30,
    val wallTextSize: String = WallTextSize.MEDIUM.name,
    val importance: String = ImportanceDisplay.TITLE_AND_BORDER.name,
    val lastOpenedOn: String? = null,
    val font: String = Fonts.FIGTREE.name,
    val reduceTransparency: Boolean = false,
    val window: WindowPrefs? = null,
)

private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

/** [DesktopPrefs] in `settings.json`, written on every change. */
class DesktopPrefsStore(private val file: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    private val state =
        MutableStateFlow(
            runCatching { json.decodeFromString<DesktopPrefs>(file.readText()) }.getOrDefault(DesktopPrefs())
        )
    val prefs: StateFlow<DesktopPrefs> = state

    @Synchronized
    fun update(change: (DesktopPrefs) -> DesktopPrefs) {
        state.update(change)
        runCatching { writeAtomically(file, json.encodeToString(state.value)) }
            .onFailure { System.err.println("Grit Glass: settings not saved (${it.message})") }
    }

    fun <T> flow(read: (DesktopPrefs) -> T): Flow<T> = state.map(read).distinctUntilChanged()
}

class DesktopSettings(private val store: DesktopPrefsStore) : SettingsDatastore {
    override fun getStartingSectionPref() = store.flow { enumOr(it.startingPage, Sections.Overview) }

    override suspend fun setStartingPage(page: Sections) = store.update { it.copy(startingPage = page.name) }

    override fun getIs24Hr() = store.flow { it.is24Hr }

    override suspend fun setIs24Hr(pref: Boolean) = store.update { it.copy(is24Hr = pref) }

    override fun getNotificationsFlow() = store.flow { it.notifications }

    override suspend fun setNotifications(pref: Boolean) = store.update { it.copy(notifications = pref) }

    override fun getBiometricLockPref() = store.flow { false }

    override suspend fun setBiometricPref(pref: Boolean) {}

    override fun getLastChangelogShown() = store.flow { it.lastChangelog }

    override suspend fun updateLastChangelogShown(version: String) =
        store.update { it.copy(lastChangelog = version) }

    override fun getWidgetTextSizeFlow() = store.flow { enumOr(it.widgetTextSize, WidgetTextSize.MEDIUM) }

    override suspend fun setWidgetTextSize(size: WidgetTextSize) =
        store.update { it.copy(widgetTextSize = size.name) }

    override fun getWallSettingsFlow() =
        store.flow {
            WallSettings(
                thresholds =
                    WallThresholds(it.wallA, it.wallB, it.wallC).takeIf { t -> t.isValid } ?: WallThresholds(),
                textSize = enumOr(it.wallTextSize, WallTextSize.MEDIUM),
                importance = enumOr(it.importance, ImportanceDisplay.TITLE_AND_BORDER),
            )
        }

    override suspend fun setWallSettings(settings: WallSettings) =
        store.update {
            it.copy(
                wallA = settings.thresholds.a,
                wallB = settings.thresholds.b,
                wallC = settings.thresholds.c,
                wallTextSize = settings.textSize.name,
                importance = settings.importance.name,
            )
        }

    override suspend fun getLastOpenedOn(): LocalDate? =
        store.prefs.value.lastOpenedOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    override suspend fun setLastOpenedOn(date: LocalDate) =
        store.update { it.copy(lastOpenedOn = date.toString()) }
}

class DesktopTheme(private val store: DesktopPrefsStore) : ThemeDatastore {
    override fun getFontPrefFlow() = store.flow { enumOr(it.font, Fonts.FIGTREE) }

    override suspend fun setFontPref(font: Fonts) = store.update { it.copy(font = font.name) }

    override fun getReduceTransparencyFlow() = store.flow { it.reduceTransparency }

    override suspend fun setReduceTransparency(pref: Boolean) =
        store.update { it.copy(reduceTransparency = pref) }
}
