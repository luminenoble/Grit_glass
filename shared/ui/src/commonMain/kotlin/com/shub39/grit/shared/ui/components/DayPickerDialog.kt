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
package com.shub39.grit.shared.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** Picks one calendar day, e.g. a deadline. Days before [notBefore] cannot be chosen. */
@Composable
fun DayPickerDialog(
    initial: LocalDate?,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    notBefore: LocalDate? = null,
) {
    // The picker works in UTC days.
    fun LocalDate.millis() = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val selectable =
        remember(notBefore) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    notBefore == null || utcTimeMillis >= notBefore.millis()
            }
        }
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = initial?.millis(),
            selectableDates = selectable,
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date)
                    }
                },
            ) {
                Text("确定")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = state)
    }
}
