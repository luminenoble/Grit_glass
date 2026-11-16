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
package com.shub39.grit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.shub39.grit.shared.ui.theme.ensureReadableOnPaper
import com.shub39.grit.shared.ui.theme.readableTitleColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleColorContrastTest {
    @Test
    fun darkColoursStayUnchanged() {
        for (hex in listOf("#3C3A36", "#B4462F", "#3E5FA8", "#7A4C9A")) {
            val parsed = readableTitleColors(hex)!!
            assertEquals(1, parsed.size)
            assertTrue(parsed[0].luminance() <= 0.146f)
        }
        val dark = Color(0xFF3C3A36)
        assertEquals(dark, dark.ensureReadableOnPaper())
    }

    @Test
    fun lightColoursAreClampedForPaperContrast() {
        for (c in listOf(Color.White, Color(0xFFFFF9C4), Color(0xFFFFD1DC), Color(0xFFD08B2C))) {
            val clamped = c.ensureReadableOnPaper()
            assertTrue("expected ${clamped.luminance()} <= 0.15f", clamped.luminance() <= 0.15f)
        }
    }

    @Test
    fun nullOrMalformedReturnsNull() {
        assertNull(readableTitleColors(null))
        assertNull(readableTitleColors("not-a-color"))
    }
}
