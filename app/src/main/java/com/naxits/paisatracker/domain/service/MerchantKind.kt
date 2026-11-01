package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.presentation.common.icons.CategoryMapping

/**
 * Tells a company, firm or brand from a person, going by the name alone. A category is remembered
 * for a vendor only when it is a business: one person can be paid for very different things, so
 * filing all their payments under one category would be wrong more often than right.
 *
 * When the name is unclear it counts as a person, so a guess never fixes a category by mistake.
 */
object MerchantKind {

    private val HONORIFICS = setOf("mr", "mrs", "ms", "smt", "shri", "sri", "shree", "kumari", "master")

    // "s/o", "d/o", "w/o", "c/o" appear in the names of people
    private val RELATION = Regex("""\b[sdwc]\s*/?\s*o\b""")

    private val BUSINESS_WORDS = setOf(
        "traders", "trader", "trading", "enterprises", "enterprise", "store", "stores", "mart", "station", "pump",
        "agency", "agencies", "centre", "center", "kendra", "services", "service", "general", "electronics",
        "communication", "communications", "medical", "medicos", "pharmacy", "hospital", "clinic", "trust", "bank",
        "finance", "insurance", "ltd", "limited", "pvt", "private", "llp", "inc", "corp", "company", "co",
        "industries", "industry", "bhandar", "bhandaar", "emporium", "shop", "restaurant", "hotel", "dhaba", "cafe",
        "sweets", "foods", "food", "dairy", "motors", "auto", "automobiles", "garage", "works", "associates",
        "solutions", "technologies", "tech", "systems", "labs", "school", "college", "academy", "institute",
        "foundation", "samiti", "ashram", "mandir", "brothers", "bros", "sons", "depot", "house", "plaza", "complex",
        "market", "mall", "retail", "wholesale", "distributors", "suppliers", "builders", "hardware", "tailors",
        "jewellers", "jewelers", "optical", "opticals", "studio", "salon", "parlour", "gym", "fitness", "club",
        "cinema", "gas", "petroleum", "fuel", "filling", "udyog", "vyapar", "nigam", "board", "society",
        "department", "billdesk", "payments", "pay", "mobile", "mobiles", "boutique", "garments", "textiles"
    )

    // What the app writes when it does not know who was paid
    private val PLACEHOLDERS = setOf("unknown", "merchant", "transferred", "unknown merchant", "transferred to mr")

    /**
     * @param isKnownBrand whether the name is a company the app already knows (the brand list)
     */
    fun isBusiness(
        name: String,
        isKnownBrand: (String) -> Boolean = { CategoryMapping.getCategory(it) != "Miscellaneous" }
    ): Boolean {
        val lower = name.trim().lowercase()
        if (lower.isEmpty() || lower in PLACEHOLDERS || lower.startsWith("unknown")) return false
        // A UPI address is a person's handle unless the name says otherwise
        if (lower.contains('@')) return false

        val tokens = lower.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        if (tokens.any { it in HONORIFICS } || RELATION.containsMatchIn(lower)) return false

        if (tokens.any { it in BUSINESS_WORDS }) return true
        if (KeywordCategories.match(name) != null) return true
        if (isKnownBrand(name)) return true
        return false
    }
}
