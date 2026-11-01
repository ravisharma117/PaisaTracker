package com.naxits.paisatracker.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.naxits.paisatracker.data.database.entity.PartyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartyDao {

    @Query("SELECT * FROM parties WHERE is_archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM parties WHERE is_archived = 0")
    suspend fun getActive(): List<PartyEntity>

    @Query("SELECT * FROM parties")
    suspend fun getAll(): List<PartyEntity>

    @Query("SELECT * FROM parties WHERE id = :id")
    suspend fun getById(id: Long): PartyEntity?

    /** Active parties whose name or an alias contains [pattern] (already wrapped in % signs). */
    @Query(
        """
        SELECT * FROM parties
        WHERE is_archived = 0 AND (name LIKE :pattern OR aliases LIKE :pattern)
        ORDER BY name COLLATE NOCASE LIMIT :limit
        """
    )
    suspend fun search(pattern: String, limit: Int): List<PartyEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(party: PartyEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(parties: List<PartyEntity>)

    @Update
    suspend fun update(party: PartyEntity)

    @Query("DELETE FROM parties")
    suspend fun deleteAll()

    @Query("UPDATE transactions SET party_id = :partyId WHERE id = :transactionId")
    suspend fun setPartyForTransaction(transactionId: Long, partyId: Long?)

    /** Moves every transaction of [fromId] to [toId], for merging two parties. */
    @Query("UPDATE transactions SET party_id = :toId WHERE party_id = :fromId")
    suspend fun moveTransactions(fromId: Long, toId: Long)

    @Query("SELECT COUNT(*) FROM transactions WHERE party_id = :partyId AND is_deleted = 0")
    suspend fun transactionCount(partyId: Long): Int

    /**
     * The party of the latest earlier transaction with this merchant that a person has settled:
     * a transaction whose party is still only a suggestion does not count.
     */
    @Query(
        """
        SELECT party_id FROM transactions
        WHERE merchant_name = :merchant AND party_id IS NOT NULL AND is_deleted = 0
          AND id != :excludingTransactionId
          AND NOT EXISTS (
              SELECT 1 FROM transaction_reviews r
              WHERE r.transaction_id = transactions.id AND r.kind = 'PARTY' AND r.status = 'PENDING'
          )
        ORDER BY date_time DESC LIMIT 1
        """
    )
    suspend fun latestSettledPartyForMerchant(merchant: String, excludingTransactionId: Long): Long?
}
