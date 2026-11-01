package com.naxits.paisatracker.presentation.ui.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naxits.paisatracker.R
import com.naxits.paisatracker.domain.service.RecurringPatternDetector
import com.naxits.paisatracker.presentation.ui.components.ListItem
import com.naxits.paisatracker.presentation.ui.components.ListItemPosition
import com.naxits.paisatracker.presentation.ui.components.toShape
import com.naxits.paisatracker.presentation.ui.theme.orange_dark
import com.naxits.paisatracker.presentation.ui.theme.orange_light

/** "7.5%" for 75 tenths of a percent, "10%" for 100. */
fun formatToleranceTenths(tenths: Int): String =
    if (tenths % 10 == 0) "${tenths / 10}%" else "${tenths / 10}.${tenths % 10}%"

/** Settings entry for how far an amount may differ before an entry stops counting as part of a series. */
@Composable
fun RecurringToleranceRow(
    tenths: Int,
    onSelect: (Int) -> Unit,
    position: ListItemPosition = ListItemPosition.Middle
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headline = {
            Text(
                text = stringResource(R.string.recurring_suggestions_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        },
        supporting = {
            Text(
                text = stringResource(R.string.recurring_tolerance_subtitle, formatToleranceTenths(tenths)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leading = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color = orange_light, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = orange_dark)
            }
        },
        trailing = {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        onClick = { showDialog = true },
        shape = position.toShape(),
        padding = PaddingValues(0.dp)
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.recurring_suggestions_title)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(
                        text = stringResource(R.string.recurring_tolerance_explain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    RecurringPatternDetector.TOLERANCE_CHOICES_TENTHS.forEach { choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(choice)
                                    showDialog = false
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = choice == tenths, onClick = null)
                            Text(
                                text = stringResource(R.string.recurring_tolerance_option, formatToleranceTenths(choice)),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.cancel_lbl)) }
            }
        )
    }
}
