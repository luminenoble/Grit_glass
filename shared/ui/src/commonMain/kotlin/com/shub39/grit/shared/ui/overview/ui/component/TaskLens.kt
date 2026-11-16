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
package com.shub39.grit.shared.ui.overview.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.shub39.grit.core.nextMonday
import com.shub39.grit.core.tasks.Category
import com.shub39.grit.core.tasks.Task
import com.shub39.grit.core.tasks.daysLeft
import com.shub39.grit.core.tasks.hasSunk
import com.shub39.grit.core.tasks.idleDays
import com.shub39.grit.core.tasks.isOverdue
import com.shub39.grit.shared.ui.components.glass.GlassButton
import com.shub39.grit.shared.ui.components.glass.GlassButtonStyle
import com.shub39.grit.shared.ui.task.ui.component.DraftBackHandler
import com.shub39.grit.shared.ui.theme.GlassBackdrop
import com.shub39.grit.shared.ui.theme.GlassShapes
import com.shub39.grit.shared.ui.theme.GlassTier
import com.shub39.grit.shared.ui.theme.GlassTokens
import com.shub39.grit.shared.ui.theme.glassBlurActive
import com.shub39.grit.shared.ui.theme.glassSurface
import com.shub39.grit.shared.ui.theme.titleBrush
import com.shub39.grit.shared.ui.theme.withTitleColor
import grit.shared.ui.generated.resources.Res
import grit.shared.ui.generated.resources.check
import grit.shared.ui.generated.resources.check_circle
import grit.shared.ui.generated.resources.circle_border
import grit.shared.ui.generated.resources.edit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.vectorResource

private val LensShape = GlassShapes.Panel

/**
 * The preview of a task: a pane of glass that swells out of the card at [anchor] and lies over the
 * wall, which stays sharp only through it while a veil of paper washes the rest back. It is drawn
 * in the page rather than in a dialog window, so the glass really samples [backdrop].
 *
 * It answers the things a card is tapped for without opening the editor: tick Sections, finish,
 * suspend, park under "Later", and decide about a deadline that has passed or a task that has
 * sunk. Changes go out through [onUpdate]; [onDismiss] is called once the pane has closed.
 */
@Composable
fun TaskLens(
    task: Task,
    category: Category?,
    today: LocalDate,
    anchor: Rect,
    backdrop: GlassBackdrop,
    bottomInset: androidx.compose.ui.unit.Dp,
    onUpdate: (Task) -> Unit,
    onEdit: () -> Unit,
    onPickDeadline: () -> Unit,
    onPickSuspendDay: () -> Unit,
    onAbandon: () -> Unit,
    onDismiss: () -> Unit,
) {
    val open = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { open.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 420f)) }
    val close: () -> Unit = {
        scope.launch {
            open.animateTo(0f, tween(160))
            onDismiss()
        }
    }
    // Leaves the wall once it is acted on: close first, then let the card go.
    val closeThen: (() -> Unit) -> Unit = { then ->
        scope.launch {
            open.animateTo(0f, tween(160))
            onDismiss()
            then()
        }
    }
    DraftBackHandler(enabled = true, onBack = close)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp
        val width = minOf(maxWidth - 32.dp, 420.dp)
        val tallest = (maxHeight - top - bottomInset - 12.dp).coerceAtLeast(160.dp)
        val sampling = glassBlurActive(backdrop)

        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = open.value }
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.42f))
                .pointerInput(Unit) { detectTapGestures { close() } }
        )

        Layout(
            content = {
                Box(
                    Modifier.glassSurface(
                            GlassTier.Regular,
                            LensShape,
                            backdrop,
                            // Thinner than a menu, so the cards still glow through the pane.
                            tintAlpha = if (sampling) 0.48f else null,
                        )
                        .clip(LensShape)
                ) {
                    // Laid out at its final size from the first frame; the pane only uncovers it.
                    Box(
                        Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                            .width(width)
                            .heightIn(max = tallest)
                            .graphicsLayer { alpha = open.value.coerceIn(0f, 1f) }
                            // Taps on the pane itself must not fall through to the veil.
                            .pointerInput(Unit) { detectTapGestures {} }
                    ) {
                        LensContent(
                            task = task,
                            category = category,
                            today = today,
                            onUpdate = onUpdate,
                            onEdit = { closeThen(onEdit) },
                            onPickDeadline = onPickDeadline,
                            onPickSuspendDay = onPickSuspendDay,
                            onAbandon = onAbandon,
                            closeThen = closeThen,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val pane = measurables.first()
            val w = with(density) { width.roundToPx() }
            val margin = with(density) { 16.dp.roundToPx() }
            val topPx = with(density) { top.roundToPx() }
            val bottomPx = constraints.maxHeight - with(density) { (bottomInset + 12.dp).roundToPx() }
            val h = min(pane.maxIntrinsicHeight(w), with(density) { tallest.roundToPx() })
            // Opens where the card is, pulled in as far as it takes to stay on screen.
            val x =
                (anchor.center.x - w / 2f)
                    .coerceIn(margin.toFloat(), max(margin, constraints.maxWidth - margin - w).toFloat())
            val y = anchor.top.coerceIn(topPx.toFloat(), max(topPx, bottomPx - h).toFloat())
            val p = open.value
            val placeable =
                pane.measure(
                    Constraints.fixed(
                        lerp(anchor.width, w.toFloat(), p).roundToInt().coerceAtLeast(1),
                        lerp(anchor.height, h.toFloat(), p).roundToInt().coerceAtLeast(1),
                    )
                )
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(lerp(anchor.left, x, p).roundToInt(), lerp(anchor.top, y, p).roundToInt())
            }
        }
    }
}

@Composable
private fun LensContent(
    task: Task,
    category: Category?,
    today: LocalDate,
    onUpdate: (Task) -> Unit,
    onEdit: () -> Unit,
    onPickDeadline: () -> Unit,
    onPickSuspendDay: () -> Unit,
    onAbandon: () -> Unit,
    closeThen: (() -> Unit) -> Unit,
) {
    val quiet = GlassTokens.SecondaryText
    val suspended = task.isSuspended(today)
    val onWall = task.isActive(today)
    val overdue = onWall && task.isOverdue(today)
    val sunk = onWall && task.hasSunk(today)
    var choosingDay by remember(task.id) { mutableStateOf(false) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(titleBrush(category?.titleColor, quiet), CircleShape))
            Text(
                category?.name.orEmpty(),
                Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = quiet,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${task.priority.label()} priority",
                style = MaterialTheme.typography.labelMedium,
                color = quiet,
            )
        }

        Text(
            task.title,
            style =
                MaterialTheme.typography.titleLarge
                    .copy(fontWeight = FontWeight.SemiBold)
                    .withTitleColor(task.titleColor, MaterialTheme.colorScheme.onSurface),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        if (task.description.isNotBlank())
            Text(
                task.description,
                style = MaterialTheme.typography.bodyMedium,
                color = quiet,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )

        Text(
            when {
                task.isCompleted -> task.completedDay()?.let { "Completed ${it.label(today)}" } ?: "Completed"
                task.isSomeday -> "Under Later, untouched for ${days(task.idleDays(today))}"
                suspended -> "Suspended until ${task.hiddenUntil?.label(today)}"
                task.deadline != null -> {
                    val left = task.daysLeft(today) ?: 0
                    val day = task.deadline?.label(today)
                    when {
                        left < 0 -> "Deadline was $day"
                        left == 0 -> "Deadline is today"
                        else -> "Deadline $day, ${days(left)} left"
                    }
                }
                task.idleDays(today) == 0 -> "No deadline, touched today"
                else -> "No deadline, untouched for ${days(task.idleDays(today))}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = quiet,
        )

        if (task.sections.isNotEmpty())
            Column(Modifier.heightIn(max = 196.dp).verticalScroll(rememberScrollState())) {
                task.sections.forEachIndexed { index, section ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Checkbox) {
                                onUpdate(
                                    task.copy(
                                        sections =
                                            task.sections.map {
                                                if (it.id == section.id) it.toggleCompleted() else it
                                            }
                                    )
                                )
                            }
                            .heightIn(min = 40.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            vectorResource(
                                if (section.isCompleted) Res.drawable.check_circle
                                else Res.drawable.circle_border
                            ),
                            contentDescription = if (section.isCompleted) "Done" else "Not done",
                            modifier = Modifier.size(20.dp),
                            tint = if (section.isCompleted) GlassTokens.Done else quiet,
                        )
                        Text(
                            "${index + 1}.",
                            Modifier.padding(start = 10.dp, end = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = quiet,
                        )
                        Box(Modifier.weight(1f)) {
                            Text(
                                section.title,
                                style =
                                    MaterialTheme.typography.bodyMedium.withTitleColor(
                                        section.titleColor,
                                        MaterialTheme.colorScheme.onSurface,
                                        alpha = if (section.isCompleted) 0.62f else 1f,
                                    ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

        // A passed deadline or a long silence asks one quiet question; it can simply be left.
        if (overdue || sunk) {
            Text(
                if (overdue) "This deadline has passed. What now?"
                else "Untouched for ${days(task.idleDays(today))}. Still worth doing?",
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (overdue) {
                    LensChip("New date", onClick = onPickDeadline)
                    LensChip("No date", onClick = { onUpdate(task.copy(deadline = null)) })
                } else {
                    LensChip("Make it smaller", onClick = onEdit)
                    LensChip(
                        "Later",
                        onClick = { closeThen { onUpdate(task.copy(isSomeday = true)) } },
                    )
                }
                LensChip("Give up", onClick = onAbandon, style = GlassButtonStyle.Danger)
            }
        }

        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                task.isCompleted ->
                    LensButton(
                        "Reopen",
                        Modifier.weight(1f),
                        style = GlassButtonStyle.Prominent,
                        onClick = { closeThen { onUpdate(task.copy(isCompleted = false)) } },
                    )
                task.isSomeday ->
                    LensButton(
                        "Back to the wall",
                        Modifier.weight(1f),
                        style = GlassButtonStyle.Prominent,
                        onClick = { closeThen { onUpdate(task.copy(isSomeday = false)) } },
                    )
                suspended ->
                    LensButton(
                        "Bring back now",
                        Modifier.weight(1f),
                        style = GlassButtonStyle.Prominent,
                        onClick = { closeThen { onUpdate(task.copy(hiddenUntil = null)) } },
                    )
                else ->
                    LensButton(
                        "Complete",
                        Modifier.weight(1f),
                        style = GlassButtonStyle.Prominent,
                        icon = Res.drawable.check,
                        onClick = { closeThen { onUpdate(task.copy(isCompleted = true)) } },
                    )
            }
            LensButton("Edit", Modifier.weight(1f), icon = Res.drawable.edit, onClick = onEdit)
        }

        if (onWall) {
            if (!choosingDay)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LensButton("Suspend", Modifier.weight(1f), onClick = { choosingDay = true })
                    if (!sunk)
                        LensButton(
                            "Later",
                            Modifier.weight(1f),
                            onClick = { closeThen { onUpdate(task.copy(isSomeday = true)) } },
                        )
                }
            else {
                val until: (LocalDate) -> Unit = { day ->
                    closeThen { onUpdate(task.copy(hiddenUntil = day)) }
                }
                Text(
                    "Suspend until when? It comes back by itself on that day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = quiet,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LensChip("Tomorrow", onClick = { until(today.plus(1, DateTimeUnit.DAY)) })
                    LensChip("Monday", onClick = { until(today.nextMonday()) })
                    LensChip("In a month", onClick = { until(today.plus(1, DateTimeUnit.MONTH)) })
                    LensChip("Pick a day", onClick = onPickSuspendDay)
                }
            }
        }
    }
}

@Composable
private fun LensButton(
    text: String,
    modifier: Modifier = Modifier,
    style: GlassButtonStyle = GlassButtonStyle.Plain,
    icon: org.jetbrains.compose.resources.DrawableResource? = null,
    onClick: () -> Unit,
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier,
        style = style,
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        if (icon != null) Icon(vectorResource(icon), contentDescription = null, Modifier.size(18.dp))
        Text(text, maxLines = 1, style = LocalTextStyle.current)
    }
}

@Composable
private fun LensChip(
    text: String,
    style: GlassButtonStyle = GlassButtonStyle.Plain,
    onClick: () -> Unit,
) {
    GlassButton(onClick = onClick, style = style, contentPadding = PaddingValues(horizontal = 12.dp)) {
        Text(text, maxLines = 1)
    }
}
