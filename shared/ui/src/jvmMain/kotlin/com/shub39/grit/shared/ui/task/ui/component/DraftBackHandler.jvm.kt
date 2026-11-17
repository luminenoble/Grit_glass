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
package com.shub39.grit.shared.ui.task.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState

/**
 * Esc on the desktop: the window hands the key to [DesktopBack.dispatch], which goes to the most
 * recently shown handler that is enabled, the way the system back gesture does on Android.
 */
object DesktopBack {
    internal class Entry(var enabled: Boolean, var onBack: () -> Unit)

    private val entries = ArrayList<Entry>()

    internal fun add(entry: Entry) {
        entries += entry
    }

    internal fun remove(entry: Entry) {
        entries -= entry
    }

    /** Runs the newest enabled handler; false if there was none. */
    fun dispatch(): Boolean {
        val entry = entries.lastOrNull { it.enabled } ?: return false
        entry.onBack()
        return true
    }
}

@Composable
actual fun DraftBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val current = rememberUpdatedState(onBack)
    val entry = androidx.compose.runtime.remember { DesktopBack.Entry(enabled) { current.value() } }
    entry.enabled = enabled
    DisposableEffect(entry) {
        DesktopBack.add(entry)
        onDispose { DesktopBack.remove(entry) }
    }
}
