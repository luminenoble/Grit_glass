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
package com.shub39.grit.core.settings

import com.shub39.grit.core.tasks.WallThresholds

/** Title size on the task wall; card cells keep their size. */
enum class WallTextSize {
    SMALL,
    MEDIUM,
    LARGE,
}

/** What fades with a task's priority on the wall. */
enum class ImportanceDisplay {
    /** Title and category border; every card is plain paper. */
    TITLE_AND_BORDER,
    /** As above, and the paper is washed with the category colour. */
    WITH_BACKGROUND,
}

data class WallSettings(
    val thresholds: WallThresholds = WallThresholds(),
    val textSize: WallTextSize = WallTextSize.MEDIUM,
    val importance: ImportanceDisplay = ImportanceDisplay.TITLE_AND_BORDER,
)
