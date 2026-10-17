package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.dao.TransactionReviewDao
import com.naxits.paisatracker.data.database.entity.ReviewResolvedBy
import com.naxits.paisatracker.data.database.entity.ReviewStatus
import java.time.LocalDateTime

/**
 * A review nobody looks at for three months is confirmed as it stands. Only the review changes:
 * the transaction keeps the category and customer it already has, and the review records that it
 * was confirmed automatically rather than by the user.
 */
class ReviewAutoConfirmer(
    private val reviewDao: TransactionReviewDao
) {
    companion object {
        const val MONTHS = 3L
    }

    /** Confirms every pending review that is at least three months old at [now]; returns how many. */
    suspend fun run(now: LocalDateTime = LocalDateTime.now()): Int {
        val due = reviewDao.getPendingCreatedOnOrBefore(now.minusMonths(MONTHS))
        due.forEach { review ->
            reviewDao.update(
                review.copy(
                    status = ReviewStatus.AUTO_CONFIRMED,
                    resolvedAt = now,
                    resolvedBy = ReviewResolvedBy.AUTO_THREE_MONTHS,
                    resolvedValue = review.suggestedValue
                )
            )
        }
        return due.size
    }
}
