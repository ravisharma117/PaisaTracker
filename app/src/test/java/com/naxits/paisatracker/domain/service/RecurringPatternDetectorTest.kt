package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.CategoryPatternEntity
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurringPatternDetectorTest {

    private fun date(month: Int, day: Int, year: Int = 2026) = LocalDateTime.of(year, month, day, 9, 0)

    private fun entry(month: Int, day: Int, amount: String, text: String = "Acme Payroll", year: Int = 2026) =
        RecurrenceEntry(date(month, day, year), BigDecimal(amount), text)

    private fun pattern(category: String = "Salary", subcategory: String? = null) = CategoryPatternEntity(
        patternKey = "upi:client@testbank",
        type = "INCOME",
        category = category,
        subcategory = subcategory,
        count = 3,
        lastAmount = BigDecimal("50000"),
        lastDate = date(3, 1)
    )

    private val monthlySalary = listOf(
        entry(3, 1, "50000"),
        entry(2, 1, "50000"),
        entry(1, 1, "50000")
    )

    private fun suggest(
        current: RecurrenceEntry,
        earlier: List<RecurrenceEntry> = monthlySalary,
        strong: Boolean = true,
        tolerance: Int = RecurringPatternDetector.DEFAULT_TOLERANCE_TENTHS,
        pattern: CategoryPatternEntity = pattern()
    ) = RecurringPatternDetector.suggest(current, pattern, earlier, strong, "account", tolerance)

    @Test
    fun aMonthlySalaryToTheSameAccountIsSuggestedItsCategory() {
        val s = suggest(entry(4, 1, "50500"))

        assertNotNull(s)
        assertEquals("Salary", s.category)
        assertEquals(RecurrencePeriod.MONTHLY, s.period)
        assertEquals(3, s.earlierEntries)
        assertTrue(s.confidence >= RecurringPatternDetector.THRESHOLD)
        assertTrue("monthly" in s.reason && "account" in s.reason && "Salary" in s.reason, s.reason)
        assertTrue("within 1%" in s.reason, "the reason says how close the amounts were: ${s.reason}")
    }

    @Test
    fun theSameLogicWorksForAnyCategory() {
        val rent = pattern(category = "Rent", subcategory = "Office")
        val earlier = listOf(entry(3, 5, "12000", "Test Landlord"), entry(2, 5, "12000", "Test Landlord"))

        val s = suggest(entry(4, 6, "12000", "Test Landlord"), earlier, pattern = rent)

        assertNotNull(s)
        assertEquals("Rent", s.category)
        assertEquals("Office", s.subcategory)
    }

    @Test
    fun weeklyAndYearlySeriesAreRecognisedToo() {
        val weekly = listOf(entry(3, 8, "300", "Test Tiffin"), entry(3, 1, "300", "Test Tiffin"))
        assertEquals(RecurrencePeriod.WEEKLY, suggest(entry(3, 15, "300", "Test Tiffin"), weekly)?.period)

        val yearly = listOf(entry(3, 1, "8000", "Test Insurer", 2025), entry(3, 1, "8000", "Test Insurer", 2024))
        assertEquals(RecurrencePeriod.YEARLY, suggest(entry(3, 1, "8000", "Test Insurer", 2026), yearly)?.period)
    }

    @Test
    fun anAmountWithinTheToleranceStillMatches() {
        // 5% above the earlier 50000
        assertNotNull(suggest(entry(4, 1, "52500"), tolerance = 50))
    }

    @Test
    fun anAmountJustOutsideTheToleranceDoesNot() {
        // 6% above: outside 5%, inside 10%
        assertNull(suggest(entry(4, 1, "53000"), tolerance = 50))
        assertNotNull(suggest(entry(4, 1, "53000"), tolerance = 100))
    }

    @Test
    fun anAmountFarFromEveryEarlierOneIsNotSuggested() {
        assertNull(suggest(entry(4, 1, "90000")))
    }

    @Test
    fun aSeriesWithoutARegularRhythmGetsNoSuggestion() {
        val irregular = listOf(entry(3, 12, "50000"), entry(3, 3, "50000"), entry(2, 27, "50000"))
        assertNull(suggest(entry(3, 22, "50000"), irregular))
    }

    @Test
    fun theSameAmountOnConsecutiveDaysIsNotASeries() {
        assertNull(suggest(entry(3, 2, "50000"), listOf(entry(3, 1, "50000"), entry(2, 28, "50000"))))
    }

    @Test
    fun oneEarlierEntryIsNotEnough() {
        assertNull(suggest(entry(4, 1, "50000"), listOf(entry(3, 1, "50000"))))
    }

    @Test
    fun entriesAfterTheCurrentOneAreIgnored() {
        val withFuture = listOf(entry(5, 1, "50000"), entry(6, 1, "50000"))
        assertNull(suggest(entry(4, 1, "50000"), withFuture))
    }

    @Test
    fun aKnownPersonOutscoresMerchantTextAlone() {
        val strong = suggest(entry(4, 1, "50000"), strong = true)!!
        val weak = suggest(entry(4, 1, "50000"), strong = false)!!
        assertTrue(strong.confidence > weak.confidence)
    }

    @Test
    fun differentWordingLowersTheConfidence() {
        val same = suggest(entry(4, 1, "50000", "Acme Payroll"))!!
        val different = suggest(entry(4, 1, "50000", "Unrelated Words Here"))!!
        assertTrue(same.confidence > different.confidence)
    }

    @Test
    fun aWeakMatchWithNothingElseGoingForItIsNotEnough() {
        // Merchant text only, amounts only just inside, no shared wording, rhythm only for the latest gap
        val earlier = listOf(entry(3, 1, "50000", "aa"), entry(1, 10, "50000", "aa"))
        assertNull(suggest(entry(4, 1, "54000", "bb"), earlier, strong = false))
    }

    @Test
    fun theReasonMentionsTheCategoryAndTheRhythm() {
        val s = suggest(entry(4, 1, "50000"))!!
        assertEquals("3 earlier monthly entries with the same account, same amount, filed under Salary", s.reason)
    }

    @Test
    fun aZeroEarlierAmountOnlyMatchesZero() {
        val zeroes = listOf(entry(3, 1, "0"), entry(2, 1, "0"))
        assertNull(suggest(entry(4, 1, "10"), zeroes))
    }

    @Test
    fun periodsHaveRoomForMovingPayDays() {
        assertEquals(RecurrencePeriod.MONTHLY, RecurrencePeriod.of(28))
        assertEquals(RecurrencePeriod.MONTHLY, RecurrencePeriod.of(33))
        assertEquals(RecurrencePeriod.WEEKLY, RecurrencePeriod.of(7))
        assertNull(RecurrencePeriod.of(1))
        assertNull(RecurrencePeriod.of(60))
    }
}
