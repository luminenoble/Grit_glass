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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.shub39.grit.core.theme.Fonts
import com.shub39.grit.shared.ui.components.ExpressiveSwitch
import com.shub39.grit.shared.ui.components.GlassGroup
import com.shub39.grit.shared.ui.components.detachedItemShape
import com.shub39.grit.shared.ui.components.glass.GlassIconButton
import com.shub39.grit.shared.ui.components.glassItem
import com.shub39.grit.shared.ui.components.glassListItemColors
import com.shub39.grit.shared.ui.setting.*
import com.shub39.grit.shared.ui.theme.GlassTokens
import com.shub39.grit.shared.ui.theme.LocalFloatingBarInset
import com.shub39.grit.shared.ui.theme.flexFontEmphasis
import com.shub39.grit.shared.ui.toDisplayString
import grit.shared.ui.generated.resources.Res
import grit.shared.ui.generated.resources.nav_arrow_back
import org.jetbrains.compose.resources.vectorResource

@Composable
fun LookAndFeelPage(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    isUserSubscribed: Boolean,
    onNavigateToPaywall: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Look and Feel", fontFamily = flexFontEmphasis()) },
            navigationIcon = {
                GlassIconButton(
                    icon = vectorResource(Res.drawable.nav_arrow_back),
                    contentDescription = "Back",
                    onClick = onNavigateBack,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 12.dp,
                    top = 16.dp,
                    end = 12.dp,
                    bottom = LocalFloatingBarInset.current + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ListItem(
                    headlineContent = { Text("Reduce transparency") },
                    supportingContent = {
                        Text(
                            "Makes glass opaque and turns off background blur, for readability and performance"
                        )
                    },
                    trailingContent = {
                        ExpressiveSwitch(
                            checked = state.theme.reduceTransparency,
                            onCheckedChange = {
                                onAction(SettingsAction.ChangeReduceTransparency(it))
                            },
                        )
                    },
                    colors = glassListItemColors(),
                    modifier =
                        Modifier.glassItem(detachedItemShape()).clickable {
                            onAction(
                                SettingsAction.ChangeReduceTransparency(
                                    !state.theme.reduceTransparency
                                )
                            )
                        },
                )
            }
            item {
                Column(Modifier.selectableGroup()) {
                    Text(
                        "Font",
                        Modifier.padding(start = 4.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = GlassTokens.SecondaryText,
                    )
                    GlassGroup {
                        Fonts.entries.forEach { font ->
                            val selected = state.theme.font == font
                            ListItem(
                                headlineContent = { Text(font.toDisplayString()) },
                                trailingContent = {
                                    RadioButton(selected = selected, onClick = null)
                                },
                                colors = glassListItemColors(),
                                modifier =
                                    Modifier.selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = { onAction(SettingsAction.ChangeFontPref(font)) },
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}
