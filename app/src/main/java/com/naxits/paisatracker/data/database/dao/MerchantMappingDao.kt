package com.naxits.paisatracker.data.database.dao

import androidx.room.*
import com.naxits.paisatracker.data.database.entity.MerchantMappingEntity
import kotlinx.coroutines.flow.Flow

/** How many entries of one payee name sit under one category and sub-category. */
data class VendorGroup(
    @ColumnInfo(name = "merchant_name") val merchantName: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "subcategory") val subcategory: String?,
    @ColumnInfo(name = "n") val count: Int
)

@Dao
interface MerchantMappingDao {

    /** Spending entries that are already in a real category, counted per payee, category and sub-category. */
    @Query(
        """
        SELECT merchant_name, category, subcategory, COUNT(*) AS n FROM transactions
        WHERE is_deleted = 0 AND transaction_type IN ('EXPENSE', 'CREDIT')
          AND category != 'Miscellaneous' AND merchant_name != ''
        GROUP BY merchant_name, category, subcategory
        """
    )
    suspend fun vendorGroups(): List<VendorGroup>

    /** Every payee name that appears on a spending entry, to find the spellings of one vendor. */
    @Query(
        """
        SELECT DISTINCT merchant_name FROM transactions
        WHERE is_deleted = 0 AND transaction_type IN ('EXPENSE', 'CREDIT') AND merchant_name != ''
        """
    )
    suspend fun vendorNames(): List<String>

    /** Files every spending entry from these payee names under one category. Returns how many changed. */
    @Query(
        """
        UPDATE transactions SET category = :category, subcategory = :subcategory
        WHERE merchant_name IN (:merchantNames) AND is_deleted = 0 AND transaction_type IN ('EXPENSE', 'CREDIT')
        """
    )
    suspend fun refile(merchantNames: List<String>, category: String, subcategory: String?): Int
    
    @Query("SELECT category FROM merchant_mappings WHERE merchant_name = :merchantName")
    suspend fun getCategoryForMerchant(merchantName: String): String?

    /** The mapping for a vendor: the exact name first, otherwise any spelling that has the same [matchKey]. */
    @Query(
        """
        SELECT * FROM merchant_mappings
        WHERE merchant_name = :merchantName OR (:matchKey != '' AND match_key = :matchKey)
        ORDER BY (merchant_name = :merchantName) DESC, updated_at DESC
        LIMIT 1
        """
    )
    suspend fun findForVendor(merchantName: String, matchKey: String): MerchantMappingEntity?

    /** Forgets the vendor under every spelling it was saved as. */
    @Query("DELETE FROM merchant_mappings WHERE merchant_name = :merchantName OR (:matchKey != '' AND match_key = :matchKey)")
    suspend fun deleteForVendor(merchantName: String, matchKey: String)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMapping(mapping: MerchantMappingEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: MerchantMappingEntity)
    
    @Query("DELETE FROM merchant_mappings WHERE merchant_name = :merchantName")
    suspend fun deleteMapping(merchantName: String)
    
    @Query("SELECT * FROM merchant_mappings ORDER BY merchant_name ASC")
    fun getAllMappings(): Flow<List<MerchantMappingEntity>>
    
    @Query("SELECT COUNT(*) FROM merchant_mappings")
    suspend fun getMappingCount(): Int
    
    @Query("DELETE FROM merchant_mappings")
    suspend fun deleteAllMappings()
}