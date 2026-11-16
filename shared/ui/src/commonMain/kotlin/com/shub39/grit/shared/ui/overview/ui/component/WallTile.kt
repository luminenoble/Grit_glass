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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.shub39.grit.core.tasks.WallTier
import com.shub39.grit.shared.ui.theme.GlassTokens
import com.shub39.grit.shared.ui.theme.GritRadius
import com.shub39.grit.shared.ui.theme.parseTitleColors
import com.shub39.grit.shared.ui.theme.readableTitleColors
import com.shub39.grit.shared.ui.theme.withoutFontPadding
import grit.shared.ui.generated.resources.Res
import grit.shared.ui.generated.resources.light_mode
import org.jetbrains.compose.resources.vectorResource

/** The wall grid: cells of a fixed height, however large the titles are set. */
object WallMetrics {
    val Row = 48.dp
    val Gap = 8.dp
    val Margin = 16.dp
    /** Cell width on wide screens, where the wall has 8 columns of this size. */
    val Column = 76.dp
    val Label = 24.dp
}

/** How far a priority fades a card's title and border into the paper. */
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

private fun titleSize(tier: WallTier, size: WallTextSize): Float =
    when (tier) {
        WallTier.XL ->
            when (size) {
                WallTextSize.SMALL -> 16f
                WallTextSize.MEDIUM -> 18f
                WallTextSize.LARGE -> 20.5f
            }
        WallTier.L ->
            when (size) {
                WallTextSize.SMALL -> 14f
                WallTextSize.MEDIUM -> 15.5f
                WallTextSize.LARGE -> 17.5f
            }
        WallTier.M,
        WallTier.S ->
            when (size) {
                WallTextSize.SMALL -> 12f
                WallTextSize.MEDIUM -> 13f
                WallTextSize.LARGE -> 15f
            }
    }

/**
 * A task as a tactile paper card on the wall. Its size is the [tier]; its category is the border
 * colour; its priority determines border weight/fade, optional paper wash, and elevation. Task
 * title colours remain high-contrast against the paper (never faded into the background).
 *
 * [meta] is the small neutral note about time, and [corner] a second one (the date) that two-row
 * cards have room for beside their Section count. [overdue] adds a thin mark on the left; [sunk]
 * recesses the card into the deeper beige of finished things with no outer shadow. [footer] takes
 * the corner's place, for the choices on an overdue card. Taps report the card's coordinates so a
 * preview can open from where the card is.
 */
@Composable
fun WallTile(
    task: Task,
    category: Category?,
    tier: WallTier,
    settings: WallSettings,
    onClick: (LayoutCoordinates) -> Unit,
    onLongClick: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    corner: String? = null,
    overdue: Boolean = false,
    sunk: Boolean = false,
    footer: (@Composable RowScope.() -> Unit)? = null,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = if (sunk) GlassTokens.Settled else MaterialTheme.colorScheme.surface
    val fade = task.priority.fade
    val high = task.priority == Priority.HIGH
    val categoryStops =
        remember(category?.titleColor) { parseTitleColors(category?.titleColor) } ?: listOf(ink)
    val titleStops =
        remember(task.titleColor) { readableTitleColors(task.titleColor) } ?: listOf(ink)
    val border = brushOf(categoryStops.map { lerp(it, paper, fade) })
    val fill =
        if (settings.importance == ImportanceDisplay.WITH_BACKGROUND)
            brushOf(categoryStops.map { lerp(paper, it, task.priority.wash) })
        else SolidColor(paper)
    val cornerRadius = if (tier.rows == 1) GritRadius.md else GritRadius.lg
    val shape = RoundedCornerShape(cornerRadius)

    val textSize = titleSize(tier, settings.textSize)
    val titleStyle =
        MaterialTheme.typography.titleSmall
            .withoutFontPadding()
            .copy(
                fontSize = textSize.sp,
                lineHeight = (textSize * 1.3f).sp,
                letterSpacing = 0.sp,
                fontWeight =
                    when {
                        high -> FontWeight.Bold
                        tier == WallTier.XL -> FontWeight.SemiBold
                        else -> FontWeight.Medium
                    },
            )
            .let {
                if (titleStops.size == 1) it.copy(color = titleStops[0])
                else it.copy(brush = Brush.horizontalGradient(titleStops))
            }
    // Cells do not grow with the text, so the title gets as many lines as its card has room for.
    val line = with(LocalDensity.current) { (textSize * 1.3f).sp.toDp() }
    // The widest cards have room for a line of the description under the title.
    val description =
        task.description.takeIf { tier == WallTier.XL && footer == null && it.isNotBlank() }
    val lines =
        when (tier) {
            WallTier.S -> 1
            WallTier.M -> ((WallMetrics.Row - 12.dp) / line).toInt()
            WallTier.L -> (60.dp / line).toInt()
            WallTier.XL ->
                ((if (footer != null) 44.dp else if (description != null) 42.dp else 60.dp) / line)
                    .toInt()
        }.coerceAtLeast(1)

    val position = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val markInset = if (overdue) 8.dp else 0.dp
    val borderWidth = task.priority.borderWidth
    val elevatedModifier =
        if (sunk) Modifier
        else
            Modifier.shadow(
                elevation = if (high) 6.dp else 2.dp,
                shape = shape,
                clip = false,
                ambientColor = GlassTokens.Shadow.copy(alpha = if (high) 0.36f else 0.18f),
                spotColor = GlassTokens.Shadow.copy(alpha = if (high) 0.56f else 0.30f),
            )
    Box(
        modifier
            .onGloballyPositioned { position[0] = it }
            .then(elevatedModifier)
            .background(fill, shape)
            .border(borderWidth, border, shape)
            .clip(shape)
            .drawBehind {
                val borderPx = borderWidth.toPx()
                val cornerPx = cornerRadius.toPx()
                if (sunk) {
                    // Recessed paper: inner top shadow band + dark inner lip, no outer shadow.
                    drawRect(
                        Brush.verticalGradient(
                            0f to GlassTokens.Shadow.copy(alpha = 0.16f),
                            1f to Color.Transparent,
                            startY = borderPx,
                            endY = borderPx + 10.dp.toPx(),
                        )
                    )
                    val rimW = 1.dp.toPx()
                    val rimInset = borderPx + rimW / 2f
                    val rimRadius = (cornerPx - rimInset).coerceAtLeast(0f)
                    drawRoundRect(
                        Brush.verticalGradient(
                            0f to GlassTokens.Shadow.copy(alpha = 0.22f),
                            0.35f to Color.Transparent,
                        ),
                        topLeft = Offset(rimInset, rimInset),
                        size = Size(size.width - rimInset * 2f, size.height - rimInset * 2f),
                        cornerRadius = CornerRadius(rimRadius),
                        style = Stroke(rimW),
                    )
                    drawRoundRect(
                        Brush.verticalGradient(
                            0.75f to Color.Transparent,
                            1f to Color.White.copy(alpha = 0.32f),
                        ),
                        topLeft = Offset(rimInset, rimInset),
                        size = Size(size.width - rimInset * 2f, size.height - rimInset * 2f),
                        cornerRadius = CornerRadius(rimRadius),
                        style = Stroke(0.5.dp.toPx()),
                    )
                } else {
                    // Tactile elevation: top-left lit bevel + subtle dark bottom closing edge.
                    val highlightW = (if (high) 1.dp else 0.75.dp).toPx()
                    val highlightInset = borderPx + highlightW / 2f
                    val highlightRadius = (cornerPx - highlightInset).coerceAtLeast(0f)
                    drawRoundRect(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = if (high) 0.85f else 0.58f),
                            0.45f to Color.White.copy(alpha = if (high) 0.26f else 0.14f),
                            0.75f to Color.Transparent,
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                        topLeft = Offset(highlightInset, highlightInset),
                        size =
                            Size(
                                size.width - highlightInset * 2f,
                                size.height - highlightInset * 2f,
                            ),
                        cornerRadius = CornerRadius(highlightRadius),
                        style = Stroke(highlightW),
                    )
                    val bottomW = (if (high) 0.75.dp else 0.5.dp).toPx()
                    val bottomInset = borderPx + bottomW / 2f
                    val bottomRadius = (cornerPx - bottomInset).coerceAtLeast(0f)
                    drawRoundRect(
                        Brush.verticalGradient(
                            0.65f to Color.Transparent,
                            1f to GlassTokens.Shadow.copy(alpha = if (high) 0.14f else 0.08f),
                        ),
                        topLeft = Offset(bottomInset, bottomInset),
                        size =
                            Size(
                                size.width - bottomInset * 2f,
                                size.height - bottomInset * 2f,
                            ),
                        cornerRadius = CornerRadius(bottomRadius),
                        style = Stroke(bottomW),
                    )
                }
                if (overdue) {
                    val inset = 12.dp.toPx()
                    drawRoundRect(
                        GlassTokens.SecondaryText,
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
        val title: @Composable (Modifier) -> Unit = { titleModifier ->
            Text(
                task.title,
                titleModifier,
                style = titleStyle,
                maxLines = lines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val note: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (task.isToday)
                    Icon(
                        vectorResource(Res.drawable.light_mode),
                        contentDescription = "On Today",
                        modifier = Modifier.padding(end = if (meta != null) 3.dp else 0.dp).size(12.dp),
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
        when (tier) {
            WallTier.S ->
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    title(Modifier)
                    Box(Modifier.align(Alignment.End)) { note() }
                }
            WallTier.M ->
                Row(
                    Modifier.fillMaxSize()
                        .padding(start = 12.dp + markInset, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    title(Modifier.weight(1f))
                    Box(Modifier.padding(start = 8.dp)) { note() }
                }
            WallTier.L,
            WallTier.XL ->
                Column(
                    Modifier.fillMaxSize()
                        .padding(start = 14.dp + markInset, end = 14.dp, top = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        title(Modifier)
                        if (description != null)
                            Text(
                                description,
                                style =
                                    MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.sp),
                                color = GlassTokens.SecondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val progress =
                            task.sections
                                .takeIf { it.isNotEmpty() }
                                ?.let { all -> "${all.count { it.isCompleted }}/${all.size}" }
                        if (footer != null) footer()
                        else
                            Text(
                                listOfNotNull(corner, progress).joinToString("　"),
                                style =
                                    MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                                color = GlassTokens.SecondaryText,
                                maxLines = 1,
                            )
                        Spacer(Modifier.weight(1f))
                        note()
                    }
                }
        }
    }
}
