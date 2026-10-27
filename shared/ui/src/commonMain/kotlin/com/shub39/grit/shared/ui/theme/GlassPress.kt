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
package com.shub39.grit.shared.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.RippleConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import androidx.compose.material.ripple.RippleAlpha
import kotlinx.coroutines.launch

/**
 * Pressing glass fills it with light from the finger outward, beneath the label; it never darkens.
 * Default indication for `clickable` in [GritTheme]; place a `clip` before the clickable to keep
 * the light inside the shape. Keyboard focus shows as an ink ring.
 */
object GlassPressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        GlassPressNode(interactionSource)

    override fun equals(other: Any?) = other === this

    override fun hashCode() = 0x61A55
}

/** Material components draw their own ripple; on glass it is light, never a grey state layer. */
val GlassRipple =
    RippleConfiguration(
        color = Color.White,
        rippleAlpha =
            RippleAlpha(
                draggedAlpha = 0.32f,
                focusedAlpha = 0.32f,
                hoveredAlpha = 0.16f,
                pressedAlpha = 0.48f,
            ),
    )

private class GlassPressNode(private val source: InteractionSource) :
    Modifier.Node(), DrawModifierNode {
    private val light = Animatable(0f)
    private var origin = Offset.Unspecified
    private var focused = false

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        origin = interaction.pressPosition
                        launch { light.animateTo(1f, tween(90)) { invalidateDraw() } }
                    }
                    is PressInteraction.Release,
                    is PressInteraction.Cancel ->
                        launch { light.animateTo(0f, tween(360)) { invalidateDraw() } }
                    is FocusInteraction.Focus -> {
                        focused = true
                        invalidateDraw()
                    }
                    is FocusInteraction.Unfocus -> {
                        focused = false
                        invalidateDraw()
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        // Under the label and icon: the glass lights up, the ink stays crisp.
        val a = light.value
        if (a > 0f) {
            val center = if (origin.isSpecified) origin else Offset(size.width / 2f, size.height / 2f)
            val reach = maxOf(size.width, size.height) * 1.1f
            clipRect {
                drawRect(
                    Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.62f * a),
                        0.45f to Color.White.copy(alpha = 0.30f * a),
                        1f to Color.White.copy(alpha = 0.10f * a),
                        center = center,
                        radius = reach,
                    )
                )
            }
        }
        drawContent()
        if (focused) {
            val w = 2.dp.toPx()
            drawRoundRect(
                GlassTokens.SecondaryText,
                topLeft = Offset(w / 2f, w / 2f),
                size = size.copy(width = size.width - w, height = size.height - w),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(minOf(size.width, size.height) / 2f),
                style = Stroke(w),
            )
        }
    }
}
