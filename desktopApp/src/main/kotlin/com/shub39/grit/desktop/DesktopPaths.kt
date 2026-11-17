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

import java.io.File

/**
 * Where the desktop app keeps its data: `%APPDATA%\GritGlass` on Windows, `$XDG_DATA_HOME/GritGlass`
 * (or `~/.local/share/GritGlass`) elsewhere. `GRIT_GLASS_HOME` overrides it, for tests and trials.
 */
class DesktopPaths(val root: File) {
    /** The library, as a Grit Glass v2 backup. */
    val tasks = File(root, "tasks.json")
    val settings = File(root, "settings.json")
    /** Where the cards lie on the desk. Local only. */
    val wallLayout = File(root, "wall_layout.json")
    /** Copies saved before LAN sync or a restore replaced the library. */
    val rollback = File(root, "rollback")

    companion object {
        fun resolve(): DesktopPaths {
            val override = System.getenv("GRIT_GLASS_HOME")?.takeIf { it.isNotBlank() }
            val root =
                when {
                    override != null -> File(override)
                    System.getProperty("os.name").orEmpty().startsWith("Windows") ->
                        File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "GritGlass")
                    else ->
                        File(
                            System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                                ?: (System.getProperty("user.home") + "/.local/share"),
                            "GritGlass",
                        )
                }
            root.mkdirs()
            return DesktopPaths(root)
        }
    }
}
