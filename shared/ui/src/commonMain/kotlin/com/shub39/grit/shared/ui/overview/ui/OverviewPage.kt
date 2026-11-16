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
package com.shub39.grit.shared.ui.overview.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.shub39.grit.shared.ui.navigation.horizontalTransitionMetadata
import com.shub39.grit.shared.ui.task.TaskAction
import com.shub39.grit.shared.ui.task.TaskState
import com.shub39.grit.shared.ui.task.ui.TaskDetailScreen
import com.shub39.grit.shared.ui.task.ui.TaskErrorDialog
import com.shub39.grit.shared.ui.task.ui.component.ArchiveToast
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

@Serializable
private sealed interface OverviewRoutes : NavKey {
    @Serializable data object Cards : OverviewRoutes

    /** Full-screen editor for a task opened from a card. */
    @Serializable data class Detail(val taskId: Long) : OverviewRoutes
}

private val overviewConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(OverviewRoutes.Cards::class, OverviewRoutes.Cards.serializer())
            subclass(OverviewRoutes.Detail::class, OverviewRoutes.Detail.serializer())
        }
    }
}

/** The 总览 tab: the task wall, what is shelved for later, and the archive. */
@Composable
fun OverviewPage(state: TaskState, onAction: (TaskAction) -> Unit) {
    val backStack = rememberNavBackStack(overviewConfiguration, OverviewRoutes.Cards)
    val back: () -> Unit = { if (backStack.size != 1) backStack.removeLastOrNull() }

    Box(Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            entryProvider =
                entryProvider {
                    entry<OverviewRoutes.Cards> {
                        OverviewScreen(
                            state = state,
                            onAction = onAction,
                            onOpenTask = { backStack.add(OverviewRoutes.Detail(it.id)) },
                        )
                    }

                    entry<OverviewRoutes.Detail>(metadata = horizontalTransitionMetadata()) { route
                        ->
                        TaskDetailScreen(state, route.taskId, onAction, onClose = back)
                    }
                },
        )
        ArchiveToast(state, onAction)
    }

    TaskErrorDialog(state, onAction)
}
