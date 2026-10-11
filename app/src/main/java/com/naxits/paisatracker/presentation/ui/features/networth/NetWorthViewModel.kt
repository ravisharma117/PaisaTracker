package com.naxits.paisatracker.presentation.ui.features.networth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naxits.paisatracker.data.database.entity.NetWorthItemEntity
import com.naxits.paisatracker.data.database.entity.NetWorthSnapshotEntity
import com.naxits.paisatracker.data.repository.CurrencyRepository
import com.naxits.paisatracker.domain.service.NetWorthBreakdown
import com.naxits.paisatracker.domain.service.NetWorthService
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One bar of the trend chart. */
data class TrendPoint(val label: String, val netWorth: BigDecimal, val isEstimate: Boolean)

/** How the figure moved against an earlier month; null when there is nothing to compare with. */
data class NetWorthChange(val amount: BigDecimal, val percent: BigDecimal?)

data class NetWorthUiState(
    val breakdown: NetWorthBreakdown? = null,
    val monthly: List<TrendPoint> = emptyList(),
    val yearly: List<TrendPoint> = emptyList(),
    val sinceLastMonth: NetWorthChange? = null,
    val overTheYear: NetWorthChange? = null
)

@HiltViewModel
class NetWorthViewModel @Inject constructor(
    private val service: NetWorthService,
    private val currencyRepository: CurrencyRepository
) : ViewModel() {

    val uiState: StateFlow<NetWorthUiState> = combine(
        service.observeCurrent(currencyRepository.effectiveBaseCurrencyCode),
        service.observeSnapshots()
    ) { breakdown, snapshots -> buildState(breakdown, snapshots, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NetWorthUiState())

    val items: StateFlow<List<NetWorthItemEntity>> = service.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val baseCurrency: StateFlow<String> = currencyRepository.effectiveBaseCurrencyCode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "INR")

    init {
        // Keeps this month's snapshot fresh and fills in months that have none
        viewModelScope.launch {
            currencyRepository.effectiveBaseCurrencyCode.collectLatest { service.refreshSnapshots(it) }
        }
    }

    fun add(item: NetWorthItemEntity) = viewModelScope.launch {
        service.addItem(item)
        service.refreshSnapshots(baseCurrency.value)
    }

    /** Saves edited details, and a new value when it changed. */
    fun save(item: NetWorthItemEntity, newValue: BigDecimal, asOf: LocalDate) = viewModelScope.launch {
        // Compare with what was stored before the edit, so a changed value is kept in the history
        val before = items.value.firstOrNull { it.id == item.id }
        service.updateItem(item)
        if (before == null || newValue.compareTo(before.currentValue) != 0 || asOf != before.asOfDate) {
            service.updateValue(item.id, newValue, asOf)
        }
        service.refreshSnapshots(baseCurrency.value)
    }

    fun delete(itemId: Long) = viewModelScope.launch {
        service.deleteItem(itemId)
        service.refreshSnapshots(baseCurrency.value)
    }

    companion object {
        private const val MONTHS_SHOWN = 12

        /** Pure so it can be tested: turns the live figure and stored snapshots into the screen state. */
        fun buildState(
            breakdown: NetWorthBreakdown,
            snapshots: List<NetWorthSnapshotEntity>,
            today: LocalDate
        ): NetWorthUiState {
            val current = YearMonth.from(today)
            val byMonth = snapshots.associateBy { it.yearMonth }
            val now = breakdown.netWorth

            // The running month always uses the live figure
            val series: List<Triple<YearMonth, BigDecimal, Boolean>> =
                (snapshots.map { Triple(YearMonth.parse(it.yearMonth), it.netWorth, it.isEstimate) }
                    .filter { it.first != current } + Triple(current, now, false))
                    .sortedBy { it.first }

            fun change(reference: BigDecimal?): NetWorthChange? = reference?.let {
                val delta = now.subtract(it)
                val percent = if (it.signum() != 0) {
                    delta.multiply(BigDecimal(100)).divide(it.abs(), 1, RoundingMode.HALF_UP)
                } else null
                NetWorthChange(delta, percent)
            }

            val monthly = series.takeLast(MONTHS_SHOWN).map { (month, value, estimate) ->
                TrendPoint(month.toString(), value, estimate)
            }
            // One bar per year: the latest month recorded in it
            val yearly = series.groupBy { it.first.year }.toSortedMap().map { (year, months) ->
                val last = months.maxBy { it.first }
                TrendPoint(year.toString(), last.second, last.third)
            }

            return NetWorthUiState(
                breakdown = breakdown,
                monthly = monthly,
                yearly = yearly,
                sinceLastMonth = change(byMonth[current.minusMonths(1).toString()]?.netWorth),
                overTheYear = change(byMonth[current.minusMonths(12).toString()]?.netWorth)
            )
        }
    }
}
