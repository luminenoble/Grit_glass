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
package com.shub39.grit.di

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.core.DataStore
import com.shub39.grit.core.sync.LanSync
import com.shub39.grit.core.sync.RollbackStore
import com.shub39.grit.core.sync.WallLayoutStore
import com.shub39.grit.sync.ClientLanSync
import com.shub39.grit.sync.FileRollbackStore
import com.shub39.grit.sync.FileWallLayoutStore
import java.io.File
import androidx.datastore.preferences.core.Preferences
import com.shub39.grit.core.data.datastore.DatastoreFactory
import com.shub39.grit.shared.ui.di.UIModules
import com.shub39.grit.tasks.data.database.CategoryDao
import com.shub39.grit.tasks.data.database.SectionDao
import com.shub39.grit.tasks.data.database.TaskDatabase
import com.shub39.grit.tasks.data.database.TaskDbFactory
import com.shub39.grit.tasks.data.database.TasksDao
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module(includes = [UIModules::class])
@ComponentScan("com.shub39.grit")
class GritModules {
    @Single fun getTaskDb(dbFactory: TaskDbFactory): TaskDatabase = dbFactory.create().build()

    @Single fun getTaskDao(db: TaskDatabase): TasksDao = db.taskDao()

    @Single fun getSectionDao(db: TaskDatabase): SectionDao = db.sectionDao()

    @Single fun getCategoryDao(db: TaskDatabase): CategoryDao = db.categoryDao()

    @Single
    fun getDatastore(factory: DatastoreFactory): DataStore<Preferences> =
        factory.getPreferencesDataStore()

    /** The phone connects to the desktop app; it never hosts. */
    @Single
    fun getLanSync(context: Context): LanSync =
        ClientLanSync(
            runCatching {
                    Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                }
                .getOrNull()
                ?.takeIf { it.isNotBlank() } ?: "${Build.MANUFACTURER} ${Build.MODEL}"
        )

    @Single
    fun getRollbackStore(context: Context): RollbackStore =
        FileRollbackStore(File(context.filesDir, "rollback"))

    /** Where cards lie on the desk on a tablet; this device only. */
    @Single
    fun getWallLayoutStore(context: Context): WallLayoutStore =
        FileWallLayoutStore(File(context.filesDir, "wall_layout.json"))
}
