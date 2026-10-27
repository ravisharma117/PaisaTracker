package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.dao.VendorGroup

/** A company, firm or brand the user keeps paying under one category, offered for remembering. */
data class VendorSuggestion(
    val merchantName: String,
    val category: String,
    val subcategory: String?,
    val entryCount: Int
)

/**
 * Works out which vendors are worth offering to remember: businesses with at least [MIN_ENTRIES]
 * categorised entries that are not remembered yet and that the user has not turned down. Spellings
 * of one vendor are counted together, and the category most of its entries are in is the one offered.
 */
object VendorSuggestions {
    const val MIN_ENTRIES = 2

    fun build(
        groups: List<VendorGroup>,
        rememberedKeys: Set<String>,
        ignoredKeys: Set<String>,
        isBusiness: (String) -> Boolean
    ): List<VendorSuggestion> =
        groups.groupBy { MerchantKey.normalize(it.merchantName) }
            .mapNotNull { (key, rows) ->
                if (key.isEmpty() || key in rememberedKeys || key in ignoredKeys) return@mapNotNull null
                val total = rows.sumOf { it.count }
                if (total < MIN_ENTRIES) return@mapNotNull null

                // The spelling used most often is the one shown
                val name = rows.groupBy { it.merchantName }.maxByOrNull { (_, r) -> r.sumOf { it.count } }!!.key
                if (!isBusiness(name)) return@mapNotNull null

                val dominant = rows.groupBy { it.category to it.subcategory }
                    .maxByOrNull { (_, r) -> r.sumOf { it.count } }!!.key
                VendorSuggestion(name, dominant.first, dominant.second, total)
            }
            .sortedWith(compareByDescending<VendorSuggestion> { it.entryCount }.thenBy { it.merchantName.lowercase() })
}
