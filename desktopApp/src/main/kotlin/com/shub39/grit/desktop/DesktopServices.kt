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

import com.shub39.grit.core.app.Changelog
import com.shub39.grit.core.billing.BillingHandler
import com.shub39.grit.core.billing.SubscriptionResult
import com.shub39.grit.core.interfaces.AlarmScheduler
import com.shub39.grit.core.interfaces.BiometricUtils
import com.shub39.grit.core.interfaces.ChangelogManager
import com.shub39.grit.core.settings.backup.ExportRepo
import com.shub39.grit.core.settings.backup.RestorePreview
import com.shub39.grit.core.settings.backup.RestoreRepo
import com.shub39.grit.core.settings.backup.RestoreResult
import com.shub39.grit.core.settings.backup.TaskBackup
import com.shub39.grit.core.tasks.Task
import com.shub39.grit.core.tasks.TaskRepo
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Everything is free here. */
class DesktopBilling : BillingHandler {
    override suspend fun isPlusUser() = true

    override suspend fun userResult() = SubscriptionResult.Subscribed
}

/** The desktop has no reminders; tasks keep their reminder for the phone. */
class NoAlarms : AlarmScheduler {
    override fun schedule(task: Task) {}

    override fun cancel(task: Task) {}

    override fun cancelAll() {}
}

class NoBiometrics : BiometricUtils {
    override fun getAuthenticators() = 0

    override fun authenticationAvailable() = false
}

/** The same changelog the Android app ships, bundled as a resource. */
class ResourceChangelog : ChangelogManager {
    override val changelogs: Flow<Changelog> = flow {
        val text =
            withContext(Dispatchers.IO) {
                ResourceChangelog::class.java.getResourceAsStream("/changelog.json")?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
            }
        emit(text?.let { runCatching { Json.decodeFromString<Changelog>(it) }.getOrNull() } ?: emptyList())
    }
}

class DesktopExport(private val taskRepo: TaskRepo) : ExportRepo {
    override suspend fun exportToJson(): Boolean {
        val snapshot = taskRepo.exportSnapshot()
        val text = withContext(Dispatchers.IO) { TaskBackup.json.encodeToString(snapshot) }
        val file =
            FileKit.openFileSaver(
                suggestedName = "GritGlass-${snapshot.exportedAt.toString().replace(":", "")}",
                defaultExtension = "json",
            ) ?: return false
        withContext(Dispatchers.IO) { file.writeString(text) }
        return true
    }
}

class DesktopRestore(private val taskRepo: TaskRepo) : RestoreRepo {
    override suspend fun prepareRestore(): RestorePreview? {
        val file =
            FileKit.openFilePicker(mode = FileKitMode.Single, type = FileKitType.File("json"))
                ?: return null
        return withContext(Dispatchers.IO) { RestorePreview(TaskBackup.decode(file.readString())) }
    }

    override suspend fun restoreData(backup: TaskBackup): RestoreResult =
        try {
            taskRepo.replaceData(backup)
            RestoreResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RestoreResult.Failure(e.message ?: "Import failed. Your data was not changed")
        }
}
