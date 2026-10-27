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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shub39.grit.shared.ui.blurPossible
import kotlin.math.ceil
import kotlin.math.min

/**
 * Liquid glass material, see docs/项目概览.md §4.
 *
 * Glass is the control layer floating above paper content; content itself stays opaque. A glass
 * surface is described by light rather than fill:
 * 1. the backdrop seen through it, slightly blurred, more saturated and bent inward along a curved
 *    rim with a little colour dispersion ([GlassLens], Android 13+), or only blurred (Android 12);
 * 2. a thin warm body tint;
 * 3. a specular rim brightest towards the top left, and light gathering along the inner bottom;
 * 4. a soft umber shadow drawn only outside the shape, so it never greys the glass from beneath.
 * Without a backdrop the remaining layers still read as glass over the paper.
 */
enum class GlassTier {
    /** Floating controls: nav pill, primary action, icon buttons, chips, trays, toasts. */
    Thin,
    /** Menus, popovers and scrolled top bars, where text sits on the glass. */
    Regular,
    /** Dialogs and sheets. */
    Thick,
    /** Content such as cards and settings rows: opaque paper, keeps only the optical edge. */
    Content,
}

/** [Reduced] makes every tier opaque and disables backdrop blur ("降低透明度"). */
enum class GlassMode {
    Standard,
    Reduced,
}

@Immutable
data class GlassSpec(
    val tint: Color,
    /** Body tint over a sampled backdrop. */
    val tintAlpha: Float,
    /** Body tint when nothing is sampled and the paper shows through as is. */
    val fallbackAlpha: Float,
    val blur: Dp,
    /** Width of the curved rim that bends the backdrop; 0 for flat glass. */
    val bevel: Dp,
    /** Colour split across the rim, 0..1. */
    val dispersion: Float,
    val rimBright: Float,
    val rimFaint: Float,
    /** Light gathered along the inner bottom edge, as through a lens. */
    val caustic: Float,
    val elevation: Dp,
    val shadowAlpha: Float,
)

object GlassTokens {
    /** Warm paper white, a step lighter than the beige surfaces. */
    val Tint = Color(0xFFFBF8F3)
    val ThickTint = Color(0xFFF2EDE5)
    /** Warm umber shadow; grey shadows look dirty on beige. */
    val Shadow = Color(0xFF5B4B3A)
    val Hairline = Color(0xFF5B554B).copy(alpha = 0.10f)
    /** Secondary text on glass; onSurfaceVariant falls short of 4.5:1 over beige glass. */
    val SecondaryText = Color(0xFF57534B)
    /** Destructive ink on clear glass. */
    val Danger = Color(0xFF9A3B30)
    /** A selected lens: denser and brighter than the glass it sits in, still see-through. */
    val Raised = Color(0xFFFFFDF9)
    const val RaisedAlpha = 0.62f
    /** Finished content settles into a deeper beige, as in the widget's done rows. */
    val Settled = Color(0xFFE3DCCE)
    /** Done marks: a muted olive that reads on beige without shouting. */
    val Done = Color(0xFF66704F)

    fun spec(tier: GlassTier): GlassSpec =
        when (tier) {
            GlassTier.Thin ->
                GlassSpec(Tint, 0.08f, 0.36f, 1.5.dp, 14.dp, 0.35f, 1f, 0.12f, 0.12f, 10.dp, 0.16f)
            GlassTier.Regular ->
                GlassSpec(Tint, 0.55f, 0.82f, 18.dp, 10.dp, 0f, 0.80f, 0.12f, 0.10f, 12.dp, 0.14f)
            GlassTier.Thick ->
                GlassSpec(ThickTint, 0.82f, 0.95f, 30.dp, 0.dp, 0f, 0.70f, 0.10f, 0.06f, 18.dp, 0.16f)
            GlassTier.Content ->
                GlassSpec(
                    GritBaseColors.surface,
                    1f,
                    1f,
                    0.dp,
                    0.dp,
                    0f,
                    0.55f,
                    0.06f,
                    0f,
                    3.dp,
                    0.08f,
                )
        }
}

/** One size for every glass control, one for the primary action. */
object GlassMetrics {
    /** Icon buttons, text buttons, chips and nav items. */
    val Control = 44.dp
    /** The primary floating action. */
    val Primary = 56.dp
    /** Gap between a glass tray's edge and the controls set in it. */
    val TrayInset = 6.dp
    val Icon = 22.dp
}

val LocalGlassMode = staticCompositionLocalOf { GlassMode.Standard }

/** Height of the floating navigation layer content may scroll beneath; 0 when there is none. */
val LocalFloatingBarInset = compositionLocalOf { 0.dp }

object GlassShapes {
    val Capsule = RoundedCornerShape(percent = 50)
    val Toolbar = RoundedCornerShape(22.dp)
    val Panel = RoundedCornerShape(28.dp)
    val Card = RoundedCornerShape(16.dp)
}

/**
 * The lens of a glass surface sampling a backdrop, all lengths in pixels of the recorded backdrop
 * layer. The rim of width [bevel] follows a circular profile: the backdrop is pulled inward by up
 * to [bend] at the very edge, as a convex glass edge magnifies what lies under it, with the red and
 * blue channels displaced by ±[dispersion] of that shift.
 */
@Immutable
data class GlassLens(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val radius: Float,
    val bevel: Float,
    val bend: Float,
    val dispersion: Float,
    val saturation: Float,
    val rimLight: Float,
    val blur: Float,
)

/** [lens] as a render effect for the backdrop layer, or null where only a blur is possible. */
internal expect fun glassLensEffect(lens: GlassLens): RenderEffect?

/**
 * Content that glass surfaces may sample. The source records its drawing into a graphics layer each
 * time it draws and bumps [frame], so surfaces drawn later in the same frame see fresh content.
 */
@Stable
class GlassBackdrop {
    internal var layer: GraphicsLayer? = null
    internal var origin by mutableStateOf(Offset.Zero)
    internal var size = IntSize.Zero
    internal var frame by mutableIntStateOf(0)
}

@Composable fun rememberGlassBackdrop(): GlassBackdrop = remember { GlassBackdrop() }

/**
 * Records this node's content as the backdrop for glass surfaces drawn above it. Surfaces sampling
 * it must not be drawn inside it.
 */
@Composable
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier {
    val layer = rememberGraphicsLayer()
    backdrop.layer = layer
    return onGloballyPositioned { backdrop.origin = it.positionInRoot() }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            backdrop.size = IntSize(size.width.toInt(), size.height.toInt())
            // Must not observe its own counter: reading it here would invalidate this draw on
            // every write and redraw the source forever.
            Snapshot.withoutReadObservation { backdrop.frame += 1 }
            drawLayer(layer)
        }
}

/** Whether a backdrop blur would actually be drawn for the current mode and platform. */
@Composable
fun glassBlurActive(backdrop: GlassBackdrop?): Boolean =
    backdrop != null && LocalGlassMode.current == GlassMode.Standard && blurPossible()

/**
 * Draws the glass material of [tier] behind the content, clipped to [shape], with its shadow. A
 * [tint] replaces the tier tint (selected lenses use [GlassTokens.Raised]); [tintAlpha] overrides
 * the tier's body opacity.
 */
@Composable
fun Modifier.glassSurface(
    tier: GlassTier,
    shape: Shape,
    backdrop: GlassBackdrop? = null,
    tint: Color? = null,
    shadow: Boolean = true,
    tintAlpha: Float? = null,
): Modifier {
    val spec = GlassTokens.spec(tier)
    val reduced = LocalGlassMode.current == GlassMode.Reduced
    val sampling = glassBlurActive(backdrop) && tier != GlassTier.Content
    val alpha =
        when {
            reduced -> 1f
            tintAlpha != null -> tintAlpha
            sampling -> spec.tintAlpha
            else -> spec.fallbackAlpha
        }
    val fill = (tint ?: spec.tint).copy(alpha = alpha)
    val backdropLayer = if (sampling) rememberGraphicsLayer() else null
    // A soft shadow drawn outside the shape needs a blur; without one the platform shadow is used.
    val softShadow = shadow && spec.elevation > 0.dp && blurPossible()
    val shadowLayer = if (softShadow) rememberGraphicsLayer() else null
    var position by remember { mutableStateOf(Offset.Zero) }
    val base =
        if (shadow && spec.elevation > 0.dp && !softShadow)
            shadow(
                spec.elevation,
                shape,
                clip = false,
                ambientColor = GlassTokens.Shadow.copy(alpha = spec.shadowAlpha * 0.6f),
                spotColor = GlassTokens.Shadow.copy(alpha = spec.shadowAlpha),
            )
        else this
    return base
        .then(
            if (backdropLayer != null)
                Modifier.onGloballyPositioned { position = it.positionInRoot() }
            else Modifier
        )
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val clip = Path().apply { addOutline(outline) }
            val sheen =
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = spec.rimFaint),
                    0.4f to Color.Transparent,
                    endY = size.height,
                )
            val caustic =
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to Color.White.copy(alpha = spec.caustic),
                    endY = size.height,
                )
            val rim =
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = spec.rimBright),
                    0.45f to Color.White.copy(alpha = spec.rimFaint),
                    1f to Color.White.copy(alpha = spec.rimBright * 0.55f),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
            val rimStroke = Stroke(width = 1.2.dp.toPx() * 2f) // the outer half is clipped away
            val hairline = Stroke(width = 0.5.dp.toPx())

            val blurRadius = spec.blur.toPx()
            val margin = ceil(blurRadius).toInt()
            if (backdropLayer != null) {
                val corner =
                    when (outline) {
                        is Outline.Rectangle -> 0f
                        is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
                        is Outline.Generic -> -1f
                    }
                val bevel = min(spec.bevel.toPx(), min(size.width, size.height) * 0.42f)
                backdropLayer.renderEffect =
                    (if (corner >= 0f && bevel > 0f)
                        glassLensEffect(
                            GlassLens(
                                left = margin.toFloat(),
                                top = margin.toFloat(),
                                width = size.width,
                                height = size.height,
                                radius = min(corner, min(size.width, size.height) / 2f),
                                bevel = bevel,
                                bend = bevel * 1.25f,
                                dispersion = spec.dispersion,
                                saturation = 1.25f,
                                rimLight = 0.22f,
                                blur = blurRadius,
                            )
                        )
                    else null) ?: BlurEffect(blurRadius, blurRadius, TileMode.Clamp)
            }

            val shadowRadius = spec.elevation.toPx()
            val shadowMargin = ceil(shadowRadius * 2f).toInt()
            shadowLayer?.renderEffect = BlurEffect(shadowRadius, shadowRadius, TileMode.Decal)

            onDrawBehind {
                if (shadowLayer != null) {
                    shadowLayer.record(
                        IntSize(
                            size.width.toInt() + shadowMargin * 2,
                            size.height.toInt() + shadowMargin * 2,
                        )
                    ) {
                        translate(shadowMargin.toFloat(), shadowMargin + shadowRadius * 0.35f) {
                            drawOutline(outline, GlassTokens.Shadow.copy(alpha = spec.shadowAlpha))
                        }
                    }
                    clipPath(clip, ClipOp.Difference) {
                        translate(-shadowMargin.toFloat(), -shadowMargin.toFloat()) {
                            drawLayer(shadowLayer)
                        }
                    }
                }
                val source = backdrop?.layer
                if (backdropLayer != null && source != null) {
                    // Re-run when the source redraws.
                    backdrop.frame
                    val offset = position - backdrop.origin
                    backdropLayer.record(
                        IntSize(size.width.toInt() + margin * 2, size.height.toInt() + margin * 2)
                    ) {
                        translate(margin - offset.x, margin - offset.y) { drawLayer(source) }
                    }
                    clipPath(clip) {
                        translate(-margin.toFloat(), -margin.toFloat()) { drawLayer(backdropLayer) }
                    }
                }
                drawOutline(outline, fill)
                drawOutline(outline, sheen)
                if (spec.caustic > 0f) drawOutline(outline, caustic)
                clipPath(clip) { drawOutline(outline, rim, style = rimStroke) }
                drawOutline(outline, GlassTokens.Hairline, style = hairline)
            }
        }
}
