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
package com.shub39.grit.shared.ui.app

import com.shub39.grit.core.sync.LanPairing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Requests from outside the composition (a keyboard shortcut, a link the app was opened with)
 * that a page picks up when it is shown. Each stays pending until the page that handles it takes
 * it.
 */
object AppSignals {
    private val newTask = MutableStateFlow(false)
    private val pairing = MutableStateFlow<LanPairing?>(null)

    /** Ctrl+N on the desktop: open a new task on the Task page. */
    val newTaskRequested: StateFlow<Boolean> = newTask.asStateFlow()

    /** A `gritglass://sync` link: open LAN sync with these details filled in. */
    val pairingRequested: StateFlow<LanPairing?> = pairing.asStateFlow()

    fun requestNewTask() {
        newTask.value = true
    }

    fun takeNewTask(): Boolean = newTask.value.also { newTask.value = false }

    fun requestPairing(link: LanPairing) {
        pairing.value = link
    }

    fun takePairing(): LanPairing? = pairing.value.also { pairing.value = null }
}
