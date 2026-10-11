package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.LendBorrowType
import com.naxits.paisatracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

enum class FlowKind { INCOME, EXPENSE }

/**
 * The tracker rules for what counts as money in and money out. Money lent and borrowed is shown
 * on its own, and transfers, investments and balance updates never count.
 */
object PeriodRules {

    /** Types the tracker query has to load. */
    val COUNTED_TYPES: List<TransactionType> =
        listOf(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.CREDIT)

    fun kindOf(type: TransactionType): FlowKind? = when (type) {
        TransactionType.INCOME -> FlowKind.INCOME
        TransactionType.EXPENSE, TransactionType.CREDIT -> FlowKind.EXPENSE
        else -> null
    }

    /** Same fallback the Analysis tab uses for a transaction without a category. */
    const val UNCATEGORISED = "Miscellaneous"
}

/** One counted transaction, already converted to the display currency. */
data class PeriodEntry(
    val kind: FlowKind,
    val amount: BigDecimal,
    val date: LocalDate,
    val category: String,
    val bankName: String?,
    val accountLast4: String?
)

/** One lending record, already converted to the display currency. */
data class LendEntry(val type: LendBorrowType, val amount: BigDecimal, val date: LocalDate)

data class PeriodFigures(val income: BigDecimal, val expenses: BigDecimal) {
    val savings: BigDecimal get() = income.subtract(expenses)

    /** Savings as a share of income, or null when there was no income to take a share of. */
    val savingsRatePercent: Float?
        get() = if (income.signum() <= 0) null
        else savings.multiply(HUNDRED).divide(income, 1, RoundingMode.HALF_UP).toFloat()

    val isEmpty: Boolean get() = income.signum() == 0 && expenses.signum() == 0

    companion object {
        val ZERO = PeriodFigures(BigDecimal.ZERO, BigDecimal.ZERO)
        private val HUNDRED = BigDecimal(100)
    }
}

data class MonthFigures(val month: YearMonth, val figures: PeriodFigures)

data class AccountFigures(
    val bankName: String?,
    val accountLast4: String?,
    val spent: BigDecimal,
    val received: BigDecimal
)

data class LendingFigures(
    val lent: BigDecimal,
    val borrowed: BigDecimal,
    /** Paid back to people the user borrowed from. */
    val repaid: BigDecimal,
    /** Returned by people the user lent to. */
    val receivedBack: BigDecimal
) {
    val isEmpty: Boolean
        get() = lent.signum() == 0 && borrowed.signum() == 0 && repaid.signum() == 0 && receivedBack.signum() == 0

    companion object {
        val ZERO = LendingFigures(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    }
}

/** A change between two figures. The percentage is null when the earlier one was zero. */
data class Change(val amount: BigDecimal, val percent: Float?)

/**
 * The arithmetic behind the Month and Year trackers, free of Android and the database so it can
 * be tested with fixed data. Entries are expected to be limited to the period being shown.
 */
object PeriodCalculator {

    fun figures(entries: List<PeriodEntry>): PeriodFigures = PeriodFigures(
        income = entries.filter { it.kind == FlowKind.INCOME }.sumAmounts(),
        expenses = entries.filter { it.kind == FlowKind.EXPENSE }.sumAmounts()
    )

    fun figuresForMonth(entries: List<PeriodEntry>, month: YearMonth): PeriodFigures =
        figures(entries.filter { YearMonth.from(it.date) == month })

    /** One row per month in [months], empty months included, in the order given. */
    fun byMonth(entries: List<PeriodEntry>, months: List<YearMonth>): List<MonthFigures> {
        val grouped = entries.groupBy { YearMonth.from(it.date) }
        return months.map { MonthFigures(it, figures(grouped[it].orEmpty())) }
    }

    fun expensesByCategory(entries: List<PeriodEntry>): Map<String, BigDecimal> =
        entries.filter { it.kind == FlowKind.EXPENSE }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumAmounts() }

    /** Spending and income per account, biggest spender first. */
    fun byAccount(entries: List<PeriodEntry>): List<AccountFigures> =
        entries.groupBy { it.bankName to it.accountLast4 }
            .map { (key, list) ->
                AccountFigures(
                    bankName = key.first,
                    accountLast4 = key.second,
                    spent = list.filter { it.kind == FlowKind.EXPENSE }.sumAmounts(),
                    received = list.filter { it.kind == FlowKind.INCOME }.sumAmounts()
                )
            }
            .sortedByDescending { it.spent }

    fun lending(entries: List<LendEntry>, from: LocalDate, to: LocalDate): LendingFigures {
        val inRange = entries.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        fun sum(type: LendBorrowType) = inRange.filter { it.type == type }.fold(BigDecimal.ZERO) { a, e -> a.add(e.amount) }
        return LendingFigures(
            lent = sum(LendBorrowType.LENT),
            borrowed = sum(LendBorrowType.BORROWED),
            repaid = sum(LendBorrowType.SETTLEMENT_BORROWED),
            receivedBack = sum(LendBorrowType.SETTLEMENT_LENT)
        )
    }

    /** Lent and borrowed for each month in [months], empty months included. */
    fun lendingByMonth(entries: List<LendEntry>, months: List<YearMonth>): List<Pair<YearMonth, LendingFigures>> =
        months.map { it to lending(entries, it.atDay(1), it.atEndOfMonth()) }

    fun change(current: BigDecimal, previous: BigDecimal): Change {
        val delta = current.subtract(previous)
        val percent = if (previous.signum() == 0) null
        else delta.multiply(BigDecimal(100)).divide(previous.abs(), 1, RoundingMode.HALF_UP).toFloat()
        return Change(delta, percent)
    }

    private fun List<PeriodEntry>.sumAmounts(): BigDecimal = fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amount) }
}
