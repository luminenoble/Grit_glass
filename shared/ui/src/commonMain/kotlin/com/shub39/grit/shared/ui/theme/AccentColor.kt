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

import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.text.TextStyle
import com.shub39.grit.core.tasks.titleColorStops

/** Max relative luminance for user-chosen text colours on warm beige paper (~4.5:1 on #F0EBE2). */
private const val MAX_PAPER_TEXT_LUMINANCE = 0.145f

/**
 * Darkens overly light colours in linear RGB so user-chosen [titleColor] values remain legible on
 * warm beige paper without altering what is stored.
 */
fun Color.ensureReadableOnPaper(maxLuminance: Float = MAX_PAPER_TEXT_LUMINANCE): Color {
    val linear = convert(ColorSpaces.LinearSrgb)
    val lum = 0.2126f * linear.red + 0.7152f * linear.green + 0.0722f * linear.blue
    if (lum <= maxLuminance) return this
    val factor = (maxLuminance / lum).coerceIn(0f, 1f)
    return Color(
            red = linear.red * factor,
            green = linear.green * factor,
            blue = linear.blue * factor,
            alpha = linear.alpha,
            colorSpace = ColorSpaces.LinearSrgb,
        )
        .convert(ColorSpaces.Srgb)
}

/** Stops of a stored title colour (one for a solid colour), or null when unset or malformed. */
fun parseTitleColors(value: String?): List<Color>? =
    titleColorStops(value)?.map { Color(0xFF000000 or it.toLong()) }

/** Stops clamped for text contrast on warm paper, or null when unset or malformed. */
fun readableTitleColors(value: String?): List<Color>? =
    parseTitleColors(value)?.map { it.ensureReadableOnPaper() }

/** The solid colour, or the first stop of a gradient. */
fun parseAccentColor(value: String?): Color? = parseTitleColors(value)?.first()

fun Color.toHexString(): String =
    "#" + (toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

/** Stored form of [colors]: "#RRGGBB", or a comma-separated gradient. */
fun titleColorValue(colors: List<Color>): String = colors.joinToString(",") { it.toHexString() }

/** Selected text colour as stored; a gradient's first stop. */
fun titleColor(value: String?, default: Color = GritBaseColors.onSurface): Color =
    parseAccentColor(value) ?: default

/** Left-to-right brush for the stored colour; a solid colour for one stop or none. */
fun titleBrush(value: String?, default: Color = GritBaseColors.onSurface): Brush {
    val colors = parseTitleColors(value) ?: return SolidColor(default)
    return if (colors.size == 1) SolidColor(colors[0]) else Brush.horizontalGradient(colors)
}

/**
 * [this] drawn in the stored title colour (contrast-protected against warm paper): a solid colour,
 * or a left-to-right gradient across the text. A gradient brush wins over any `color` passed to
 * Text alongside the style.
 */
fun TextStyle.withTitleColor(
    value: String?,
    default: Color = GritBaseColors.onSurface,
    alpha: Float = 1f,
): TextStyle {
    val colors = readableTitleColors(value)
    return if (colors == null || colors.size == 1) {
        val c = colors?.first() ?: default
        copy(color = c.copy(alpha = c.alpha * alpha))
    } else copy(brush = Brush.horizontalGradient(colors), alpha = alpha)
}
