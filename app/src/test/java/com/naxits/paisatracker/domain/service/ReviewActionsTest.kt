package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.PartyEntity
import com.naxits.paisatracker.data.database.entity.ReviewKind
import com.naxits.paisatracker.data.database.entity.ReviewResolvedBy
import com.naxits.paisatracker.data.database.entity.ReviewStatus
import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.data.database.entity.TransactionReviewEntity
import com.naxits.paisatracker.data.database.entity.TransactionType
import com.naxits.paisatracker.domain.model.PartyIdentifier
import com.naxits.paisatracker.domain.model.toDomain
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewActionsTest {

    private val now = LocalDateTime.of(2026, 3, 1, 9, 0)
    private val created = LocalDateTime.of(2026, 2, 20, 9, 0)
    private val reviews = FakeReviewDao()
    private val parties = FakePartyDao()

    private val transaction = TransactionEntity(
        id = 1,
        amount = BigDecimal("5000.00"),
        merchantName = "ACME TRD",
        category = "Income",
        transactionType = TransactionType.INCOME,
        dateTime = created,
        smsBody = "Rs.5000.00 credited to A/c XXXX1234 by client@testbank. Ref 712345678901",
        bankName = "Test Bank",
        accountNumber = "1234",
        transactionHash = "hash-1"
    )

    private val actions = ReviewActions(
        partyDao = parties,
        reviewDao = reviews,
        transactionById = { id -> transaction.takeIf { it.id == id }?.copy(partyId = parties.transactionParty[id]) },
        now = { now }
    )

    private suspend fun suggest(partyId: Long?, name: String): Long {
        parties.setPartyForTransaction(1, partyId)
        return reviews.insert(
            TransactionReviewEntity(
                transactionId = 1, kind = ReviewKind.PARTY, suggestedValue = name,
                suggestedPartyId = partyId, reason = "test", confidence = 85, createdAt = created
            )
        )
    }

    @Test
    fun confirmingTheSuggestionRecordsWhoWhenAndHow() = runTest {
        val partyId = parties.insert(PartyEntity(name = "Acme Traders"))
        val reviewId = suggest(partyId, "Acme Traders")

        assertTrue(actions.confirmParty(reviewId))

        val done = reviews.getById(reviewId)!!
        assertEquals(ReviewStatus.CONFIRMED, done.status)
        assertEquals(ReviewResolvedBy.MANUAL, done.resolvedBy)
        assertEquals(now, done.resolvedAt)
        assertEquals("Acme Traders", done.resolvedValue)
        assertEquals(created, done.createdAt, "what was suggested, and when, stays on record")
        assertEquals("Acme Traders", done.suggestedValue)
        assertEquals(partyId, parties.transactionParty[1L])
    }

    @Test
    fun confirmingTeachesThePartyWhatItWasKnownBy() = runTest {
        val partyId = parties.insert(PartyEntity(name = "Acme Traders"))
        val reviewId = suggest(partyId, "Acme Traders")

        actions.confirmParty(reviewId)

        val learned = parties.getById(partyId)!!.toDomain()
        assertTrue(PartyIdentifier.Upi("client@testbank") in learned.identifiers)
        assertTrue("ACME TRD" in learned.aliases)
    }

    @Test
    fun correctingToAnotherPartyStoresAndLearnsThatOne() = runTest {
        val suggested = parties.insert(PartyEntity(name = "Acme Traders"))
        val right = parties.insert(PartyEntity(name = "Sample Vendor"))
        val reviewId = suggest(suggested, "Acme Traders")

        assertTrue(actions.confirmParty(reviewId, right))

        val done = reviews.getById(reviewId)!!
        assertEquals("Sample Vendor", done.resolvedValue)
        assertEquals("Acme Traders", done.suggestedValue, "the original suggestion is kept for auditing")
        assertEquals(right, parties.transactionParty[1L])
        assertTrue("ACME TRD" in parties.getById(right)!!.toDomain().aliases)
        assertTrue(parties.getById(suggested)!!.toDomain().aliases.isEmpty())
    }

    @Test
    fun anUnknownSuggestionCanBeCorrectedToAParty() = runTest {
        val right = parties.insert(PartyEntity(name = "Sample Vendor"))
        val reviewId = suggest(null, "Unknown")

        assertTrue(actions.confirmParty(reviewId, right))

        assertEquals(right, parties.transactionParty[1L])
        assertEquals("Sample Vendor", reviews.getById(reviewId)!!.resolvedValue)
    }

    @Test
    fun anUnknownSuggestionCanBeConfirmedAsUnknown() = runTest {
        val reviewId = suggest(null, "Unknown")

        assertTrue(actions.confirmParty(reviewId))

        assertEquals("Unknown", reviews.getById(reviewId)!!.resolvedValue)
        assertEquals(ReviewStatus.CONFIRMED, reviews.getById(reviewId)!!.status)
        assertNull(parties.transactionParty[1L])
    }

    @Test
    fun aSettledReviewCannotBeConfirmedAgain() = runTest {
        val partyId = parties.insert(PartyEntity(name = "Acme Traders"))
        val reviewId = suggest(partyId, "Acme Traders")
        assertTrue(actions.confirmParty(reviewId))

        assertFalse(actions.confirmParty(reviewId))
        assertFalse(actions.dismiss(reviewId))
    }

    @Test
    fun dismissingClosesTheReviewAndKeepsTheCurrentParty() = runTest {
        val partyId = parties.insert(PartyEntity(name = "Acme Traders"))
        val reviewId = suggest(partyId, "Acme Traders")

        assertTrue(actions.dismiss(reviewId))

        val done = reviews.getById(reviewId)!!
        assertEquals(ReviewStatus.DISMISSED, done.status)
        assertEquals(ReviewResolvedBy.MANUAL, done.resolvedBy)
        assertEquals(partyId, parties.transactionParty[1L])
        assertTrue(parties.getById(partyId)!!.toDomain().aliases.isEmpty(), "dismissing teaches nothing")
    }

    @Test
    fun aMissingReviewOrAnotherKindIsRefused() = runTest {
        assertFalse(actions.confirmParty(999))
        assertFalse(actions.dismiss(999))

        val categoryReview = reviews.insert(
            TransactionReviewEntity(
                transactionId = 2, kind = ReviewKind.CATEGORY, suggestedValue = "Salary", reason = "test"
            )
        )
        assertFalse(actions.confirmParty(categoryReview))
        assertEquals(ReviewStatus.PENDING, reviews.getById(categoryReview)!!.status)
    }
}
