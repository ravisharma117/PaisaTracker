package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.ReviewKind
import com.naxits.paisatracker.data.database.entity.ReviewResolvedBy
import com.naxits.paisatracker.data.database.entity.ReviewStatus
import com.naxits.paisatracker.data.database.entity.TransactionReviewEntity
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewAutoConfirmerTest {

    private val now = LocalDateTime.of(2026, 6, 15, 12, 0)
    private val reviews = FakeReviewDao()
    private val confirmer = ReviewAutoConfirmer(reviews)

    private suspend fun add(
        transactionId: Long,
        createdAt: LocalDateTime,
        kind: String = ReviewKind.CATEGORY,
        suggested: String = "Salary",
        status: String = ReviewStatus.PENDING
    ): Long = reviews.insert(
        TransactionReviewEntity(
            transactionId = transactionId, kind = kind, suggestedValue = suggested,
            reason = "test", createdAt = createdAt, status = status
        )
    )

    @Test
    fun aReviewJustUnderThreeMonthsOldStaysPending() = runTest {
        val id = add(1, now.minusMonths(3).plusMinutes(1))

        assertEquals(0, confirmer.run(now))

        assertEquals(ReviewStatus.PENDING, reviews.getById(id)!!.status)
    }

    @Test
    fun aReviewThreeMonthsOldIsConfirmedAutomatically() = runTest {
        val id = add(1, now.minusMonths(3))

        assertEquals(1, confirmer.run(now))

        val done = reviews.getById(id)!!
        assertEquals(ReviewStatus.AUTO_CONFIRMED, done.status)
        assertEquals(ReviewResolvedBy.AUTO_THREE_MONTHS, done.resolvedBy)
        assertEquals(now, done.resolvedAt)
    }

    @Test
    fun theSuggestedValueIsKeptExactlyAsItWas() = runTest {
        val id = add(1, now.minusMonths(4), suggested = "Salary")

        confirmer.run(now)

        val done = reviews.getById(id)!!
        assertEquals("Salary", done.suggestedValue)
        assertEquals("Salary", done.resolvedValue)
    }

    @Test
    fun customerReviewsAreConfirmedLikeCategoryOnes() = runTest {
        val party = add(1, now.minusMonths(5), kind = ReviewKind.PARTY, suggested = "Acme Traders")
        val unknown = add(2, now.minusMonths(5), kind = ReviewKind.PARTY, suggested = "Unknown")
        val category = add(3, now.minusMonths(5), kind = ReviewKind.CATEGORY)

        assertEquals(3, confirmer.run(now))

        assertEquals("Acme Traders", reviews.getById(party)!!.resolvedValue)
        assertEquals("Unknown", reviews.getById(unknown)!!.resolvedValue)
        assertTrue(listOf(party, unknown, category).all { reviews.getById(it)!!.status == ReviewStatus.AUTO_CONFIRMED })
    }

    @Test
    fun aReviewTheUserAlreadySettledIsLeftAlone() = runTest {
        val manual = add(1, now.minusMonths(6), status = ReviewStatus.CONFIRMED)
        val dismissed = add(2, now.minusMonths(6), status = ReviewStatus.DISMISSED)

        assertEquals(0, confirmer.run(now))

        assertEquals(ReviewStatus.CONFIRMED, reviews.getById(manual)!!.status)
        assertNull(reviews.getById(manual)!!.resolvedBy)
        assertEquals(ReviewStatus.DISMISSED, reviews.getById(dismissed)!!.status)
    }

    @Test
    fun manualAndAutomaticConfirmationsStayDistinguishable() = runTest {
        val old = add(1, now.minusMonths(4))
        val fresh = add(2, now.minusDays(2))

        confirmer.run(now)
        // The user then confirms the fresh one themselves
        reviews.update(
            reviews.getById(fresh)!!.copy(
                status = ReviewStatus.CONFIRMED, resolvedBy = ReviewResolvedBy.MANUAL, resolvedAt = now
            )
        )

        assertEquals(ReviewResolvedBy.AUTO_THREE_MONTHS, reviews.getById(old)!!.resolvedBy)
        assertEquals(ReviewResolvedBy.MANUAL, reviews.getById(fresh)!!.resolvedBy)
        assertEquals(2, reviews.getAll().size, "reviews are never deleted, so the history stays")
    }

    @Test
    fun runningItTwiceConfirmsOnlyOnce() = runTest {
        add(1, now.minusMonths(4))

        assertEquals(1, confirmer.run(now))
        assertEquals(0, confirmer.run(now.plusDays(1)))
    }
}
