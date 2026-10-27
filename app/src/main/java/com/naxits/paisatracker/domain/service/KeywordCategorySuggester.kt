package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.dao.CategoryPatternDao
import com.naxits.paisatracker.data.database.dao.TransactionReviewDao
import com.naxits.paisatracker.data.database.entity.ReviewKind
import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.data.database.entity.TransactionReviewEntity
import com.naxits.paisatracker.data.database.entity.TransactionType
import java.time.LocalDateTime

/**
 * For a new SMS entry the app could not place: when a word in the payee name says what it is
 * ("filling station", "pizza", "electronics"), applies that category and files a review saying
 * which word it went by, so the user can confirm or change it.
 *
 * It only fills in what is missing. An entry already in a real category keeps it; at most its
 * sub-category is completed, quietly, when the keyword agrees with the category.
 */
class KeywordCategorySuggester(
    private val patternDao: CategoryPatternDao,
    private val reviewDao: TransactionReviewDao,
    private val now: () -> LocalDateTime = { LocalDateTime.now() }
) {
    companion object {
        const val UNCATEGORISED = "Miscellaneous"

        /** The words describe what was bought, so they say nothing about money coming in. */
        val SPENDING_TYPES = setOf(TransactionType.EXPENSE, TransactionType.CREDIT)

        /** A keyword is a good guess, not a certainty: below the history-based suggestions. */
        private const val CONFIDENCE = 60
    }

    /** The review that was filed, or null when nothing was suggested. */
    suspend fun suggest(transactionId: Long, transaction: TransactionEntity): TransactionReviewEntity? {
        if (transaction.transactionType !in SPENDING_TYPES) return null
        if (reviewDao.get(transactionId, ReviewKind.CATEGORY) != null) return null
        val found = KeywordCategories.match(transaction.merchantName, transaction.description) ?: return null

        val sameCategory = found.category.equals(transaction.category, ignoreCase = true)
        if (sameCategory) {
            // The category is already right; only the sub-category may be missing
            if (transaction.subcategory == null && found.subcategory != null) {
                patternDao.setCategory(transactionId, transaction.category, found.subcategory)
            }
            return null
        }
        if (!transaction.category.equals(UNCATEGORISED, ignoreCase = true)) return null

        patternDao.setCategory(transactionId, found.category, found.subcategory)
        val review = TransactionReviewEntity(
            transactionId = transactionId,
            kind = ReviewKind.CATEGORY,
            suggestedValue = found.category,
            suggestedSubcategory = found.subcategory,
            reason = "Name contains \"${found.keyword}\"",
            confidence = CONFIDENCE,
            createdAt = now()
        )
        val id = reviewDao.insert(review)
        if (id == -1L) return null
        return review.copy(id = id)
    }
}
