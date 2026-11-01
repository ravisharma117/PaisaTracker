package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.AccountBalanceEntity
import com.naxits.paisatracker.data.database.entity.LendBorrowTransactionEntity
import com.naxits.paisatracker.data.database.entity.LendBorrowType
import com.naxits.paisatracker.data.database.entity.NetWorthItemEntity
import com.naxits.paisatracker.data.database.entity.NetWorthItemValueEntity
import com.naxits.paisatracker.domain.model.AccountKind
import com.naxits.paisatracker.domain.model.CashAccount
import com.naxits.paisatracker.domain.model.accountKindOf
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Net worth as it stood on a given day, worked out from the records the app already keeps. The
 * same function gives today's figure and every past month, so the two can never disagree.
 *
 * Past months use today's exchange rates, so they are estimates for anything in a foreign
 * currency. Amounts are converted with [factors] (units of the display currency per unit of the
 * given currency); a currency missing from the map is taken as already in the display currency.
 */
object NetWorthHistory {

    private const val MAX_BACKFILL_MONTHS = 120

    fun breakdownAt(
        date: LocalDate,
        balances: List<AccountBalanceEntity>,
        lendEntries: List<LendBorrowTransactionEntity>,
        activePersonIds: Set<Long>,
        items: List<NetWorthItemEntity>,
        values: List<NetWorthItemValueEntity>,
        factors: Map<String, BigDecimal>,
        currency: String
    ): NetWorthBreakdown {
        fun convert(amount: BigDecimal, from: String): BigDecimal =
            amount.multiply(factors[from] ?: BigDecimal.ONE)

        val endOfDay = date.atTime(LocalTime.MAX)

        val accounts = balances
            .filter { !it.timestamp.isAfter(endOfDay) }
            .groupBy { it.bankName to it.accountLast4 }
            .mapNotNull { (_, rows) ->
                val latest = rows.maxByOrNull { it.timestamp } ?: return@mapNotNull null
                val kind = accountKindOf(latest.bankName, latest.accountLast4, latest.isCreditCard, latest.isWallet)
                val name = if (kind == AccountKind.CASH) CashAccount.BANK_NAME else "${latest.bankName} ••${latest.accountLast4}"
                AccountAmount(kind, name, convert(latest.balance, latest.currency))
            }

        val (lent, borrowed) = outstandingLending(date, lendEntries, activePersonIds, ::convert)

        val itemAmounts = items.mapNotNull { item ->
            val anchor = latestValue(item, values, date) ?: return@mapNotNull null
            val balance = if (item.type.isLoan) {
                LoanMath.balanceOn(anchor.first, anchor.second, item.annualRatePercent, item.emiAmount, item.emiDay, date)
            } else anchor.first
            val estimated = item.type.isLoan && balance.compareTo(anchor.first) != 0
            ItemAmount(item.id, item.name, item.type, convert(balance, item.currency), estimated)
        }

        return NetWorthCalculator.calculate(NetWorthInput(accounts, lent, borrowed, itemAmounts), currency)
    }

    /** What is still owed to the user and by the user on [date], in the display currency. */
    private fun outstandingLending(
        date: LocalDate,
        entries: List<LendBorrowTransactionEntity>,
        activePersonIds: Set<Long>,
        convert: (BigDecimal, String) -> BigDecimal
    ): Pair<BigDecimal, BigDecimal> {
        val upToDate = entries.filter { it.personId in activePersonIds && !it.date.toLocalDate().isAfter(date) }
        var lent = BigDecimal.ZERO
        var borrowed = BigDecimal.ZERO
        upToDate.groupBy { it.personId to it.currency }.forEach { (key, rows) ->
            fun sum(type: LendBorrowType, rememberOnly: Boolean = false) = rows
                .filter { it.type == type && it.rememberOnly == rememberOnly }
                .fold(BigDecimal.ZERO) { a, e -> a.add(e.amount) }
            // Entries kept only as a reminder are not counted; repayments go to the counted ones first
            val owedToUser = LendingBalance.outstanding(
                sum(LendBorrowType.LENT), sum(LendBorrowType.LENT, rememberOnly = true), sum(LendBorrowType.SETTLEMENT_LENT)
            ).counted
            val userOwes = LendingBalance.outstanding(
                sum(LendBorrowType.BORROWED), sum(LendBorrowType.BORROWED, rememberOnly = true), sum(LendBorrowType.SETTLEMENT_BORROWED)
            ).counted
            lent = lent.add(convert(owedToUser, key.second))
            borrowed = borrowed.add(convert(userOwes, key.second))
        }
        return lent to borrowed
    }

    /** The value an item had on [date] and the day it was recorded, or null if it did not exist yet. */
    private fun latestValue(
        item: NetWorthItemEntity,
        values: List<NetWorthItemValueEntity>,
        date: LocalDate
    ): Pair<BigDecimal, LocalDate>? {
        val recorded = values.filter { it.itemId == item.id && !it.date.isAfter(date) }.maxByOrNull { it.date }
        if (recorded != null) return recorded.value to recorded.date
        return if (!item.asOfDate.isAfter(date) && values.none { it.itemId == item.id }) item.currentValue to item.asOfDate else null
    }

    /**
     * The months to rebuild: every month from the earliest record up to the one before [current]
     * that has no snapshot yet. Never more than ten years back.
     */
    fun monthsToBackfill(earliest: YearMonth?, existing: Set<String>, current: YearMonth): List<YearMonth> {
        if (earliest == null) return emptyList()
        val start = maxOf(earliest, current.minusMonths(MAX_BACKFILL_MONTHS.toLong()))
        return generateSequence(start) { it.plusMonths(1) }
            .takeWhile { it.isBefore(current) }
            .filter { it.toString() !in existing }
            .toList()
    }

    fun roundForStorage(amount: BigDecimal): BigDecimal = amount.setScale(2, RoundingMode.HALF_UP)
}
