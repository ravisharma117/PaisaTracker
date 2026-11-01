package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.dao.PartyDao
import com.naxits.paisatracker.data.database.dao.TransactionReviewDao
import com.naxits.paisatracker.data.database.entity.ReviewKind
import com.naxits.paisatracker.data.database.entity.ReviewResolvedBy
import com.naxits.paisatracker.data.database.entity.ReviewStatus
import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.domain.model.toDomain
import com.naxits.paisatracker.domain.model.toEntity
import java.time.LocalDateTime

/** What the user can do with a party review. Every action is recorded on the review itself. */
class ReviewActions(
    private val partyDao: PartyDao,
    private val reviewDao: TransactionReviewDao,
    private val transactionById: suspend (Long) -> TransactionEntity?,
    private val now: () -> LocalDateTime = { LocalDateTime.now() }
) {
    /**
     * Confirms the review. [partyId] corrects it to another party; null keeps what was suggested,
     * and a review that suggested Unknown is confirmed as Unknown. Returns false when there is no
     * such pending party review.
     */
    suspend fun confirmParty(reviewId: Long, partyId: Long? = null): Boolean {
        val review = reviewDao.getById(reviewId) ?: return false
        if (review.kind != ReviewKind.PARTY || !review.isPending) return false

        val finalPartyId = partyId ?: review.suggestedPartyId
        partyDao.setPartyForTransaction(review.transactionId, finalPartyId)

        val party = finalPartyId?.let { partyDao.getById(it) }
        if (party != null) {
            transactionById(review.transactionId)?.let { transaction ->
                val counterparty = CounterpartyExtractor.extract(
                    transaction.smsBody, transaction.merchantName, transaction.accountNumber
                )
                val learned = PartyLearning.learn(party.toDomain(), counterparty, transaction.merchantName)
                partyDao.update(learned.toEntity(createdAt = party.createdAt))
            }
        }

        reviewDao.update(
            review.copy(
                status = ReviewStatus.CONFIRMED,
                resolvedAt = now(),
                resolvedBy = ReviewResolvedBy.MANUAL,
                resolvedValue = party?.name ?: PartyReviewDetector.UNKNOWN
            )
        )
        return true
    }

    /** Closes the review without learning anything; the transaction keeps its current party. */
    suspend fun dismiss(reviewId: Long): Boolean {
        val review = reviewDao.getById(reviewId) ?: return false
        if (!review.isPending) return false
        val transaction = transactionById(review.transactionId)
        // What the entry holds right now, which dismissing leaves as it is
        val current = if (review.kind == ReviewKind.CATEGORY) {
            transaction?.category
        } else {
            transaction?.partyId?.let { partyDao.getById(it) }?.name
        }
        reviewDao.update(
            review.copy(
                status = ReviewStatus.DISMISSED,
                resolvedAt = now(),
                resolvedBy = ReviewResolvedBy.MANUAL,
                resolvedValue = current ?: PartyReviewDetector.UNKNOWN
            )
        )
        return true
    }
}
