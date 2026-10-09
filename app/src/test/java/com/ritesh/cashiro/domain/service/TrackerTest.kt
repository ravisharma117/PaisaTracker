package com.ritesh.cashiro.domain.service

import com.ritesh.cashiro.data.database.entity.BillPaymentEntity
import com.ritesh.cashiro.data.database.entity.BillPaymentStatus
import com.ritesh.cashiro.data.database.entity.BudgetCategoryLimitEntity
import com.ritesh.cashiro.data.database.entity.BudgetEntity
import com.ritesh.cashiro.data.database.entity.BudgetPeriod
import com.ritesh.cashiro.data.database.entity.BudgetType
import com.ritesh.cashiro.data.database.entity.LendBorrowType
import com.ritesh.cashiro.data.database.entity.NetWorthSnapshotEntity
import com.ritesh.cashiro.data.database.entity.OccurrenceStatus
import com.ritesh.cashiro.data.database.entity.RecurringOccurrenceEntity
import com.ritesh.cashiro.data.database.entity.RecurringState
import com.ritesh.cashiro.data.database.entity.RecurringTransactionEntity
import com.ritesh.cashiro.data.database.entity.SubscriptionEntity
import com.ritesh.cashiro.data.database.entity.TransactionType
import com.ritesh.cashiro.domain.usecase.PeriodData
import com.ritesh.cashiro.presentation.ui.features.tracker.MonthlyTrackerBuilder
import com.ritesh.cashiro.presentation.ui.features.tracker.YearlyTrackerBuilder
import com.ritesh.cashiro.utils.CompactAmount
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

private fun d(text: String) = LocalDate.parse(text)
private fun bd(text: String) = BigDecimal(text)

private fun assertMoney(expected: String, actual: BigDecimal?) =
    assertEquals(0, bd(expected).compareTo(actual), "expected $expected but was $actual")

private fun income(amount: String, date: String, category: String = "Salary", bank: String? = "Bank A", last4: String? = "1111") =
    PeriodEntry(FlowKind.INCOME, bd(amount), d(date), category, bank, last4)

private fun expense(amount: String, date: String, category: String = "Food", bank: String? = "Bank A", last4: String? = "1111") =
    PeriodEntry(FlowKind.EXPENSE, bd(amount), d(date), category, bank, last4)

class PeriodRulesTest {

    @Test
    fun incomeAndExpenseCountAndCreditCardSpendsAreExpenses() {
        assertEquals(FlowKind.INCOME, PeriodRules.kindOf(TransactionType.INCOME))
        assertEquals(FlowKind.EXPENSE, PeriodRules.kindOf(TransactionType.EXPENSE))
        assertEquals(FlowKind.EXPENSE, PeriodRules.kindOf(TransactionType.CREDIT))
    }

    @Test
    fun transfersInvestmentsBalanceUpdatesAndLendingNeverCount() {
        listOf(
            TransactionType.TRANSFER, TransactionType.INVESTMENT, TransactionType.BALANCE_UPDATE,
            TransactionType.LENT, TransactionType.BORROWED
        ).forEach { assertNull(PeriodRules.kindOf(it), "$it should not count") }
    }
}

class PeriodCalculatorTest {

    @Test
    fun savingsAndSavingsRate() {
        val f = PeriodCalculator.figures(listOf(income("1000", "2026-10-01"), expense("250", "2026-10-02")))
        assertMoney("750", f.savings)
        assertEquals(75.0f, f.savingsRatePercent)
    }

    @Test
    fun noIncomeGivesNoRateRatherThanADivisionByZero() {
        val f = PeriodCalculator.figures(listOf(expense("250", "2026-10-02")))
        assertMoney("-250", f.savings)
        assertNull(f.savingsRatePercent)
        assertTrue(PeriodFigures.ZERO.isEmpty)
    }

    @Test
    fun monthBoundariesBelongToTheirOwnMonth() {
        val entries = listOf(
            expense("10", "2026-10-31"), expense("20", "2026-11-01"), expense("30", "2026-09-30")
        )
        assertMoney("10", PeriodCalculator.figuresForMonth(entries, YearMonth.of(2026, 10)).expenses)
        assertMoney("20", PeriodCalculator.figuresForMonth(entries, YearMonth.of(2026, 11)).expenses)
    }

    @Test
    fun everyRequestedMonthGetsARowEvenWhenEmpty() {
        val months = FinancialYear.months(2026)
        val rows = PeriodCalculator.byMonth(listOf(expense("10", "2026-05-10")), months)
        assertEquals(12, rows.size)
        assertEquals(months, rows.map { it.month })
        assertMoney("10", rows[1].figures.expenses)
        assertTrue(rows.filterIndexed { i, _ -> i != 1 }.all { it.figures.isEmpty })
    }

    @Test
    fun theYearsTotalsEqualTheSumOfItsMonths() {
        val entries = listOf(
            income("5000", "2026-04-01"), expense("120.50", "2026-04-30"), expense("80.25", "2026-05-01"),
            income("5000", "2027-03-31"), expense("999.99", "2027-03-31"), expense("1", "2026-12-25")
        )
        val rows = PeriodCalculator.byMonth(entries, FinancialYear.months(2026))
        val total = PeriodCalculator.figures(entries)
        assertMoney(total.income.toPlainString(), rows.fold(BigDecimal.ZERO) { a, r -> a.add(r.figures.income) })
        assertMoney(total.expenses.toPlainString(), rows.fold(BigDecimal.ZERO) { a, r -> a.add(r.figures.expenses) })
    }

    @Test
    fun categoryTotalsOnlyCountExpenses() {
        val totals = PeriodCalculator.expensesByCategory(
            listOf(expense("10", "2026-10-01", "Food"), expense("5", "2026-10-02", "Food"), income("99", "2026-10-03", "Salary"))
        )
        assertEquals(setOf("Food"), totals.keys)
        assertMoney("15", totals["Food"])
    }

    @Test
    fun accountsListSpentAndReceivedBiggestSpenderFirst() {
        val accounts = PeriodCalculator.byAccount(
            listOf(
                expense("10", "2026-10-01", bank = "Bank A", last4 = "1111"),
                expense("50", "2026-10-02", bank = "Bank B", last4 = "2222"),
                income("70", "2026-10-03", bank = "Bank A", last4 = "1111")
            )
        )
        assertEquals(listOf("Bank B", "Bank A"), accounts.map { it.bankName })
        assertMoney("70", accounts[1].received)
    }

    @Test
    fun lendingFiguresCoverTheRangeOnly() {
        val entries = listOf(
            LendEntry(LendBorrowType.LENT, bd("100"), d("2026-10-05")),
            LendEntry(LendBorrowType.BORROWED, bd("40"), d("2026-10-31")),
            LendEntry(LendBorrowType.SETTLEMENT_LENT, bd("25"), d("2026-10-10")),
            LendEntry(LendBorrowType.SETTLEMENT_BORROWED, bd("5"), d("2026-11-01"))
        )
        val oct = PeriodCalculator.lending(entries, d("2026-10-01"), d("2026-10-31"))
        assertMoney("100", oct.lent)
        assertMoney("40", oct.borrowed)
        assertMoney("25", oct.receivedBack)
        assertMoney("0", oct.repaid)
        assertTrue(PeriodCalculator.lending(entries, d("2026-01-01"), d("2026-01-31")).isEmpty)
    }

    @Test
    fun changeHasNoPercentageFromZero() {
        assertNull(PeriodCalculator.change(bd("10"), BigDecimal.ZERO).percent)
        val c = PeriodCalculator.change(bd("150"), bd("100"))
        assertMoney("50", c.amount)
        assertEquals(50.0f, c.percent)
    }
}

class FinancialYearTest {

    @Test
    fun theYearRunsAprilToMarch() {
        val months = FinancialYear.months(2026)
        assertEquals(YearMonth.of(2026, 4), months.first())
        assertEquals(YearMonth.of(2027, 3), months.last())
        assertEquals(12, months.size)
    }

    @Test
    fun marchBelongsToTheYearThatStartedTheApril() {
        assertEquals(2025, FinancialYear.startYearOf(d("2026-03-31")))
        assertEquals(2026, FinancialYear.startYearOf(d("2026-04-01")))
        assertEquals(2026, FinancialYear.startYearOf(YearMonth.of(2027, 1)))
    }

    @Test
    fun aLeapDayLandsInFebruaryOfTheRightYear() {
        // 29 February 2028 is in the financial year that started in April 2027
        assertEquals(2027, FinancialYear.startYearOf(d("2028-02-29")))
        val rows = PeriodCalculator.byMonth(listOf(expense("7", "2028-02-29")), FinancialYear.months(2027))
        assertMoney("7", rows.first { it.month == YearMonth.of(2028, 2) }.figures.expenses)
        assertEquals(d("2028-03-31"), FinancialYear.end(2027))
    }

    @Test
    fun labelsAreShort() {
        assertEquals("2026-27", FinancialYear.label(2026))
        assertEquals("2099-00", FinancialYear.label(2099))
    }
}

class CategoryBudgetTest {

    @Test
    fun budgetSpentAndRemaining() {
        val table = CategoryBudgetCalculator.table(
            spent = mapOf("Food" to bd("300")),
            limits = mapOf("Food" to bd("1000"))
        )
        val row = table.rows.single()
        assertMoney("700", row.remaining)
        assertFalse(row.isOverBudget)
    }

    @Test
    fun overBudgetGivesANegativeRemaining() {
        val row = CategoryBudgetCalculator.table(mapOf("Food" to bd("1200")), mapOf("Food" to bd("1000"))).rows.single()
        assertMoney("-200", row.remaining)
        assertTrue(row.isOverBudget)
    }

    @Test
    fun spendingWithoutALimitShowsNoBudgetAndNoRemaining() {
        val row = CategoryBudgetCalculator.table(mapOf("Fuel" to bd("50")), emptyMap()).rows.single()
        assertNull(row.budget)
        assertNull(row.remaining)
        assertFalse(row.isOverBudget)
    }

    @Test
    fun aLimitWithNoSpendingStillGetsARow() {
        val row = CategoryBudgetCalculator.table(emptyMap(), mapOf("Travel" to bd("500"))).rows.single()
        assertMoney("0", row.spent)
        assertMoney("500", row.remaining)
    }

    @Test
    fun totalsCountEverythingSpentButOnlyLimitedCategoriesAgainstTheBudget() {
        val table = CategoryBudgetCalculator.table(
            spent = mapOf("Food" to bd("300"), "Fuel" to bd("50")),
            limits = mapOf("Food" to bd("1000"), "Travel" to bd("500"))
        )
        assertMoney("1500", table.totalBudget)
        assertMoney("350", table.totalSpent)
        assertMoney("1200", table.totalRemaining)
        assertTrue(table.hasBudget)
        // biggest spender first
        assertEquals(listOf("Food", "Fuel", "Travel"), table.rows.map { it.category })
    }

    @Test
    fun zeroSpendWithoutALimitIsLeftOut() {
        assertTrue(CategoryBudgetCalculator.table(mapOf("Food" to BigDecimal.ZERO), emptyMap()).rows.isEmpty())
    }

    private fun budget(id: Long, amount: String, period: BudgetPeriod = BudgetPeriod.MONTHLY, from: String = "2026-10-01", to: String = "2026-10-31",
                       type: BudgetType = BudgetType.EXPENSE, active: Boolean = true, currency: String = "INR") =
        BudgetEntity(
            id = id, name = "B$id", amount = bd(amount), year = 2026, month = 10, currency = currency, isActive = active,
            startDate = LocalDateTime.parse("${from}T00:00:00"), endDate = LocalDateTime.parse("${to}T23:59:59"),
            periodType = period, budgetType = type
        )

    private fun limit(budgetId: Long, name: String, amount: String) =
        BudgetCategoryLimitEntity(budgetId = budgetId, categoryName = name, limitAmount = bd(amount))

    @Test
    fun onlyActiveExpenseBudgetsOverlappingTheMonthApply() {
        val budgets = listOf(
            budget(1, "1000"),
            budget(2, "999", active = false),
            budget(3, "999", type = BudgetType.SAVINGS),
            budget(4, "999", period = BudgetPeriod.YEARLY, from = "2026-01-01", to = "2026-12-31"),
            budget(5, "999", from = "2026-09-01", to = "2026-09-30")
        )
        val result = CategoryBudgetCalculator.budgetFor(
            YearMonth.of(2026, 10), budgets,
            listOf(limit(1, "Food", "400"), limit(2, "Food", "1"), limit(5, "Food", "2")),
            { BigDecimal.ONE }
        )
        assertMoney("1000", result.overall)
        assertEquals(setOf("Food"), result.categoryLimits.keys)
        assertMoney("400", result.categoryLimits["Food"])
    }

    @Test
    fun budgetsInAnotherCurrencyAreConverted() {
        val result = CategoryBudgetCalculator.budgetFor(
            YearMonth.of(2026, 10), listOf(budget(1, "10", currency = "USD")), listOf(limit(1, "Food", "2")),
            { if (it == "USD") bd("80") else BigDecimal.ONE }
        )
        assertMoney("800", result.overall)
        assertMoney("160", result.categoryLimits["Food"])
    }

    @Test
    fun aMonthWithNoBudgetHasNoOverallFigure() {
        assertNull(CategoryBudgetCalculator.budgetFor(YearMonth.of(2026, 10), emptyList(), emptyList(), { BigDecimal.ONE }).overall)
    }
}

class MonthActivityTest {

    private val month = YearMonth.of(2026, 10)

    private fun schedule(id: Long, next: String, amount: String = "100", state: RecurringState = RecurringState.ACTIVE, end: String? = null) =
        RecurringTransactionEntity(
            id = id, title = "S$id", amount = bd(amount), category = "Bills",
            startDate = d("2026-01-15"), nextRunDate = d(next), state = state, endDate = end?.let { d(it) }
        )

    private fun occurrence(id: Long, due: String, status: OccurrenceStatus) =
        RecurringOccurrenceEntity(recurringId = id, dueDate = d(due), status = status)

    @Test
    fun createdSkippedAndWaitingComeFromTheRecords() {
        val result = MonthActivityCalculator.recurring(
            month, d("2026-10-20"), listOf(schedule(1, "2026-11-15")),
            listOf(
                occurrence(1, "2026-10-15", OccurrenceStatus.CREATED),
                occurrence(1, "2026-09-15", OccurrenceStatus.CREATED),
                occurrence(1, "2026-10-01", OccurrenceStatus.SKIPPED),
                occurrence(1, "2026-10-02", OccurrenceStatus.PENDING)
            ),
            { it.amount }
        )
        assertEquals(1, result.createdCount)
        assertMoney("100", result.createdTotal)
        assertEquals(1, result.skippedCount)
        assertEquals(1, result.waitingCount)
        assertTrue(result.upcoming.isEmpty())
    }

    @Test
    fun upcomingDatesAreTheOnesLeftInTheMonth() {
        // Monthly on the 15th (start 15 Jan); today is the 10th, so the 15th is still to come
        val result = MonthActivityCalculator.recurring(
            month, d("2026-10-10"), listOf(schedule(1, "2026-10-15")), emptyList(), { it.amount }
        )
        assertEquals(listOf(d("2026-10-15")), result.upcoming.map { it.date })
        assertMoney("100", result.upcomingTotal)
    }

    @Test
    fun aDateAlreadyRecordedIsNotUpcoming() {
        val result = MonthActivityCalculator.recurring(
            month, d("2026-10-15"), listOf(schedule(1, "2026-10-15")),
            listOf(occurrence(1, "2026-10-15", OccurrenceStatus.CREATED)), { it.amount }
        )
        assertTrue(result.upcoming.isEmpty())
    }

    @Test
    fun pausedAndEndedSchedulesHaveNothingUpcoming() {
        val result = MonthActivityCalculator.recurring(
            month, d("2026-10-01"),
            listOf(schedule(1, "2026-10-15", state = RecurringState.PAUSED), schedule(2, "2026-10-15", end = "2026-09-30")),
            emptyList(), { it.amount }
        )
        assertTrue(result.upcoming.isEmpty())
    }

    @Test
    fun aPastMonthNeverHasUpcomingDates() {
        val result = MonthActivityCalculator.recurring(
            YearMonth.of(2026, 8), d("2026-10-10"), listOf(schedule(1, "2026-10-15")), emptyList(), { it.amount }
        )
        assertTrue(result.upcoming.isEmpty())
    }

    private fun bill(id: Long, due: String?, last: String? = null) =
        SubscriptionEntity(id = id, merchantName = "Bill$id", amount = bd("200"), nextPaymentDate = due?.let { d(it) }, lastPaidDate = last?.let { d(it) })

    private fun payment(sub: Long, due: String, status: BillPaymentStatus, amount: String = "180") =
        BillPaymentEntity(subscriptionId = sub, dueDate = d(due), amount = bd(amount), status = status, paidDate = if (status == BillPaymentStatus.PAID) d(due) else null)

    @Test
    fun paidAndUnpaidBills() {
        val result = MonthActivityCalculator.bills(
            month,
            listOf(bill(1, "2026-11-05"), bill(2, "2026-10-25")),
            listOf(payment(1, "2026-10-05", BillPaymentStatus.PAID)),
            { _, amount -> amount }
        )
        assertEquals(1, result.paid.size)
        assertMoney("180", result.paidTotal)
        // Bill 2 is due this month and nothing says it was paid
        assertEquals(listOf(2L), result.unpaid.map { it.subscriptionId })
        assertMoney("200", result.unpaidTotal)
        assertMoney("380", result.total)
    }

    @Test
    fun aBillAlreadyPaidForItsDueDateIsNotUnpaid() {
        val result = MonthActivityCalculator.bills(
            month, listOf(bill(1, "2026-10-25", last = "2026-10-25")), emptyList(), { _, a -> a }
        )
        assertTrue(result.isEmpty)
    }

    @Test
    fun aSkippedCycleIsNeitherPaidNorUnpaid() {
        val result = MonthActivityCalculator.bills(
            month, listOf(bill(1, "2026-10-25")), listOf(payment(1, "2026-10-25", BillPaymentStatus.SKIPPED)), { _, a -> a }
        )
        assertTrue(result.isEmpty)
    }

    @Test
    fun aRecordedUnpaidCycleIsNotCountedTwice() {
        val result = MonthActivityCalculator.bills(
            month, listOf(bill(1, "2026-10-25")), listOf(payment(1, "2026-10-25", BillPaymentStatus.UNPAID, "210")), { _, a -> a }
        )
        assertEquals(1, result.unpaid.size)
        assertMoney("210", result.unpaidTotal)
    }
}

class TrackerBuilderTest {

    private fun data(entries: List<PeriodEntry>, lending: List<LendEntry> = emptyList()) =
        PeriodData("INR", entries, lending, emptyMap())

    @Test
    fun theMonthScreenMatchesItsOwnCategoryTable() {
        val entries = listOf(
            income("1000", "2026-10-02"), expense("300", "2026-10-03", "Food"), expense("100", "2026-10-04", "Fuel"),
            expense("999", "2026-09-30", "Food")
        )
        val state = MonthlyTrackerBuilder.build(YearMonth.of(2026, 10), data(entries), MonthBudget(bd("2000"), mapOf("Food" to bd("500"))))

        assertMoney("400", state.figures.expenses)
        assertMoney("400", state.table.totalSpent)
        assertMoney("999", state.previous.expenses)
        assertMoney("200", state.table.rows.first { it.category == "Food" }.remaining)
        assertMoney("2000", state.overallBudget)
        assertEquals(0.2f, state.budgetUsedFraction)
        assertFalse(state.isOverBudget)
    }

    @Test
    fun anEmptyMonthIsEmptyNotBroken() {
        val state = MonthlyTrackerBuilder.build(YearMonth.of(2026, 10), data(emptyList()), MonthBudget.NONE)
        assertTrue(state.figures.isEmpty)
        assertTrue(state.table.rows.isEmpty())
        assertNull(state.overallBudget)
        assertEquals(0f, state.budgetUsedFraction)
        assertTrue(state.accounts.isEmpty())
    }

    @Test
    fun everyMonthRowOfTheYearEqualsTheMonthScreen() {
        val entries = listOf(
            income("5000", "2026-04-01"), expense("120", "2026-04-30"), expense("80", "2026-06-15"),
            income("100", "2027-03-31"), expense("5", "2027-03-31")
        )
        val year = YearlyTrackerBuilder.build(2026, data(entries), emptyList(), d("2027-06-01"))
        year.rows.forEach { row ->
            val month = MonthlyTrackerBuilder.build(row.month, data(entries), MonthBudget.NONE)
            assertMoney(month.figures.income.toPlainString(), row.figures.income)
            assertMoney(month.figures.expenses.toPlainString(), row.figures.expenses)
        }
        assertMoney("5100", year.total.income)
        assertMoney("205", year.total.expenses)
    }

    @Test
    fun monthsAfterTodayAreMarkedAsFuture() {
        val year = YearlyTrackerBuilder.build(2026, data(emptyList()), emptyList(), d("2026-10-09"))
        assertFalse(year.rows.first { it.month == YearMonth.of(2026, 10) }.isFuture)
        assertTrue(year.rows.first { it.month == YearMonth.of(2026, 11) }.isFuture)
        assertFalse(year.hasData)
    }

    @Test
    fun topCategoriesAreFoldedIntoOther() {
        val entries = (1..8).map { expense((it * 10).toString(), "2026-05-01", "C$it") }
        val year = YearlyTrackerBuilder.build(2026, data(entries), emptyList(), d("2026-10-09"))
        assertEquals(YearlyTrackerBuilder.TOP_CATEGORIES, year.topCategories.size)
        assertEquals("C8", year.topCategories.first().category)
        assertMoney("30", year.otherCategoriesTotal) // C1 (10) + C2 (20)
    }

    @Test
    fun netWorthMarksOnlyCoverTheYear() {
        fun snap(month: String, worth: String) = NetWorthSnapshotEntity(yearMonth = month, assets = bd(worth), liabilities = BigDecimal.ZERO, netWorth = bd(worth))
        val year = YearlyTrackerBuilder.build(
            2026, data(emptyList()), listOf(snap("2026-03", "1"), snap("2026-04", "2"), snap("2027-03", "3"), snap("2027-04", "4")),
            d("2027-06-01")
        )
        assertEquals(listOf(YearMonth.of(2026, 4), YearMonth.of(2027, 3)), year.netWorth.map { it.month })
    }

    @Test
    fun lendingForTheYearAndByMonth() {
        val lending = listOf(
            LendEntry(LendBorrowType.LENT, bd("100"), d("2026-05-02")),
            LendEntry(LendBorrowType.LENT, bd("50"), d("2027-04-02")), // next financial year
            LendEntry(LendBorrowType.BORROWED, bd("20"), d("2027-03-31"))
        )
        val year = YearlyTrackerBuilder.build(2026, data(emptyList(), lending), emptyList(), d("2027-06-01"))
        assertMoney("100", year.lending.lent)
        assertMoney("20", year.lending.borrowed)
        assertEquals(12, year.lendingByMonth.size)
        assertTrue(year.hasData)
    }
}

class CompactAmountTest {

    @Test
    fun rupeesUseThousandsLakhsAndCrores() {
        assertEquals("950", CompactAmount.format(bd("950"), "INR"))
        assertEquals("80K", CompactAmount.format(bd("80000"), "INR"))
        assertEquals("1.2L", CompactAmount.format(bd("120000"), "INR"))
        assertEquals("3Cr", CompactAmount.format(bd("30000000"), "INR"))
    }

    @Test
    fun otherCurrenciesUseThousandsMillionsAndBillions() {
        assertEquals("80K", CompactAmount.format(bd("80000"), "USD"))
        assertEquals("1.2M", CompactAmount.format(bd("1200000"), "USD"))
        assertEquals("2B", CompactAmount.format(bd("2000000000"), "USD"))
    }

    @Test
    fun negativeAmountsKeepTheirSign() {
        assertEquals("-1.5K", CompactAmount.format(bd("-1500"), "INR"))
        assertEquals("-20", CompactAmount.format(bd("-20.4"), "INR"))
    }
}
