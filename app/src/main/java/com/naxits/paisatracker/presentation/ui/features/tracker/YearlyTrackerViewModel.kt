package com.naxits.paisatracker.presentation.ui.features.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naxits.paisatracker.data.database.entity.NetWorthSnapshotEntity
import com.naxits.paisatracker.domain.service.FinancialYear
import com.naxits.paisatracker.domain.service.LendingFigures
import com.naxits.paisatracker.domain.service.NetWorthService
import com.naxits.paisatracker.domain.service.PeriodCalculator
import com.naxits.paisatracker.domain.service.PeriodFigures
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
import kotlinx.coroutines.flow.stateIn

/** One month of the year table. Future months have nothing to show yet. */
data class YearMonthRow(val month: YearMonth, val figures: PeriodFigures, val isFuture: Boolean)

data class CategoryShare(val category: String, val total: BigDecimal)

data class NetWorthMark(val month: YearMonth, val value: BigDecimal, val isEstimate: Boolean)

data class YearlyTrackerUiState(
    /** The calendar year the financial year starts in. */
    val startYear: Int = FinancialYear.startYearOf(LocalDate.now()),
    val currency: String = "",
    val isLoading: Boolean = true,
    val rows: List<YearMonthRow> = emptyList(),
    val total: PeriodFigures = PeriodFigures.ZERO,
    val lending: LendingFigures = LendingFigures.ZERO,
    val lendingByMonth: List<Pair<YearMonth, LendingFigures>> = emptyList(),
    /** The biggest spending categories of the year; the rest are folded into one "other" entry. */
    val topCategories: List<CategoryShare> = emptyList(),
    val otherCategoriesTotal: BigDecimal = BigDecimal.ZERO,
    /** Month-end net worth for the months that have a snapshot. */
    val netWorth: List<NetWorthMark> = emptyList()
) {
    val canGoNext: Boolean get() = startYear < FinancialYear.startYearOf(LocalDate.now())
    val hasData: Boolean get() = !total.isEmpty || !lending.isEmpty
}

@HiltViewModel
class YearlyTrackerViewModel @Inject constructor(
    private val summary: GetPeriodSummaryUseCase,
    private val netWorth: NetWorthService
) : ViewModel() {

    private val selected = MutableStateFlow(FinancialYear.startYearOf(LocalDate.now()))
    private var initialised = false

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<YearlyTrackerUiState> = selected
        .flatMapLatest { startYear ->
            combine(
                summary.observe(FinancialYear.start(startYear), FinancialYear.end(startYear)),
                netWorth.observeSnapshots()
            ) { data, snapshots -> YearlyTrackerBuilder.build(startYear, data, snapshots, LocalDate.now()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), YearlyTrackerUiState())

    /** Opens on [startYear] the first time only. */
    fun start(startYear: Int?) {
        if (initialised || startYear == null) return
        initialised = true
        selected.value = startYear
    }

    fun previous() = select(selected.value - 1)

    fun next() {
        if (selected.value < FinancialYear.startYearOf(LocalDate.now())) select(selected.value + 1)
    }

    fun select(startYear: Int) {
        initialised = true
        selected.value = startYear.coerceAtMost(FinancialYear.startYearOf(LocalDate.now()))
    }
}

/** Turns one year's data into the year screen's figures; the part that needs no database. */
object YearlyTrackerBuilder {

    const val TOP_CATEGORIES = 6

    fun build(
        startYear: Int,
        data: PeriodData,
        snapshots: List<NetWorthSnapshotEntity>,
        today: LocalDate
    ): YearlyTrackerUiState {
        val months = FinancialYear.months(startYear)
        val thisMonth = YearMonth.from(today)
        val rows = PeriodCalculator.byMonth(data.entries, months)
            .map { YearMonthRow(it.month, it.figures, isFuture = it.month.isAfter(thisMonth)) }

        val byCategory = PeriodCalculator.expensesByCategory(data.entries)
            .map { CategoryShare(it.key, it.value) }
            .filter { it.total.signum() > 0 }
            .sortedByDescending { it.total }
        val top = byCategory.take(TOP_CATEGORIES)
        val other = byCategory.drop(TOP_CATEGORIES).fold(BigDecimal.ZERO) { acc, c -> acc.add(c.total) }

        val inYear = months.map { it.toString() }.toSet()
        val marks = snapshots.filter { it.yearMonth in inYear }
            .map { NetWorthMark(YearMonth.parse(it.yearMonth), it.netWorth, it.isEstimate) }
            .sortedBy { it.month }

        return YearlyTrackerUiState(
            startYear = startYear,
            currency = data.currency,
            isLoading = false,
            rows = rows,
            total = PeriodCalculator.figures(data.entries),
            lending = PeriodCalculator.lending(data.lending, FinancialYear.start(startYear), FinancialYear.end(startYear)),
            lendingByMonth = PeriodCalculator.lendingByMonth(data.lending, months),
            topCategories = top,
            otherCategoriesTotal = other,
            netWorth = marks
        )
    }
}
