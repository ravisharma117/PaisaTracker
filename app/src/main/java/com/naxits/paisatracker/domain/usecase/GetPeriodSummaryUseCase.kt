package com.naxits.paisatracker.domain.usecase

import com.naxits.paisatracker.data.currency.CurrencyConversionService
import com.naxits.paisatracker.data.database.dao.LendBorrowDao
import com.naxits.paisatracker.data.database.dao.TrackerRow
import com.naxits.paisatracker.data.database.dao.TransactionDao
import com.naxits.paisatracker.data.repository.CurrencyRepository
import com.naxits.paisatracker.domain.service.LendEntry
import com.naxits.paisatracker.domain.service.PeriodEntry
import com.naxits.paisatracker.domain.service.PeriodRules
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/** Everything the trackers add up for a date range, in [currency]. */
data class PeriodData(
    val currency: String,
    val entries: List<PeriodEntry>,
    val lending: List<LendEntry>,
    /** Units of [currency] per unit of the given currency; a missing one is already in [currency]. */
    val factors: Map<String, BigDecimal>
) {
    fun factorOf(from: String): BigDecimal = factors[from] ?: BigDecimal.ONE
}

/**
 * Supplies the Month and Year trackers with the transactions that count, by the one set of rules
 * in [PeriodRules]: deleted rows are skipped by the query, income counts as income, expense and
 * credit-card spends count as expenses, and transfers, investments and balance updates stay out.
 * Everything is converted to the base currency with one rate per currency.
 */
class GetPeriodSummaryUseCase @Inject constructor(
    private val transactionDao: TransactionDao,
    private val lendDao: LendBorrowDao,
    private val currencyRepository: CurrencyRepository,
    private val conversion: CurrencyConversionService
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(from: LocalDate, to: LocalDate): Flow<PeriodData> =
        combine(currencyRepository.effectiveBaseCurrencyCode, conversion.rateChangeTrigger) { base, _ -> base }
            .flatMapLatest { base ->
                combine(
                    transactionDao.observeTrackerRows(from.atStartOfDay(), to.atTime(LocalTime.MAX)),
                    lendDao.getAllTransactions()
                ) { rows, lend ->
                    val currencies = rows.map { it.currency } + lend.map { it.currency }
                    val factors = currencies.toSet().filter { it != base }.associateWith { currency ->
                        // A currency without a known rate is counted at face value rather than dropped
                        conversion.getExchangeRate(currency, base) ?: BigDecimal.ONE
                    }
                    fun convert(amount: BigDecimal, currency: String) = amount.multiply(factors[currency] ?: BigDecimal.ONE)

                    PeriodData(
                        currency = base,
                        entries = rows.mapNotNull { it.toEntry(::convert) },
                        lending = lend.map { LendEntry(it.type, convert(it.amount, it.currency), it.date.toLocalDate()) },
                        factors = factors
                    )
                }
            }

    private fun TrackerRow.toEntry(convert: (BigDecimal, String) -> BigDecimal): PeriodEntry? {
        val kind = PeriodRules.kindOf(type) ?: return null
        return PeriodEntry(
            kind = kind,
            amount = convert(amount, currency),
            date = dateTime.toLocalDate(),
            category = category ?: PeriodRules.UNCATEGORISED,
            bankName = bankName,
            accountLast4 = accountNumber
        )
    }
}
