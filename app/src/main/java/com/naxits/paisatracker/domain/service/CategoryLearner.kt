package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.dao.CategoryPatternDao
import com.naxits.paisatracker.data.database.dao.TransactionReviewDao
import com.naxits.paisatracker.data.database.entity.CategoryPatternEntity
import com.naxits.paisatracker.data.database.entity.ReviewKind
import com.naxits.paisatracker.data.database.entity.ReviewResolvedBy
import com.naxits.paisatracker.data.database.entity.ReviewStatus
import com.naxits.paisatracker.data.database.entity.TransactionEntity
import java.time.LocalDateTime

/**
 * Remembers the category the user chose, so the next entry in the same series can be suggested
 * the same one, and settles a pending category suggestion when the user decides it themselves.
 */
class CategoryLearner(
    private val patternDao: CategoryPatternDao,
    private val reviewDao: TransactionReviewDao,
    private val transactionById: suspend (Long) -> TransactionEntity?,
    private val now: () -> LocalDateTime = { LocalDateTime.now() }
) {
    /** The user filed [transaction] under its current category; remember that for everyone it is known as. */
    suspend fun learn(transaction: TransactionEntity) {
        if (transaction.transactionType !in CATEGORISED_TYPES) return
        val counterparty = CounterpartyExtractor.extract(transaction.smsBody, transaction.merchantName, transaction.accountNumber)
        val type = transaction.transactionType.name
        PatternKeys.keysFor(transaction.partyId, counterparty, transaction.merchantName).forEach { key ->
            val existing = patternDao.get(key, type)
            val sameChoice = existing != null &&
                existing.category.equals(transaction.category, ignoreCase = true) &&
                existing.subcategory == transaction.subcategory
            patternDao.upsert(
                CategoryPatternEntity(
                    patternKey = key,
                    type = type,
                    category = transaction.category,
                    subcategory = transaction.subcategory,
                    count = if (sameChoice) existing!!.count + 1 else 1,
                    lastAmount = transaction.amount,
                    lastDate = transaction.dateTime,
                    updatedAt = now()
                )
            )
        }
    }

    /**
     * The user has set [transaction]'s category themselves: learn it, and if a suggestion for it
     * was still waiting, settle that as confirmed with what the user chose.
     */
    suspend fun userCategorised(transaction: TransactionEntity) {
        learn(transaction)
        val review = reviewDao.get(transaction.id, ReviewKind.CATEGORY) ?: return
        if (!review.isPending) return
        reviewDao.update(
            review.copy(
                status = ReviewStatus.CONFIRMED,
                resolvedAt = now(),
                resolvedBy = ReviewResolvedBy.MANUAL,
                resolvedValue = transaction.category
            )
        )
    }

    /** Confirms the suggested category as it stands. False when there is no such pending category review. */
    suspend fun confirm(reviewId: Long): Boolean {
        val review = reviewDao.getById(reviewId) ?: return false
        if (review.kind != ReviewKind.CATEGORY || !review.isPending) return false
        val transaction = transactionById(review.transactionId)
        if (transaction != null) learn(transaction)
        reviewDao.update(
            review.copy(
                status = ReviewStatus.CONFIRMED,
                resolvedAt = now(),
                resolvedBy = ReviewResolvedBy.MANUAL,
                resolvedValue = transaction?.category ?: review.suggestedValue
            )
        )
        return true
    }
}
