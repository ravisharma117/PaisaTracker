package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.ReviewKind
import com.naxits.paisatracker.data.database.entity.ReviewResolvedBy
import com.naxits.paisatracker.data.database.entity.ReviewStatus
import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.data.database.entity.TransactionReviewEntity
import com.naxits.paisatracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryLearnerTest {

    private val now = LocalDateTime.of(2026, 4, 2, 8, 0)
    private val patterns = FakePatternDao()
    private val reviews = FakeReviewDao()
    private var stored: TransactionEntity? = null

    private val learner = CategoryLearner(
        patternDao = patterns,
        reviewDao = reviews,
        transactionById = { id -> stored?.takeIf { it.id == id } },
        now = { now }
    )

    private fun tx(category: String = "Salary", subcategory: String? = null, partyId: Long? = null, type: TransactionType = TransactionType.INCOME) =
        TransactionEntity(
            id = 10,
            amount = BigDecimal("50000.00"),
            merchantName = "Acme Payroll",
            category = category,
            subcategory = subcategory,
            transactionType = type,
            dateTime = LocalDateTime.of(2026, 4, 1, 9, 0),
            smsBody = "Rs.50000.00 credited to A/c XXXX1234 by client@testbank. Ref 712345678901",
            bankName = "Test Bank",
            accountNumber = "1234",
            transactionHash = "hash-10",
            partyId = partyId
        )

    private suspend fun pendingCategoryReview(suggested: String = "Salary") = reviews.insert(
        TransactionReviewEntity(
            transactionId = 10, kind = ReviewKind.CATEGORY, suggestedValue = suggested, reason = "test",
            confidence = 85, createdAt = LocalDateTime.of(2026, 4, 1, 9, 5)
        )
    )

    @Test
    fun aChosenCategoryIsRememberedForEveryWayTheCounterpartyIsKnown() = runTest {
        learner.learn(tx(partyId = 7))

        val keys = patterns.patterns.map { it.patternKey }.toSet()
        assertEquals(setOf("party:7", "upi:client@testbank", "merchant:acme payroll"), keys)
        assertTrue(patterns.patterns.all { it.category == "Salary" && it.type == "INCOME" && it.count == 1 })
        assertEquals(now, patterns.patterns.first().updatedAt)
    }

    @Test
    fun choosingTheSameCategoryAgainCountsUp() = runTest {
        learner.learn(tx())
        learner.learn(tx())

        assertEquals(2, patterns.get("upi:client@testbank", "INCOME")?.count)
    }

    @Test
    fun choosingADifferentCategoryReplacesTheOldChoice() = runTest {
        learner.learn(tx(category = "Salary"))
        learner.learn(tx(category = "Freelance", subcategory = "Design"))

        val pattern = patterns.get("upi:client@testbank", "INCOME")!!
        assertEquals("Freelance", pattern.category)
        assertEquals("Design", pattern.subcategory)
        assertEquals(1, pattern.count)
    }

    @Test
    fun transfersAreNotLearned() = runTest {
        learner.learn(tx(type = TransactionType.TRANSFER))

        assertTrue(patterns.patterns.isEmpty())
    }

    @Test
    fun categorisingSettlesAWaitingSuggestionWithWhatTheUserChose() = runTest {
        val reviewId = pendingCategoryReview(suggested = "Salary")

        learner.userCategorised(tx(category = "Freelance"))

        val done = reviews.getById(reviewId)!!
        assertEquals(ReviewStatus.CONFIRMED, done.status)
        assertEquals(ReviewResolvedBy.MANUAL, done.resolvedBy)
        assertEquals("Freelance", done.resolvedValue)
        assertEquals("Salary", done.suggestedValue, "what the app suggested stays on record")
        assertEquals(now, done.resolvedAt)
        assertNotNull(patterns.get("upi:client@testbank", "INCOME"))
    }

    @Test
    fun categorisingWithNothingWaitingJustLearns() = runTest {
        learner.userCategorised(tx())

        assertTrue(reviews.getAll().isEmpty())
        assertEquals("Salary", patterns.get("upi:client@testbank", "INCOME")?.category)
    }

    @Test
    fun confirmingKeepsTheSuggestedCategoryAndLearnsIt() = runTest {
        stored = tx(category = "Salary")
        val reviewId = pendingCategoryReview()

        assertTrue(learner.confirm(reviewId))

        val done = reviews.getById(reviewId)!!
        assertEquals(ReviewStatus.CONFIRMED, done.status)
        assertEquals(ReviewResolvedBy.MANUAL, done.resolvedBy)
        assertEquals("Salary", done.resolvedValue)
        assertEquals("Salary", patterns.get("upi:client@testbank", "INCOME")?.category)
    }

    @Test
    fun aSettledOrMissingOrPartyReviewCannotBeConfirmedAsACategory() = runTest {
        stored = tx()
        val reviewId = pendingCategoryReview()
        assertTrue(learner.confirm(reviewId))
        assertFalse(learner.confirm(reviewId))
        assertFalse(learner.confirm(999))

        val partyReview = reviews.insert(
            TransactionReviewEntity(transactionId = 11, kind = ReviewKind.PARTY, suggestedValue = "Unknown", reason = "test")
        )
        assertFalse(learner.confirm(partyReview))
        assertNull(reviews.getById(partyReview)!!.resolvedAt)
    }
}
