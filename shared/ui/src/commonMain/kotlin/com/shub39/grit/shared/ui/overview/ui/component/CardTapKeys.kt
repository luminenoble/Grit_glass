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
package com.shub39.grit.shared.ui.overview.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shub39.grit.shared.ui.theme.GlassBackdrop
import com.shub39.grit.shared.ui.theme.GlassMetrics
import com.shub39.grit.shared.ui.theme.GlassShapes
import com.shub39.grit.shared.ui.theme.GlassTier
import com.shub39.grit.shared.ui.theme.GlassTokens
import com.shub39.grit.shared.ui.theme.glassSurface
import grit.shared.ui.generated.resources.Res
import grit.shared.ui.generated.resources.edit
import grit.shared.ui.generated.resources.search
import org.jetbrains.compose.resources.vectorResource

/** What a tap on a card does; a long press does the other. */
enum class CardTap {
    /** Opens the glass preview over the wall. */
    Preview,
    /** Opens the task's editor. */
    Detail,
}

/**
 * The two keys floating over the cards that choose what a tap does. Built like the navigation
 * pill: the chosen key is a raised lens that opens to show its name, the other lies flat.
 */
@Composable
fun CardTapKeys(
    selected: CardTap,
    onSelect: (CardTap) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
) {
    Row(
        modifier
            .glassSurface(GlassTier.Thin, GlassShapes.Capsule, backdrop)
            .padding(GlassMetrics.TrayInset)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardTap.entries.forEach { key ->
            val chosen = key == selected
            val name =
                when (key) {
                    CardTap.Preview -> "Preview"
                    CardTap.Detail -> "Details"
                }
            val ink by
                animateColorAsState(
                    if (chosen) MaterialTheme.colorScheme.onSurface else GlassTokens.SecondaryText,
                    MaterialTheme.motionScheme.fastEffectsSpec(),
                )
            Row(
                Modifier.then(
                        if (chosen)
                            Modifier.glassSurface(
                                GlassTier.Thin,
                                GlassShapes.Capsule,
                                tint = GlassTokens.Raised,
                                tintAlpha = GlassTokens.RaisedAlpha,
                            )
                        else Modifier
                    )
                    .clip(GlassShapes.Capsule)
                    .selectable(
                        selected = chosen,
                        role = Role.RadioButton,
                        onClick = { onSelect(key) },
                    )
                    .height(GlassMetrics.Control)
                    .padding(horizontal = if (chosen) 14.dp else 11.dp)
                    .animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    vectorResource(
                        when (key) {
                            CardTap.Preview -> Res.drawable.search
                            CardTap.Detail -> Res.drawable.edit
                        }
                    ),
                    contentDescription = if (chosen) null else "Tap a card: $name",
                    modifier = Modifier.size(GlassMetrics.Icon),
                    tint = ink,
                )
                AnimatedVisibility(chosen) {
                    Text(
                        name,
                        Modifier.padding(start = 6.dp),
                        color = ink,
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
