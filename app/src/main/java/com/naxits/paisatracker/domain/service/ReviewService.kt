package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.dao.CategoryPatternDao
import com.naxits.paisatracker.data.database.dao.PartyDao
import com.naxits.paisatracker.data.database.dao.TransactionReviewDao
import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.data.database.entity.TransactionReviewEntity
import com.naxits.paisatracker.data.preferences.UserPreferencesRepository
import com.naxits.paisatracker.data.repository.AccountMetaRepository
import com.naxits.paisatracker.data.repository.TransactionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The app-facing side of reviews: called after an SMS transaction is saved, and by the screens.
 * It never settles a review without the user, except for the three-month rule.
 */
@Singleton
class ReviewService @Inject constructor(
    partyDao: PartyDao,
    private val reviewDao: TransactionReviewDao,
    private val patternDao: CategoryPatternDao,
    accountMetaRepository: AccountMetaRepository,
    transactions: TransactionRepository,
    preferences: UserPreferencesRepository
) {
    private val transactionLookup: suspend (Long) -> TransactionEntity? = { id -> transactions.getTransactionById(id) }

    private val detector = PartyReviewDetector(
        partyDao = partyDao,
        reviewDao = reviewDao,
        isBusinessAccount = { bank, last4 -> accountMetaRepository.get(bank, last4).isBusiness }
    )
    private val actions = ReviewActions(
        partyDao = partyDao,
        reviewDao = reviewDao,
        transactionById = { id -> transactions.getTransactionById(id) }
    )
    private val suggester = CategorySuggester(
        patternDao = patternDao,
        reviewDao = reviewDao,
        toleranceTenths = { preferences.recurringToleranceTenths.first() }
    )
    private val keywordSuggester = KeywordCategorySuggester(
        patternDao = patternDao,
        reviewDao = reviewDao
    )
    private val learner = CategoryLearner(
        patternDao = patternDao,
        reviewDao = reviewDao,
        transactionById = { id -> transactions.getTransactionById(id) }
    )
    private val autoConfirmer = ReviewAutoConfirmer(reviewDao)

    /** Ids of the transactions that still have something to review. */
    fun observePendingTransactionIds(): Flow<Set<Long>> =
        reviewDao.observePendingTransactionIds().map { it.toSet() }

    fun observeForTransaction(transactionId: Long): Flow<List<TransactionReviewEntity>> =
        reviewDao.observeForTransaction(transactionId)

    /**
     * For a transaction that was just saved. A failure here must never affect saving it.
     *
     * @param categoryChosenByUser true when a rule or a saved merchant choice already set the
     * category, which a suggestion must never override
     */
    suspend fun onTransactionSaved(
        transactionId: Long,
        transaction: TransactionEntity,
        categoryChosenByUser: Boolean = false
    ) {
        try {
            val partyReview = detector.detect(transactionId, transaction)
            if (!categoryChosenByUser) {
                val withParty = transaction.copy(partyId = partyReview?.suggestedPartyId ?: transaction.partyId)
                // History of the same series is stronger evidence than a word in the name
                if (suggester.suggest(transactionId, withParty) == null) {
                    keywordSuggester.suggest(transactionId, withParty)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // A review is a convenience; nothing about the transaction itself depends on it
        }
    }

    suspend fun confirmParty(reviewId: Long, partyId: Long? = null): Boolean = actions.confirmParty(reviewId, partyId)

    suspend fun dismiss(reviewId: Long): Boolean = actions.dismiss(reviewId)

    /** Clears every review and everything learned from the user's categories. */
    suspend fun deleteAll() {
        reviewDao.deleteAll()
        patternDao.deleteAll()
    }

    /** Confirms the category the app suggested for [reviewId]. */
    suspend fun confirmCategory(reviewId: Long): Boolean = learner.confirm(reviewId)

    /** The user filed [transaction] under its current category: remember it, and settle a waiting suggestion. */
    suspend fun userCategorised(transaction: TransactionEntity) {
        try {
            learner.userCategorised(transaction)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Remembering is a convenience; the category itself is already saved
        }
    }

    /** Same, for a transaction that was just added by hand. */
    suspend fun userCategorised(transactionId: Long) {
        try {
            transactionLookup(transactionId)?.let { learner.userCategorised(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // See above
        }
    }

    /** Confirms reviews nobody looked at for three months. Returns how many. */
    suspend fun autoConfirmOldReviews(): Int = autoConfirmer.run()
}
