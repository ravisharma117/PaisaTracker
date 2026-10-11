package com.naxits.paisatracker.presentation.ui.features.tracker

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naxits.paisatracker.R
import com.naxits.paisatracker.domain.service.FinancialYear
import com.naxits.paisatracker.presentation.ui.components.CashiroCard
import com.naxits.paisatracker.presentation.ui.theme.Spacing
import com.naxits.paisatracker.utils.CompactAmount
import com.naxits.paisatracker.utils.CurrencyFormatter
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * The financial year (April to March) at a glance: a month-by-month table, the year's totals, and
 * charts. Headline totals and net worth follow the hide-totals setting; month rows stay visible.
 *
 * With [onNavigateBack] null it is the Year view of the Analysis tab; otherwise it is its own
 * screen (a deep link).
 */
@Composable
fun YearlyTrackerScreen(
    initialStartYear: Int?,
    onNavigateBack: (() -> Unit)?,
    onOpenMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    modeSwitch: (@Composable () -> Unit)? = null,
    viewModel: YearlyTrackerViewModel = hiltViewModel()
) {
    LaunchedEffect(initialStartYear) { viewModel.start(initialStartYear) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    val income = incomeColor()
    val expense = expenseColor()
    val saving = MaterialTheme.colorScheme.tertiary
    val netWorthColor = MaterialTheme.colorScheme.primary

    TrackerScaffold(
        title = stringResource(if (onNavigateBack == null) R.string.analytics else R.string.tracker_yearly_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) {
        if (modeSwitch != null) item(key = "mode") { modeSwitch() }
        item(key = "switcher") {
            PeriodSwitcher(
                title = stringResource(R.string.tracker_fy_label, FinancialYear.label(state.startYear)),
                canGoNext = state.canGoNext,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                onPick = { showPicker = true }
            )
        }
        if (state.isLoading) return@TrackerScaffold

        val currency = state.currency
        val months = state.rows.map { it.month }
        val labels = months.map { it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3) }
        fun series(name: String, color: Color, pick: (YearMonthRow) -> BigDecimal) =
            ChartSeries(name, state.rows.map { if (it.isFuture) null else pick(it) }, color)

        item(key = "summary") {
            SummaryCard(
                income = state.total.income,
                expenses = state.total.expenses,
                savings = state.total.savings,
                savingsRatePercent = state.total.savingsRatePercent,
                currency = currency
            ) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(
                        R.string.tracker_year_lending,
                        CurrencyFormatter.formatCurrency(state.lending.lent, currency),
                        CurrencyFormatter.formatCurrency(state.lending.borrowed, currency)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!state.hasData) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.tracker_year_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.md)
                )
            }
        }

        item(key = "table") { MonthTable(state, onOpenMonth) }

        item(key = "income") {
            BarChartCard(stringResource(R.string.tracker_chart_income), labels, listOf(series(stringResource(R.string.tracker_income), income) { it.figures.income }), currency)
        }
        item(key = "expenses") {
            BarChartCard(stringResource(R.string.tracker_chart_expenses), labels, listOf(series(stringResource(R.string.tracker_expenses), expense) { it.figures.expenses }), currency)
        }
        item(key = "savings") {
            BarChartCard(stringResource(R.string.tracker_chart_savings), labels, listOf(series(stringResource(R.string.tracker_savings), saving) { it.figures.savings }), currency)
        }
        item(key = "cashflow") {
            BarChartCard(
                stringResource(R.string.tracker_chart_cash_flow), labels,
                listOf(
                    series(stringResource(R.string.tracker_income), income) { it.figures.income },
                    series(stringResource(R.string.tracker_expenses), expense) { it.figures.expenses }
                ),
                currency
            )
        }
        item(key = "categories") {
            val entries = state.topCategories.map { it.category to it.total } +
                if (state.otherCategoriesTotal.signum() > 0) listOf(stringResource(R.string.tracker_other) to state.otherCategoriesTotal) else emptyList()
            ShareBarsCard(
                title = stringResource(R.string.tracker_chart_categories),
                entries = entries,
                total = state.total.expenses,
                currency = currency
            )
        }
        item(key = "lending") {
            val byMonth = state.lendingByMonth.associate { it.first to it.second }
            BarChartCard(
                stringResource(R.string.tracker_chart_lending), labels,
                listOf(ChartSeries(stringResource(R.string.tracker_lent), state.rows.map { r -> if (r.isFuture) null else byMonth[r.month]?.lent }, income)),
                currency
            )
        }
        item(key = "borrowing") {
            val byMonth = state.lendingByMonth.associate { it.first to it.second }
            BarChartCard(
                stringResource(R.string.tracker_chart_borrowing), labels,
                listOf(ChartSeries(stringResource(R.string.tracker_borrowed), state.rows.map { r -> if (r.isFuture) null else byMonth[r.month]?.borrowed }, expense)),
                currency
            )
        }
        item(key = "networth") {
            val byMonth = state.netWorth.associate { it.month to it.value }
            BarChartCard(
                stringResource(R.string.tracker_chart_net_worth), labels,
                listOf(ChartSeries(stringResource(R.string.net_worth), months.map { byMonth[it] }, netWorthColor)),
                currency,
                valuesAreTotals = true
            )
        }
    }

    if (showPicker) {
        val current = FinancialYear.startYearOf(LocalDate.now())
        PickerDialog(
            title = stringResource(R.string.tracker_pick_year),
            options = (0 until PICKER_YEARS).map { back ->
                val year = current - back
                stringResource(R.string.tracker_fy_label, FinancialYear.label(year)) to { viewModel.select(year) }
            },
            onDismiss = { showPicker = false }
        )
    }
}

private const val PICKER_YEARS = 10

@Composable
private fun MonthTable(state: YearlyTrackerUiState, onOpenMonth: (YearMonth) -> Unit) {
    val currency = state.currency
    CashiroCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.tracker_month_table, currency),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(Spacing.sm))
        TableHeader(
            listOf(
                stringResource(R.string.tracker_col_month), stringResource(R.string.tracker_income),
                stringResource(R.string.tracker_expenses), stringResource(R.string.tracker_savings)
            ),
            firstWeight = 1f
        )
        val error = MaterialTheme.colorScheme.error
        state.rows.forEach { row ->
            val name = row.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            if (row.isFuture) {
                TableRow(listOf(name, DASH, DASH, DASH), firstWeight = 1f)
            } else {
                TableRow(
                    cells = listOf(
                        name,
                        compact(row.figures.income, currency),
                        compact(row.figures.expenses, currency),
                        compact(row.figures.savings, currency)
                    ),
                    firstWeight = 1f,
                    cellColors = listOf(Color.Unspecified, Color.Unspecified, Color.Unspecified, if (row.figures.savings.signum() < 0) error else Color.Unspecified),
                    onClick = { onOpenMonth(row.month) }
                )
            }
        }
        HorizontalDivider()
        TableRow(
            cells = listOf(
                stringResource(R.string.tracker_total),
                CurrencyFormatter.formatTotal(state.total.income, currency),
                CurrencyFormatter.formatTotal(state.total.expenses, currency),
                CurrencyFormatter.formatTotal(state.total.savings, currency)
            ),
            firstWeight = 1f,
            bold = true
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(R.string.tracker_month_table_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private const val DASH = "–"

private fun compact(amount: BigDecimal, currency: String): String =
    if (amount.signum() == 0) "0" else CompactAmount.format(amount, currency)
