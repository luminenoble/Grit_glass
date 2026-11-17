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

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import java.io.File

/**
 * Maple Mono NL NF CN, read from the Windows font folders (system-wide or per user). It covers
 * Latin and CJK in one design, so mixed Chinese and English text keeps one weight and rhythm.
 * Null when it is not installed; the app's own fonts are used then.
 */
private object MapleMono {
    private val dirs =
        listOfNotNull(
            System.getenv("WINDIR")?.let { File(it, "Fonts") },
            System.getenv("LOCALAPPDATA")?.let { File(it, "Microsoft/Windows/Fonts") },
        )

    private fun read(name: String): ByteArray? =
        dirs.map { File(it, name) }.firstOrNull { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }

    private val regular by lazy { read("MapleMonoNL-NF-CN-Regular.ttf") }
    private val boldBytes by lazy { read("MapleMonoNL-NF-CN-Bold.ttf") }

    val family: FontFamily? by lazy {
        val r = regular ?: return@lazy null
        FontFamily(
            listOfNotNull(
                Font("MapleMonoNL-NF-CN-Regular", r, FontWeight.Normal),
                boldBytes?.let { Font("MapleMonoNL-NF-CN-Bold", it, FontWeight.Bold) },
            )
        )
    }

    val bold: FontFamily? by lazy {
        val b = boldBytes ?: return@lazy family
        FontFamily(Font("MapleMonoNL-NF-CN-Bold", b, FontWeight.Bold))
    }
}

internal actual fun platformFontFamily(bold: Boolean): FontFamily? =
    if (bold) MapleMono.bold else MapleMono.family
