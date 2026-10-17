package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.dao.PartyDao
import com.naxits.paisatracker.data.database.dao.TransactionReviewDao
import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.data.database.entity.TransactionReviewEntity
import com.naxits.paisatracker.data.repository.AccountMetaRepository
import com.naxits.paisatracker.data.repository.TransactionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The app-facing side of reviews: called after an SMS transaction is saved, and by the screens.
 * It never settles a review without the user, except for the three-month rule.
 */
@Singleton
class ReviewService @Inject constructor(
    partyDao: PartyDao,
    private val reviewDao: TransactionReviewDao,
    accountMetaRepository: AccountMetaRepository,
    transactions: TransactionRepository
) {
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

    /** Ids of the transactions that still have something to review. */
    fun observePendingTransactionIds(): Flow<Set<Long>> =
        reviewDao.observePendingTransactionIds().map { it.toSet() }

    fun observeForTransaction(transactionId: Long): Flow<List<TransactionReviewEntity>> =
        reviewDao.observeForTransaction(transactionId)

    /** For a transaction that was just saved. A failure here must never affect saving it. */
    suspend fun onTransactionSaved(transactionId: Long, transaction: TransactionEntity) {
        try {
            detector.detect(transactionId, transaction)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // A review is a convenience; nothing about the transaction itself depends on it
        }
    }

    suspend fun confirmParty(reviewId: Long, partyId: Long? = null): Boolean = actions.confirmParty(reviewId, partyId)

    suspend fun dismiss(reviewId: Long): Boolean = actions.dismiss(reviewId)

    suspend fun deleteAll() = reviewDao.deleteAll()
}
