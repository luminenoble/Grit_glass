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
package com.shub39.grit.desktop

import org.junit.Assert.assertNotNull
import org.junit.Test

class GlassLensTest {
    /** The SkSL lens must compile on the desktop's Skia, or every glass falls back to a blur. */
    @Test
    fun lensShaderCompiles() {
        val file = Class.forName("com.shub39.grit.shared.ui.theme.GlassLens_jvmKt")
        val getter = file.getDeclaredMethod("getLensEffect").apply { isAccessible = true }
        assertNotNull(getter.invoke(null))
    }
}
