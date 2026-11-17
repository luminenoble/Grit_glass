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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shub39.grit.core.settings.WallSettings
import com.shub39.grit.core.tasks.Category
import com.shub39.grit.core.tasks.Task
import com.shub39.grit.core.tasks.Urgency
import com.shub39.grit.core.tasks.dueOrder
import com.shub39.grit.core.tasks.isOverdue
import com.shub39.grit.core.tasks.urgency
import com.shub39.grit.shared.ui.LocalWindowSizeClass
import com.shub39.grit.shared.ui.components.DayPickerDialog
import com.shub39.grit.shared.ui.components.glass.GlassAlertDialog
import com.shub39.grit.shared.ui.components.glass.GlassButton
import com.shub39.grit.shared.ui.components.glass.GlassButtonStyle
import com.shub39.grit.shared.ui.components.glass.GlassSegmented
import com.shub39.grit.shared.ui.overview.ui.component.CardTap
import com.shub39.grit.shared.ui.overview.ui.component.CardTapKeys
import com.shub39.grit.shared.ui.overview.ui.component.Desk
import com.shub39.grit.shared.ui.overview.ui.component.DeskToolbar
import com.shub39.grit.shared.ui.overview.ui.component.PaperChip
import com.shub39.grit.shared.ui.overview.ui.component.PaperTones
import com.shub39.grit.shared.ui.overview.ui.component.TaskLens
import com.shub39.grit.shared.ui.overview.ui.component.TaskTile
import com.shub39.grit.shared.ui.overview.ui.component.WallMetrics
import com.shub39.grit.shared.ui.overview.ui.component.completedDay
import com.shub39.grit.shared.ui.overview.ui.component.days
import com.shub39.grit.shared.ui.overview.ui.component.deadlineLabel
import com.shub39.grit.shared.ui.overview.ui.component.label
import com.shub39.grit.shared.ui.overview.ui.component.rememberDeskState
import com.shub39.grit.shared.ui.task.TaskAction
import com.shub39.grit.shared.ui.task.TaskState
import com.shub39.grit.shared.ui.task.ui.component.CategoryToggleButton
import com.shub39.grit.shared.ui.theme.GlassMetrics
import com.shub39.grit.shared.ui.theme.GlassShapes
import com.shub39.grit.shared.ui.theme.GlassTier
import com.shub39.grit.shared.ui.theme.GlassTokens
import com.shub39.grit.shared.ui.theme.LocalFloatingBarInset
import com.shub39.grit.shared.ui.theme.flexFontEmphasis
import com.shub39.grit.shared.ui.theme.glassSource
import com.shub39.grit.shared.ui.theme.glassSurface
import com.shub39.grit.shared.ui.theme.rememberGlassBackdrop
import com.shub39.grit.shared.ui.theme.titleBrush
import grit.shared.ui.generated.resources.Res
import grit.shared.ui.generated.resources.overview_tab
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource

/**
 * The places an unfinished or finished task can be, in the order it moves through them. A wide
 * window shows the open tasks as a desk ([Wall]); a phone lists the dated ones by deadline ([Due]).
 */
private enum class OverviewView {
    Wall,
    Due,
    Later,
    Done,
}

/** The card a preview is open on, and where that card sits in the page. */
private data class Peek(val taskId: Long, val anchor: Rect)

/** Which date is being picked for a task, from a card or from its preview. */
private sealed interface DayRequest {
    val task: Task

    data class Deadline(override val task: Task) : DayRequest

    data class Suspend(override val task: Task) : DayRequest
}

@Composable
fun OverviewScreen(
    state: TaskState,
    onAction: (TaskAction) -> Unit,
    onOpenTask: (Task) -> Unit,
) {
    var view by rememberSaveable { mutableStateOf(OverviewView.Wall) }
    var tap by rememberSaveable { mutableStateOf(CardTap.Preview) }
    var onlyCategory by rememberSaveable { mutableStateOf<Long?>(null) }
    var peek by remember { mutableStateOf<Peek?>(null) }
    var dayRequest by remember { mutableStateOf<DayRequest?>(null) }
    var abandoning by remember { mutableStateOf<Task?>(null) }
    var clearingArchive by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val backdrop = rememberGlassBackdrop()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val widthClass = LocalWindowSizeClass.current.widthSizeClass
    val wide = widthClass == WindowWidthSizeClass.Expanded
    // The free desk needs room and a pointer; a phone gets the deadline list instead.
    val deskMode = widthClass != WindowWidthSizeClass.Compact
    val views =
        if (deskMode) listOf(OverviewView.Wall, OverviewView.Later, OverviewView.Done)
        else listOf(OverviewView.Due, OverviewView.Later, OverviewView.Done)
    val shownView = if (view in views) view else views.first()
    val barInset = LocalFloatingBarInset.current
    val today = state.today

    val categories = remember(state.tasks) { state.tasks.keys.toList() }
    val categoryOf = remember(categories) { categories.associateBy { it.id } }
    val filter = onlyCategory?.takeIf { it in categoryOf }
    fun List<Task>.shown() = if (filter == null) this else filter { it.categoryId == filter }
    val active = remember(state.tasks, today) { state.allTasks.filter { it.isActive(today) } }
    val wallTasks = remember(active, filter) { active.shown() }
    val due = remember(wallTasks, today) { dueOrder(wallTasks, today) }
    // Spots are only pruned once the library has loaded, never against the empty first state.
    val keep =
        remember(state.tasks) {
            state.allTasks.takeIf { it.isNotEmpty() }?.filter { !it.isCompleted }?.map { it.id }?.toSet()
        }
    val desk = if (deskMode) rememberDeskState() else null
    val someday = remember(state.tasks, filter) { state.somedayTasks.shown() }
    val suspended = remember(state.tasks, today, filter) { state.suspendedTasks.shown() }
    val done = remember(state.tasks, filter) { state.completedTasks.shown() }

    val update: (Task) -> Unit = { onAction(TaskAction.UpsertTask(it)) }
    val preview: (Task, LayoutCoordinates) -> Unit = { task, card ->
        host?.let { peek = Peek(task.id, it.localBoundingBoxOf(card, clipBounds = false)) }
    }
    // A tap does what the keys say; a long press does the other.
    val onTap: (Task, LayoutCoordinates) -> Unit = { task, card ->
        if (tap == CardTap.Preview) preview(task, card) else onOpenTask(task)
    }
    val onHold: (Task, LayoutCoordinates) -> Unit = { task, card ->
        if (tap == CardTap.Preview) onOpenTask(task) else preview(task, card)
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { host = it }) {
        Column(
            Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                .fillMaxSize()
                // The paper is part of what the glass samples: recorded without it, the blurred
                // cards would be translucent and the sharp wall would show through the preview.
                .glassSource(backdrop)
                .background(MaterialTheme.colorScheme.background)
        ) {
            OverviewTopBar(
                scrollBehavior = scrollBehavior,
                views = views,
                view = shownView,
                onView = { view = it },
                summary =
                    when (shownView) {
                        OverviewView.Wall -> "${wallTasks.size} on the desk"
                        OverviewView.Due -> "${due.size} with a deadline"
                        OverviewView.Later ->
                            "${someday.size} later, ${suspended.size} suspended"
                        OverviewView.Done -> "${done.size} completed"
                    },
            )

            if (categories.isNotEmpty())
                CategoryFilter(categories, selected = filter, onSelect = { onlyCategory = it })

            val motion = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = shownView,
                transitionSpec = {
                    fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
                },
                modifier = Modifier.fillMaxSize(),
            ) { shown ->
                when (shown) {
                    OverviewView.Wall ->
                        if (desk != null)
                            Desk(
                                state = desk,
                                shown = wallTasks,
                                active = active,
                                keep = keep,
                                categoryOf = categoryOf,
                                today = today,
                                settings = state.wall,
                                onTap = onTap,
                                onHold = onHold,
                                onEdit = onOpenTask,
                                overdueChoices = { task ->
                                    PaperChip("New date") { dayRequest = DayRequest.Deadline(task) }
                                    PaperChip("No date") { update(task.copy(deadline = null)) }
                                    PaperChip("Give up", ink = GlassTokens.Danger) { abandoning = task }
                                },
                            )
                    OverviewView.Due -> {
                        val t = state.wall.thresholds
                        fun within(level: Urgency) =
                            due.filter { !it.isOverdue(today) && it.urgency(today, t) == level }
                        TileList(
                            settings = state.wall,
                            categoryOf = categoryOf,
                            onTap = onTap,
                            onHold = onHold,
                            empty =
                                "No deadlines. Tasks with a deadline are listed here, soonest first; on a wide screen every task lies on the desk.",
                            groups =
                                listOf(
                                    TileGroup("Passed", due.filter { it.isOverdue(today) }, tinted = true) {
                                        it.deadline?.label(today)
                                    },
                                    TileGroup("Within ${days(t.a)}", within(Urgency.DUE), tinted = true) {
                                        it.deadlineLabel(today)
                                    },
                                    TileGroup("Within ${days(t.b)}", within(Urgency.SOON), tinted = true) {
                                        it.deadlineLabel(today)
                                    },
                                    TileGroup("Within ${days(t.c)}", within(Urgency.UPCOMING), tinted = true) {
                                        it.deadlineLabel(today)
                                    },
                                    TileGroup("Later dates", within(Urgency.AHEAD), tinted = true) {
                                        it.deadline?.label(today)
                                    },
                                ),
                            today = today,
                        )
                    }
                    OverviewView.Later ->
                        TileList(
                            settings = state.wall,
                            categoryOf = categoryOf,
                            onTap = onTap,
                            onHold = onHold,
                            empty =
                                "Nothing here. Tasks you are not doing for now can go under Later or be suspended until a day; they leave the Task page and the wall and stay here.",
                            groups =
                                listOf(
                                    TileGroup("Later", someday) { null },
                                    TileGroup("Suspended", suspended) {
                                        it.hiddenUntil?.label(today)
                                    },
                                ),
                            today = today,
                        )
                    OverviewView.Done ->
                        TileList(
                            settings = state.wall,
                            categoryOf = categoryOf,
                            onTap = onTap,
                            onHold = onHold,
                            empty = "No completed tasks yet. Tasks are archived here when you complete them.",
                            groups =
                                listOf(TileGroup(null, done) { it.completedDay()?.label(today) }),
                            today = today,
                            header =
                                if (state.completedTasks.isEmpty()) null
                                else {
                                    {
                                        GlassButton(
                                            onClick = { clearingArchive = true },
                                            style = GlassButtonStyle.Danger,
                                            enabled = !state.saving,
                                        ) {
                                            Text("Delete completed")
                                        }
                                    }
                                },
                        )
                }
            }
        }

        if (desk != null && shownView == OverviewView.Wall)
            DeskToolbar(
                state = desk,
                active = active,
                categoryOrder = categories.map { it.id },
                today = today,
                settings = state.wall,
                backdrop = backdrop,
                modifier =
                    Modifier.align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 16.dp + barInset)
                        .then(if (wide) Modifier.navigationBarsPadding() else Modifier),
            )

        CardTapKeys(
            selected = tap,
            onSelect = { tap = it },
            backdrop = backdrop,
            modifier =
                Modifier.align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp + barInset)
                    .then(if (wide) Modifier.navigationBarsPadding() else Modifier),
        )

        val peeked = peek?.let { open -> state.allTasks.firstOrNull { it.id == open.taskId } }
        val open = peek
        if (open != null && peeked != null)
            key(open.taskId) {
                TaskLens(
                    task = peeked,
                    category = categoryOf[peeked.categoryId],
                    today = today,
                    anchor = open.anchor,
                    backdrop = backdrop,
                    bottomInset = barInset,
                    onUpdate = update,
                    onEdit = { onOpenTask(peeked) },
                    onPickDeadline = { dayRequest = DayRequest.Deadline(peeked) },
                    onPickSuspendDay = { dayRequest = DayRequest.Suspend(peeked) },
                    onAbandon = { abandoning = peeked },
                    onDismiss = { peek = null },
                )
            }
    }

    dayRequest?.let { request ->
        val task = request.task
        when (request) {
            is DayRequest.Deadline ->
                DayPickerDialog(
                    initial = task.deadline?.takeIf { it >= today },
                    notBefore = today,
                    onPick = {
                        update(task.copy(deadline = it))
                        dayRequest = null
                    },
                    onDismiss = { dayRequest = null },
                )
            is DayRequest.Suspend ->
                DayPickerDialog(
                    initial = null,
                    notBefore = today.plus(1, DateTimeUnit.DAY),
                    onPick = {
                        update(task.copy(hiddenUntil = it))
                        dayRequest = null
                        peek = null
                    },
                    onDismiss = { dayRequest = null },
                )
        }
    }

    abandoning?.let { task ->
        GlassAlertDialog(
            onDismissRequest = { abandoning = null },
            title = { Text("Give up this task") },
            text = { Text("“${task.title}” will be deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    enabled = !state.saving,
                    onClick = {
                        onAction(TaskAction.DeleteTask(task))
                        abandoning = null
                        peek = null
                    },
                ) {
                    Text("Give up", color = GlassTokens.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { abandoning = null }) { Text("Keep") } },
        )
    }

    if (clearingArchive)
        GlassAlertDialog(
            onDismissRequest = { clearingArchive = false },
            title = { Text("Delete completed") },
            text = { Text("All ${state.completedTasks.size} completed tasks will be deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(TaskAction.DeleteTasks)
                        clearingArchive = false
                    }
                ) {
                    Text("Delete", color = GlassTokens.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { clearingArchive = false }) { Text("Cancel") } },
        )
}

@Composable
private fun OverviewTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    views: List<OverviewView>,
    view: OverviewView,
    onView: (OverviewView) -> Unit,
    summary: String,
) {
    Box(Modifier.fillMaxWidth()) {
        // Glass fades in as the cards scroll under the bar; at rest the bar is clear.
        Box(
            Modifier.matchParentSize()
                .graphicsLayer { alpha = scrollBehavior.state.overlappedFraction }
                .glassSurface(GlassTier.Regular, RectangleShape, shadow = false)
        )
        LargeFlexibleTopAppBar(
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            scrollBehavior = scrollBehavior,
            title = {
                Text(stringResource(Res.string.overview_tab), fontFamily = flexFontEmphasis())
            },
            subtitle = {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTokens.SecondaryText,
                )
            },
            actions = {
                GlassSegmented(
                    options = views,
                    selected = view,
                    onSelect = onView,
                    label = {
                        when (it) {
                            OverviewView.Wall -> "Wall"
                            OverviewView.Due -> "Due"
                            OverviewView.Later -> "Later"
                            OverviewView.Done -> "Done"
                        }
                    },
                    modifier = Modifier.padding(end = 12.dp),
                )
            },
        )
    }
}

/** Narrows every view to one category; the same glass tray as on the Task page. */
@Composable
private fun CategoryFilter(categories: List<Category>, selected: Long?, onSelect: (Long?) -> Unit) {
    LazyRow(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .glassSurface(GlassTier.Thin, GlassShapes.Capsule)
                .clip(GlassShapes.Capsule),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(GlassMetrics.TrayInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "all") {
            CategoryToggleButton(checked = selected == null, onCheckedChange = { onSelect(null) }) {
                Text("All", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        items(categories, key = { it.id }) { category ->
            CategoryToggleButton(
                checked = selected == category.id,
                onCheckedChange = { onSelect(category.id) },
            ) {
                if (category.titleColor != null) {
                    Box(
                        Modifier.size(10.dp)
                            .clip(CircleShape)
                            .background(titleBrush(category.titleColor))
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    category.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Cards dissolve into the paper as they slide up under the category tray. */
private fun Modifier.fadingTop(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black,
                    endY = 12.dp.toPx(),
                ),
                blendMode = BlendMode.DstIn,
            )
        }

/** Room under a list for the navigation pill and the keys floating above it. */
@Composable
private fun listPadding(): PaddingValues =
    PaddingValues(
        top = 14.dp,
        bottom = LocalFloatingBarInset.current + GlassMetrics.Primary + 24.dp,
    )

/** Width of the tile grid and of one cell: 4 columns, or 8 cells of the phone's size on wide screens. */
private data class WallGrid(val columns: Int, val width: Dp, val cell: Dp)

private fun wallGrid(available: Dp): WallGrid {
    val eight = WallMetrics.Column * 8 + WallMetrics.Gap * 7
    return if (available >= eight + WallMetrics.Margin * 2)
        WallGrid(8, eight, WallMetrics.Column)
    else {
        val width = available - WallMetrics.Margin * 2
        WallGrid(4, width, (width - WallMetrics.Gap * 3) / 4)
    }
}

/** A titled run of tiles; [tinted] tiles take the paper of how near their deadline is. */
private data class TileGroup(
    val title: String?,
    val tasks: List<Task>,
    val tinted: Boolean = false,
    val meta: (Task) -> String?,
)

/** Dated, shelved or archived tasks: equal tiles, two cells wide, in the order of their [groups]. */
@Composable
private fun TileList(
    groups: List<TileGroup>,
    today: LocalDate,
    settings: WallSettings,
    categoryOf: Map<Long, Category>,
    onTap: (Task, LayoutCoordinates) -> Unit,
    onHold: (Task, LayoutCoordinates) -> Unit,
    empty: String,
    header: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val grid = wallGrid(maxWidth)
        val perRow = grid.columns / 2
        LazyColumn(
            modifier = Modifier.fillMaxSize().fadingTop(),
            contentPadding = listPadding(),
            verticalArrangement = Arrangement.spacedBy(WallMetrics.Gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (groups.all { it.tasks.isEmpty() }) item { EmptyNote(empty) }
            if (header != null)
                item(key = "header") {
                    Box(Modifier.width(grid.width), contentAlignment = Alignment.CenterEnd) {
                        header()
                    }
                }
            groups
                .filter { it.tasks.isNotEmpty() }
                .forEach { group ->
                    if (group.title != null)
                        item(key = "label-${group.title}") {
                            WallLabel(group.title, Modifier.width(grid.width))
                        }
                    tileRows(group, perRow, grid, today, settings, categoryOf, onTap, onHold)
                }
        }
    }
}

private fun LazyListScope.tileRows(
    group: TileGroup,
    perRow: Int,
    grid: WallGrid,
    today: LocalDate,
    settings: WallSettings,
    categoryOf: Map<Long, Category>,
    onTap: (Task, LayoutCoordinates) -> Unit,
    onHold: (Task, LayoutCoordinates) -> Unit,
) {
    items(group.tasks.chunked(perRow), key = { it.first().id }) { row ->
        Row(
            Modifier.width(grid.width),
            horizontalArrangement = Arrangement.spacedBy(WallMetrics.Gap),
        ) {
            row.forEach { task ->
                key(task.id) {
                    TaskTile(
                        task = task,
                        category = categoryOf[task.categoryId],
                        settings = settings,
                        onClick = { onTap(task, it) },
                        onLongClick = { onHold(task, it) },
                        modifier =
                            Modifier.size(grid.cell * 2 + WallMetrics.Gap, WallMetrics.Row),
                        meta = group.meta(task),
                        paper =
                            if (group.tinted) PaperTones.of(task.urgency(today, settings.thresholds))
                            else null,
                        overdue = group.tinted && task.isOverdue(today),
                    )
                }
            }
        }
    }
}

/** A full-width label row on the wall, such as "No deadline": a name and a hairline. */
@Composable
private fun WallLabel(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.height(WallMetrics.Label),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
            color = GlassTokens.SecondaryText,
        )
        Box(
            Modifier.weight(1f)
                .height(1.dp)
                .background(GlassTokens.Shadow.copy(alpha = 0.16f))
        )
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        Modifier.widthIn(max = 300.dp).padding(top = 96.dp, start = 16.dp, end = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = GlassTokens.SecondaryText,
        textAlign = TextAlign.Center,
    )
}
