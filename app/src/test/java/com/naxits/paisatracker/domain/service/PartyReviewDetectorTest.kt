package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.PartyEntity
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

class PartyReviewDetectorTest {

    private val now = LocalDateTime.of(2026, 3, 1, 9, 0)
    private val reviews = FakeReviewDao()
    private val parties = FakePartyDao { reviews.pendingTransactionIds() }
    private val detector = PartyReviewDetector(
        partyDao = parties,
        reviewDao = reviews,
        isBusinessAccount = { bank, last4 -> bank == "Test Bank" && last4 == "1234" },
        now = { now }
    )

    private fun smsTransaction(
        id: Long = 1,
        merchant: String = "Acme Traders",
        body: String? = "Rs.5000.00 credited to A/c XXXX1234 by Acme Traders. Ref 712345678901",
        type: TransactionType = TransactionType.INCOME,
        bank: String? = "Test Bank",
        last4: String? = "1234"
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal("5000.00"),
        merchantName = merchant,
        category = "Income",
        transactionType = type,
        dateTime = now.minusDays(1),
        smsBody = body,
        bankName = bank,
        accountNumber = last4,
        transactionHash = "hash-$id"
    )

    private suspend fun addAcme(): Long =
        parties.insert(PartyEntity(name = "Acme Traders", phone = "7000000001"))

    @Test
    fun aTransactionNobodyMatchesIsReviewedAsUnknown() = runTest {
        val review = detector.detect(1, smsTransaction(merchant = "Random Shop"))

        assertNotNull(review)
        assertEquals(ReviewKind.PARTY, review.kind)
        assertEquals(PartyReviewDetector.UNKNOWN, review.suggestedValue)
        assertNull(review.suggestedPartyId)
        assertEquals(ReviewStatus.PENDING, review.status)
        assertEquals(now, review.createdAt)
        assertNull(parties.transactionParty[1L], "an Unknown suggestion stores no party")
    }

    @Test
    fun aMatchIsStoredAsASuggestionWithItsReason() = runTest {
        val id = addAcme()

        val review = detector.detect(1, smsTransaction())

        assertNotNull(review)
        assertEquals("Acme Traders", review.suggestedValue)
        assertEquals(id, review.suggestedPartyId)
        assertEquals(id, parties.transactionParty[1L])
        assertTrue(review.reason.isNotBlank())
        assertTrue(review.confidence >= PartyMatcher.RELIABLE)
        assertTrue(review.isPending)
    }

    @Test
    fun aMatchByPhoneInTheMessageIsFound() = runTest {
        val id = addAcme()
        val tx = smsTransaction(merchant = "7000000001@ybl", body = "Rs.5000 credited by 7000000001@ybl")

        val review = detector.detect(1, tx)

        assertEquals(id, review?.suggestedPartyId)
    }

    @Test
    fun anAccountThatIsNotBusinessGetsNoReview() = runTest {
        assertNull(detector.detect(1, smsTransaction(last4 = "9999")))
        assertNull(detector.detect(2, smsTransaction(id = 2, bank = "Other Bank")))
        assertTrue(reviews.getAll().isEmpty())
    }

    @Test
    fun aTransactionWithoutAnSmsGetsNoReview() = runTest {
        assertNull(detector.detect(1, smsTransaction(body = null)))
        assertNull(detector.detect(2, smsTransaction(id = 2, body = "  ")))
    }

    @Test
    fun transfersAndOtherTypesAreNotReviewed() = runTest {
        assertNull(detector.detect(1, smsTransaction(type = TransactionType.TRANSFER)))
        assertNull(detector.detect(2, smsTransaction(id = 2, type = TransactionType.BALANCE_UPDATE)))
        assertNull(detector.detect(3, smsTransaction(id = 3, type = TransactionType.INVESTMENT)))
    }

    @Test
    fun theSameTransactionIsNeverReviewedTwice() = runTest {
        assertNotNull(detector.detect(1, smsTransaction()))
        assertNull(detector.detect(1, smsTransaction()))
        assertEquals(1, reviews.getAll().size)
    }

    @Test
    fun anEarlierSettledTransactionWithTheSameMerchantIsTheHistoryMatch() = runTest {
        val id = parties.insert(PartyEntity(name = "Different Name"))
        parties.addTransaction(50, "Local Store", id, now.minusDays(30))

        val review = detector.detect(1, smsTransaction(merchant = "Local Store", body = "Rs.5000 credited by Local Store"))

        assertEquals(id, review?.suggestedPartyId)
        assertTrue(review!!.reason.contains("earlier transaction"))
    }

    @Test
    fun anEarlierTransactionStillWaitingForReviewIsNotTrusted() = runTest {
        val id = parties.insert(PartyEntity(name = "Different Name"))
        parties.addTransaction(50, "Local Store", id, now.minusDays(30))
        reviews.insert(
            com.naxits.paisatracker.data.database.entity.TransactionReviewEntity(
                transactionId = 50, kind = ReviewKind.PARTY, suggestedValue = "Different Name",
                suggestedPartyId = id, reason = "test"
            )
        )

        val review = detector.detect(1, smsTransaction(merchant = "Local Store", body = "Rs.5000 credited by Local Store"))

        assertNull(review?.suggestedPartyId, "a guess that nobody confirmed is not evidence")
    }
}
