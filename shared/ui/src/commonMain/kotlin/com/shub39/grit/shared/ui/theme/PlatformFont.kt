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

/**
 * A font the platform uses in place of the chosen one, or null to use the app's fonts. The
 * desktop uses an installed CJK-complete font so Chinese text does not fall back glyph by glyph to
 * mismatched system fonts; [bold] gives the family for emphasis titles.
 */
internal expect fun platformFontFamily(bold: Boolean = false): FontFamily?
