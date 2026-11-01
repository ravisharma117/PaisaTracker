package com.naxits.paisatracker.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.naxits.paisatracker.data.database.entity.TransactionReviewEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface TransactionReviewDao {

    /** Returns -1 when this transaction already has a review of that kind. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(review: TransactionReviewEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reviews: List<TransactionReviewEntity>)

    @Update
    suspend fun update(review: TransactionReviewEntity)

    @Query("SELECT * FROM transaction_reviews WHERE id = :id")
    suspend fun getById(id: Long): TransactionReviewEntity?

    @Query("SELECT * FROM transaction_reviews WHERE transaction_id = :transactionId AND kind = :kind")
    suspend fun get(transactionId: Long, kind: String): TransactionReviewEntity?

    @Query("SELECT * FROM transaction_reviews WHERE transaction_id = :transactionId ORDER BY created_at")
    fun observeForTransaction(transactionId: Long): Flow<List<TransactionReviewEntity>>

    @Query("SELECT * FROM transaction_reviews WHERE transaction_id = :transactionId ORDER BY created_at")
    suspend fun getForTransaction(transactionId: Long): List<TransactionReviewEntity>

    /** Ids of the transactions that still have something to review, so lists can show a badge. */
    @Query(
        """
        SELECT DISTINCT r.transaction_id FROM transaction_reviews r
        JOIN transactions t ON t.id = r.transaction_id
        WHERE r.status = 'PENDING' AND t.is_deleted = 0
        """
    )
    fun observePendingTransactionIds(): Flow<List<Long>>

    @Query("SELECT * FROM transaction_reviews WHERE status = 'PENDING' ORDER BY created_at")
    suspend fun getPending(): List<TransactionReviewEntity>

    /** Pending reviews created at or before [cutoff], the ones due for automatic confirmation. */
    @Query("SELECT * FROM transaction_reviews WHERE status = 'PENDING' AND created_at <= :cutoff")
    suspend fun getPendingCreatedOnOrBefore(cutoff: LocalDateTime): List<TransactionReviewEntity>

    @Query("SELECT * FROM transaction_reviews")
    suspend fun getAll(): List<TransactionReviewEntity>

    @Query("DELETE FROM transaction_reviews")
    suspend fun deleteAll()
}
