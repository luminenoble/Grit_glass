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
package com.shub39.grit.tasks.data.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * 1 → 2 (Grit Glass 1.1.0). Written by hand because existing values are converted, which an auto
 * migration cannot do:
 * - tasks gain `deadline`, `priority` (normal), `lastTouchedAt` (their `updatedAt`), `completedAt`
 *   (`updatedAt` for completed tasks), `hiddenUntil` and `isSomeday`;
 * - sections replace the `completedOn` date with the `isCompleted` flag;
 * - categories lose `hideCompleted`.
 *
 * Every table is rebuilt and renamed into place. Room turns foreign keys on only after migrating,
 * so dropping a parent table here does not cascade into its children.
 */
val Migration1To2 =
    object : Migration(1, 2) {
        override suspend fun migrate(connection: SQLiteConnection) {
            listOf(
                    "CREATE TABLE `_new_categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `titleColor` TEXT, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL)",
                    "INSERT INTO `_new_categories` (`id`, `name`, `description`, `orderIndex`, `titleColor`, `createdAt`, `updatedAt`) SELECT `id`, `name`, `description`, `orderIndex`, `titleColor`, `createdAt`, `updatedAt` FROM `categories`",
                    "DROP TABLE `categories`",
                    "ALTER TABLE `_new_categories` RENAME TO `categories`",
                    "CREATE INDEX IF NOT EXISTS `index_categories_orderIndex` ON `categories` (`orderIndex`)",
                    "CREATE TABLE `_new_tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `categoryId` INTEGER NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `titleColor` TEXT, `isCompleted` INTEGER NOT NULL, `isToday` INTEGER NOT NULL, `reminder` TEXT, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `deadline` TEXT, `priority` INTEGER NOT NULL, `lastTouchedAt` TEXT NOT NULL, `completedAt` TEXT, `hiddenUntil` TEXT, `isSomeday` INTEGER NOT NULL, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                    "INSERT INTO `_new_tasks` (`id`, `categoryId`, `title`, `description`, `orderIndex`, `titleColor`, `isCompleted`, `isToday`, `reminder`, `createdAt`, `updatedAt`, `deadline`, `priority`, `lastTouchedAt`, `completedAt`, `hiddenUntil`, `isSomeday`) SELECT `id`, `categoryId`, `title`, `description`, `orderIndex`, `titleColor`, `isCompleted`, `isToday`, `reminder`, `createdAt`, `updatedAt`, NULL, 1, `updatedAt`, CASE WHEN `isCompleted` THEN `updatedAt` END, NULL, 0 FROM `tasks`",
                    "DROP TABLE `tasks`",
                    "ALTER TABLE `_new_tasks` RENAME TO `tasks`",
                    "CREATE INDEX IF NOT EXISTS `index_tasks_categoryId_orderIndex` ON `tasks` (`categoryId`, `orderIndex`)",
                    "CREATE TABLE `_new_sections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `taskId` INTEGER NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `titleColor` TEXT DEFAULT NULL, FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                    "INSERT INTO `_new_sections` (`id`, `taskId`, `title`, `content`, `orderIndex`, `isCompleted`, `createdAt`, `updatedAt`, `titleColor`) SELECT `id`, `taskId`, `title`, `content`, `orderIndex`, `completedOn` IS NOT NULL, `createdAt`, `updatedAt`, `titleColor` FROM `sections`",
                    "DROP TABLE `sections`",
                    "ALTER TABLE `_new_sections` RENAME TO `sections`",
                    "CREATE INDEX IF NOT EXISTS `index_sections_taskId_orderIndex` ON `sections` (`taskId`, `orderIndex`)",
                )
                .forEach { connection.execSQL(it) }
        }
    }
