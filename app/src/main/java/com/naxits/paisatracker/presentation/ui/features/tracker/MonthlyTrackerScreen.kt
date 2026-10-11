package com.naxits.paisatracker.presentation.ui.features.tracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naxits.paisatracker.R
import com.naxits.paisatracker.domain.model.CashAccount
import com.naxits.paisatracker.presentation.ui.components.CashiroCard
import com.naxits.paisatracker.presentation.ui.components.CustomTitleTopAppBar
import com.naxits.paisatracker.presentation.ui.features.categories.NavigationContent
import com.naxits.paisatracker.presentation.ui.features.categories.displayName
import com.naxits.paisatracker.presentation.ui.theme.Dimensions
import com.naxits.paisatracker.presentation.ui.theme.Spacing
import com.naxits.paisatracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal
import java.time.YearMonth

/**
 * The month at a glance: totals, the category budget table, and what lending, recurring payments
 * and bills did. Headline totals follow the hide-totals setting; the table, accounts and other
 * rows stay visible, the same as the Categories tab.
 *
 * With [onNavigateBack] null it is the Month view of the Analysis tab; otherwise it is its own
 * screen (opened from the year table or a deep link).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MonthlyTrackerScreen(
    initialMonth: YearMonth?,
    onNavigateBack: (() -> Unit)?,
    onOpenCategory: (name: String, month: YearMonth) -> Unit,
    onOpenAccount: (bankName: String, last4: String) -> Unit,
    onOpenBills: () -> Unit,
    onOpenRecurring: () -> Unit,
    modifier: Modifier = Modifier,
    modeSwitch: (@Composable () -> Unit)? = null,
    viewModel: MonthlyTrackerViewModel = hiltViewModel()
) {
    LaunchedEffect(initialMonth) { viewModel.start(initialMonth) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    TrackerScaffold(
        title = stringResource(if (onNavigateBack == null) R.string.analytics else R.string.tracker_monthly_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier
    ) {
        if (modeSwitch != null) item(key = "mode") { modeSwitch() }
        item(key = "switcher") {
            PeriodSwitcher(
                title = state.month.displayName(),
                canGoNext = state.canGoNext,
                onPrevious = viewModel::previous,
                onNext = viewModel::next,
                onPick = { showPicker = true }
            )
        }
        if (state.isLoading) return@TrackerScaffold

        val currency = state.currency
        item(key = "summary") {
            SummaryCard(
                income = state.figures.income,
                expenses = state.figures.expenses,
                savings = state.figures.savings,
                savingsRatePercent = state.figures.savingsRatePercent,
                currency = currency
            ) {
                if (!state.previous.isEmpty) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        changeText(state.expenseChange, currency, R.string.tracker_expenses_vs_last),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        changeText(state.incomeChange, currency, R.string.tracker_income_vs_last),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.figures.isEmpty && state.lending.isEmpty && state.table.rows.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.tracker_month_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.md)
                )
            }
        }

        item(key = "table") { CategoryTableCard(state, onOpenCategory) }

        state.overallBudget?.let { overall ->
            item(key = "budget") { BudgetUsageCard(state, overall, currency) }
        }

        item(key = "chart") {
            val shown = state.table.rows.filter { it.spent.signum() > 0 }.take(CHART_CATEGORIES)
            val rest = state.table.rows.filter { it.spent.signum() > 0 }.drop(CHART_CATEGORIES)
                .fold(BigDecimal.ZERO) { acc, r -> acc.add(r.spent) }
            val entries = shown.map { it.category to it.spent } +
                if (rest.signum() > 0) listOf(stringResource(R.string.tracker_other) to rest) else emptyList()
            ShareBarsCard(
                title = stringResource(R.string.tracker_category_chart),
                entries = entries,
                total = state.table.totalSpent,
                currency = currency,
                onEntryClick = { name -> if (shown.any { it.category == name }) onOpenCategory(name, state.month) }
            )
        }

        item(key = "accounts") { AccountsCard(state, onOpenAccount) }
        item(key = "lending") { LendingCard(state) }
        item(key = "recurring") { RecurringCard(state, onOpenRecurring) }
        item(key = "bills") { BillsCard(state, onOpenBills) }
    }

    if (showPicker) {
        val now = YearMonth.now()
        PickerDialog(
            title = stringResource(R.string.tracker_pick_month),
            options = (0 until PICKER_MONTHS).map { back ->
                val month = now.minusMonths(back.toLong())
                month.displayName() to { viewModel.select(month) }
            },
            onDismiss = { showPicker = false }
        )
    }
}

private const val CHART_CATEGORIES = 8
private const val PICKER_MONTHS = 36

@Composable
private fun CategoryTableCard(state: MonthlyTrackerUiState, onOpenCategory: (String, YearMonth) -> Unit) {
    val table = state.table
    val currency = state.currency
    CashiroCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.tracker_category_table, currency),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(Spacing.sm))
        if (table.rows.isEmpty()) {
            Text(
                stringResource(R.string.tracker_table_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@CashiroCard
        }
        TableHeader(
            listOf(
                stringResource(R.string.tracker_col_category), stringResource(R.string.tracker_col_budget),
                stringResource(R.string.tracker_col_spent), stringResource(R.string.tracker_col_remaining)
            )
        )
        val error = MaterialTheme.colorScheme.error
        val normal = androidx.compose.ui.graphics.Color.Unspecified
        table.rows.forEach { row ->
            TableRow(
                cells = listOf(
                    row.category,
                    row.budget?.let { cellAmount(it, currency) } ?: DASH,
                    cellAmount(row.spent, currency),
                    row.remaining?.let { cellAmount(it, currency) } ?: DASH
                ),
                cellColors = listOf(normal, normal, if (row.isOverBudget) error else normal, if (row.isOverBudget) error else normal),
                onClick = { onOpenCategory(row.category, state.month) }
            )
        }
        androidx.compose.material3.HorizontalDivider()
        TableRow(
            cells = listOf(
                stringResource(R.string.tracker_total),
                if (table.hasBudget) cellAmount(table.totalBudget, currency) else DASH,
                CurrencyFormatter.formatTotal(table.totalSpent, currency),
                if (table.hasBudget) cellAmount(table.totalRemaining, currency) else DASH
            ),
            bold = true
        )
    }
}

@Composable
private fun BudgetUsageCard(state: MonthlyTrackerUiState, overall: BigDecimal, currency: String) {
    CashiroCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.tracker_budget_usage),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(Spacing.sm))
        LinearProgressIndicator(
            progress = { state.budgetUsedFraction },
            color = if (state.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            stringResource(
                R.string.tracker_budget_used,
                CurrencyFormatter.formatTotal(state.figures.expenses, currency),
                CurrencyFormatter.formatTotal(overall, currency)
            ),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AccountsCard(state: MonthlyTrackerUiState, onOpenAccount: (String, String) -> Unit) {
    val currency = state.currency
    CashiroCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.tracker_accounts),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(Spacing.sm))
        if (state.accounts.isEmpty()) {
            Text(
                stringResource(R.string.tracker_accounts_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@CashiroCard
        }
        TableHeader(
            listOf(
                stringResource(R.string.tracker_col_account),
                stringResource(R.string.tracker_col_spent),
                stringResource(R.string.tracker_col_received)
            )
        )
        state.accounts.forEach { account ->
            val bank = account.bankName
            val last4 = account.accountLast4
            TableRow(
                cells = listOf(
                    accountLabel(bank, last4),
                    cellAmount(account.spent, currency),
                    cellAmount(account.received, currency)
                ),
                onClick = if (bank != null && last4 != null) ({ onOpenAccount(bank, last4) }) else null
            )
        }
    }
}

@Composable
private fun accountLabel(bank: String?, last4: String?): String = when {
    bank == null -> stringResource(R.string.tracker_other_account)
    CashAccount.isCash(bank, last4) -> bank
    last4.isNullOrBlank() -> bank
    else -> "$bank ••$last4"
}

@Composable
private fun LendingCard(state: MonthlyTrackerUiState) {
    val c = state.currency
    val l = state.lending
    KeyValueCard(
        title = stringResource(R.string.tracker_lending),
        rows = if (l.isEmpty) emptyList() else listOf(
            stringResource(R.string.tracker_lent) to CurrencyFormatter.formatCurrency(l.lent, c),
            stringResource(R.string.tracker_borrowed) to CurrencyFormatter.formatCurrency(l.borrowed, c),
            stringResource(R.string.tracker_received_back) to CurrencyFormatter.formatCurrency(l.receivedBack, c),
            stringResource(R.string.tracker_repaid) to CurrencyFormatter.formatCurrency(l.repaid, c)
        ),
        emptyText = stringResource(R.string.tracker_lending_empty)
    )
}

@Composable
private fun RecurringCard(state: MonthlyTrackerUiState, onOpen: () -> Unit) {
    val c = state.currency
    val r = state.recurring
    KeyValueCard(
        title = stringResource(R.string.tracker_recurring),
        rows = if (r == null || r.isEmpty) emptyList() else buildList {
            add(stringResource(R.string.tracker_recurring_created, r.createdCount) to CurrencyFormatter.formatCurrency(r.createdTotal, c))
            add(stringResource(R.string.tracker_recurring_upcoming, r.upcoming.size) to CurrencyFormatter.formatCurrency(r.upcomingTotal, c))
            if (r.waitingCount > 0) add(stringResource(R.string.tracker_recurring_waiting) to r.waitingCount.toString())
            if (r.skippedCount > 0) add(stringResource(R.string.tracker_recurring_skipped) to r.skippedCount.toString())
        },
        emptyText = stringResource(R.string.tracker_recurring_empty),
        onRowClick = { onOpen() }
    )
}

@Composable
private fun BillsCard(state: MonthlyTrackerUiState, onOpen: () -> Unit) {
    val c = state.currency
    val b = state.bills
    KeyValueCard(
        title = stringResource(R.string.tracker_bills),
        rows = if (b == null || b.isEmpty) emptyList() else listOf(
            stringResource(R.string.tracker_bills_paid, b.paid.size) to CurrencyFormatter.formatCurrency(b.paidTotal, c),
            stringResource(R.string.tracker_bills_unpaid, b.unpaid.size) to CurrencyFormatter.formatCurrency(b.unpaidTotal, c),
            stringResource(R.string.tracker_total) to CurrencyFormatter.formatCurrency(b.total, c)
        ),
        emptyText = stringResource(R.string.tracker_bills_empty),
        onRowClick = { onOpen() }
    )
}

private const val DASH = "–"

/** Shared frame for both tracker screens: the standard top bar and a padded, scrolling column. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackerScaffold(
    title: String,
    onNavigateBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = title,
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = onNavigateBack != null,
                navigationContent = { if (onNavigateBack != null) NavigationContent(onNavigateBack) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().hazeSource(state = hazeState),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = content
        )
    }
}
