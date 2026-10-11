package com.naxits.paisatracker.presentation.ui.features.tracker

import androidx.lifecycle.ViewModel
import com.naxits.paisatracker.data.database.dao.BillPaymentDao
import com.naxits.paisatracker.data.database.dao.BudgetDao
import com.naxits.paisatracker.data.database.dao.RecurringTransactionDao
import com.naxits.paisatracker.data.database.dao.SubscriptionDao
import com.naxits.paisatracker.domain.service.AccountFigures
import com.naxits.paisatracker.domain.service.BillsMonth
import com.naxits.paisatracker.domain.service.CategoryBudgetCalculator
import com.naxits.paisatracker.domain.service.CategoryBudgetTable
import com.naxits.paisatracker.domain.service.Change
import com.naxits.paisatracker.domain.service.LendingFigures
import com.naxits.paisatracker.domain.service.MonthActivityCalculator
import com.naxits.paisatracker.domain.service.MonthBudget
import com.naxits.paisatracker.domain.service.PeriodCalculator
import com.naxits.paisatracker.domain.service.PeriodFigures
import com.naxits.paisatracker.domain.service.RecurringMonth
import com.naxits.paisatracker.domain.usecase.GetPeriodSummaryUseCase
import com.naxits.paisatracker.domain.usecase.PeriodData
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

data class MonthlyTrackerUiState(
    val month: YearMonth = YearMonth.now(),
    val currency: String = "",
    val isLoading: Boolean = true,
    val figures: PeriodFigures = PeriodFigures.ZERO,
    val previous: PeriodFigures = PeriodFigures.ZERO,
    val table: CategoryBudgetTable = CategoryBudgetCalculator.table(emptyMap(), emptyMap()),
    /** The month's overall budget, or null when no budget applies. */
    val overallBudget: BigDecimal? = null,
    val accounts: List<AccountFigures> = emptyList(),
    val lending: LendingFigures = LendingFigures.ZERO,
    val recurring: RecurringMonth? = null,
    val bills: BillsMonth? = null
) {
    val canGoNext: Boolean get() = month < YearMonth.now()
    val incomeChange: Change get() = PeriodCalculator.change(figures.income, previous.income)
    val expenseChange: Change get() = PeriodCalculator.change(figures.expenses, previous.expenses)
    val savingsChange: Change get() = PeriodCalculator.change(figures.savings, previous.savings)

    /** 0..1 of the overall budget used, capped for the progress bar. */
    val budgetUsedFraction: Float
        get() = overallBudget?.takeIf { it.signum() > 0 }
            ?.let { (figures.expenses.toFloat() / it.toFloat()).coerceIn(0f, 1f) } ?: 0f
    val isOverBudget: Boolean get() = overallBudget != null && figures.expenses > overallBudget
}

@HiltViewModel
class MonthlyTrackerViewModel @Inject constructor(
    private val summary: GetPeriodSummaryUseCase,
    private val budgetDao: BudgetDao,
    private val recurringDao: RecurringTransactionDao,
    private val subscriptionDao: SubscriptionDao,
    private val billPaymentDao: BillPaymentDao
) : ViewModel() {

    private val selected = MutableStateFlow(YearMonth.now())
    private var chosenByUser = false

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MonthlyTrackerUiState> = selected
        .flatMapLatest { month ->
            combine(
                summary.observe(month.minusMonths(1).atDay(1), month.atEndOfMonth()),
                budgetDao.getAllBudgets(),
                budgetDao.getAllCategoryLimits()
            ) { data, budgets, limits ->
                val budget = CategoryBudgetCalculator.budgetFor(month, budgets, limits, data::factorOf)
                build(month, data, budget)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthlyTrackerUiState())

    /** Opens on [month] the first time only, so rotating or returning does not undo a choice. */
    fun start(month: YearMonth?) {
        if (chosenByUser || month == null) return
        chosenByUser = true
        selected.value = month
    }

    fun previous() = select(selected.value.minusMonths(1))

    fun next() {
        if (selected.value < YearMonth.now()) select(selected.value.plusMonths(1))
    }

    fun select(month: YearMonth) {
        chosenByUser = true
        selected.value = month.coerceAtMost(YearMonth.now())
    }

    private suspend fun build(month: YearMonth, data: PeriodData, budget: MonthBudget): MonthlyTrackerUiState {
        val state = MonthlyTrackerBuilder.build(month, data, budget)
        val today = LocalDate.now()
        val recurring = MonthActivityCalculator.recurring(
            month, today,
            schedules = recurringDao.getAll(),
            occurrences = recurringDao.getAllOccurrences(),
            amountOf = { it.amount.multiply(data.factorOf(it.currency)) }
        )
        val bills = MonthActivityCalculator.bills(
            month,
            subscriptions = subscriptionDao.getActiveList(),
            payments = billPaymentDao.getAll(),
            amountOf = { sub, amount -> amount.multiply(data.factorOf(sub.currency)) }
        )
        return state.copy(recurring = recurring, bills = bills)
    }
}

/** Turns one period's data into the month screen's figures; the part that needs no database. */
object MonthlyTrackerBuilder {

    fun build(month: YearMonth, data: PeriodData, budget: MonthBudget): MonthlyTrackerUiState {
        val inMonth = data.entries.filter { YearMonth.from(it.date) == month }
        val figures = PeriodCalculator.figures(inMonth)
        val previous = PeriodCalculator.figuresForMonth(data.entries, month.minusMonths(1))
        return MonthlyTrackerUiState(
            month = month,
            currency = data.currency,
            isLoading = false,
            figures = figures,
            previous = previous,
            table = CategoryBudgetCalculator.table(PeriodCalculator.expensesByCategory(inMonth), budget.categoryLimits),
            overallBudget = budget.overall,
            accounts = PeriodCalculator.byAccount(inMonth),
            lending = PeriodCalculator.lending(data.lending, month.atDay(1), month.atEndOfMonth())
        )
    }
}
