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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shub39.grit.core.settings.WallSettings
import com.shub39.grit.core.settings.WallTextSize
import com.shub39.grit.core.sync.WallLayoutStore
import com.shub39.grit.core.tasks.CardSize
import com.shub39.grit.core.tasks.CardSpot
import com.shub39.grit.core.tasks.Category
import com.shub39.grit.core.tasks.Priority
import com.shub39.grit.core.tasks.Task
import com.shub39.grit.core.tasks.Urgency
import com.shub39.grit.core.tasks.cardSize
import com.shub39.grit.core.tasks.clusterByCategory
import com.shub39.grit.core.tasks.hasSunk
import com.shub39.grit.core.tasks.idleDays
import com.shub39.grit.core.tasks.isOverdue
import com.shub39.grit.core.tasks.placeMissing
import com.shub39.grit.core.tasks.pullUrgentToCentre
import com.shub39.grit.core.tasks.scatter
import com.shub39.grit.core.tasks.urgency
import com.shub39.grit.shared.ui.theme.GlassBackdrop
import com.shub39.grit.shared.ui.theme.GlassMetrics
import com.shub39.grit.shared.ui.theme.GlassShapes
import com.shub39.grit.shared.ui.theme.GlassTier
import com.shub39.grit.shared.ui.theme.GlassTokens
import com.shub39.grit.shared.ui.theme.GritRadius
import com.shub39.grit.shared.ui.theme.glassSurface
import com.shub39.grit.shared.ui.theme.parseTitleColors
import com.shub39.grit.shared.ui.theme.readableTitleColors
import com.shub39.grit.shared.ui.theme.titleBrush
import com.shub39.grit.shared.ui.theme.withoutFontPadding
import grit.shared.ui.generated.resources.Res
import grit.shared.ui.generated.resources.edit
import grit.shared.ui.generated.resources.light_mode
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject

private const val MinZoom = 0.35f
private const val MaxZoom = 1.8f

/**
 * The desk on a wide window: where every card lies (kept in [WallLayoutStore], on this device
 * only), and the camera over it. A point on the desk in dp shows at
 * `viewport centre + pan + point × density × zoom`.
 */
@Stable
class DeskState internal constructor(
    private val store: WallLayoutStore,
    private val scope: CoroutineScope,
) {
    /** Saved spots by task id; null until they are read. */
    var stored by mutableStateOf<Map<Long, CardSpot>?>(null)
        private set

    var zoom by mutableFloatStateOf(1f)
        private set

    var pan by mutableStateOf(Offset.Zero)
    var viewport by mutableStateOf(IntSize.Zero)
        internal set

    /** The layout before the last arrange, while it can still be taken back. */
    var undo by mutableStateOf<Map<Long, CardSpot>?>(null)
        private set

    /** The card last moved, drawn above the others. */
    var top by mutableStateOf<Long?>(null)
        internal set

    internal var fitted = false
    internal var density = 1f

    internal suspend fun load() {
        stored =
            try {
                store.load()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyMap()
            }
    }

    private fun save(spots: Map<Long, CardSpot>) {
        scope.launch { runCatching { store.save(spots) } }
    }

    /** Takes in new placements and drops spots of tasks that are gone or done. */
    internal fun reconcile(placed: Map<Long, CardSpot>, keep: Set<Long>?) {
        val old = stored ?: return
        val merged = (old + placed).let { all -> if (keep == null) all else all.filterKeys { it in keep } }
        if (merged != old) {
            stored = merged
            save(merged)
        }
    }

    internal fun move(id: Long, from: CardSpot, dx: Float, dy: Float) {
        val old = stored ?: return
        top = id
        stored = old + (id to (old[id] ?: from).let { it.copy(x = it.x + dx, y = it.y + dy) })
    }

    internal fun commit() {
        stored?.let(::save)
    }

    /** Replaces the whole layout, keeping the old one for [undoArrange]. */
    fun arrange(spots: Map<Long, CardSpot>, sizes: (Long) -> CardSize?) {
        val old = stored ?: return
        undo = old
        stored = old + spots
        save(stored!!)
        fit(sizes)
    }

    fun undoArrange(sizes: (Long) -> CardSize?) {
        val previous = undo ?: return
        undo = null
        stored = previous
        save(previous)
        fit(sizes)
    }

    internal fun forgetUndo() {
        undo = null
    }

    private val centre: Offset
        get() = Offset(viewport.width / 2f, viewport.height / 2f)

    fun zoomAround(point: Offset, factor: Float) {
        val next = (zoom * factor).coerceIn(MinZoom, MaxZoom)
        val f = next / zoom
        val p = point - centre
        pan = p - (p - pan) * f
        zoom = next
    }

    fun zoomBy(factor: Float) = zoomAround(centre, factor)

    fun resetZoom() = zoomAround(centre, 1f / zoom)

    /** Frames every placed card whose size [sizes] knows. */
    fun fit(sizes: (Long) -> CardSize?) {
        val spots = stored ?: return
        val boxes = spots.mapNotNull { (id, s) -> sizes(id)?.let { s to it } }
        if (boxes.isEmpty() || viewport.width == 0) {
            pan = Offset.Zero
            zoom = 1f
            return
        }
        val left = boxes.minOf { (s, c) -> s.x - c.width / 2 }
        val right = boxes.maxOf { (s, c) -> s.x + c.width / 2 }
        val topEdge = boxes.minOf { (s, c) -> s.y - c.height / 2 }
        val bottom = boxes.maxOf { (s, c) -> s.y + c.height / 2 }
        // Room around the cards, and at the bottom for the floating controls.
        val w = (right - left + 120f) * density
        val h = (bottom - topEdge + 220f) * density
        zoom = minOf(viewport.width / w, viewport.height / h).coerceIn(MinZoom, 1.1f)
        val cx = (left + right) / 2f
        val cy = (topEdge + bottom) / 2f + 40f
        pan = Offset(-cx * density * zoom, -cy * density * zoom)
    }
}

@Composable
fun rememberDeskState(): DeskState {
    val store = koinInject<WallLayoutStore>()
    val scope = rememberCoroutineScope()
    val state = remember(store) { DeskState(store, scope) }
    LaunchedEffect(state) { state.load() }
    return state
}

/**
 * The task wall on a wide window: a warm paper desk with the open tasks lying on it as cards. Size
 * is importance, the depth of the paper is how near the deadline is. Cards can be dragged
 * anywhere; empty desk drags (or a middle-button drag, or the wheel) pan, Ctrl + wheel or a pinch
 * zooms. Hovering a card straightens and lifts it; a click does what the tap keys say, a double
 * click opens the editor.
 *
 * [shown] are the tasks to draw (narrowed by category); [active] every task in play, which decides
 * where new cards go so they never land on hidden ones; [keep] the ids whose spots are worth
 * keeping, or null while the tasks are still loading.
 */
@Composable
fun Desk(
    state: DeskState,
    shown: List<Task>,
    active: List<Task>,
    keep: Set<Long>?,
    categoryOf: Map<Long, Category>,
    today: LocalDate,
    settings: WallSettings,
    onTap: (Task, LayoutCoordinates) -> Unit,
    onHold: (Task, LayoutCoordinates) -> Unit,
    onEdit: (Task) -> Unit,
    overdueChoices: @Composable RowScope.(Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    state.density = density.density
    val stored = state.stored
    val spots =
        remember(stored, active, today, settings.thresholds) {
            stored?.let { placeMissing(active, it, today, settings.thresholds) }
        }
    LaunchedEffect(spots, keep) { spots?.let { state.reconcile(it, keep) } }
    LaunchedEffect(spots != null, state.viewport) {
        if (spots != null && state.viewport.width > 0 && !state.fitted) {
            state.fitted = true
            val sizeOf = active.associate { it.id to it.cardSize(today) }
            state.fit { sizeOf[it] }
        }
    }
    val dots = GlassTokens.Shadow.copy(alpha = 0.13f)

    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { state.viewport = it }
            .drawBehind {
                // A faint dot grid that moves with the desk, so panning can be felt.
                val step = 32.dp.toPx() * state.zoom
                if (step >= 10f) {
                    val origin = Offset(size.width / 2f, size.height / 2f) + state.pan
                    val startX = origin.x - floor(origin.x / step) * step
                    val startY = origin.y - floor(origin.y / step) * step
                    val points = ArrayList<Offset>()
                    var y = startY
                    while (y < size.height) {
                        var x = startX
                        while (x < size.width) {
                            points += Offset(x, y)
                            x += step
                        }
                        y += step
                    }
                    drawPoints(points, PointMode.Points, dots, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            .pointerInput(state) {
                // Wheel and middle-button pans come first, so they work over cards too.
                awaitPointerEventScope {
                    var last: Offset? = null
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: continue
                        when (event.type) {
                            PointerEventType.Scroll -> {
                                val d = change.scrollDelta
                                when {
                                    event.keyboardModifiers.isCtrlPressed ->
                                        state.zoomAround(change.position, 1.12f.pow(-d.y))
                                    event.keyboardModifiers.isShiftPressed ->
                                        state.pan -= Offset(d.y * 64f, 0f)
                                    else -> state.pan -= Offset(d.x * 64f, d.y * 64f)
                                }
                                change.consume()
                            }
                            else ->
                                if (event.buttons.isTertiaryPressed) {
                                    last?.let { state.pan += change.position - it }
                                    last = change.position
                                    event.changes.forEach { it.consume() }
                                } else last = null
                        }
                    }
                }
            }
            .pointerInput(state) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    state.pan += panChange
                    if (zoomChange != 1f) state.zoomAround(centroid, zoomChange)
                }
            }
    ) {
        if (spots == null) return@Box
        if (shown.isEmpty())
            Text(
                "The desk is empty. Tasks you add on the Task page lie here: bigger when they matter more, deeper paper as the deadline nears.",
                Modifier.align(Alignment.Center).widthIn(max = 340.dp).padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTokens.SecondaryText,
                textAlign = TextAlign.Center,
            )
        val cards = shown.filter { it.id in spots }
        Layout(
            content = {
                cards.forEach { task ->
                    key(task.id) {
                        val spot = spots.getValue(task.id)
                        val overdue = task.isOverdue(today)
                        DeskCard(
                            task = task,
                            category = categoryOf[task.categoryId],
                            today = today,
                            settings = settings,
                            angle = spot.angle,
                            onTap = { onTap(task, it) },
                            onHold = { onHold(task, it) },
                            onEdit = { onEdit(task) },
                            onDrag = { dx, dy -> state.move(task.id, spot, dx, dy) },
                            onDragEnd = { state.commit() },
                            footer = if (overdue) { { overdueChoices(task) } } else null,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val placeables =
                measurables.mapIndexed { i, m ->
                    val size = cards[i].cardSize(today)
                    m.measure(Constraints.fixed(size.width.dp.roundToPx(), size.height.dp.roundToPx()))
                }
            layout(constraints.maxWidth, constraints.maxHeight) {
                val cx = constraints.maxWidth / 2f + state.pan.x
                val cy = constraints.maxHeight / 2f + state.pan.y
                val z = state.zoom
                placeables.forEachIndexed { i, p ->
                    val task = cards[i]
                    val spot = spots.getValue(task.id)
                    val x = cx + spot.x * density.density * z - p.width / 2f
                    val y = cy + spot.y * density.density * z - p.height / 2f
                    // Drawn by importance, the card last moved on top of all.
                    val z0 = if (state.top == task.id) 10f else task.priority.ordinal.toFloat()
                    p.placeWithLayer(x.roundToInt(), y.roundToInt(), zIndex = z0) {
                        scaleX = z
                        scaleY = z
                    }
                }
            }
        }
    }
}

private fun WallTextSize.deskTitle(size: CardSize): Float {
    val base =
        when (size) {
            CardSize.LARGE -> 18f
            CardSize.MEDIUM -> 15.5f
            CardSize.SMALL -> 14f
        }
    return when (this) {
        WallTextSize.SMALL -> base - 1.5f
        WallTextSize.MEDIUM -> base
        WallTextSize.LARGE -> base + 2f
    }
}

/** What a desk card says about time: the date and days left, or how long it has sat untouched. */
private fun Task.deskNote(today: LocalDate): String {
    val due = deadline
    return when {
        due == null -> idleDays(today).let { if (it == 0) "Touched today" else "Idle ${days(it)}" }
        isOverdue(today) -> due.label(today)
        else -> "${due.label(today)} · ${deadlineLabel(today)}"
    }
}

/**
 * A task as a note on the desk: paper deepened by [Urgency], a strip of category-coloured tape at
 * the top, the title in its own colour. It lies at [angle] until hovered or dragged, then
 * straightens and lifts. Drags report their movement in desk dp.
 */
@Composable
private fun DeskCard(
    task: Task,
    category: Category?,
    today: LocalDate,
    settings: WallSettings,
    angle: Float,
    onTap: (LayoutCoordinates) -> Unit,
    onHold: (LayoutCoordinates) -> Unit,
    onEdit: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    footer: (@Composable RowScope.() -> Unit)?,
) {
    val size = task.cardSize(today)
    val urgency = task.urgency(today, settings.thresholds)
    val overdue = task.isOverdue(today)
    val sunk = task.hasSunk(today)
    val high = task.priority == Priority.HIGH
    val paper = if (sunk) GlassTokens.Settled else PaperTones.of(urgency)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val raised = hovered || dragging
    val tilt by animateFloatAsState(if (raised) 0f else angle)
    val lift by animateFloatAsState(if (raised) 1f else 0f)
    val position = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val corner = if (size == CardSize.SMALL) GritRadius.md else GritRadius.lg
    val shape = RoundedCornerShape(corner)
    val density = LocalDensity.current
    val ink = MaterialTheme.colorScheme.onSurface
    val tape = remember(category?.titleColor) { parseTitleColors(category?.titleColor) }
    val titleStops = remember(task.titleColor) { readableTitleColors(task.titleColor) } ?: listOf(ink)

    val textSize = settings.textSize.deskTitle(size)
    val lineDp = with(density) { (textSize * 1.28f).sp.toDp() }
    val bodyLine = 17.dp
    val header = if (size == CardSize.SMALL) 0.dp else 18.dp
    val footerHeight = if (footer != null) 32.dp else 18.dp
    val room = size.height.dp - 24.dp - header - footerHeight
    val titleLines = (room / lineDp).toInt().coerceIn(1, 3)
    val description =
        task.description.takeIf { size == CardSize.LARGE && footer == null && it.isNotBlank() }
    val descriptionLines =
        if (description == null) 0 else ((room - lineDp * titleLines) / bodyLine).toInt().coerceIn(0, 2)
    val markInset = if (overdue || urgency == Urgency.DUE) 8.dp else 0.dp

    Box(
        Modifier.onGloballyPositioned { position[0] = it }
            .hoverable(interaction)
            .pointerInput(task.id) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        onDragEnd()
                    },
                    onDragCancel = {
                        dragging = false
                        onDragEnd()
                    },
                ) { change, amount ->
                    change.consume()
                    onDrag(amount.x / density.density, amount.y / density.density)
                }
            }
            .graphicsLayer {
                rotationZ = tilt
                val s = 1f + 0.025f * lift
                scaleX = s
                scaleY = s
            }
            .paperElevation(shape, corner, high, sunk, lift = lift)
            .background(paper, shape)
            .border(0.5.dp, GlassTokens.Shadow.copy(alpha = 0.12f), shape)
            .clip(shape)
            .drawBehind {
                // Tape across the top edge, in the category colour (plain masking tape without one).
                val w = 46.dp.toPx()
                val brush =
                    tape?.let {
                        if (it.size == 1) SolidColor(it[0].copy(alpha = 0.62f))
                        else Brush.horizontalGradient(it.map { c -> c.copy(alpha = 0.62f) })
                    } ?: SolidColor(GlassTokens.Shadow.copy(alpha = 0.10f))
                drawRect(brush, topLeft = Offset((size.width - w) / 2f, 0f), size = Size(w, 7.dp.toPx()))
                if (overdue || urgency == Urgency.DUE) {
                    val inset = 14.dp.toPx()
                    drawRoundRect(
                        if (overdue) PaperTones.PassedMark else GlassTokens.SecondaryText,
                        topLeft = Offset(8.dp.toPx(), inset),
                        size = Size(3.dp.toPx(), size.height - inset * 2),
                        cornerRadius = CornerRadius(1.5.dp.toPx()),
                    )
                }
            }
            .combinedClickable(
                onClick = { position[0]?.let(onTap) },
                onLongClick = { position[0]?.let(onHold) },
                onDoubleClick = onEdit,
            )
    ) {
        Column(
            Modifier.fillMaxSize()
                .padding(start = 14.dp + markInset, end = 12.dp, top = 12.dp, bottom = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                if (size != CardSize.SMALL)
                    Row(Modifier.height(header), verticalAlignment = Alignment.CenterVertically) {
                        if (category != null) {
                            if (category.titleColor != null) {
                                Box(
                                    Modifier.size(8.dp)
                                        .clip(CircleShape)
                                        .background(titleBrush(category.titleColor))
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(
                                category.name,
                                Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                                color = GlassTokens.SecondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                Text(
                    task.title,
                    style =
                        MaterialTheme.typography.titleSmall
                            .withoutFontPadding()
                            .copy(
                                fontSize = textSize.sp,
                                lineHeight = (textSize * 1.28f).sp,
                                letterSpacing = 0.sp,
                                fontWeight = if (high) FontWeight.Bold else FontWeight.SemiBold,
                            )
                            .let {
                                if (titleStops.size == 1) it.copy(color = titleStops[0])
                                else it.copy(brush = Brush.horizontalGradient(titleStops))
                            },
                    maxLines = titleLines,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description != null && descriptionLines > 0)
                    Text(
                        description,
                        Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.sp, lineHeight = 17.sp),
                        color = GlassTokens.SecondaryText,
                        maxLines = descriptionLines,
                        overflow = TextOverflow.Ellipsis,
                    )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (footer != null) footer()
                else {
                    val progress =
                        task.sections
                            .takeIf { it.isNotEmpty() }
                            ?.let { all -> "${all.count { it.isCompleted }}/${all.size}" }
                    Text(
                        listOfNotNull(task.deskNote(today), progress).joinToString("　"),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                        color = GlassTokens.SecondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (footer != null) Spacer(Modifier.weight(1f))
                if (task.isToday)
                    Icon(
                        vectorResource(Res.drawable.light_mode),
                        contentDescription = "On Today",
                        modifier = Modifier.size(13.dp),
                        tint = GlassTokens.SecondaryText,
                    )
            }
        }
        if (hovered)
            Box(
                Modifier.align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(GlassTokens.Shadow.copy(alpha = 0.08f))
                    .clickable(role = Role.Button, onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    vectorResource(Res.drawable.edit),
                    contentDescription = "Edit",
                    modifier = Modifier.size(16.dp),
                    tint = GlassTokens.SecondaryText,
                )
            }
    }
}

/**
 * The desk's floating controls: three ways to tidy up, then the zoom. After tidying, "Undo" puts
 * the cards back where they were for a few seconds.
 */
@Composable
fun DeskToolbar(
    state: DeskState,
    active: List<Task>,
    categoryOrder: List<Long>,
    today: LocalDate,
    settings: WallSettings,
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier,
) {
    val sizes = remember(active, today) { active.associate { it.id to it.cardSize(today) } }
    val sizeOf: (Long) -> CardSize? = { sizes[it] }
    LaunchedEffect(state.undo) {
        if (state.undo != null) {
            delay(8000)
            state.forgetUndo()
        }
    }
    var seed by remember { mutableStateOf(0) }
    Row(
        modifier
            .glassSurface(GlassTier.Thin, GlassShapes.Capsule, backdrop)
            .padding(GlassMetrics.TrayInset),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val enabled = state.stored != null && active.isNotEmpty()
        TrayAction("Scatter", enabled) {
            seed++
            state.arrange(scatter(active, today, settings.thresholds, seed), sizeOf)
        }
        TrayAction("By category", enabled) {
            state.arrange(clusterByCategory(active, categoryOrder, today, settings.thresholds), sizeOf)
        }
        TrayAction("Urgent to centre", enabled) {
            state.arrange(pullUrgentToCentre(active, today, settings.thresholds), sizeOf)
        }
        if (state.undo != null) TrayAction("Undo", true, bold = true) { state.undoArrange(sizeOf) }
        Box(
            Modifier.padding(horizontal = 4.dp)
                .width(0.5.dp)
                .height(24.dp)
                .background(GlassTokens.Shadow.copy(alpha = 0.22f))
        )
        TrayAction("−", true) { state.zoomBy(1 / 1.2f) }
        TrayAction("${(state.zoom * 100).roundToInt()}%", true) { state.resetZoom() }
        TrayAction("+", true) { state.zoomBy(1.2f) }
        TrayAction("Fit", enabled) { state.fit(sizeOf) }
    }
}

@Composable
private fun TrayAction(text: String, enabled: Boolean, bold: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(GlassShapes.Capsule)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .height(GlassMetrics.Control)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium
                ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
            maxLines = 1,
        )
    }
}
