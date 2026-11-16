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
package com.shub39.grit.shared.ui.setting.ui.section

import com.shub39.grit.shared.ui.theme.LocalFloatingBarInset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import com.shub39.grit.core.settings.ImportanceDisplay
import com.shub39.grit.core.settings.WallSettings
import com.shub39.grit.core.settings.WallTextSize
import com.shub39.grit.core.tasks.WallThresholds
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.shub39.grit.core.settings.Sections
import com.shub39.grit.core.settings.WidgetTextSize
import com.shub39.grit.shared.ui.GritPreviewWrapper
import com.shub39.grit.shared.ui.components.ExpressiveSwitch
import com.shub39.grit.shared.ui.components.detachedItemShape
import com.shub39.grit.shared.ui.components.endItemShape
import com.shub39.grit.shared.ui.components.leadingItemShape
import com.shub39.grit.shared.ui.components.GlassGroup
import com.shub39.grit.shared.ui.components.glass.GlassSegmented
import com.shub39.grit.shared.ui.components.glassItem
import com.shub39.grit.shared.ui.components.glassListItemColors
import com.shub39.grit.shared.ui.setting.SettingsAction
import com.shub39.grit.shared.ui.setting.SettingsState
import com.shub39.grit.shared.ui.setting.ui.component.LocalePickerSheet
import com.shub39.grit.shared.ui.setting.ui.component.UpdateAvailableDialog
import com.shub39.grit.shared.ui.theme.flexFontEmphasis
import com.shub39.grit.shared.ui.toStringRes
import com.shub39.grit.shared.ui.update.AppVersionHolder
import com.shub39.grit.shared.ui.update.UPDATE_REPO
import com.shub39.grit.shared.ui.update.UpdateCheckState
import grit.shared.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

/** Root settings page all roads start from here */
@Composable
fun RootPage(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    onNavigateToLookAndFeel: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    onNavigateToChangelog: () -> Unit,
    onNavigateToAppInfo: () -> Unit,
) {
    var showLocalePicker by remember { mutableStateOf(false) }

    val uriHandler = LocalUriHandler.current
    val appVersion = AppVersionHolder.versionName.ifBlank { state.currentVersion.orEmpty() }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Column(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).fillMaxSize()) {
        LargeFlexibleTopAppBar(
            scrollBehavior = scrollBehavior,
            title = {
                Text(text = stringResource(Res.string.settings), fontFamily = flexFontEmphasis())
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Runs on beneath the floating nav bar.
            contentPadding =
                PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 16.dp,
                    bottom = LocalFloatingBarInset.current + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Grit Plus
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(Res.string.grit_plus)) },
                    colors = glassListItemColors(),
                    modifier =
                        Modifier.glassItem(detachedItemShape()).clickable { onNavigateToPaywall() },
                    trailingContent = {
                        Icon(
                            imageVector = vectorResource(Res.drawable.arrow_forward),
                            contentDescription = "Grit Plus",
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = vectorResource(Res.drawable.grit_icon),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }

            item {
                ListItem(
                    colors = glassListItemColors(),
                    leadingContent = {
                        Icon(
                            imageVector = vectorResource(Res.drawable.github),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    supportingContent = {
                        Text(
                            text =
                                when (val check = state.updateCheck) {
                                    UpdateCheckState.Idle -> "从 $UPDATE_REPO 检查新版本"
                                    UpdateCheckState.Checking -> "检查中…"
                                    is UpdateCheckState.UpToDate ->
                                        "已是最新版本 ${check.versionName}".trim()
                                    UpdateCheckState.NoReleases -> "$UPDATE_REPO 暂无已发布版本"
                                    is UpdateCheckState.Available ->
                                        "发现新版本 ${check.release.versionName}"
                                    is UpdateCheckState.Failed -> "检查失败：${check.message}"
                                }
                        )
                    },
                    trailingContent = {
                        if (state.updateCheck is UpdateCheckState.Checking) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(
                                imageVector = vectorResource(Res.drawable.arrow_forward),
                                contentDescription = null,
                            )
                        }
                    },
                    headlineContent = { Text(text = "检查更新") },
                    modifier =
                        Modifier.glassItem(detachedItemShape()).clickable(
                            enabled = state.updateCheck !is UpdateCheckState.Checking
                        ) {
                            onAction(SettingsAction.OnCheckForUpdates)
                        },
                )
            }

            // General settings
            item {
                GlassGroup {
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.pause_notifications)) },
                        supportingContent = {
                            Text(stringResource(Res.string.pause_notifications_desc))
                        },
                        trailingContent = {
                            ExpressiveSwitch(
                                checked = state.pauseNotifications,
                                onCheckedChange = {
                                    onAction(SettingsAction.ChangePauseNotifications(it))
                                },
                            )
                        },
                        colors = glassListItemColors(),
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.widget_text_size)) },
                        supportingContent = {
                            Column {
                                Text(stringResource(Res.string.widget_text_size_desc))
                                val labels =
                                    WidgetTextSize.entries.associateWith {
                                        stringResource(it.toStringRes())
                                    }
                                GlassSegmented(
                                    options = WidgetTextSize.entries,
                                    selected = state.widgetTextSize,
                                    onSelect = { onAction(SettingsAction.ChangeWidgetTextSize(it)) },
                                    label = { labels.getValue(it) },
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        },
                        colors = glassListItemColors(),
                    )
                    ListItem(
                        headlineContent = { Text("启动页面") },
                        supportingContent = {
                            GlassSegmented(
                                options = Sections.entries,
                                selected = state.startingPage,
                                onSelect = { onAction(SettingsAction.ChangeStartingPage(it)) },
                                label = { page ->
                                    when (page) {
                                        Sections.Tasks -> "Task"
                                        Sections.Overview -> "总览"
                                        Sections.Settings -> "Setting"
                                    }
                                },
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        },
                        colors = glassListItemColors(),
                    )
                    if (state.isBiometricLockAvailable)
                        ListItem(
                            headlineContent = { Text(stringResource(Res.string.biometric_lock)) },
                            trailingContent = {
                                ExpressiveSwitch(
                                    checked = state.isBiometricLockOn == true,
                                    onCheckedChange = {
                                        onAction(SettingsAction.ChangeBiometricLock(it))
                                    },
                                )
                            },
                            colors = glassListItemColors(),
                        )
                    ListItem(
                        headlineContent = { Text(stringResource(Res.string.use_24Hr)) },
                        supportingContent = { Text(stringResource(Res.string.use_24Hr_desc)) },
                        trailingContent = {
                            ExpressiveSwitch(
                                checked = state.is24Hr,
                                onCheckedChange = { onAction(SettingsAction.ChangeIs24Hr(it)) },
                            )
                        },
                        colors = glassListItemColors(),
                    )
                }
            }

            // The task wall
            item { WallSettingsGroup(state.wall) { onAction(SettingsAction.ChangeWallSettings(it)) } }

            // look and feel customizations
            item {
                GlassGroup {
                    ListItem(
                        modifier =
                            Modifier.clip(leadingItemShape()).clickable {
                                onNavigateToLookAndFeel()
                            },
                        headlineContent = { Text(text = stringResource(Res.string.look_and_feel)) },
                        supportingContent = {
                            Text(text = stringResource(Res.string.look_and_feel_desc))
                        },
                        trailingContent = {
                            Icon(
                                imageVector = vectorResource(Res.drawable.arrow_forward),
                                contentDescription = "Navigate",
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = vectorResource(Res.drawable.palette),
                                contentDescription = "Navigate",
                            )
                        },
                        colors = glassListItemColors(),
                    )

                    ListItem(
                        modifier = Modifier.clip(endItemShape()).clickable { onNavigateToBackup() },
                        colors = glassListItemColors(),
                        headlineContent = { Text(text = stringResource(Res.string.backup)) },
                        supportingContent = { Text(text = stringResource(Res.string.backup_desc)) },
                        trailingContent = {
                            Icon(
                                imageVector = vectorResource(Res.drawable.arrow_forward),
                                contentDescription = "Navigate",
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = vectorResource(Res.drawable.download),
                                contentDescription = "Backup",
                            )
                        },
                    )
                }
            }

            // Changelogs
            item {
                GlassGroup {
                    ListItem(
                        colors = glassListItemColors(),
                        leadingContent = {
                            Icon(
                                imageVector = vectorResource(Res.drawable.info),
                                contentDescription = null,
                            )
                        },
                        supportingContent = {
                            Text(text = "Grit Glass ${appVersion.ifBlank { "x.x.x" }}")
                        },
                        trailingContent = {
                            Icon(
                                imageVector = vectorResource(Res.drawable.arrow_forward),
                                contentDescription = "Navigate",
                            )
                        },
                        headlineContent = { Text(text = stringResource(Res.string.about)) },
                        modifier =
                            Modifier.clip(leadingItemShape()).clickable { onNavigateToAppInfo() },
                    )

                    ListItem(
                        colors = glassListItemColors(),
                        leadingContent = {
                            Icon(
                                imageVector = vectorResource(Res.drawable.check_list),
                                contentDescription = null,
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = vectorResource(Res.drawable.arrow_forward),
                                contentDescription = "Navigate",
                            )
                        },
                        headlineContent = { Text(text = stringResource(Res.string.changelog)) },
                        modifier =
                            Modifier.clip(endItemShape()).clickable { onNavigateToChangelog() },
                    )
                }
            }

            // language picker
            languagePicker(onClick = { showLocalePicker = true })
        }

        if (showLocalePicker) {
            LocalePickerSheet(onDismissRequest = { showLocalePicker = false })
        }

        val updateCheck = state.updateCheck
        if (updateCheck is UpdateCheckState.Available) {
            UpdateAvailableDialog(
                release = updateCheck.release,
                currentVersion = appVersion,
                onDownload = {
                    uriHandler.openUri(updateCheck.release.pageUrl)
                    onAction(SettingsAction.OnDismissUpdateResult)
                },
                onDismissRequest = { onAction(SettingsAction.OnDismissUpdateResult) },
            )
        }
    }
}

/** The three day counts as typed, or null while any of them is not a number. */
private fun thresholdsOf(days: List<String>): WallThresholds? {
    val (a, b, c) = days.map { it.toIntOrNull() ?: return null }
    return WallThresholds(a, b, c)
}

/** How the task wall sizes and shades its cards. */
@Composable
private fun WallSettingsGroup(wall: WallSettings, onChange: (WallSettings) -> Unit) {
    GlassGroup {
        ListItem(
            headlineContent = { Text("任务墙卡片大小") },
            supportingContent = {
                Column {
                    Text("离 DDL 越近，卡片越大。超过最后一档或没有 DDL 的任务用最小的卡片。")
                    // Typing passes through states that are not valid yet (a ≥ b); those stay in
                    // the fields and are saved once the three days rise again.
                    var days by
                        remember(wall.thresholds) {
                            mutableStateOf(wall.thresholds.run { listOf(a, b, c) }.map { "$it" })
                        }
                    val typed = thresholdsOf(days)
                    Row(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("最大", "大", "中").forEachIndexed { i, size ->
                            OutlinedTextField(
                                value = days[i],
                                onValueChange = { text ->
                                    days =
                                        days.toMutableList().also {
                                            it[i] = text.filter(Char::isDigit).take(4)
                                        }
                                    val next = thresholdsOf(days)
                                    if (next != null && next.isValid && next != wall.thresholds)
                                        onChange(wall.copy(thresholds = next))
                                },
                                label = { Text(size) },
                                suffix = { Text("天内") },
                                singleLine = true,
                                isError = typed?.isValid != true,
                                keyboardOptions =
                                    KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (typed?.isValid != true)
                        Text(
                            "三个天数都要大于 0，并且从左到右越来越大。改对之后才会保存。",
                            Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                }
            },
            colors = glassListItemColors(),
        )
        ListItem(
            headlineContent = { Text("任务墙字号") },
            supportingContent = {
                val labels =
                    mapOf(
                        WallTextSize.SMALL to stringResource(Res.string.text_size_small),
                        WallTextSize.MEDIUM to stringResource(Res.string.text_size_medium),
                        WallTextSize.LARGE to stringResource(Res.string.text_size_large),
                    )
                GlassSegmented(
                    options = WallTextSize.entries,
                    selected = wall.textSize,
                    onSelect = { onChange(wall.copy(textSize = it)) },
                    label = { labels.getValue(it) },
                    modifier = Modifier.padding(top = 6.dp),
                )
            },
            colors = glassListItemColors(),
        )
        ListItem(
            headlineContent = { Text("重要度显示") },
            supportingContent = {
                Column {
                    Text("优先级越低，卡片上这些部分越淡。")
                    GlassSegmented(
                        options = ImportanceDisplay.entries,
                        selected = wall.importance,
                        onSelect = { onChange(wall.copy(importance = it)) },
                        label = {
                            when (it) {
                                ImportanceDisplay.TITLE_AND_BORDER -> "标题与边框"
                                ImportanceDisplay.WITH_BACKGROUND -> "标题、边框与底色"
                            }
                        },
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            },
            colors = glassListItemColors(),
        )
    }
}

expect fun LazyListScope.languagePicker(onClick: () -> Unit)

@PreviewWrapper(GritPreviewWrapper::class)
@PreviewLightDark
@Composable
private fun Preview() {
    RootPage(
        state = SettingsState(),
        onAction = {},
        onNavigateToLookAndFeel = {},
        onNavigateToBackup = {},
        onNavigateToPaywall = {},
        onNavigateToChangelog = {},
        onNavigateToAppInfo = {},
    )
}
