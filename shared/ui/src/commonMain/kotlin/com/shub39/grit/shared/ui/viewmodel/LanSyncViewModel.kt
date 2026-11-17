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
package com.shub39.grit.shared.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shub39.grit.core.settings.backup.TaskBackup
import com.shub39.grit.core.sync.HostState
import com.shub39.grit.core.sync.LanPairing
import com.shub39.grit.core.sync.LanSync
import com.shub39.grit.core.sync.RollbackPoint
import com.shub39.grit.core.sync.RollbackStore
import com.shub39.grit.core.sync.SnapshotMeta
import com.shub39.grit.core.sync.meta
import com.shub39.grit.core.tasks.TaskRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided

/** Which way a sync goes: the phone's library replaces the desktop's, or the other way round. */
enum class SyncDirection {
    Push,
    Pull,
}

@Immutable
data class LanSyncState(
    val canHost: Boolean = false,
    val canConnect: Boolean = false,
    val deviceName: String = "",
    val host: HostState = HostState.Stopped(),
    // Connecting to a desktop.
    val address: String = "",
    val port: String = "",
    val pin: String = "",
    val local: SnapshotMeta? = null,
    val remote: SnapshotMeta? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val confirm: SyncDirection? = null,
    // Restore points on this device.
    val restorePoints: List<RollbackPoint> = emptyList(),
    val confirmRestore: RollbackPoint? = null,
) {
    val pairing: LanPairing
        get() =
            LanPairing(
                hosts = address.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() },
                port = port.toIntOrNull() ?: 0,
                pin = pin.trim(),
            )
}

sealed interface LanSyncAction {
    data class SetAddress(val text: String) : LanSyncAction

    data class SetPort(val text: String) : LanSyncAction

    data class SetPin(val text: String) : LanSyncAction

    data object Connect : LanSyncAction

    data class Ask(val direction: SyncDirection) : LanSyncAction

    data object ConfirmSync : LanSyncAction

    data object CancelSync : LanSyncAction

    data object StartHost : LanSyncAction

    data object StopHost : LanSyncAction

    data class AskRestore(val point: RollbackPoint) : LanSyncAction

    data object ConfirmRestore : LanSyncAction

    data object CancelRestore : LanSyncAction

    data object DismissMessage : LanSyncAction

    /** Details from a `gritglass://sync` link: filled in and connected at once. */
    data class UsePairing(val pairing: LanPairing) : LanSyncAction
}

/**
 * LAN sync and the restore points it leaves. Before anything replaces this device's library, the
 * library is saved as a restore point; restoring one saves the current library first as well, so
 * every step can be taken back.
 */
@KoinViewModel
class LanSyncViewModel(
    @Provided private val repo: TaskRepo,
    @Provided private val lanSync: LanSync,
    @Provided private val rollback: RollbackStore,
) : ViewModel() {
    private val mutable =
        MutableStateFlow(
            LanSyncState(
                canHost = lanSync.canHost,
                canConnect = lanSync.canConnect,
                deviceName = lanSync.deviceName,
            )
        )
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch { refreshRestorePoints() }
        viewModelScope.launch {
            // Keeps "this device" current, including after a phone replaces the desktop's data.
            repo.getTasksFlow().collectLatest { refreshLocal() }
        }
        viewModelScope.launch {
            lanSync.host.collect { host -> mutable.update { it.copy(host = host) } }
        }
        viewModelScope.launch {
            // A push from a phone leaves a restore point behind on the desktop.
            lanSync.host.map { (it as? HostState.Running)?.events?.size ?: 0 }.distinctUntilChanged().collect {
                refreshRestorePoints()
            }
        }
    }

    private fun usePairing(link: LanPairing) {
        mutable.update {
            it.copy(
                address = link.hosts.joinToString(", "),
                port = link.port.toString(),
                pin = link.pin,
                remote = null,
                error = null,
            )
        }
        onAction(LanSyncAction.Connect)
    }

    private suspend fun refreshLocal() {
        runCatching { repo.exportSnapshot().meta(lanSync.deviceName) }
            .onSuccess { meta -> mutable.update { it.copy(local = meta) } }
    }

    private suspend fun refreshRestorePoints() {
        runCatching { rollback.list() }
            .onSuccess { points -> mutable.update { it.copy(restorePoints = points) } }
    }

    private fun work(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(error = e.message ?: "Sync failed. Nothing was changed") }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    /** Saves what this device holds now, then puts [incoming] in its place. */
    private suspend fun replaceWith(incoming: TaskBackup, reason: String) {
        rollback.save(repo.exportSnapshot(), reason)
        repo.replaceData(incoming)
        refreshRestorePoints()
        refreshLocal()
    }

    fun onAction(action: LanSyncAction) {
        when (action) {
            is LanSyncAction.SetAddress -> mutable.update { it.copy(address = action.text, remote = null) }
            is LanSyncAction.SetPort ->
                mutable.update { it.copy(port = action.text.filter(Char::isDigit).take(5), remote = null) }
            is LanSyncAction.SetPin ->
                mutable.update { it.copy(pin = action.text.filter(Char::isDigit).take(6), remote = null) }
            LanSyncAction.Connect -> {
                val pairing = mutable.value.pairing
                if (!pairing.isValid) {
                    mutable.update {
                        it.copy(error = "Enter the address, port and 6-digit PIN shown on the desktop")
                    }
                    return
                }
                work {
                    val remote = lanSync.fetchMeta(pairing)
                    refreshLocal()
                    mutable.update { it.copy(remote = remote) }
                }
            }
            is LanSyncAction.Ask -> mutable.update { it.copy(confirm = action.direction) }
            LanSyncAction.CancelSync -> mutable.update { it.copy(confirm = null) }
            LanSyncAction.ConfirmSync -> {
                val direction = mutable.value.confirm ?: return
                val pairing = mutable.value.pairing
                mutable.update { it.copy(confirm = null) }
                work {
                    when (direction) {
                        SyncDirection.Pull -> {
                            val incoming = lanSync.pull(pairing)
                            val from = mutable.value.remote?.device ?: "the desktop"
                            replaceWith(incoming, "Before pulling from $from")
                            mutable.update {
                                it.copy(
                                    remote = incoming.meta(from),
                                    message =
                                        "This device now holds $from's ${incoming.categories.size} categories and ${incoming.tasks.size} tasks. The previous data is under Restore points.",
                                )
                            }
                        }
                        SyncDirection.Push -> {
                            val outgoing = repo.exportSnapshot()
                            val remote = lanSync.push(pairing, outgoing)
                            mutable.update {
                                it.copy(
                                    remote = remote,
                                    message =
                                        "${remote.device} now holds this device's ${remote.categories} categories and ${remote.tasks} tasks. Its previous data is under its Restore points.",
                                )
                            }
                        }
                    }
                }
            }
            LanSyncAction.StartHost -> {
                mutable.update { it.copy(error = null) }
                runCatching { lanSync.startHost() }
                    .onFailure { e -> mutable.update { it.copy(error = e.message ?: "Could not start") } }
            }
            LanSyncAction.StopHost -> lanSync.stopHost()
            is LanSyncAction.AskRestore -> mutable.update { it.copy(confirmRestore = action.point) }
            LanSyncAction.CancelRestore -> mutable.update { it.copy(confirmRestore = null) }
            LanSyncAction.ConfirmRestore -> {
                val point = mutable.value.confirmRestore ?: return
                mutable.update { it.copy(confirmRestore = null) }
                work {
                    val saved =
                        try {
                            rollback.load(point.id)
                        } catch (e: IllegalArgumentException) {
                            throw IllegalStateException(
                                "This restore point does not pass the import checks, so it was not restored (${e.message})"
                            )
                        }
                    replaceWith(saved, "Before restoring a restore point")
                    mutable.update {
                        it.copy(message = "Restored ${saved.categories.size} categories and ${saved.tasks.size} tasks.")
                    }
                }
            }
            LanSyncAction.DismissMessage -> mutable.update { it.copy(message = null, error = null) }
            is LanSyncAction.UsePairing -> usePairing(action.pairing)
        }
    }

    override fun onCleared() {
        // Leaving the page ends the session; the desktop never serves in the background.
        if (lanSync.canHost) lanSync.stopHost()
    }
}
