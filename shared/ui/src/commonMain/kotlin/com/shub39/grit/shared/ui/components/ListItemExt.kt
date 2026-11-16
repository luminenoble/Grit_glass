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
package com.shub39.grit.shared.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shub39.grit.shared.ui.theme.GlassShapes
import com.shub39.grit.shared.ui.theme.GlassTier
import com.shub39.grit.shared.ui.theme.GlassTokens
import com.shub39.grit.shared.ui.theme.GritRadius
import com.shub39.grit.shared.ui.theme.glassSurface

@Composable
fun listItemColors(): ListItemColors {
    return ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
}

/** Rows laid on glass ([GlassGroup], [glassItem]) stay clear so the material shows through. */
@Composable
fun glassListItemColors(): ListItemColors =
    ListItemDefaults.colors(containerColor = Color.Transparent)

/** A standalone row on content-tier glass; clipped so its ripple follows [shape]. */
@Composable
fun Modifier.glassItem(shape: Shape): Modifier =
    glassSurface(GlassTier.Content, shape, shadow = false).clip(shape)

/**
 * Settings group: one content-tier glass panel with hairlines between its rows, replacing a column
 * of separately coloured rows.
 */
@Composable
fun GlassGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dividers = remember { mutableListOf<Float>() }
    val inset = with(LocalDensity.current) { 16.dp.toPx() }
    Layout(
        content = content,
        modifier =
            modifier
                .glassSurface(GlassTier.Content, GlassShapes.Card)
                .clip(GlassShapes.Card)
                .drawWithContent {
                    drawContent()
                    for (y in dividers)
                        drawLine(
                            GlassTokens.Hairline.copy(alpha = 0.18f),
                            Offset(inset, y),
                            Offset(size.width - inset, y),
                            strokeWidth = 0.5.dp.toPx(),
                        )
                },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val width = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
        layout(width, placeables.sumOf { it.height }) {
            dividers.clear()
            var y = 0
            placeables.forEachIndexed { i, placeable ->
                if (i > 0) dividers += y.toFloat()
                placeable.place(0, y)
                y += placeable.height
            }
        }
    }
}

fun leadingItemShape(
    topRadius: Dp = GritRadius.lg,
    bottomRadius: Dp = GritRadius.join,
): Shape =
    RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomEnd = bottomRadius,
        bottomStart = bottomRadius,
    )

fun middleItemShape(radius: Dp = GritRadius.join): Shape = RoundedCornerShape(radius)

fun endItemShape(
    topRadius: Dp = GritRadius.join,
    bottomRadius: Dp = GritRadius.lg,
): Shape =
    RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomEnd = bottomRadius,
        bottomStart = bottomRadius,
    )

fun detachedItemShape(radius: Dp = GritRadius.lg): Shape = RoundedCornerShape(radius)

/** Concentric item shape inside a grouped list; pass [endRadius] = [GritRadius.md] inside 28 dp dialogs. */
fun groupedItemShape(
    index: Int,
    count: Int,
    endRadius: Dp = GritRadius.lg,
    joinRadius: Dp = GritRadius.join,
): Shape =
    when {
        count <= 1 -> detachedItemShape(endRadius)
        index == 0 -> leadingItemShape(topRadius = endRadius, bottomRadius = joinRadius)
        index == count - 1 -> endItemShape(topRadius = joinRadius, bottomRadius = endRadius)
        else -> middleItemShape(joinRadius)
    }

@Composable
fun segmentedListItemShapes(
    index: Int,
    count: Int,
    singleElement: Boolean = count == 1,
    endRadius: Dp = GritRadius.md,
): ListItemShapes {
    val base = RoundedCornerShape(if (singleElement) endRadius else GritRadius.join)
    return ListItemDefaults.segmentedShapes(
        index,
        count,
        ListItemDefaults.shapes(
            shape = base,
            selectedShape = base,
            pressedShape = base,
            focusedShape = base,
            hoveredShape = base,
            draggedShape = base,
        ),
    )
}
