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
package com.shub39.grit.shared.ui.task.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shub39.grit.shared.ui.theme.GlassMetrics
import com.shub39.grit.shared.ui.theme.GlassPressIndication
import com.shub39.grit.shared.ui.theme.GlassShapes
import com.shub39.grit.shared.ui.theme.GlassTier
import com.shub39.grit.shared.ui.theme.GlassTokens
import com.shub39.grit.shared.ui.theme.glassSurface

/**
 * Category chip in a glass tray, [GlassMetrics.Control] tall like every other control. The selected
 * chip is a raised lens (brighter, see-through, warm shadow, semibold label); the others lie flat in
 * quieter ink. Pressing fills a chip with light.
 */
@Composable
fun CategoryToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .then(
                if (checked)
                    Modifier.glassSurface(
                        GlassTier.Thin,
                        GlassShapes.Capsule,
                        tint = GlassTokens.Raised,
                        tintAlpha = GlassTokens.RaisedAlpha,
                    )
                else Modifier
            )
            .clip(GlassShapes.Capsule)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = GlassPressIndication,
                enabled = enabled,
                role = Role.Tab,
                onValueChange = onCheckedChange,
            )
            .height(GlassMetrics.Control)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides
                if (checked) MaterialTheme.colorScheme.onSurface else GlassTokens.SecondaryText,
            LocalTextStyle provides
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Medium
                ),
        ) {
            content()
        }
    }
}
