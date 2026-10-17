package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.CategoryPatternEntity
import com.naxits.paisatracker.data.database.entity.ReviewKind
import com.naxits.paisatracker.data.database.entity.ReviewStatus
import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategorySuggesterTest {

    private val now = LocalDateTime.of(2026, 4, 2, 8, 0)
    private val patterns = FakePatternDao()
    private val reviews = FakeReviewDao()
    private var tolerance = 100
    private val suggester = CategorySuggester(patterns, reviews, { tolerance }, { now })

    private val body = "Rs.50000.00 credited to A/c XXXX1234 by client@testbank on 01-04-26. Ref 712345678901"

    private fun incoming(
        id: Long = 10,
        category: String = "Income",
        type: TransactionType = TransactionType.INCOME,
        amount: String = "50000.00",
        partyId: Long? = null,
        merchant: String = "Acme Payroll",
        smsBody: String? = body
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal(amount),
        merchantName = merchant,
        category = category,
        transactionType = type,
        dateTime = LocalDateTime.of(2026, 4, 1, 9, 0),
        smsBody = smsBody,
        bankName = "Test Bank",
        accountNumber = "1234",
        transactionHash = "hash-$id",
        partyId = partyId
    )

    private fun earlierMonthly(partyId: Long? = null, merchant: String = "Acme Payroll", type: TransactionType = TransactionType.INCOME) {
        listOf(1, 2, 3).forEachIndexed { index, offset ->
            patterns.transactions += FakeHistoryTx(
                id = (offset + 100).toLong(), type = type,
                dateTime = LocalDateTime.of(2026, 4 - offset, 1, 9, 0),
                amount = BigDecimal("50000.00"), merchant = merchant, partyId = partyId, smsBody = body
            )
        }
    }

    private fun remember(key: String, category: String = "Salary", type: String = "INCOME") {
        patterns.patterns += CategoryPatternEntity(
            patternKey = key, type = type, category = category, count = 3,
            lastAmount = BigDecimal("50000"), lastDate = LocalDateTime.of(2026, 3, 1, 9, 0)
        )
    }

    @Test
    fun aMonthlyEntryOnAKnownUpiIdGetsTheRememberedCategoryAndAReview() = runTest {
        earlierMonthly()
        remember("upi:client@testbank")

        val review = suggester.suggest(10, incoming())

        assertNotNull(review)
        assertEquals(ReviewKind.CATEGORY, review.kind)
        assertEquals("Salary", review.suggestedValue)
        assertEquals(ReviewStatus.PENDING, review.status)
        assertEquals(now, review.createdAt)
        assertTrue(review.reason.contains("monthly") && review.reason.contains("UPI id"), review.reason)
        assertEquals("Salary" to null, patterns.categoryWrites[10L])
        assertEquals(1, reviews.getAll().size)
    }

    @Test
    fun aSavedCustomerRecognisesTheSeriesToo() = runTest {
        earlierMonthly(partyId = 7)
        remember("party:7")

        val review = suggester.suggest(10, incoming(partyId = 7, smsBody = null))

        assertEquals("Salary", review?.suggestedValue)
        assertTrue(review!!.reason.contains("customer or vendor"))
    }

    @Test
    fun merchantTextAloneCanRecogniseAMonthlyPayment() = runTest {
        earlierMonthly(merchant = "Test Streaming", type = TransactionType.EXPENSE)
        remember("merchant:test streaming", category = "Entertainment", type = "EXPENSE")

        val review = suggester.suggest(
            10,
            incoming(type = TransactionType.EXPENSE, merchant = "Test Streaming", smsBody = null)
        )

        assertEquals("Entertainment", review?.suggestedValue)
    }

    @Test
    fun withNothingRememberedNothingIsSuggested() = runTest {
        earlierMonthly()

        assertNull(suggester.suggest(10, incoming()))
        assertTrue(reviews.getAll().isEmpty())
        assertTrue(patterns.categoryWrites.isEmpty())
    }

    @Test
    fun anUncertainMatchLeavesTheCategoryAlone() = runTest {
        // Remembered, but only one earlier entry exists
        patterns.transactions += FakeHistoryTx(101, TransactionType.INCOME, LocalDateTime.of(2026, 3, 1, 9, 0), BigDecimal("50000"), "Acme Payroll", smsBody = body)
        remember("upi:client@testbank")

        assertNull(suggester.suggest(10, incoming()))
        assertTrue(patterns.categoryWrites.isEmpty())
    }

    @Test
    fun anAmountOutsideTheChosenToleranceIsNotSuggested() = runTest {
        earlierMonthly()
        remember("upi:client@testbank")
        tolerance = 50

        assertNull(suggester.suggest(10, incoming(amount = "53000.00")))
    }

    @Test
    fun anEntryAlreadyInTheSuggestedCategoryNeedsNoReview() = runTest {
        earlierMonthly()
        remember("upi:client@testbank")

        assertNull(suggester.suggest(10, incoming(category = "Salary")))
        assertTrue(reviews.getAll().isEmpty())
    }

    @Test
    fun incomeAndExpenseToTheSameKeyAreRememberedApart() = runTest {
        earlierMonthly(type = TransactionType.EXPENSE)
        remember("upi:client@testbank", category = "Rent", type = "EXPENSE")

        assertNull(suggester.suggest(10, incoming(type = TransactionType.INCOME)))
    }

    @Test
    fun transfersAreNeverSuggestedACategory() = runTest {
        earlierMonthly()
        remember("upi:client@testbank")

        assertNull(suggester.suggest(10, incoming(type = TransactionType.TRANSFER)))
    }

    @Test
    fun theSameEntryIsNeverSuggestedTwice() = runTest {
        earlierMonthly()
        remember("upi:client@testbank")

        assertNotNull(suggester.suggest(10, incoming()))
        assertNull(suggester.suggest(10, incoming()))
        assertEquals(1, reviews.getAll().size)
    }

    @Test
    fun deletedEarlierEntriesDoNotCount() = runTest {
        earlierMonthly()
        patterns.transactions.replaceAll { it.copy(deleted = true) }
        remember("upi:client@testbank")

        assertNull(suggester.suggest(10, incoming()))
    }

    @Test
    fun theStrongestKeyThatMatchesWins() = runTest {
        earlierMonthly(partyId = 7)
        remember("party:7", category = "Salary")
        remember("merchant:acme payroll", category = "Miscellaneous")

        val review = suggester.suggest(10, incoming(partyId = 7))

        assertEquals("Salary", review?.suggestedValue)
    }
}
