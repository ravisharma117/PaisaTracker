package com.naxits.paisatracker.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.naxits.paisatracker.data.database.entity.CategoryPatternEntity
import com.naxits.paisatracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime

/** One earlier transaction, as much of it as recurring detection needs. */
data class HistoryRow(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "date_time") val dateTime: LocalDateTime,
    @ColumnInfo(name = "amount") val amount: BigDecimal,
    @ColumnInfo(name = "merchant_name") val merchantName: String,
    @ColumnInfo(name = "description") val description: String?
)

@Dao
interface CategoryPatternDao {

    @Query("SELECT * FROM category_patterns WHERE pattern_key = :key AND type = :type")
    suspend fun get(key: String, type: String): CategoryPatternEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pattern: CategoryPatternEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(patterns: List<CategoryPatternEntity>)

    @Query("SELECT * FROM category_patterns")
    suspend fun getAll(): List<CategoryPatternEntity>

    @Query("DELETE FROM category_patterns")
    suspend fun deleteAll()

    /** Changes only the category of a transaction, leaving everything else (and its balances) alone. */
    @Query("UPDATE transactions SET category = :category, subcategory = :subcategory WHERE id = :transactionId")
    suspend fun setCategory(transactionId: Long, category: String, subcategory: String?)

    @Query(
        """
        SELECT id, date_time, amount, merchant_name, description FROM transactions
        WHERE party_id = :partyId AND transaction_type = :type AND is_deleted = 0
          AND id != :excludingTransactionId AND date_time < :before
        ORDER BY date_time DESC LIMIT :limit
        """
    )
    suspend fun historyForParty(
        partyId: Long, type: TransactionType, excludingTransactionId: Long, before: LocalDateTime, limit: Int
    ): List<HistoryRow>

    @Query(
        """
        SELECT id, date_time, amount, merchant_name, description FROM transactions
        WHERE merchant_name = :merchant COLLATE NOCASE AND transaction_type = :type AND is_deleted = 0
          AND id != :excludingTransactionId AND date_time < :before
        ORDER BY date_time DESC LIMIT :limit
        """
    )
    suspend fun historyForMerchant(
        merchant: String, type: TransactionType, excludingTransactionId: Long, before: LocalDateTime, limit: Int
    ): List<HistoryRow>

    /** Earlier transactions whose message mentions [pattern] (already wrapped in % signs, escaped with a backslash). */
    @Query(
        """
        SELECT id, date_time, amount, merchant_name, description FROM transactions
        WHERE sms_body LIKE :pattern ESCAPE '\' AND transaction_type = :type AND is_deleted = 0
          AND id != :excludingTransactionId AND date_time < :before
        ORDER BY date_time DESC LIMIT :limit
        """
    )
    suspend fun historyForMessageText(
        pattern: String, type: TransactionType, excludingTransactionId: Long, before: LocalDateTime, limit: Int
    ): List<HistoryRow>
}
