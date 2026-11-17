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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shub39.grit.core.settings.ImportanceDisplay
import com.shub39.grit.core.settings.WallSettings
import com.shub39.grit.core.settings.WallTextSize
import com.shub39.grit.core.tasks.Category
import com.shub39.grit.core.tasks.Priority
import com.shub39.grit.core.tasks.Task
import com.shub39.grit.shared.ui.theme.GlassTokens
import com.shub39.grit.shared.ui.theme.GritRadius
import com.shub39.grit.shared.ui.theme.parseTitleColors
import com.shub39.grit.shared.ui.theme.readableTitleColors
import com.shub39.grit.shared.ui.theme.withoutFontPadding
import grit.shared.ui.generated.resources.Res
import grit.shared.ui.generated.resources.light_mode
import org.jetbrains.compose.resources.vectorResource

/** The tile lists: rows of a fixed height, however large the titles are set. */
object WallMetrics {
    val Row = 48.dp
    val Gap = 8.dp
    val Margin = 16.dp
    /** Tile column width on wide screens, where a row holds four tiles of two columns. */
    val Column = 76.dp
    val Label = 24.dp
}

/** How far a priority fades a tile's border into the paper. */
private val Priority.fade: Float
    get() =
        when (this) {
            Priority.HIGH -> 0f
            Priority.NORMAL -> 0.48f
            Priority.LOW -> 0.68f
        }

/** Colours cannot go deeper than they are stored, so a high priority is drawn heavier instead. */
private val Priority.borderWidth: Dp
    get() = if (this == Priority.HIGH) 2.5.dp else 1.5.dp

/** How strongly the category colour washes the paper, when the background shows importance. */
private val Priority.wash: Float
    get() =
        when (this) {
            Priority.HIGH -> 0.34f
            Priority.NORMAL -> 0.10f
            Priority.LOW -> 0.04f
        }

private fun brushOf(stops: List<Color>): Brush =
    if (stops.size == 1) SolidColor(stops[0]) else Brush.horizontalGradient(stops)

private fun WallTextSize.tileTitle(): Float =
    when (this) {
        WallTextSize.SMALL -> 12f
        WallTextSize.MEDIUM -> 13f
        WallTextSize.LARGE -> 15f
    }

/**
 * A task as a one-row paper tile in the Due, Later and Done lists. Its category is the border
 * colour; its priority sets the border weight and fade, an optional wash and the elevation. The
 * title keeps its own colour at full contrast.
 *
 * [meta] is the small neutral note about time. [paper] replaces the plain paper (the Due list
 * deepens it as the deadline nears); [overdue] adds a brick mark on the left; [sunk] recesses the
 * tile. Taps report the tile's coordinates so a preview can open from where it is.
 */
@Composable
fun TaskTile(
    task: Task,
    category: Category?,
    settings: WallSettings,
    onClick: (LayoutCoordinates) -> Unit,
    onLongClick: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    paper: Color? = null,
    overdue: Boolean = false,
    sunk: Boolean = false,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val base = paper ?: if (sunk) GlassTokens.Settled else MaterialTheme.colorScheme.surface
    val high = task.priority == Priority.HIGH
    val categoryStops =
        remember(category?.titleColor) { parseTitleColors(category?.titleColor) } ?: listOf(ink)
    val titleStops =
        remember(task.titleColor) { readableTitleColors(task.titleColor) } ?: listOf(ink)
    val border = brushOf(categoryStops.map { lerp(it, base, task.priority.fade) })
    val fill =
        if (settings.importance == ImportanceDisplay.WITH_BACKGROUND && paper == null)
            brushOf(categoryStops.map { lerp(base, it, task.priority.wash) })
        else SolidColor(base)
    val corner = GritRadius.md
    val shape = RoundedCornerShape(corner)
    val textSize = settings.textSize.tileTitle()
    val titleStyle =
        MaterialTheme.typography.titleSmall
            .withoutFontPadding()
            .copy(
                fontSize = textSize.sp,
                lineHeight = (textSize * 1.3f).sp,
                letterSpacing = 0.sp,
                fontWeight = if (high) FontWeight.Bold else FontWeight.Medium,
            )
            .let {
                if (titleStops.size == 1) it.copy(color = titleStops[0])
                else it.copy(brush = Brush.horizontalGradient(titleStops))
            }
    val line = with(LocalDensity.current) { (textSize * 1.3f).sp.toDp() }
    val lines = ((WallMetrics.Row - 12.dp) / line).toInt().coerceAtLeast(1)
    val position = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val borderWidth = task.priority.borderWidth

    Box(
        modifier
            .onGloballyPositioned { position[0] = it }
            .paperElevation(shape, corner, high, sunk, inset = borderWidth)
            .background(fill, shape)
            .border(borderWidth, border, shape)
            .clip(shape)
            .drawBehind {
                if (overdue) {
                    val inset = 12.dp.toPx()
                    drawRoundRect(
                        PaperTones.PassedMark,
                        topLeft = Offset(8.dp.toPx(), inset),
                        size = Size(3.dp.toPx(), size.height - inset * 2),
                        cornerRadius = CornerRadius(1.5.dp.toPx()),
                    )
                }
            }
            .combinedClickable(
                onClick = { position[0]?.let(onClick) },
                onLongClick = { position[0]?.let(onLongClick) },
            )
    ) {
        Row(
            Modifier.fillMaxSize()
                .padding(
                    start = if (overdue) 20.dp else 12.dp,
                    end = 12.dp,
                    top = 6.dp,
                    bottom = 6.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                task.title,
                Modifier.weight(1f),
                style = titleStyle,
                maxLines = lines,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (task.isToday)
                    Icon(
                        vectorResource(Res.drawable.light_mode),
                        contentDescription = "On Today",
                        modifier =
                            Modifier.padding(end = if (meta != null) 3.dp else 0.dp).size(12.dp),
                        tint = GlassTokens.SecondaryText,
                    )
                if (meta != null)
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                        color = GlassTokens.SecondaryText,
                        maxLines = 1,
                    )
            }
        }
    }
}
