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
package com.shub39.grit.shared.ui.setting.ui.section

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shub39.grit.shared.ui.app.AppSignals
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shub39.grit.core.now
import com.shub39.grit.core.sync.HostState
import com.shub39.grit.core.sync.LanProtocol
import com.shub39.grit.core.sync.QrMatrix
import com.shub39.grit.core.sync.SnapshotMeta
import com.shub39.grit.shared.ui.components.GlassGroup
import com.shub39.grit.shared.ui.components.glass.GlassAlertDialog
import com.shub39.grit.shared.ui.components.glass.GlassButton
import com.shub39.grit.shared.ui.components.glass.GlassButtonStyle
import com.shub39.grit.shared.ui.components.glass.GlassIconButton
import com.shub39.grit.shared.ui.components.glassListItemColors
import com.shub39.grit.shared.ui.overview.ui.component.label
import com.shub39.grit.shared.ui.theme.GlassTokens
import com.shub39.grit.shared.ui.theme.LocalFloatingBarInset
import com.shub39.grit.shared.ui.theme.flexFontEmphasis
import com.shub39.grit.shared.ui.viewmodel.LanSyncAction
import com.shub39.grit.shared.ui.viewmodel.LanSyncState
import com.shub39.grit.shared.ui.viewmodel.SyncDirection
import grit.shared.ui.generated.resources.Res
import grit.shared.ui.generated.resources.nav_arrow_back
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.vectorResource

/** "Nov 17, 14:03", with the year once it is not this one. */
private fun Instant.short(): String {
    val at = toLocalDateTime(TimeZone.currentSystemDefault())
    return "${at.date.label(LocalDate.now())}, ${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"
}

/**
 * LAN sync between the desktop and the phone. The desktop serves for a while behind a one-time PIN
 * and shows how to reach it; the phone connects, compares both sides and replaces one side's
 * library with the other's. Both list the restore points saved before their data was replaced.
 */
@Composable
fun LanSyncPage(state: LanSyncState, onAction: (LanSyncAction) -> Unit, onNavigateBack: () -> Unit) {
    // A sync link the app was opened with is taken once this page shows.
    val link by AppSignals.pairingRequested.collectAsStateWithLifecycle()
    LaunchedEffect(link) { AppSignals.takePairing()?.let { onAction(LanSyncAction.UsePairing(it)) } }
    // Leaving the page ends a desktop session at once.
    DisposableEffect(state.canHost) {
        onDispose { if (state.canHost) onAction(LanSyncAction.StopHost) }
    }

    state.confirm?.let { direction ->
        val other = state.remote?.device ?: "the desktop"
        GlassAlertDialog(
            onDismissRequest = { onAction(LanSyncAction.CancelSync) },
            title = { Text(if (direction == SyncDirection.Push) "Replace $other's data" else "Replace this device's data") },
            text = {
                Text(
                    when (direction) {
                        SyncDirection.Push ->
                            "Everything on $other is replaced with this device's ${state.local?.categories ?: 0} categories and ${state.local?.tasks ?: 0} tasks. $other keeps a restore point of what it had."
                        SyncDirection.Pull ->
                            "Everything on this device is replaced with $other's ${state.remote?.categories ?: 0} categories and ${state.remote?.tasks ?: 0} tasks. A restore point of what is here now is kept below."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(LanSyncAction.ConfirmSync) }) {
                    Text(if (direction == SyncDirection.Push) "Push" else "Pull", color = GlassTokens.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { onAction(LanSyncAction.CancelSync) }) { Text("Cancel") } },
        )
    }

    state.confirmRestore?.let { point ->
        GlassAlertDialog(
            onDismissRequest = { onAction(LanSyncAction.CancelRestore) },
            title = { Text("Restore this point") },
            text = {
                Text(
                    "Everything on this device is replaced with the ${point.categories} categories and ${point.tasks} tasks saved ${point.savedAt.short()}. What is here now is saved as a new restore point first."
                )
            },
            confirmButton = {
                TextButton(onClick = { onAction(LanSyncAction.ConfirmRestore) }) {
                    Text("Restore", color = GlassTokens.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { onAction(LanSyncAction.CancelRestore) }) { Text("Cancel") } },
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Column(
        Modifier.fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .background(MaterialTheme.colorScheme.background)
    ) {
        MediumFlexibleTopAppBar(
            scrollBehavior = scrollBehavior,
            title = { Text("LAN Sync", fontFamily = flexFontEmphasis()) },
            subtitle = {
                Text(
                    if (state.canHost) "Share with the phone app" else "Sync with the desktop app",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTokens.SecondaryText,
                )
            },
            navigationIcon = {
                GlassIconButton(
                    icon = vectorResource(Res.drawable.nav_arrow_back),
                    contentDescription = "Back",
                    onClick = onNavigateBack,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            },
        )

        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding =
                PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = LocalFloatingBarInset.current + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.canHost) hostSection(state, onAction)
            else if (state.canConnect) connectSection(state, onAction)

            if (state.error != null || state.message != null)
                item {
                    Text(
                        state.error ?: state.message.orEmpty(),
                        Modifier.padding(horizontal = 12.dp),
                        color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }

            item { SectionLabel("Restore points") }
            item {
                Text(
                    "Before sync or a restore replaces this device's data, a copy is kept here. The last 5 stay.",
                    Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTokens.SecondaryText,
                )
            }
            if (state.restorePoints.isEmpty())
                item {
                    Text(
                        "None yet.",
                        Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTokens.SecondaryText,
                    )
                }
            else
                item {
                    GlassGroup {
                        state.restorePoints.forEach { point ->
                            ListItem(
                                headlineContent = { Text(point.savedAt.short()) },
                                supportingContent = {
                                    Text("${point.reason} · ${point.categories} categories, ${point.tasks} tasks")
                                },
                                trailingContent = {
                                    TextButton(
                                        enabled = !state.busy,
                                        onClick = { onAction(LanSyncAction.AskRestore(point)) },
                                    ) {
                                        Text("Restore")
                                    }
                                },
                                colors = glassListItemColors(),
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        Modifier.padding(start = 12.dp, top = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.hostSection(
    state: LanSyncState,
    onAction: (LanSyncAction) -> Unit,
) {
    item {
        Text(
            "Lets the Grit Glass phone app on the same network copy this computer's tasks, or replace them with its own. Nothing leaves your network. The service stops when you leave this page, after ${LanProtocol.IdleMinutes} minutes without use, or after ${LanProtocol.MaxWrongPins} wrong PINs.",
            Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    when (val host = state.host) {
        is HostState.Stopped ->
            item {
                Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    host.reason?.let { Text(it, color = GlassTokens.SecondaryText) }
                    GlassButton(onClick = { onAction(LanSyncAction.StartHost) }, style = GlassButtonStyle.Prominent) {
                        Text("Turn on LAN Sync")
                    }
                }
            }
        is HostState.Running -> {
            item {
                GlassGroup {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        host.qr?.let { QrCode(it, Modifier.size(196.dp)) }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Scan with the phone's camera, or on the phone open Setting → Backup → LAN Sync and enter:",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Field("Address", host.pairing.hosts.joinToString("\n"))
                            Field("Port", host.pairing.port.toString())
                            Field("PIN", host.pairing.pin.chunked(3).joinToString(" "), large = true)
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassButton(onClick = { onAction(LanSyncAction.StopHost) }) { Text("Turn off") }
                }
            }
            if (host.events.isNotEmpty()) {
                item { SectionLabel("This session") }
                items(host.events.reversed()) { event ->
                    Text(event, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    state.local?.let { local ->
        item { SectionLabel("This computer") }
        item { GlassGroup { MetaRows(local, null) } }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.connectSection(
    state: LanSyncState,
    onAction: (LanSyncAction) -> Unit,
) {
    item {
        Text(
            "On the computer, open Setting → Backup → LAN Sync in Grit Glass and turn it on. Scan its QR code with the camera, or type in what it shows. Both must be on the same network.",
            Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    item {
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.address,
                onValueChange = { onAction(LanSyncAction.SetAddress(it)) },
                label = { Text("Address") },
                placeholder = { Text("192.168.1.20") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.port,
                    onValueChange = { onAction(LanSyncAction.SetPort(it)) },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.pin,
                    onValueChange = { onAction(LanSyncAction.SetPin(it)) },
                    label = { Text("PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton(
                    onClick = { onAction(LanSyncAction.Connect) },
                    enabled = !state.busy,
                    style = GlassButtonStyle.Prominent,
                ) {
                    Text(if (state.remote == null) "Connect" else "Compare again")
                }
                if (state.busy) CircularProgressIndicator(Modifier.size(24.dp))
            }
        }
    }
    val remote = state.remote
    if (remote != null) {
        item { SectionLabel("Compare") }
        item {
            GlassGroup {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.weight(1f)) { MetaRows(state.local, remote, title = "This device") }
                    Column(Modifier.weight(1f)) { MetaRows(remote, state.local, title = remote.device) }
                }
            }
        }
        item {
            FlowRow(
                Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassButton(onClick = { onAction(LanSyncAction.Ask(SyncDirection.Push)) }, enabled = !state.busy) {
                    Text("Push to ${remote.device}")
                }
                GlassButton(onClick = { onAction(LanSyncAction.Ask(SyncDirection.Pull)) }, enabled = !state.busy) {
                    Text("Pull from ${remote.device}")
                }
            }
        }
        item {
            Text(
                "Push replaces the computer's data with this device's; Pull does the opposite. Settings and the desk layout stay on each device.",
                Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = GlassTokens.SecondaryText,
            )
        }
    }
}

@Composable
private fun Field(name: String, value: String, large: Boolean = false) {
    Column {
        Text(name, style = MaterialTheme.typography.labelMedium, color = GlassTokens.SecondaryText)
        Text(
            value,
            style =
                if (large)
                    MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                    )
                else MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

/** One side's numbers; with [other], the newer last change is marked. */
@Composable
private fun MetaRows(meta: SnapshotMeta?, other: SnapshotMeta?, title: String? = null) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        if (meta == null) {
            Text("…", color = GlassTokens.SecondaryText)
            return@Column
        }
        val newer =
            other != null && meta.lastChange != null &&
                (other.lastChange == null || meta.lastChange!! > other.lastChange!!)
        Text(
            "Last change: ${meta.lastChange?.short() ?: "none"}" + if (newer) "  · newer" else "",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (newer) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text("${meta.categories} categories", style = MaterialTheme.typography.bodyMedium)
        Text("${meta.tasks} tasks, ${meta.sections} sections", style = MaterialTheme.typography.bodyMedium)
    }
}

/** The pairing link as a QR code, dark modules on white with a quiet zone of 4. */
@Composable
private fun QrCode(matrix: QrMatrix, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White)) {
        val cells = matrix.size + 8
        val cell = size.minDimension / cells
        for (y in 0 until matrix.size) for (x in 0 until matrix.size) {
            if (matrix[x, y])
                drawRect(
                    Color(0xFF1F1D1A),
                    topLeft = Offset((x + 4) * cell, (y + 4) * cell),
                    size = Size(cell + 0.5f, cell + 0.5f),
                )
        }
    }
}
