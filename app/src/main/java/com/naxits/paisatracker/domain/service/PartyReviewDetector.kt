package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.dao.PartyDao
import com.naxits.paisatracker.data.database.dao.TransactionReviewDao
import com.naxits.paisatracker.data.database.entity.ReviewKind
import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.data.database.entity.TransactionReviewEntity
import com.naxits.paisatracker.data.database.entity.TransactionType
import com.naxits.paisatracker.domain.model.toDomain
import java.time.LocalDateTime

/**
 * For an SMS transaction on a business account: works out who it was with, stores that as a
 * suggestion, and files a review so the user can confirm or correct it. When nothing matches
 * reliably the party stays Unknown, and that is reviewed too.
 */
class PartyReviewDetector(
    private val partyDao: PartyDao,
    private val reviewDao: TransactionReviewDao,
    private val isBusinessAccount: suspend (bankName: String, accountLast4: String) -> Boolean,
    private val now: () -> LocalDateTime = { LocalDateTime.now() }
) {
    companion object {
        const val UNKNOWN = "Unknown"

        private val ELIGIBLE_TYPES = setOf(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.CREDIT)
    }

    /** The review that was filed, or null when the transaction is not eligible or already has one. */
    suspend fun detect(transactionId: Long, transaction: TransactionEntity): TransactionReviewEntity? {
        if (transaction.transactionType !in ELIGIBLE_TYPES) return null
        if (transaction.smsBody.isNullOrBlank()) return null
        val bank = transaction.bankName ?: return null
        val last4 = transaction.accountNumber ?: return null
        if (!isBusinessAccount(bank, last4)) return null
        if (reviewDao.get(transactionId, ReviewKind.PARTY) != null) return null

        val counterparty = CounterpartyExtractor.extract(transaction.smsBody, transaction.merchantName, last4)
        val parties = partyDao.getActive().map { it.toDomain() }
        val history = partyDao.latestSettledPartyForMerchant(transaction.merchantName, transactionId)
        val match = PartyMatcher.match(counterparty, transaction.merchantName, parties, history)

        val review = TransactionReviewEntity(
            transactionId = transactionId,
            kind = ReviewKind.PARTY,
            suggestedValue = match.partyName ?: UNKNOWN,
            suggestedPartyId = match.partyId,
            reason = match.reason,
            confidence = match.confidence,
            createdAt = now()
        )
        val id = reviewDao.insert(review)
        if (id == -1L) return null
        if (match.partyId != null) partyDao.setPartyForTransaction(transactionId, match.partyId)
        return review.copy(id = id)
    }
}
