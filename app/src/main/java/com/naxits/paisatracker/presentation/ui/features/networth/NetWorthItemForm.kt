package com.naxits.paisatracker.presentation.ui.features.networth

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.naxits.paisatracker.R
import com.naxits.paisatracker.data.database.entity.NetWorthItemEntity
import com.naxits.paisatracker.data.database.entity.NetWorthItemType
import com.naxits.paisatracker.domain.service.LoanMath
import com.naxits.paisatracker.presentation.ui.components.DatePicker
import com.naxits.paisatracker.presentation.ui.theme.Dimensions
import com.naxits.paisatracker.presentation.ui.theme.Spacing
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Add or edit a holding or loan. Changing the value of an existing one keeps the old value in its
 * history, and for a loan the typed-in balance restarts the repayment schedule from that day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthItemForm(
    item: NetWorthItemEntity?,
    currency: String,
    onSave: (item: NetWorthItemEntity, value: BigDecimal, asOf: LocalDate) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(item?.type ?: NetWorthItemType.MUTUAL_FUND) }
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var valueText by remember { mutableStateOf(item?.currentValue?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var asOf by remember { mutableStateOf(item?.asOfDate ?: LocalDate.now()) }
    var rateText by remember { mutableStateOf(item?.annualRatePercent?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var monthsText by remember { mutableStateOf("") }
    var emiText by remember { mutableStateOf(item?.emiAmount?.toPlainString().orEmpty()) }
    var emiEdited by remember { mutableStateOf(item?.emiAmount != null) }
    var emiDayText by remember { mutableStateOf(item?.emiDay?.toString().orEmpty()) }
    var notes by remember { mutableStateOf(item?.notes.orEmpty()) }
    var pickingDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val value = valueText.replace(',', '.').toBigDecimalOrNull()
    val valueOk = value != null && value.signum() >= 0
    val nameOk = name.isNotBlank()
    val isLoan = type.isLoan
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    // The EMI follows the amount, rate and months until the user types their own
    val rate = rateText.replace(',', '.').toBigDecimalOrNull()
    val months = monthsText.toIntOrNull()
    LaunchedEffect(isLoan, emiEdited, valueText, rateText, monthsText) {
        if (isLoan && !emiEdited && value != null && rate != null && months != null && months > 0 && value.signum() > 0) {
            emiText = LoanMath.emi(value, rate, months).toPlainString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimensions.Padding.content)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            stringResource(if (item == null) R.string.nw_sheet_add_title else R.string.nw_sheet_edit_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        if (item == null) {
            Text(stringResource(R.string.nw_assets), style = MaterialTheme.typography.labelLarge)
            TypeChips(NetWorthItemType.entries.filter { !it.isLiability }, type) { type = it }
            Text(stringResource(R.string.nw_liabilities), style = MaterialTheme.typography.labelLarge)
            TypeChips(NetWorthItemType.entries.filter { it.isLiability }, type) { type = it }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.nw_field_name)) },
            isError = showErrors && !nameOk,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = valueText,
            onValueChange = { valueText = it },
            label = { Text(stringResource(if (isLoan) R.string.nw_field_value_loan else R.string.nw_field_value_asset)) },
            isError = showErrors && !valueOk,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.nw_field_date) + ": " + asOf.format(formatter))
        }

        if (isLoan) {
            Text(
                stringResource(R.string.nw_loan_estimate_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text(stringResource(R.string.nw_field_rate)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = monthsText,
                    onValueChange = { monthsText = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.nw_field_months_left)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = emiText,
                    onValueChange = { emiText = it; emiEdited = true },
                    label = { Text(stringResource(R.string.nw_field_emi)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = emiDayText,
                    onValueChange = { emiDayText = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.nw_field_emi_day)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                stringResource(R.string.nw_emi_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.nw_field_notes)) },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            if (item != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.nw_delete))
                }
            }
            Button(
                onClick = {
                    showErrors = true
                    if (!nameOk || !valueOk) return@Button
                    val emi = emiText.replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
                    val day = emiDayText.toIntOrNull()?.coerceIn(1, 31)
                    val loanTerms = isLoan && emi != null && day != null
                    val built = (item ?: NetWorthItemEntity(
                        name = "", type = type, currency = currency, currentValue = BigDecimal.ZERO, asOfDate = asOf
                    )).copy(
                        name = name.trim(),
                        type = type,
                        currentValue = value!!,
                        asOfDate = asOf,
                        notes = notes.trim().ifEmpty { null },
                        annualRatePercent = if (loanTerms) (rate ?: BigDecimal.ZERO) else null,
                        emiAmount = if (loanTerms) emi else null,
                        emiDay = if (loanTerms) day else null
                    )
                    onSave(built, value, asOf)
                    onDismiss()
                },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.nw_save)) }
        }
    }

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = asOf.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePicker(
            onDismiss = { pickingDate = false },
            onConfirm = {
                state.selectedDateMillis?.let { asOf = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                pickingDate = false
            },
            datePickerState = state
        )
    }

    if (confirmDelete && item != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.nw_delete_confirm_title, item.name)) },
            text = { Text(stringResource(R.string.nw_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { onDelete(item.id); confirmDelete = false; onDismiss() }) {
                    Text(stringResource(R.string.nw_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.recurring_cancel)) }
            }
        )
    }
}

@Composable
private fun TypeChips(types: List<NetWorthItemType>, selected: NetWorthItemType, onSelect: (NetWorthItemType) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        types.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(stringResource(typeLabel(option))) }
            )
        }
    }
}

private fun typeLabel(type: NetWorthItemType): Int = when (type) {
    NetWorthItemType.MUTUAL_FUND -> R.string.nw_type_mutual_fund
    NetWorthItemType.STOCKS -> R.string.nw_type_stocks
    NetWorthItemType.FIXED_DEPOSIT -> R.string.nw_type_fixed_deposit
    NetWorthItemType.GOLD -> R.string.nw_type_gold
    NetWorthItemType.HOUSE -> R.string.nw_type_house
    NetWorthItemType.PLOT -> R.string.nw_type_plot
    NetWorthItemType.PROPERTY -> R.string.nw_type_property
    NetWorthItemType.VEHICLE -> R.string.nw_type_vehicle
    NetWorthItemType.OTHER_ASSET -> R.string.nw_type_other_asset
    NetWorthItemType.HOME_LOAN -> R.string.nw_type_home_loan
    NetWorthItemType.VEHICLE_LOAN -> R.string.nw_type_vehicle_loan
    NetWorthItemType.PERSONAL_LOAN -> R.string.nw_type_personal_loan
    NetWorthItemType.EDUCATION_LOAN -> R.string.nw_type_education_loan
    NetWorthItemType.OTHER_LIABILITY -> R.string.nw_type_other_liability
}
