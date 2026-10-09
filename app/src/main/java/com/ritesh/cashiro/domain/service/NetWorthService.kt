package com.ritesh.cashiro.domain.service

import com.ritesh.cashiro.data.currency.CurrencyConversionService
import com.ritesh.cashiro.data.database.dao.AccountBalanceDao
import com.ritesh.cashiro.data.database.dao.LendBorrowDao
import com.ritesh.cashiro.data.database.dao.NetWorthDao
import com.ritesh.cashiro.data.database.entity.AccountBalanceEntity
import com.ritesh.cashiro.data.database.entity.LendBorrowPersonEntity
import com.ritesh.cashiro.data.database.entity.LendBorrowTransactionEntity
import com.ritesh.cashiro.data.database.entity.NetWorthItemEntity
import com.ritesh.cashiro.data.database.entity.NetWorthItemValueEntity
import com.ritesh.cashiro.data.database.entity.NetWorthSnapshotEntity
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Net worth for the app: the current figure, the stored monthly snapshots, and the holdings and
 * loans the user enters by hand. The figure for any day comes from [NetWorthHistory], so today's
 * number and the history are always calculated the same way.
 */
@Singleton
class NetWorthService @Inject constructor(
    private val netWorthDao: NetWorthDao,
    private val balanceDao: AccountBalanceDao,
    private val lendDao: LendBorrowDao,
    private val conversion: CurrencyConversionService
) {
    private val snapshotLock = Mutex()

    private data class Raw(
        val balances: List<AccountBalanceEntity>,
        val lend: List<LendBorrowTransactionEntity>,
        val persons: List<LendBorrowPersonEntity>,
        val items: List<NetWorthItemEntity>
    )

    /** Net worth now, in [currency], updated whenever any of its inputs change. */
    fun observeCurrent(currency: Flow<String>): Flow<NetWorthBreakdown> {
        val raw = combine(
            balanceDao.getAllBalances(),
            lendDao.getAllTransactions(),
            lendDao.getAllPersons(),
            netWorthDao.observeItems()
        ) { balances, lend, persons, items -> Raw(balances, lend, persons, items) }

        return combine(raw, currency, conversion.rateChangeTrigger) { data, cur, _ -> data to cur }
            .map { (data, cur) -> breakdownFor(LocalDate.now(), data, cur) }
    }

    fun observeSnapshots(): Flow<List<NetWorthSnapshotEntity>> = netWorthDao.observeSnapshots()

    fun observeItems(): Flow<List<NetWorthItemEntity>> = netWorthDao.observeItems()

    /**
     * Writes this month's snapshot, and rebuilds earlier months that have none from the records
     * the app already keeps. Rebuilt months are marked as estimates; a month that already has a
     * snapshot is never overwritten by an estimate.
     */
    suspend fun refreshSnapshots(currency: String, today: LocalDate = LocalDate.now()) = snapshotLock.withLock {
        val data = Raw(
            balances = balanceDao.getAllBalancesList(),
            lend = lendDao.getAllTransactionsList(),
            persons = lendDao.getAllPersonsList(),
            items = netWorthDao.getAllItems().filter { it.isActive }
        )
        val values = netWorthDao.getAllValues()
        val factors = factorsFor(data, currency)
        val activePersons = data.persons.filter { !it.isArchived }.map { it.id }.toSet()

        fun snapshot(month: YearMonth, date: LocalDate, estimate: Boolean): NetWorthSnapshotEntity {
            val b = NetWorthHistory.breakdownAt(date, data.balances, data.lend, activePersons, data.items, values, factors, currency)
            return NetWorthSnapshotEntity(
                yearMonth = month.toString(),
                assets = NetWorthHistory.roundForStorage(b.totalAssets),
                liabilities = NetWorthHistory.roundForStorage(b.totalLiabilities),
                netWorth = NetWorthHistory.roundForStorage(b.netWorth),
                currency = currency,
                isEstimate = estimate,
                capturedAt = LocalDateTime.now()
            )
        }

        val current = YearMonth.from(today)
        val existing = netWorthDao.getAllSnapshots()
        val earliest = earliestMonth(data, values)
        val missing = NetWorthHistory.monthsToBackfill(earliest, existing.map { it.yearMonth }.toSet(), current)

        // A past month captured before it ended (the app was not opened at month end) is rebuilt
        // from the month-end records, which is closer than a figure from the middle of the month
        val stale = existing.filter { row ->
            val month = YearMonth.parse(row.yearMonth)
            month.isBefore(current) && !row.isEstimate && row.capturedAt.toLocalDate().isBefore(month.atEndOfMonth())
        }.map { YearMonth.parse(it.yearMonth) }
        val rebuild = (missing + stale).distinct()

        if (rebuild.isNotEmpty()) {
            val ids = existing.associate { it.yearMonth to it.id }
            netWorthDao.insertSnapshots(rebuild.map { month ->
                snapshot(month, month.atEndOfMonth(), estimate = true).copy(id = ids[month.toString()] ?: 0)
            })
        }
        // The running month is real, not an estimate, and is refreshed every time
        netWorthDao.upsertSnapshot(snapshot(current, today, estimate = false).let { fresh ->
            existing.firstOrNull { it.yearMonth == fresh.yearMonth }?.let { fresh.copy(id = it.id) } ?: fresh
        })
    }

    // --- Holdings and loans entered by hand -------------------------------------------------

    suspend fun addItem(item: NetWorthItemEntity): Long {
        val id = netWorthDao.insertItem(item.copy(id = 0))
        netWorthDao.upsertValue(NetWorthItemValueEntity(itemId = id, value = item.currentValue, date = item.asOfDate))
        return id
    }

    /** Edits a holding's details. The value is changed with [updateValue] so history is kept. */
    suspend fun updateItem(item: NetWorthItemEntity) {
        netWorthDao.updateItem(item.copy(updatedAt = LocalDateTime.now()))
    }

    /**
     * A new value for a holding, or the real balance of a loan, as of [date]. The old values stay
     * in the history, and a loan's schedule restarts from this figure.
     */
    suspend fun updateValue(itemId: Long, value: BigDecimal, date: LocalDate = LocalDate.now()) {
        val item = netWorthDao.getItem(itemId) ?: return
        netWorthDao.upsertValue(NetWorthItemValueEntity(itemId = itemId, value = value, date = date))
        val latest = netWorthDao.getAllValues().filter { it.itemId == itemId }.maxByOrNull { it.date }
        if (latest != null) {
            netWorthDao.updateItem(
                item.copy(currentValue = latest.value, asOfDate = latest.date, updatedAt = LocalDateTime.now())
            )
        }
    }

    suspend fun deleteItem(itemId: Long) = netWorthDao.deleteItem(itemId)

    fun observeValues(itemId: Long): Flow<List<NetWorthItemValueEntity>> = netWorthDao.observeValues(itemId)

    // --- Internals ---------------------------------------------------------------------------

    private suspend fun breakdownFor(date: LocalDate, data: Raw, currency: String): NetWorthBreakdown {
        val values = netWorthDao.getAllValues()
        val activePersons = data.persons.filter { !it.isArchived }.map { it.id }.toSet()
        return NetWorthHistory.breakdownAt(
            date, data.balances, data.lend, activePersons, data.items, values, factorsFor(data, currency), currency
        )
    }

    private suspend fun factorsFor(data: Raw, currency: String): Map<String, BigDecimal> {
        val currencies = (data.balances.map { it.currency } + data.lend.map { it.currency } + data.items.map { it.currency })
            .toSet() - currency
        return currencies.associateWith { from ->
            // A currency without a known rate is counted at face value rather than dropped
            conversion.getExchangeRate(from, currency) ?: BigDecimal.ONE
        }
    }

    private fun earliestMonth(data: Raw, values: List<NetWorthItemValueEntity>): YearMonth? {
        val dates = data.balances.map { it.timestamp.toLocalDate() } +
            data.lend.map { it.date.toLocalDate() } +
            values.map { it.date }
        return dates.minOrNull()?.let { YearMonth.from(it) }
    }
}
