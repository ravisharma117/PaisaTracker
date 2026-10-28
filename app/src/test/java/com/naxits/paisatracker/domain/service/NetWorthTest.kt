package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.AccountBalanceEntity
import com.naxits.paisatracker.data.database.entity.LendBorrowTransactionEntity
import com.naxits.paisatracker.data.database.entity.LendBorrowType
import com.naxits.paisatracker.data.database.entity.NetWorthItemEntity
import com.naxits.paisatracker.data.database.entity.NetWorthItemType
import com.naxits.paisatracker.data.database.entity.NetWorthItemValueEntity
import com.naxits.paisatracker.domain.model.AccountKind
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

private fun d(text: String) = LocalDate.parse(text)
private fun bd(text: String) = BigDecimal(text)

/** Money compared by value, not by how many decimals it was written with. */
private fun assertMoney(expected: String, actual: BigDecimal) =
    assertEquals(0, bd(expected).compareTo(actual), "expected $expected but was $actual")

class LoanMathTest {

    @Test
    fun emiOfAOneYearLoan() {
        assertEquals(bd("8884.88"), LoanMath.emi(bd("100000"), bd("12"), 12))
    }

    @Test
    fun emiWithoutInterestIsAnEvenSplit() {
        assertEquals(bd("1000.00"), LoanMath.emi(bd("12000"), BigDecimal.ZERO, 12))
    }

    @Test
    fun theFirstPaymentCoversInterestFirst() {
        val balance = LoanMath.balanceOn(bd("100000"), d("2026-01-01"), bd("12"), bd("8884.88"), 5, d("2026-01-05"))
        // 1000.00 interest, so 7884.88 of principal
        assertMoney("92115.12", balance)
    }

    @Test
    fun beforeThePaymentDayNothingHasChanged() {
        assertMoney("100000", LoanMath.balanceOn(bd("100000"), d("2026-01-01"), bd("12"), bd("8884.88"), 5, d("2026-01-04")))
    }

    @Test
    fun aPaymentOnTheAnchorDayItselfIsNotCountedTwice() {
        // Balance typed in on the 5th is after that day's EMI; the next one is in February
        val onFifth = LoanMath.balanceOn(bd("50000"), d("2026-01-05"), bd("12"), bd("5000"), 5, d("2026-01-31"))
        assertMoney("50000", onFifth)
        val feb = LoanMath.balanceOn(bd("50000"), d("2026-01-05"), bd("12"), bd("5000"), 5, d("2026-02-05"))
        assertMoney("45500", feb)
    }

    @Test
    fun aFullTermRepaysTheLoan() {
        val end = LoanMath.balanceOn(bd("100000"), d("2026-01-01"), bd("12"), bd("8884.88"), 5, d("2026-12-05"))
        assertTrue(end.compareTo(BigDecimal.ONE) <= 0, "balance after the last EMI should be about zero: $end")
        val later = LoanMath.balanceOn(bd("100000"), d("2026-01-01"), bd("12"), bd("8884.88"), 5, d("2030-01-01"))
        assertEquals(0, later.signum())
    }

    @Test
    fun withoutAnEmiTheBalanceStaysWhereItWas() {
        assertEquals(bd("70000"), LoanMath.balanceOn(bd("70000"), d("2026-01-01"), bd("9"), null, null, d("2027-01-01")))
        assertEquals(bd("70000"), LoanMath.balanceOn(bd("70000"), d("2026-01-01"), bd("9"), bd("1000"), 5, d("2025-12-01")))
    }

    @Test
    fun theEmiDayIsTheLastDayOfShortMonths() {
        assertEquals(d("2026-02-28"), LoanMath.paymentDate(YearMonth.of(2026, 2), 31))
        assertEquals(d("2026-04-30"), LoanMath.paymentDate(YearMonth.of(2026, 4), 31))
        assertEquals(d("2026-01-31"), LoanMath.paymentDate(YearMonth.of(2026, 1), 31))
    }
}

class NetWorthCalculatorTest {

    private fun item(id: Long, type: NetWorthItemType, name: String, value: String, estimate: Boolean = false) =
        ItemAmount(id, name, type, bd(value), estimate)

    private val full = NetWorthInput(
        accounts = listOf(
            AccountAmount(AccountKind.BANK, "Bank A", bd("5000")),
            AccountAmount(AccountKind.BANK, "Bank B", bd("-400")),
            AccountAmount(AccountKind.CASH, "Cash", bd("300")),
            AccountAmount(AccountKind.WALLET, "Wallet", bd("200")),
            AccountAmount(AccountKind.CREDIT_CARD, "Card", bd("1200"))
        ),
        moneyLent = bd("1000"),
        moneyBorrowed = bd("700"),
        items = listOf(
            item(1, NetWorthItemType.MUTUAL_FUND, "Fund", "10000"),
            item(2, NetWorthItemType.GOLD, "Gold", "5000"),
            item(3, NetWorthItemType.HOME_LOAN, "Home loan", "50000", estimate = true),
            item(4, NetWorthItemType.OTHER_LIABILITY, "Tax due", "100")
        )
    )

    @Test
    fun everyGroupFromTheRequirementIsCovered() {
        val result = NetWorthCalculator.calculate(full, "INR")

        assertEquals(bd("21500"), result.totalAssets)
        assertEquals(bd("52400"), result.totalLiabilities)
        assertEquals(bd("-30900"), result.netWorth)
        assertEquals(
            listOf(AssetGroup.BANK, AssetGroup.CASH, AssetGroup.WALLET, AssetGroup.INVESTMENTS, AssetGroup.GOLD, AssetGroup.MONEY_LENT),
            result.assets.map { it.group }
        )
        assertEquals(
            listOf(LiabilityGroup.CREDIT_CARDS, LiabilityGroup.OVERDRAFT, LiabilityGroup.MONEY_BORROWED, LiabilityGroup.LOANS, LiabilityGroup.OTHER_LIABILITIES),
            result.liabilities.map { it.group }
        )
    }

    @Test
    fun aHomeAndAPlotAreTheirOwnPropertySection() {
        val result = NetWorthCalculator.calculate(
            NetWorthInput(
                accounts = emptyList(),
                moneyLent = BigDecimal.ZERO,
                moneyBorrowed = BigDecimal.ZERO,
                items = listOf(
                    item(1, NetWorthItemType.HOUSE, "Home", "4000000"),
                    item(2, NetWorthItemType.PLOT, "Plot", "1500000"),
                    item(3, NetWorthItemType.PROPERTY, "Shop", "800000"),
                    item(4, NetWorthItemType.VEHICLE, "Car", "500000")
                )
            ),
            "INR"
        )

        assertEquals(listOf(AssetGroup.PROPERTY, AssetGroup.OTHER_ASSETS), result.assets.map { it.group })
        assertEquals(bd("6300000"), result.assets.first { it.group == AssetGroup.PROPERTY }.total)
        assertEquals(bd("6800000"), result.netWorth)
    }

    @Test
    fun goldHasItsOwnSectionAndCountsTowardsNetWorth() {
        val result = NetWorthCalculator.calculate(
            NetWorthInput(emptyList(), BigDecimal.ZERO, BigDecimal.ZERO, listOf(item(1, NetWorthItemType.GOLD, "Coins", "250000"))),
            "INR"
        )

        assertEquals(listOf(AssetGroup.GOLD), result.assets.map { it.group })
        assertEquals(bd("250000"), result.netWorth)
    }

    @Test
    fun aCreditCardBalanceIsOwedNotOwned() {
        val result = NetWorthCalculator.calculate(
            NetWorthInput(listOf(AccountAmount(AccountKind.CREDIT_CARD, "Card", bd("2000"))), BigDecimal.ZERO, BigDecimal.ZERO, emptyList()),
            "INR"
        )
        assertEquals(bd("-2000"), result.netWorth)
        assertTrue(result.assets.isEmpty())
    }

    @Test
    fun anOverdraftIsShownAsALiabilityByItsSize() {
        val result = NetWorthCalculator.calculate(
            NetWorthInput(listOf(AccountAmount(AccountKind.BANK, "Bank", bd("-400"))), BigDecimal.ZERO, BigDecimal.ZERO, emptyList()),
            "INR"
        )
        assertEquals(bd("400"), result.liabilities.single().total)
        assertEquals(bd("-400"), result.netWorth)
    }

    @Test
    fun emptyGroupsAreLeftOut() {
        val result = NetWorthCalculator.calculate(NetWorthInput(emptyList(), BigDecimal.ZERO, BigDecimal.ZERO, emptyList()), "INR")
        assertTrue(result.assets.isEmpty() && result.liabilities.isEmpty())
        assertEquals(0, result.netWorth.signum())
    }

    @Test
    fun lentAndBorrowedAreSeparateLines() {
        val result = NetWorthCalculator.calculate(
            NetWorthInput(emptyList(), bd("1500"), bd("400"), emptyList()),
            "INR"
        )
        assertEquals(bd("1100"), result.netWorth)
    }

    @Test
    fun anEstimatedLoanIsFlagged() {
        val result = NetWorthCalculator.calculate(full, "INR")
        assertTrue(result.liabilities.first { it.group == LiabilityGroup.LOANS }.lines.single().isEstimate)
    }
}

class NetWorthHistoryTest {

    private fun balance(
        bank: String, last4: String, amount: String, at: String,
        card: Boolean = false, currency: String = "INR"
    ) = AccountBalanceEntity(
        bankName = bank, accountLast4 = last4, balance = bd(amount),
        timestamp = LocalDateTime.parse("${at}T10:00:00"), isCreditCard = card, currency = currency
    )

    private fun entry(person: Long, type: LendBorrowType, amount: String, on: String) =
        LendBorrowTransactionEntity(
            personId = person, type = type, amount = bd(amount), title = "t",
            date = LocalDateTime.parse("${on}T09:00:00")
        )

    private fun at(
        date: String,
        balances: List<AccountBalanceEntity> = emptyList(),
        lend: List<LendBorrowTransactionEntity> = emptyList(),
        items: List<NetWorthItemEntity> = emptyList(),
        values: List<NetWorthItemValueEntity> = emptyList(),
        factors: Map<String, BigDecimal> = emptyMap(),
        persons: Set<Long> = setOf(1L)
    ) = NetWorthHistory.breakdownAt(d(date), balances, lend, persons, items, values, factors, "INR")

    @Test
    fun anAccountUsesItsLatestBalanceUpToThatDay() {
        val balances = listOf(
            balance("Bank", "1234", "1000", "2026-07-10"),
            balance("Bank", "1234", "1500", "2026-08-20"),
            balance("Bank", "1234", "9000", "2026-10-02")
        )
        assertEquals(bd("1000"), at("2026-07-31", balances).netWorth)
        assertEquals(bd("1500"), at("2026-09-30", balances).netWorth)
        assertEquals(bd("9000"), at("2026-10-05", balances).netWorth)
    }

    @Test
    fun anAccountThatDidNotExistYetIsNotCounted() {
        val balances = listOf(balance("Bank", "1234", "1000", "2026-08-20"))
        assertEquals(0, at("2026-07-31", balances).netWorth.signum())
    }

    @Test
    fun aCreditCardIsALiabilityInThePastToo() {
        val balances = listOf(balance("Bank", "1234", "10000", "2026-07-01"), balance("Card", "9999", "2500", "2026-07-02", card = true))
        assertEquals(bd("7500"), at("2026-07-31", balances).netWorth)
    }

    @Test
    fun lendingFollowsTheEntriesUpToThatDay() {
        val lend = listOf(
            entry(1, LendBorrowType.LENT, "20000", "2026-08-01"),
            entry(1, LendBorrowType.SETTLEMENT_LENT, "5000", "2026-09-10")
        )
        assertEquals(bd("20000"), at("2026-08-31", lend = lend).netWorth)
        assertEquals(bd("15000"), at("2026-09-30", lend = lend).netWorth)
        assertEquals(0, at("2026-07-31", lend = lend).netWorth.signum())
    }

    @Test
    fun moneyLentAsRememberOnlyIsNotCounted() {
        val lend = listOf(
            entry(1, LendBorrowType.LENT, "20000", "2026-08-01"),
            entry(1, LendBorrowType.LENT, "7000", "2026-08-02").copy(rememberOnly = true)
        )
        assertEquals(bd("20000"), at("2026-08-31", lend = lend).netWorth)
    }

    @Test
    fun moneyBorrowedAsRememberOnlyIsNotADebt() {
        val lend = listOf(entry(1, LendBorrowType.BORROWED, "4000", "2026-08-01").copy(rememberOnly = true))
        assertEquals(0, at("2026-08-31", lend = lend).netWorth.signum())
    }

    @Test
    fun aRepaymentReducesTheCountedLendingFirst() {
        val lend = listOf(
            entry(1, LendBorrowType.LENT, "10000", "2026-08-01"),
            entry(1, LendBorrowType.LENT, "5000", "2026-08-02").copy(rememberOnly = true),
            entry(1, LendBorrowType.SETTLEMENT_LENT, "4000", "2026-08-10")
        )
        assertEquals(bd("6000"), at("2026-08-31", lend = lend).netWorth)
    }

    @Test
    fun anArchivedPersonIsLeftOut() {
        val lend = listOf(entry(2, LendBorrowType.LENT, "20000", "2026-08-01"))
        assertEquals(0, at("2026-08-31", lend = lend, persons = setOf(1L)).netWorth.signum())
    }

    @Test
    fun aHoldingUsesTheLatestRecordedValue() {
        val item = NetWorthItemEntity(id = 7, name = "Fund", type = NetWorthItemType.MUTUAL_FUND, currentValue = bd("12000"), asOfDate = d("2026-09-01"))
        val values = listOf(
            NetWorthItemValueEntity(itemId = 7, value = bd("10000"), date = d("2026-07-15")),
            NetWorthItemValueEntity(itemId = 7, value = bd("12000"), date = d("2026-09-01"))
        )
        assertEquals(0, at("2026-06-30", items = listOf(item), values = values).netWorth.signum())
        assertEquals(bd("10000"), at("2026-08-31", items = listOf(item), values = values).netWorth)
        assertEquals(bd("12000"), at("2026-09-30", items = listOf(item), values = values).netWorth)
    }

    @Test
    fun aLoanIsCarriedForwardFromItsLastKnownBalance() {
        val loan = NetWorthItemEntity(
            id = 3, name = "Loan", type = NetWorthItemType.PERSONAL_LOAN, currentValue = bd("100000"),
            asOfDate = d("2026-01-01"), annualRatePercent = bd("12"), emiAmount = bd("8884.88"), emiDay = 5
        )
        val values = listOf(NetWorthItemValueEntity(itemId = 3, value = bd("100000"), date = d("2026-01-01")))

        val january = at("2026-01-31", items = listOf(loan), values = values)

        assertEquals(bd("-92115.12"), january.netWorth)
        assertTrue(january.liabilities.single().lines.single().isEstimate)
    }

    @Test
    fun typingInTheRealBalanceRestartsTheSchedule() {
        val loan = NetWorthItemEntity(
            id = 3, name = "Loan", type = NetWorthItemType.PERSONAL_LOAN, currentValue = bd("80000"),
            asOfDate = d("2026-03-10"), annualRatePercent = bd("12"), emiAmount = bd("8884.88"), emiDay = 5
        )
        val values = listOf(
            NetWorthItemValueEntity(itemId = 3, value = bd("100000"), date = d("2026-01-01")),
            NetWorthItemValueEntity(itemId = 3, value = bd("80000"), date = d("2026-03-10"))
        )
        // On the day it was typed in, the real figure wins over the schedule
        assertEquals(bd("-80000"), at("2026-03-10", items = listOf(loan), values = values).netWorth)
    }

    @Test
    fun foreignAmountsAreConvertedWithTheGivenFactor() {
        val balances = listOf(balance("Bank", "1234", "100", "2026-07-01", currency = "USD"))
        assertEquals(bd("8300"), at("2026-07-31", balances, factors = mapOf("USD" to bd("83"))).netWorth)
    }

    @Test
    fun theMonthsToRebuildSkipExistingOnesAndTheCurrentMonth() {
        val months = NetWorthHistory.monthsToBackfill(
            earliest = YearMonth.of(2026, 7),
            existing = setOf("2026-08"),
            current = YearMonth.of(2026, 10)
        )
        assertEquals(listOf(YearMonth.of(2026, 7), YearMonth.of(2026, 9)), months)
    }

    @Test
    fun nothingToRebuildWithoutHistory() {
        assertTrue(NetWorthHistory.monthsToBackfill(null, emptySet(), YearMonth.of(2026, 10)).isEmpty())
        assertTrue(NetWorthHistory.monthsToBackfill(YearMonth.of(2026, 10), emptySet(), YearMonth.of(2026, 10)).isEmpty())
    }

    @Test
    fun theRebuildNeverGoesBackMoreThanTenYears() {
        val months = NetWorthHistory.monthsToBackfill(YearMonth.of(2000, 1), emptySet(), YearMonth.of(2026, 10))
        assertEquals(120, months.size)
        assertEquals(YearMonth.of(2016, 10), months.first())
        assertNull(months.firstOrNull { it == YearMonth.of(2026, 10) })
    }
}
