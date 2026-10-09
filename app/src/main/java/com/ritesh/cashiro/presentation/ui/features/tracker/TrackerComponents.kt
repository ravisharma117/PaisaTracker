package com.ritesh.cashiro.presentation.ui.features.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ritesh.cashiro.R
import com.ritesh.cashiro.domain.service.Change
import com.ritesh.cashiro.presentation.ui.components.CashiroCard
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.utils.CompactAmount
import com.ritesh.cashiro.utils.CurrencyFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** The three views of the Analysis tab. */
enum class AnalysisMode { OVERVIEW, MONTH, YEAR }

@Composable
fun AnalysisModeSwitch(selected: AnalysisMode, onSelect: (AnalysisMode) -> Unit, modifier: Modifier = Modifier) {
    val labels = listOf(
        AnalysisMode.OVERVIEW to R.string.tracker_mode_overview,
        AnalysisMode.MONTH to R.string.tracker_mode_month,
        AnalysisMode.YEAR to R.string.tracker_mode_year
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size)
            ) { Text(stringResource(label)) }
        }
    }
}

/** Previous / next arrows around a title that opens a picker when tapped. */
@Composable
fun PeriodSwitcher(
    title: String,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.tracker_previous))
        }
        TextButton(onClick = onPick) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.tracker_next))
        }
    }
}

/** A list of choices in a dialog, newest first. */
@Composable
fun PickerDialog(
    title: String,
    options: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn {
                items(options.size) { index ->
                    Text(
                        text = options[index].first,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { options[index].second(); onDismiss() }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tracker_close)) } }
    )
}

/** A whole-number amount for a table cell; large ones are shortened so columns stay narrow. */
fun cellAmount(amount: BigDecimal, currency: String): String =
    if (amount.abs() >= BigDecimal(100_000)) CompactAmount.format(amount, currency)
    else CurrencyFormatter.formatAmount(amount.setScale(0, RoundingMode.HALF_UP), currency)

/** One labelled figure in a summary strip. */
@Composable
fun FigureColumn(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun incomeColor(): Color = MaterialTheme.colorScheme.primary

@Composable
fun expenseColor(): Color = MaterialTheme.colorScheme.error

@Composable
fun savingsColor(amount: BigDecimal): Color =
    if (amount.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary

/** "+12.5% vs last month" style text. */
@Composable
fun changeText(change: Change, currency: String, vsRes: Int): String {
    val sign = if (change.amount.signum() > 0) "+" else ""
    val amount = sign + cellAmount(change.amount, currency)
    val percent = change.percent?.let { String.format(Locale.getDefault(), " (%s%.1f%%)", if (it > 0) "+" else "", it) } ?: ""
    return stringResource(vsRes, amount + percent)
}

/** Income, expenses and savings for a period. Headline totals, so they follow the hide-totals setting. */
@Composable
fun SummaryCard(
    income: BigDecimal,
    expenses: BigDecimal,
    savings: BigDecimal,
    savingsRatePercent: Float?,
    currency: String,
    modifier: Modifier = Modifier,
    footer: @Composable (() -> Unit)? = null
) {
    CashiroCard(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FigureColumn(
                stringResource(R.string.tracker_income), CurrencyFormatter.formatTotal(income, currency),
                Modifier.weight(1f), incomeColor()
            )
            FigureColumn(
                stringResource(R.string.tracker_expenses), CurrencyFormatter.formatTotal(expenses, currency),
                Modifier.weight(1f), expenseColor()
            )
            FigureColumn(
                stringResource(R.string.tracker_savings), CurrencyFormatter.formatTotal(savings, currency),
                Modifier.weight(1f), savingsColor(savings)
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = savingsRatePercent?.let { stringResource(R.string.tracker_savings_rate, String.format(Locale.getDefault(), "%.1f", it)) }
                ?: stringResource(R.string.tracker_savings_rate_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        footer?.invoke()
    }
}

/** A table row: first cell left-aligned and wider, the rest right-aligned. */
@Composable
fun TableRow(
    cells: List<String>,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    firstWeight: Float = 1.5f,
    cellColors: List<Color> = emptyList(),
    onClick: (() -> Unit)? = null
) {
    val rowModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Row(
        modifier = rowModifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        cells.forEachIndexed { index, text ->
            Text(
                text = text,
                modifier = Modifier.weight(if (index == 0) firstWeight else 1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                color = cellColors.getOrNull(index) ?: Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TableHeader(cells: List<String>, firstWeight: Float = 1.5f) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            cells.forEachIndexed { index, text ->
                Text(
                    text = text,
                    modifier = Modifier.weight(if (index == 0) firstWeight else 1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                    maxLines = 1
                )
            }
        }
        HorizontalDivider()
    }
}

/** One bar series. A null value draws no bar (a month with no snapshot, a month still to come). */
data class ChartSeries(val name: String, val values: List<BigDecimal?>, val color: Color)

/**
 * Bars for a run of labelled periods. Bars below zero hang under the baseline. Tapping a period
 * shows its full values underneath, so the shortened chart never hides an amount.
 * [valuesAreTotals] marks figures that follow the hide-totals setting.
 */
@Composable
fun BarChartCard(
    title: String,
    labels: List<String>,
    series: List<ChartSeries>,
    currency: String,
    modifier: Modifier = Modifier,
    valuesAreTotals: Boolean = false
) {
    var selected by remember(labels, series) { mutableIntStateOf(-1) }
    CashiroCard(modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Spacing.sm))

        val all = series.flatMap { it.values }.filterNotNull()
        if (all.none { it.signum() != 0 }) {
            Text(
                stringResource(R.string.tracker_chart_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@CashiroCard
        }
        val maxAbs = all.maxOf { it.abs() }.toFloat().coerceAtLeast(1f)
        val hasNegative = all.any { it.signum() < 0 }

        Row(
            modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT + 18.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            labels.forEachIndexed { index, label ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (index == selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                        .clickable { selected = if (selected == index) -1 else index },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT).padding(horizontal = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        series.forEach { s ->
                            Bar(s.values.getOrNull(index), maxAbs, hasNegative, s.color, Modifier.weight(1f))
                        }
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        if (series.size > 1) {
            Spacer(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                series.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(s.color))
                        Spacer(Modifier.padding(horizontal = 3.dp))
                        Text(s.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        val readout = if (selected in labels.indices) {
            series.joinToString("   ") { s ->
                val value = s.values.getOrNull(selected)
                val text = when {
                    value == null -> "-"
                    valuesAreTotals -> CurrencyFormatter.formatTotal(value, currency)
                    else -> CurrencyFormatter.formatCurrency(value, currency)
                }
                if (series.size > 1) "${s.name}: $text" else text
            }.let { "${labels[selected]}: $it" }
        } else stringResource(R.string.tracker_chart_tap_hint)
        Text(readout, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val CHART_HEIGHT = 96.dp

@Composable
private fun Bar(value: BigDecimal?, maxAbs: Float, hasNegative: Boolean, color: Color, modifier: Modifier) {
    val fraction = if (value == null || value.signum() == 0) 0f
    else (value.abs().toFloat() / maxAbs).coerceIn(0.02f, 1f)
    val shape = RoundedCornerShape(3.dp)
    if (!hasNegative) {
        Box(modifier.fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
            if (fraction > 0f) Box(Modifier.fillMaxWidth().fillMaxHeight(fraction).background(color, shape))
        }
    } else {
        Column(modifier.fillMaxHeight()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                if (value != null && value.signum() > 0) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(fraction).background(color, shape))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                if (value != null && value.signum() < 0) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(fraction)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.85f), shape)
                    )
                }
            }
        }
    }
}

/** Horizontal share bars: each entry's amount as a part of [total]. */
@Composable
fun ShareBarsCard(
    title: String,
    entries: List<Pair<String, BigDecimal>>,
    total: BigDecimal,
    currency: String,
    modifier: Modifier = Modifier,
    onEntryClick: ((String) -> Unit)? = null
) {
    CashiroCard(modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Spacing.sm))
        if (entries.isEmpty() || total.signum() <= 0) {
            Text(
                stringResource(R.string.tracker_chart_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@CashiroCard
        }
        entries.forEach { (name, amount) ->
            val share = (amount.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            val rowModifier = if (onEntryClick != null) Modifier.clickable { onEntryClick(name) } else Modifier
            Column(rowModifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        name, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${cellAmount(amount, currency)} · ${String.format(Locale.getDefault(), "%.0f", share * 100)}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                LinearProgressIndicator(
                    progress = { share },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

/** A titled card holding rows of "label ... value". */
@Composable
fun KeyValueCard(
    title: String,
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    emptyText: String? = null,
    onRowClick: ((Int) -> Unit)? = null
) {
    CashiroCard(modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Spacing.xs))
        if (rows.isEmpty() && emptyText != null) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        rows.forEachIndexed { index, (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onRowClick != null) Modifier.clickable { onRowClick(index) } else Modifier)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}
