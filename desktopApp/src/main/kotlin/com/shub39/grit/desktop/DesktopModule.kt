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

import com.shub39.grit.core.billing.BillingHandler
import com.shub39.grit.core.interfaces.AlarmScheduler
import com.shub39.grit.core.interfaces.BiometricUtils
import com.shub39.grit.core.interfaces.ChangelogManager
import com.shub39.grit.core.interfaces.SettingsDatastore
import com.shub39.grit.core.interfaces.ThemeDatastore
import com.shub39.grit.core.settings.backup.ExportRepo
import com.shub39.grit.core.settings.backup.RestoreRepo
import com.shub39.grit.core.sync.LanSync
import com.shub39.grit.core.sync.RollbackStore
import com.shub39.grit.core.sync.WallLayoutStore
import com.shub39.grit.core.tasks.TaskRepo
import com.shub39.grit.shared.ui.di.UIModules
import com.shub39.grit.sync.FileRollbackStore
import com.shub39.grit.sync.FileWallLayoutStore
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(includes = [UIModules::class])
class DesktopModule {
    @Single fun paths(): DesktopPaths = DesktopPaths.resolve()

    @Single fun prefs(paths: DesktopPaths): DesktopPrefsStore = DesktopPrefsStore(paths.settings)

    @Single fun tasks(paths: DesktopPaths): TaskRepo = openTaskRepo(paths)

    @Single fun settings(prefs: DesktopPrefsStore): SettingsDatastore = DesktopSettings(prefs)

    @Single fun theme(prefs: DesktopPrefsStore): ThemeDatastore = DesktopTheme(prefs)

    @Single fun rollback(paths: DesktopPaths): RollbackStore = FileRollbackStore(paths.rollback)

    @Single fun wallLayout(paths: DesktopPaths): WallLayoutStore = FileWallLayoutStore(paths.wallLayout)

    @Single fun lanSync(repo: TaskRepo, rollback: RollbackStore): LanSync = DesktopLanSync(repo, rollback)

    @Single fun billing(): BillingHandler = DesktopBilling()

    @Single fun alarms(): AlarmScheduler = NoAlarms()

    @Single fun biometrics(): BiometricUtils = NoBiometrics()

    @Single fun changelog(): ChangelogManager = ResourceChangelog()

    @Single fun export(repo: TaskRepo): ExportRepo = DesktopExport(repo)

    @Single fun restore(repo: TaskRepo): RestoreRepo = DesktopRestore(repo)
}
