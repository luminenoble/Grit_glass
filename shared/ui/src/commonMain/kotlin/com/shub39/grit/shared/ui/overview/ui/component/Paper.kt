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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shub39.grit.core.tasks.Urgency
import com.shub39.grit.shared.ui.theme.GlassShapes
import com.shub39.grit.shared.ui.theme.GlassTokens

/**
 * Paper tones for how close a deadline is: plain paper without one, then warmer and deeper as it
 * nears, ending in a caramel wash once it is due. Dark text keeps well above 4.5:1 on all of them.
 */
object PaperTones {
    val None = Color(0xFFFBF8F3)
    val Ahead = Color(0xFFF8F1E6)
    val Upcoming = Color(0xFFF3E7D3)
    val Soon = Color(0xFFF0DCBC)
    val Due = Color(0xFFEACBA9)
    /** The mark on a card whose deadline has passed: brick, not alarm red. */
    val PassedMark = GlassTokens.Danger

    fun of(urgency: Urgency): Color =
        when (urgency) {
            Urgency.NONE -> None
            Urgency.AHEAD -> Ahead
            Urgency.UPCOMING -> Upcoming
            Urgency.SOON -> Soon
            Urgency.DUE -> Due
        }
}

/**
 * Tactile paper: a warm umber shadow outside, a lit bevel at the top left and a faint dark lip at
 * the bottom inside [inset] (the border width, if any). [high] raises it further; [lift] (0–1)
 * raises it again while it is hovered or held. A [sunk] card is recessed instead: no outer
 * shadow, an inner shadow under its top edge.
 */
fun Modifier.paperElevation(
    shape: Shape,
    corner: Dp,
    high: Boolean,
    sunk: Boolean,
    inset: Dp = 0.dp,
    lift: Float = 0f,
): Modifier {
    val shadowed =
        if (sunk) this
        else
            shadow(
                elevation = (if (high) 6.dp else 2.dp) + 10.dp * lift,
                shape = shape,
                clip = false,
                ambientColor = GlassTokens.Shadow.copy(alpha = if (high) 0.36f else 0.18f),
                spotColor = GlassTokens.Shadow.copy(alpha = (if (high) 0.56f else 0.30f) + 0.2f * lift),
            )
    return shadowed.drawBehind {
        val borderPx = inset.toPx()
        val cornerPx = corner.toPx()
        fun rim(width: Float, brush: Brush) {
            val at = borderPx + width / 2f
            drawRoundRect(
                brush,
                topLeft = Offset(at, at),
                size = Size(size.width - at * 2f, size.height - at * 2f),
                cornerRadius = CornerRadius((cornerPx - at).coerceAtLeast(0f)),
                style = Stroke(width),
            )
        }
        if (sunk) {
            drawRect(
                Brush.verticalGradient(
                    0f to GlassTokens.Shadow.copy(alpha = 0.16f),
                    1f to Color.Transparent,
                    startY = borderPx,
                    endY = borderPx + 10.dp.toPx(),
                )
            )
            rim(
                1.dp.toPx(),
                Brush.verticalGradient(
                    0f to GlassTokens.Shadow.copy(alpha = 0.22f),
                    0.35f to Color.Transparent,
                ),
            )
            rim(
                0.5.dp.toPx(),
                Brush.verticalGradient(0.75f to Color.Transparent, 1f to Color.White.copy(alpha = 0.32f)),
            )
        } else {
            rim(
                (if (high) 1.dp else 0.75.dp).toPx(),
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = if (high) 0.85f else 0.58f),
                    0.45f to Color.White.copy(alpha = if (high) 0.26f else 0.14f),
                    0.75f to Color.Transparent,
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
            rim(
                (if (high) 0.75.dp else 0.5.dp).toPx(),
                Brush.verticalGradient(
                    0.65f to Color.Transparent,
                    1f to GlassTokens.Shadow.copy(alpha = if (high) 0.14f else 0.08f),
                ),
            )
        }
    }
}

/** A small choice set into a paper card: outlined, not glass, since it is part of the content. */
@Composable
fun PaperChip(text: String, ink: Color = GlassTokens.SecondaryText, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 6.dp)
            .height(30.dp)
            .clip(GlassShapes.Capsule)
            .background(GlassTokens.Shadow.copy(alpha = 0.07f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
            color = ink,
            maxLines = 1,
        )
    }
}
