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

import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * The same lens as on Android 13+, as Skia SkSL on the desktop: inside a rim of width `bevel` the
 * glass surface drops off along a quarter circle, so the sample point moves inward along the rim
 * normal by `bend * (1 - sqrt(1 - x²))`. Red and blue are sampled a little further and nearer for
 * dispersion, colour is lifted for vibrancy, and the curved rim gathers some light.
 */
private const val LENS =
    """
uniform shader content;
uniform float2 origin;
uniform float2 shapeSize;
uniform float radius;
uniform float bevel;
uniform float bend;
uniform float dispersion;
uniform float saturation;
uniform float rimLight;

float roundRectDistance(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float2 rimNormal(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + r;
    float2 n = (q.x > 0.0 && q.y > 0.0) ? normalize(q)
        : (q.x > q.y ? float2(1.0, 0.0) : float2(0.0, 1.0));
    return float2(p.x < 0.0 ? -n.x : n.x, p.y < 0.0 ? -n.y : n.y);
}

half4 vibrant(half4 c) {
    half l = dot(c.rgb, half3(0.2126, 0.7152, 0.0722));
    return half4(clamp(mix(half3(l), c.rgb, half(saturation)), half3(0.0), half3(c.a)), c.a);
}

half4 main(float2 coord) {
    float2 halfSize = shapeSize * 0.5;
    float2 p = coord - origin - halfSize;
    float inset = -roundRectDistance(p, halfSize, radius);
    if (inset <= 0.0 || inset >= bevel) return vibrant(content.eval(coord));
    float x = 1.0 - inset / bevel;
    float lift = 1.0 - sqrt(max(1.0 - x * x, 0.0));
    float2 n = rimNormal(p, halfSize, min(radius * 1.5, min(halfSize.x, halfSize.y)));
    float2 shift = n * (lift * bend);
    half4 g = content.eval(coord - shift);
    half4 c = g;
    if (dispersion > 0.0) {
        half4 r = content.eval(coord - shift * (1.0 + dispersion));
        half4 b = content.eval(coord - shift * (1.0 - dispersion));
        c = half4(r.r, g.g, b.b, g.a);
    }
    c = vibrant(c);
    c.rgb = mix(c.rgb, half3(c.a), half(lift * rimLight));
    return c;
}
"""

/** Compiled once; null if this Skia cannot compile it, and glass falls back to a plain blur. */
private val lensEffect: RuntimeEffect? by lazy { runCatching { RuntimeEffect.makeForShader(LENS) }.getOrNull() }

internal actual fun glassLensEffect(lens: GlassLens): RenderEffect? {
    val effect = lensEffect ?: return null
    return runCatching {
            val builder = RuntimeShaderBuilder(effect)
            builder.uniform("origin", lens.left, lens.top)
            builder.uniform("shapeSize", lens.width, lens.height)
            builder.uniform("radius", lens.radius)
            builder.uniform("bevel", lens.bevel)
            builder.uniform("bend", lens.bend)
            builder.uniform("dispersion", lens.dispersion)
            builder.uniform("saturation", lens.saturation)
            builder.uniform("rimLight", lens.rimLight)
            // Blur first, then bend the blurred backdrop, as the Android chain does. The blur
            // radius becomes a Gaussian sigma the way Compose converts it.
            val blur =
                if (lens.blur > 0f) {
                    val sigma = lens.blur * 0.57735f + 0.5f
                    ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP)
                } else null
            ImageFilter.makeRuntimeShader(builder, "content", blur).asComposeRenderEffect()
        }
        .getOrNull()
}
