package com.naxits.paisatracker.data.repository

import com.naxits.paisatracker.data.database.dao.MerchantMappingDao
import com.naxits.paisatracker.data.database.entity.MerchantMappingEntity
import com.naxits.paisatracker.domain.service.MerchantKey
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MerchantMappingRepository @Inject constructor(
    private val merchantMappingDao: MerchantMappingDao
) {
    
    suspend fun getCategoryForMerchant(merchantName: String): String? {
        return merchantMappingDao.getCategoryForMerchant(merchantName)
    }

    /** What the user filed this vendor under, under any spelling of its name. */
    suspend fun findForVendor(merchantName: String): MerchantMappingEntity? =
        merchantMappingDao.findForVendor(merchantName, MerchantKey.normalize(merchantName))

    suspend fun setMapping(merchantName: String, category: String, subcategory: String? = null) {
        // One mapping per vendor: a differently spelled earlier one is replaced, not kept beside it
        merchantMappingDao.deleteForVendor(merchantName, MerchantKey.normalize(merchantName))
        merchantMappingDao.insertOrUpdateMapping(
            MerchantMappingEntity(
                merchantName = merchantName,
                category = category,
                updatedAt = LocalDateTime.now(),
                subcategory = subcategory,
                matchKey = MerchantKey.normalize(merchantName)
            )
        )
    }

    /**
     * Remembers the vendor's category and files all of its existing spending entries under it, under
     * any spelling of the name. Returns how many entries were changed.
     */
    suspend fun rememberAndRefile(merchantName: String, category: String, subcategory: String?): Int {
        setMapping(merchantName, category, subcategory)
        val key = MerchantKey.normalize(merchantName)
        val spellings = merchantMappingDao.vendorNames().filter {
            it == merchantName || (key.isNotEmpty() && MerchantKey.normalize(it) == key)
        }
        if (spellings.isEmpty()) return 0
        return merchantMappingDao.refile(spellings, category, subcategory)
    }

    /** The user no longer wants this vendor filed automatically, under any spelling. */
    suspend fun removeMapping(merchantName: String) {
        merchantMappingDao.deleteForVendor(merchantName, MerchantKey.normalize(merchantName))
    }
    
    fun getAllMappings(): Flow<List<MerchantMappingEntity>> {
        return merchantMappingDao.getAllMappings()
    }
    
    suspend fun getMappingCount(): Int {
        return merchantMappingDao.getMappingCount()
    }

    suspend fun deleteAllMappings() {
        merchantMappingDao.deleteAllMappings()
    }
}