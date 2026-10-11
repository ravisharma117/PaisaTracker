package com.naxits.paisatracker.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naxits.paisatracker.R
import com.naxits.paisatracker.data.database.entity.TransactionType

/**
 * Lets an income or expense entry be kept out of the reports: an income can be marked
 * "Not income", and either type can be marked "Cash" or turned into an account-to-account
 * transfer. Shows nothing for other transaction types.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryCountingSelector(
    transactionType: TransactionType,
    excludeFromIncome: Boolean,
    isCashEntry: Boolean,
    onCountingChange: (excludeFromIncome: Boolean, isCashEntry: Boolean) -> Unit,
    onAccountToAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isIncome = transactionType == TransactionType.INCOME
    if (!isIncome && transactionType != TransactionType.EXPENSE) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.entry_counting_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CountingChip(
                label = stringResource(if (isIncome) R.string.type_income else R.string.type_expense),
                selected = !isCashEntry && !(isIncome && excludeFromIncome),
                onClick = { onCountingChange(false, false) }
            )
            if (isIncome) {
                CountingChip(
                    label = stringResource(R.string.entry_counting_not_income),
                    selected = excludeFromIncome && !isCashEntry,
                    onClick = { onCountingChange(true, false) }
                )
            }
            CountingChip(
                label = stringResource(R.string.entry_counting_cash),
                selected = isCashEntry,
                onClick = { onCountingChange(false, true) }
            )
            CountingChip(
                label = stringResource(R.string.entry_counting_account_to_account),
                selected = false,
                onClick = onAccountToAccount
            )
        }
        Text(
            text = stringResource(
                if (isIncome) R.string.entry_counting_hint_income else R.string.entry_counting_hint_expense
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CountingChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, maxLines = 1) },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(0.7f),
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderWidth = 0.dp,
            selected = selected,
            enabled = true
        )
    )
}
