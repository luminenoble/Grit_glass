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
package com.shub39.grit.shared.ui.task.ui.section

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.toShape
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shub39.grit.core.tasks.Category
import com.shub39.grit.core.tasks.Task
import com.shub39.grit.shared.ui.LocalWindowSizeClass
import com.shub39.grit.shared.ui.components.Empty
import com.shub39.grit.shared.ui.components.GritDialog
import com.shub39.grit.shared.ui.components.PageFill
import com.shub39.grit.shared.ui.components.detachedItemShape
import com.shub39.grit.shared.ui.components.endItemShape
import com.shub39.grit.shared.ui.components.leadingItemShape
import com.shub39.grit.shared.ui.components.middleItemShape
import com.shub39.grit.shared.ui.task.TaskAction
import com.shub39.grit.shared.ui.task.TaskState
import com.shub39.grit.shared.ui.task.ui.component.CategoryToggleButton
import com.shub39.grit.shared.ui.task.ui.component.TaskCard
import com.shub39.grit.shared.ui.components.glass.GlassButton
import com.shub39.grit.shared.ui.components.glass.GlassFab
import com.shub39.grit.shared.ui.components.glass.GlassIconButton
import com.shub39.grit.shared.ui.theme.GlassMetrics
import com.shub39.grit.shared.ui.theme.LocalFloatingBarInset
import com.shub39.grit.shared.ui.theme.GlassShapes
import com.shub39.grit.shared.ui.theme.GlassTier
import com.shub39.grit.shared.ui.theme.GritRadius
import com.shub39.grit.shared.ui.theme.glassSource
import com.shub39.grit.shared.ui.theme.glassSurface
import com.shub39.grit.shared.ui.theme.rememberGlassBackdrop
import com.shub39.grit.shared.ui.theme.flexFontEmphasis
import com.shub39.grit.shared.ui.theme.flexFontRounded
import com.shub39.grit.shared.ui.theme.titleBrush
import com.shub39.grit.shared.ui.theme.withTitleColor
import grit.shared.ui.generated.resources.*
import grit.shared.ui.generated.resources.add
import kotlin.invoke
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Sentinel id for the virtual "Today" category, which is not persisted. */
private const val TODAY_VIEW_ID = -1L

@Composable
fun TaskList(
    state: TaskState,
    onAction: (TaskAction) -> Unit,
    onEditCategories: () -> Unit,
    onOpenTaskDetails: (Task) -> Unit,
    onAddTask: () -> Unit,
    onAddCategory: () -> Unit,
) = PageFill {
    val windowSizeClass = LocalWindowSizeClass.current

    var editState by remember { mutableStateOf(false) }
    // Tapping a card completes it (and so archives it) only while this is on; otherwise a tap
    // does nothing, so scrolling the list can't finish a task by accident. It and reorder mode
    // exclude each other.
    var completeState by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    // The primary lens floats over the list, so it samples the page as its glass backdrop.
    val backdrop = rememberGlassBackdrop()

    Column(
        modifier =
            Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .glassSource(backdrop)
    ) {
        TaskListTopBar(
            state = state,
            scrollBehavior = scrollBehavior,
            isReorderMode = editState,
            onReorderToggle = {
                editState = it
                if (it) completeState = false
            },
            isCompleteMode = completeState,
            onCompleteToggle = {
                completeState = it
                if (it) editState = false
            },
            isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded,
        )

        if (state.tasks.isEmpty()) {
            Text("Add a category to get started, or import a Grit Glass / Grhinet backup in Settings.", Modifier.padding(16.dp))
        }

        CategorySelector(
            state = state,
            isReorderMode = editState,
            onAction = onAction,
            onAddCategoryClick = onAddCategory,
            onEditCategoriesClick = onEditCategories,
            isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded,
            onCategoryChange = {
                editState = false
                completeState = false
            },
        )

        if (windowSizeClass.widthSizeClass != WindowWidthSizeClass.Expanded) {
            CompactTasksView(
                state = state,
                isReorderMode = editState,
                isCompleteMode = completeState,
                onAction = onAction,
                onEditTask = onOpenTaskDetails,
                isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact,
            )
        } else {
            ExpandedTasksView(state = state, onAction = onAction, onEditTask = onOpenTaskDetails)
        }
    }

    val barInset = LocalFloatingBarInset.current
    AnimatedVisibility(
        visible = state.currentCategory != null,
        modifier =
            Modifier.align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp + barInset)
                .then(
                    if (windowSizeClass.widthSizeClass != WindowWidthSizeClass.Expanded) Modifier
                    else Modifier.navigationBarsPadding()
                ),
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        GlassFab(
            icon = vectorResource(Res.drawable.add),
            contentDescription = stringResource(Res.string.add_task),
            onClick = onAddTask,
            backdrop = backdrop,
            label =
                if (
                    state.openTasks(state.currentCategory).isEmpty() ||
                        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
                )
                    stringResource(Res.string.add_task)
                else null,
        )
    }
}

@Composable
private fun TaskListTopBar(
    state: TaskState,
    scrollBehavior: TopAppBarScrollBehavior,
    isReorderMode: Boolean,
    onReorderToggle: (Boolean) -> Unit,
    isCompleteMode: Boolean,
    onCompleteToggle: (Boolean) -> Unit,
    isExpanded: Boolean,
) {
    Box(Modifier.fillMaxWidth()) {
    // Glass fades in as content scrolls under the bar; at rest the bar is clear.
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
        title = { Text(text = stringResource(Res.string.tasks), fontFamily = flexFontEmphasis()) },
        subtitle = {
            Text(
                text = "${state.completedTasks.size} " + stringResource(Res.string.items_completed),
                fontFamily = flexFontRounded(),
            )
        },
        actions = {
            val motionScheme = MaterialTheme.motionScheme
            val shown =
                if (state.isTodayView) state.todayTasks else state.openTasks(state.currentCategory)

            AnimatedVisibility(
                visible = state.tasks.values.isNotEmpty() && !isExpanded,
                enter = fadeIn(motionScheme.fastEffectsSpec()),
                exit = fadeOut(motionScheme.fastEffectsSpec()),
            ) {
                CompleteModeButton(
                    checked = isCompleteMode,
                    onCheckedChange = onCompleteToggle,
                    enabled = shown.isNotEmpty(),
                    modifier = Modifier.padding(end = 8.dp),
                )
            }

            AnimatedVisibility(
                visible = state.tasks.values.isNotEmpty() && !isExpanded,
                enter = fadeIn(motionScheme.fastEffectsSpec()),
                exit = fadeOut(motionScheme.fastEffectsSpec()),
            ) {
                GlassIconButton(
                    icon = vectorResource(Res.drawable.reorder),
                    contentDescription = "Reorder",
                    onClick = { onReorderToggle(!isReorderMode) },
                    checked = isReorderMode,
                    enabled = shown.isNotEmpty(),
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        },
    )
    }
}

@Composable
private fun CategorySelector(
    state: TaskState,
    isReorderMode: Boolean,
    onAction: (TaskAction) -> Unit,
    onAddCategoryClick: () -> Unit,
    onEditCategoriesClick: () -> Unit,
    isExpanded: Boolean,
    onCategoryChange: () -> Unit,
) {
    // Category chips sit in a glass tray.
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
        if (!isExpanded) {
            // Virtual "Today" category: tasks flagged isToday across all categories.
            item(key = "today_selector") {
                CategoryToggleButton(
                    checked = state.isTodayView,
                    onCheckedChange = {
                        onAction(TaskAction.SelectTodayView)
                        onCategoryChange()
                    },
                ) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.light_mode),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(Res.string.today))
                }
            }

            items(state.tasks.keys.toList(), key = { it.id }) { category ->
                CategoryToggleButton(
                    checked = !state.isTodayView && category == state.currentCategory,
                    onCheckedChange = {
                        onAction(TaskAction.ChangeCategory(category))
                        onCategoryChange()
                    },
                ) {
                    if (category.titleColor != null) {
                        Box(
                            modifier =
                                Modifier.size(10.dp)
                                    .clip(CircleShape)
                                    .background(titleBrush(category.titleColor))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = category.name,
                        style = LocalTextStyle.current.withTitleColor(category.titleColor),
                    )
                }
            }
            item {
                Row {
                    GlassIconButton(
                        icon = vectorResource(Res.drawable.add),
                        contentDescription = stringResource(Res.string.add_category),
                        onClick = onAddCategoryClick,
                        enabled = !isReorderMode,
                        inTray = true,
                    )
                    GlassIconButton(
                        icon = vectorResource(Res.drawable.edit),
                        contentDescription = stringResource(Res.string.edit_categories),
                        onClick = onEditCategoriesClick,
                        enabled = !isReorderMode,
                        inTray = true,
                    )
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(onClick = onAddCategoryClick, enabled = !isReorderMode) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.add),
                            contentDescription = null,
                            modifier = Modifier.size(GlassMetrics.Icon),
                        )
                        Text(text = stringResource(Res.string.add_category))
                    }
                    GlassButton(onClick = onEditCategoriesClick, enabled = !isReorderMode) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.edit),
                            contentDescription = null,
                            modifier = Modifier.size(GlassMetrics.Icon),
                        )
                        Text(text = stringResource(Res.string.edit_categories))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactTasksView(
    state: TaskState,
    isReorderMode: Boolean,
    isCompleteMode: Boolean,
    onAction: (TaskAction) -> Unit,
    onEditTask: (Task) -> Unit,
    isCompact: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.padding(horizontal = if (isCompact) 0.dp else 16.dp),
    ) {
        val motionScheme = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = if (state.isTodayView) TODAY_VIEW_ID else state.currentCategory?.id,
            transitionSpec = {
                fadeIn(motionScheme.fastEffectsSpec()) togetherWith
                    fadeOut(motionScheme.fastEffectsSpec())
            },
        ) { categoryId ->
            val isTodayView = categoryId == TODAY_VIEW_ID
            val category = state.tasks.keys.firstOrNull { it.id == categoryId }
            if (isTodayView || category != null) {
                // Completed tasks are archived and shelved ones are out of the way: the list
                // only holds what is open now.
                val sourceTasks = if (isTodayView) state.todayTasks else state.openTasks(category)

                val lazyListState = rememberLazyListState()
                var reorderableTasks by
                    remember(state.tasks.values, state.today) { mutableStateOf(sourceTasks) }
                val reorderableListState =
                    rememberReorderableLazyListState(lazyListState) { from, to ->
                        reorderableTasks =
                            reorderableTasks.toMutableList().apply {
                                add(to.index, removeAt(from.index))
                            }
                    }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = lazyListState,
                    // Runs on beneath the floating bar and the primary lens.
                    contentPadding =
                        PaddingValues(
                            start = 8.dp,
                            top = 8.dp,
                            end = 8.dp,
                            bottom = LocalFloatingBarInset.current + GlassMetrics.Primary + 24.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(items = reorderableTasks, key = { _, it -> it.id }) { index, task
                        ->
                        ReorderableItem(reorderableListState, key = task.id) {
                            val cardShape =
                                when {
                                    reorderableTasks.size == 1 -> RoundedCornerShape(GritRadius.xl)
                                    index == 0 ->
                                        RoundedCornerShape(
                                            topStart = GritRadius.xl,
                                            topEnd = GritRadius.xl,
                                            bottomStart = GritRadius.join,
                                            bottomEnd = GritRadius.join,
                                        )

                                    index == reorderableTasks.size - 1 ->
                                        RoundedCornerShape(
                                            topStart = GritRadius.join,
                                            topEnd = GritRadius.join,
                                            bottomStart = GritRadius.xl,
                                            bottomEnd = GritRadius.xl,
                                        )

                                    else -> RoundedCornerShape(GritRadius.join)
                                }

                            TaskCard(
                                task = task,
                                dragState = isReorderMode && !isTodayView,
                                completeState = isCompleteMode,
                                reorderIcon = {
                                    Icon(
                                        imageVector = vectorResource(Res.drawable.drag_indicator),
                                        contentDescription = "Drag",
                                        modifier =
                                            Modifier.draggableHandle(
                                                onDragStopped = {
                                                    onAction(
                                                        TaskAction.ReorderTasks(
                                                            reorderableTasks.mapIndexed { i, t ->
                                                                i to t
                                                            }
                                                        )
                                                    )
                                                }
                                            ),
                                    )
                                },
                                is24Hr = state.is24Hour,
                                shape = cardShape,
                                onDetailsClick = { onEditTask(task) },
                                onSectionsChange = {
                                    onAction(TaskAction.UpsertTask(task.copy(sections = it)))
                                },
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .clip(cardShape)
                                        .completionToggle(isCompleteMode, task, onAction)
                                        .then(
                                            // Long press drags to reorder; the Today view mixes
                                            // categories whose indices can't be reordered.
                                            if (!isTodayView) {
                                                Modifier.longPressDraggableHandle(
                                                    onDragStopped = {
                                                        onAction(
                                                            TaskAction.ReorderTasks(
                                                                reorderableTasks.mapIndexed { i, t
                                                                    ->
                                                                    i to t
                                                                }
                                                            )
                                                        )
                                                    }
                                                )
                                            } else {
                                                Modifier
                                            }
                                        ),
                            )
                        }
                    }

                    if (reorderableTasks.isEmpty()) {
                        item { Empty(modifier = Modifier.padding(top = 150.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedTasksView(
    state: TaskState,
    onAction: (TaskAction) -> Unit,
    onEditTask: (Task) -> Unit,
) {
    val todayCategory =
        Category(
            id = TODAY_VIEW_ID,
            name = stringResource(Res.string.today),
            orderIndex = -1,
            titleColor = null,
        )
    val tasksAndCategories =
        listOf(todayCategory to state.todayTasks) +
            state.tasks.keys.map { category -> category to state.openTasks(category) }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 350.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 60.dp),
        verticalItemSpacing = 8.dp,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(tasksAndCategories, key = { it.first.id }) { (category, tasks) ->
            val isTodayPanel = category.id == TODAY_VIEW_ID
            val displayTasks = tasks
            var showReorderDialog by remember { mutableStateOf(false) }
            // Per panel: taps on this panel's cards toggle completion only while it is on.
            var completeMode by remember { mutableStateOf(false) }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(28.dp),
                modifier =
                    Modifier.widthIn(max = 350.dp)
                        .heightIn(max = 1000.dp)
                        .animateContentSize(
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                        ),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(end = 8.dp).weight(1f),
                            )

                            CompleteModeButton(
                                checked = completeMode,
                                onCheckedChange = { completeMode = it },
                                enabled = tasks.isNotEmpty(),
                                modifier = Modifier.padding(end = 8.dp),
                            )

                            GlassIconButton(
                                icon = vectorResource(Res.drawable.reorder),
                                contentDescription = "Reorder",
                                onClick = { showReorderDialog = !showReorderDialog },
                                checked = showReorderDialog,
                                enabled = !isTodayPanel && displayTasks.size > 1,
                            )
                        }
                    }

                    itemsIndexed(items = displayTasks, key = { _, it -> it.id }) { index, task ->
                        val cardShape =
                            when {
                                displayTasks.size == 1 -> RoundedCornerShape(GritRadius.xl)
                                index == 0 ->
                                    RoundedCornerShape(
                                        topStart = GritRadius.xl,
                                        topEnd = GritRadius.xl,
                                        bottomStart = GritRadius.join,
                                        bottomEnd = GritRadius.join,
                                    )

                                index == displayTasks.size - 1 ->
                                    RoundedCornerShape(
                                        topStart = GritRadius.join,
                                        topEnd = GritRadius.join,
                                        bottomStart = GritRadius.xl,
                                        bottomEnd = GritRadius.xl,
                                    )

                                else -> RoundedCornerShape(GritRadius.join)
                            }

                        TaskCard(
                            task = task,
                            dragState = false,
                            reorderIcon = {},
                            completeState = completeMode,
                            is24Hr = state.is24Hour,
                            shape = cardShape,
                            onDetailsClick = { onEditTask(task) },
                            onSectionsChange = {
                                onAction(TaskAction.UpsertTask(task.copy(sections = it)))
                            },
                            modifier =
                                Modifier.animateItem()
                                    .fillMaxWidth()
                                    .clip(cardShape)
                                    .completionToggle(completeMode, task, onAction),
                        )
                    }

                    if (tasks.isEmpty()) {
                        item { Empty(modifier = Modifier.padding(32.dp)) }
                    }
                }
            }

            if (showReorderDialog) {
                GritDialog(onDismissRequest = { showReorderDialog = false }, padding = 0.dp) {
                    var reorderableTasks = remember { displayTasks }

                    val listState = rememberLazyListState()
                    val reorderableListState =
                        rememberReorderableLazyListState(listState) { from, to ->
                            reorderableTasks =
                                reorderableTasks.toMutableList().apply {
                                    add(to.index, removeAt(from.index))
                                }

                            onAction(
                                TaskAction.ReorderTasks(
                                    reorderableTasks.mapIndexed { index, task -> index to task }
                                )
                            )
                        }

                    Column(
                        modifier =
                            Modifier.fillMaxWidth()
                                .heightIn(max = 600.dp)
                                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier.size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialShapes.Pill.toShape(),
                                    ),
                        ) {
                            Icon(
                                imageVector = vectorResource(Res.drawable.reorder),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }

                        Text(
                            text = stringResource(Res.string.reorder_tasks),
                            style =
                                MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = flexFontEmphasis()
                                ),
                        )

                        LazyColumn(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                            state = listState,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(bottom = 16.dp),
                        ) {
                            itemsIndexed(items = reorderableTasks, key = { _, it -> it.id }) {
                                index,
                                task ->
                                ReorderableItem(reorderableListState, key = task.id) {
                                    val shape =
                                        when {
                                            reorderableTasks.size == 1 -> detachedItemShape()
                                            index == 0 -> leadingItemShape()
                                            index == reorderableTasks.size - 1 -> endItemShape()
                                            else -> middleItemShape()
                                        }

                                    ListItem(
                                        modifier = Modifier.clip(shape),
                                        colors =
                                            ListItemDefaults.colors(
                                                containerColor =
                                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                            ),
                                        headlineContent = {
                                            Text(
                                                text = task.title,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                        trailingContent = {
                                            Icon(
                                                imageVector =
                                                    vectorResource(Res.drawable.drag_indicator),
                                                contentDescription = null,
                                                modifier =
                                                    Modifier.padding(horizontal = 8.dp)
                                                        .draggableHandle(),
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Switch beside "Reorder": while it is on, tapping a task card completes the task. */
@Composable
private fun CompleteModeButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    GlassIconButton(
        icon = vectorResource(Res.drawable.check_circle),
        contentDescription = "Tap a task to complete it",
        onClick = { onCheckedChange(!checked) },
        checked = checked,
        enabled = enabled,
        modifier = modifier,
    )
}

/**
 * A tap completes the task, which archives it, only when [enabled] (the completion switch is on).
 * When off the card takes no taps at all, so a touch that starts a scroll can't finish a task.
 */
private fun Modifier.completionToggle(
    enabled: Boolean,
    task: Task,
    onAction: (TaskAction) -> Unit,
): Modifier =
    clickable(enabled = enabled) { onAction(TaskAction.UpsertTask(task.copy(isCompleted = true))) }
