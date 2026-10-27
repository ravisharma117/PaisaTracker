package com.naxits.paisatracker.data.manager

import android.util.Log
import com.naxits.paisatracker.data.database.dao.CategoryPatternDao
import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.data.preferences.UserPreferencesRepository
import com.naxits.paisatracker.data.repository.MerchantMappingRepository
import com.naxits.paisatracker.data.repository.TransactionRepository
import com.naxits.paisatracker.domain.service.KeywordCategories
import com.naxits.paisatracker.domain.service.KeywordCategorySuggester
import javax.inject.Inject
import javax.inject.Singleton

/** What to file an entry under, and where that came from. */
data class CategoryChoice(val category: String, val subcategory: String?)

/**
 * Decides the category of an entry that is still uncategorised, from what the app now knows: the
 * category the user remembered for the vendor first, then a keyword in the payee name. An entry
 * already in a real category is never offered a new one.
 */
object UncategorisedFiller {

    suspend fun choose(
        entry: TransactionEntity,
        vendorChoice: suspend (merchantName: String) -> CategoryChoice?
    ): CategoryChoice? {
        if (!entry.category.equals(KeywordCategorySuggester.UNCATEGORISED, ignoreCase = true)) return null
        vendorChoice(entry.merchantName)?.let { return it }
        if (entry.transactionType !in KeywordCategorySuggester.SPENDING_TYPES) return null
        val found = KeywordCategories.match(entry.merchantName, entry.description) ?: return null
        return CategoryChoice(found.category, found.subcategory)
    }
}

/**
 * Runs once: entries still in Miscellaneous are filed under a category when the vendor was
 * remembered or a keyword in the payee name says what it is. It is silent (no review badges), and
 * entries the user, a rule or a remembered vendor already categorised are left alone.
 */
@Singleton
class KeywordCategoryBackfill @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val merchantMappingRepository: MerchantMappingRepository,
    private val patternDao: CategoryPatternDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    companion object {
        private const val TAG = "KeywordCategoryBackfill"
    }

    suspend fun runOnce() {
        if (userPreferencesRepository.isKeywordCategoryBackfillDone()) return

        var filed = 0
        for (entry in transactionRepository.getAllTransactionsList()) {
            if (entry.isDeleted) continue
            val choice = UncategorisedFiller.choose(entry) { name ->
                merchantMappingRepository.findForVendor(name)?.let { CategoryChoice(it.category, it.subcategory) }
            } ?: continue
            patternDao.setCategory(entry.id, choice.category, choice.subcategory)
            filed++
        }

        userPreferencesRepository.markKeywordCategoryBackfillDone()
        Log.d(TAG, "Filed $filed uncategorised entries")
    }
}
