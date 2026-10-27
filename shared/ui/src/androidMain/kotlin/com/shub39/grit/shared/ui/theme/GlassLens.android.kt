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

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.graphics.RenderEffect as PlatformRenderEffect

/**
 * Lens over the blurred backdrop, after the SDF lens used by Kyant's AndroidLiquidGlass and the
 * shuding/rdev liquid-glass demos of Apple's material: inside a rim of width `bevel` the glass
 * surface drops off along a quarter circle, so the sample point moves inward along the rim normal
 * by `bend * (1 - sqrt(1 - x²))`, x being 1 at the edge. Red and blue are sampled a little further
 * and nearer for dispersion, colour is lifted for vibrancy, and the curved rim gathers some light.
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

internal actual fun glassLensEffect(lens: GlassLens): RenderEffect? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) lensEffect(lens) else null

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object LensShader {
    /**
     * Compiled once. Sharing it is safe: an effect keeps its own copy of the uniforms when created,
     * later uniform writes copy on write.
     */
    val shader by lazy { RuntimeShader(LENS) }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun lensEffect(lens: GlassLens): RenderEffect {
    val shader = LensShader.shader
    shader.setFloatUniform("origin", lens.left, lens.top)
    shader.setFloatUniform("shapeSize", lens.width, lens.height)
    shader.setFloatUniform("radius", lens.radius)
    shader.setFloatUniform("bevel", lens.bevel)
    shader.setFloatUniform("bend", lens.bend)
    shader.setFloatUniform("dispersion", lens.dispersion)
    shader.setFloatUniform("saturation", lens.saturation)
    shader.setFloatUniform("rimLight", lens.rimLight)
    val refract = PlatformRenderEffect.createRuntimeShaderEffect(shader, "content")
    val effect =
        if (lens.blur > 0f)
            PlatformRenderEffect.createChainEffect(
                refract,
                PlatformRenderEffect.createBlurEffect(lens.blur, lens.blur, Shader.TileMode.CLAMP),
            )
        else refract
    return effect.asComposeRenderEffect()
}
