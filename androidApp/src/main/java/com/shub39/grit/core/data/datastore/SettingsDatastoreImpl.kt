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
package com.shub39.grit.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shub39.grit.core.interfaces.SettingsDatastore
import com.shub39.grit.core.settings.ImportanceDisplay
import com.shub39.grit.core.settings.Sections
import com.shub39.grit.core.settings.WallSettings
import com.shub39.grit.core.settings.WallTextSize
import com.shub39.grit.core.settings.WidgetTextSize
import com.shub39.grit.core.tasks.WallThresholds
import com.shub39.grit.widgets.WidgetUpdates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import org.koin.core.annotation.Single

@Single(binds = [SettingsDatastore::class])
class SettingsDatastoreImpl(
    private val context: Context,
    private val datastore: DataStore<Preferences>,
) : SettingsDatastore {

    companion object {
        private val startingSectionKey = stringPreferencesKey("starting_page")
        private val is24HrKey = booleanPreferencesKey("is_24Hr")
        private val notificationsKey = booleanPreferencesKey("notifications")
        private val biometricLockKey = booleanPreferencesKey("biometric")
        private val lastChangelogShownKey = stringPreferencesKey("last_changelog_shown")
        private val widgetTextSizeKey = stringPreferencesKey("widget_text_size")
        private val wallXlDaysKey = intPreferencesKey("wall_xl_days")
        private val wallLDaysKey = intPreferencesKey("wall_l_days")
        private val wallMDaysKey = intPreferencesKey("wall_m_days")
        private val wallTextSizeKey = stringPreferencesKey("wall_text_size")
        private val importanceDisplayKey = stringPreferencesKey("importance_display")
        private val lastOpenedOnKey = stringPreferencesKey("last_opened_on")
    }

    override fun getStartingSectionPref(): Flow<Sections> =
        datastore.data.map { pref ->
            val page = pref[startingSectionKey] ?: Sections.Tasks.name
            return@map Sections.entries.firstOrNull { it.name == page } ?: Sections.Tasks
        }

    override suspend fun setStartingPage(page: Sections) {
        datastore.edit { prefs -> prefs[startingSectionKey] = page.name }
    }

    override fun getIs24Hr(): Flow<Boolean> =
        datastore.data.map { prefs -> prefs[is24HrKey] == true }

    override suspend fun setIs24Hr(pref: Boolean) {
        datastore.edit { prefs -> prefs[is24HrKey] = pref }
    }

    override fun getNotificationsFlow(): Flow<Boolean> =
        datastore.data.map { prefs -> prefs[notificationsKey] == true }

    override suspend fun setNotifications(pref: Boolean) {
        datastore.edit { prefs -> prefs[notificationsKey] = pref }
    }

    override fun getBiometricLockPref(): Flow<Boolean> =
        datastore.data.map { prefs -> prefs[biometricLockKey] == true }

    override suspend fun setBiometricPref(pref: Boolean) {
        datastore.edit { prefs -> prefs[biometricLockKey] = pref }
    }

    override fun getLastChangelogShown(): Flow<String> =
        datastore.data.map { prefs -> prefs[lastChangelogShownKey] ?: "" }

    override suspend fun updateLastChangelogShown(version: String) {
        datastore.edit { settings -> settings[lastChangelogShownKey] = version }
    }

    override fun getWidgetTextSizeFlow(): Flow<WidgetTextSize> =
        datastore.data.map { prefs ->
            try {
                WidgetTextSize.valueOf(prefs[widgetTextSizeKey] ?: WidgetTextSize.MEDIUM.name)
            } catch (_: Exception) {
                WidgetTextSize.MEDIUM
            }
        }

    override suspend fun setWidgetTextSize(size: WidgetTextSize) {
        datastore.edit { prefs -> prefs[widgetTextSizeKey] = size.name }

        // Push the new size to every widget: a placed widget with no live Glance session
        // would otherwise keep the old text until its next data change.
        runCatching { WidgetUpdates.tasks(context) }
    }

    override fun getWallSettingsFlow(): Flow<WallSettings> =
        datastore.data.map { prefs ->
            val defaults = WallSettings()
            val stored =
                WallThresholds(
                    prefs[wallXlDaysKey] ?: defaults.thresholds.a,
                    prefs[wallLDaysKey] ?: defaults.thresholds.b,
                    prefs[wallMDaysKey] ?: defaults.thresholds.c,
                )
            WallSettings(
                thresholds = if (stored.isValid) stored else defaults.thresholds,
                textSize =
                    WallTextSize.entries.firstOrNull { it.name == prefs[wallTextSizeKey] }
                        ?: defaults.textSize,
                importance =
                    ImportanceDisplay.entries.firstOrNull {
                        it.name == prefs[importanceDisplayKey]
                    } ?: defaults.importance,
            )
        }

    override suspend fun setWallSettings(settings: WallSettings) {
        require(settings.thresholds.isValid) { "档位阈值必须是正整数，并且 a < b < c" }
        datastore.edit { prefs ->
            prefs[wallXlDaysKey] = settings.thresholds.a
            prefs[wallLDaysKey] = settings.thresholds.b
            prefs[wallMDaysKey] = settings.thresholds.c
            prefs[wallTextSizeKey] = settings.textSize.name
            prefs[importanceDisplayKey] = settings.importance.name
        }
    }

    override suspend fun getLastOpenedOn(): LocalDate? =
        datastore.data.first()[lastOpenedOnKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    override suspend fun setLastOpenedOn(date: LocalDate) {
        datastore.edit { prefs -> prefs[lastOpenedOnKey] = date.toString() }
    }
}
