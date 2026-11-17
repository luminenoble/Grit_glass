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

import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shub39.grit.core.sync.LanSync
import com.shub39.grit.shared.ui.LocalWindowSizeClass
import com.shub39.grit.shared.ui.app.AppSignals
import com.shub39.grit.shared.ui.app.MainApp
import com.shub39.grit.shared.ui.components.ChangelogSheet
import com.shub39.grit.shared.ui.task.ui.component.DesktopBack
import com.shub39.grit.shared.ui.theme.GritTheme
import com.shub39.grit.shared.ui.update.AppVersionHolder
import com.shub39.grit.shared.ui.viewmodel.MainViewModel
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import org.jetbrains.skia.Image
import org.koin.core.context.GlobalContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.plugin.module.dsl.startKoin

private fun appVersion(): String =
    object {}.javaClass.getResourceAsStream("/grit-version.txt")?.use { it.readBytes().toString(Charsets.UTF_8).trim() }
        ?: "dev"

private fun appIcon(): BitmapPainter? =
    object {}.javaClass.getResourceAsStream("/icon.png")?.use {
        BitmapPainter(Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap())
    }

@OptIn(FlowPreview::class)
fun main() {
    AppVersionHolder.versionName = appVersion()
    FileKit.init(appId = "GritGlass")
    startKoin<DesktopModule>()
    val koin = GlobalContext.get()
    val prefs = koin.get<DesktopPrefsStore>()
    val lanSync = koin.get<LanSync>()
    val icon = appIcon()

    application {
        val saved = prefs.prefs.value.window
        val windowState =
            rememberWindowState(
                placement = if (saved?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
                position =
                    if (saved?.x != null && saved.y != null) WindowPosition(saved.x.dp, saved.y.dp)
                    else WindowPosition(Alignment.Center),
                size = saved?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(1360.dp, 860.dp),
            )
        // Remembers the window's size and place for the next start.
        LaunchedEffect(windowState) {
            snapshotFlow { Triple(windowState.size, windowState.position, windowState.placement) }
                .debounce(500)
                .collect { (size, position, placement) ->
                    val maximized = placement == WindowPlacement.Maximized
                    prefs.update { p ->
                        val floating = p.window
                        p.copy(
                            window =
                                if (maximized)
                                    (floating ?: WindowPrefs(size.width.value, size.height.value)).copy(maximized = true)
                                else
                                    WindowPrefs(
                                        width = size.width.value,
                                        height = size.height.value,
                                        x = position.takeIf { it.isSpecified }?.x?.value,
                                        y = position.takeIf { it.isSpecified }?.y?.value,
                                        maximized = false,
                                    )
                        )
                    }
                }
        }

        Window(
            onCloseRequest = {
                lanSync.stopHost()
                exitApplication()
            },
            state = windowState,
            title = "Grit Glass",
            icon = icon,
            onPreviewKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown) false
                else
                    when {
                        event.key == Key.Escape -> DesktopBack.dispatch()
                        event.isCtrlPressed && event.key == Key.N -> {
                            AppSignals.requestNewTask()
                            true
                        }
                        else -> false
                    }
            },
        ) {
            window.minimumSize = java.awt.Dimension(760, 560)
            val windowSizeClass = calculateWindowSizeClass()
            val viewModel = koinViewModel<MainViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()

            CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                GritTheme(state.theme) {
                    state.currentChangelog?.let {
                        ChangelogSheet(currentLog = it, onDismissRequest = viewModel::dismissChangelog)
                    }
                    MainApp(state = state, onNavigateToPaywall = {})
                }
            }
        }
    }
}
