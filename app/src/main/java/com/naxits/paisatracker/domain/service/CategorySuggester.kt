package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.dao.CategoryPatternDao
import com.naxits.paisatracker.data.database.dao.HistoryRow
import com.naxits.paisatracker.data.database.dao.TransactionReviewDao
import com.naxits.paisatracker.data.database.entity.ReviewKind
import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.data.database.entity.TransactionReviewEntity
import com.naxits.paisatracker.data.database.entity.TransactionType
import java.time.LocalDateTime

internal val CATEGORISED_TYPES = setOf(TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.CREDIT)

/**
 * For a new SMS entry that looks like the next one in a series the user already categorised:
 * applies the remembered category and files a review saying why, so the user can confirm or
 * change it. When the match is not clear the category is left exactly as it was.
 */
class CategorySuggester(
    private val patternDao: CategoryPatternDao,
    private val reviewDao: TransactionReviewDao,
    private val toleranceTenths: suspend () -> Int,
    private val now: () -> LocalDateTime = { LocalDateTime.now() }
) {
    companion object {
        private const val HISTORY_LIMIT = 12
    }

    /** The review that was filed, or null when nothing was suggested. */
    suspend fun suggest(transactionId: Long, transaction: TransactionEntity): TransactionReviewEntity? {
        if (transaction.transactionType !in CATEGORISED_TYPES) return null
        if (reviewDao.get(transactionId, ReviewKind.CATEGORY) != null) return null

        val counterparty = CounterpartyExtractor.extract(transaction.smsBody, transaction.merchantName, transaction.accountNumber)
        val keys = PatternKeys.keysFor(transaction.partyId, counterparty, transaction.merchantName)
        val current = RecurrenceEntry(transaction.dateTime, transaction.amount, entryText(transaction.merchantName, transaction.description))
        val tolerance = toleranceTenths()

        var best: RecurringSuggestion? = null
        for (key in keys) {
            val pattern = patternDao.get(key, transaction.transactionType.name) ?: continue
            val earlier = history(key, transaction, transactionId).map {
                RecurrenceEntry(it.dateTime, it.amount, entryText(it.merchantName, it.description))
            }
            val found = RecurringPatternDetector.suggest(
                current = current,
                pattern = pattern,
                earlier = earlier,
                strongIdentity = PatternKeys.isStrong(key),
                identityLabel = PatternKeys.describe(key),
                toleranceTenths = tolerance
            ) ?: continue
            if (best == null || found.confidence > best.confidence) best = found
        }
        val suggestion = best ?: return null

        // Nothing to review when the entry already carries the suggested category
        val sameCategory = suggestion.category.equals(transaction.category, ignoreCase = true)
        if (sameCategory && (suggestion.subcategory == null || suggestion.subcategory == transaction.subcategory)) return null

        patternDao.setCategory(
            transactionId,
            suggestion.category,
            suggestion.subcategory ?: if (sameCategory) transaction.subcategory else null
        )
        val review = TransactionReviewEntity(
            transactionId = transactionId,
            kind = ReviewKind.CATEGORY,
            suggestedValue = suggestion.category,
            suggestedSubcategory = suggestion.subcategory,
            reason = suggestion.reason,
            confidence = suggestion.confidence,
            createdAt = now()
        )
        val id = reviewDao.insert(review)
        if (id == -1L) return null
        return review.copy(id = id)
    }

    private suspend fun history(key: String, transaction: TransactionEntity, transactionId: Long): List<HistoryRow> {
        val value = PatternKeys.value(key)
        val type = transaction.transactionType
        val before = transaction.dateTime
        return when {
            key.startsWith(PatternKeys.PARTY) ->
                value.toLongOrNull()?.let { patternDao.historyForParty(it, type, transactionId, before, HISTORY_LIMIT) }.orEmpty()
            key.startsWith(PatternKeys.MERCHANT) ->
                patternDao.historyForMerchant(value, type, transactionId, before, HISTORY_LIMIT)
            else -> patternDao.historyForMessageText(likePattern(value), type, transactionId, before, HISTORY_LIMIT)
        }
    }

    private fun likePattern(text: String): String =
        "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    private fun entryText(merchant: String, description: String?) = "$merchant ${description.orEmpty()}".trim()
}
