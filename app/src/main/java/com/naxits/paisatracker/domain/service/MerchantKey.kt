package com.naxits.paisatracker.domain.service

/**
 * One key for a vendor however its name is spelled. "Shakti Fuel Station", "SHAKTI FILLING STATION"
 * and "Shaktifillingstation" are the same pump, so a category remembered for one applies to all.
 */
object MerchantKey {

    // Words that only say what kind of company it is, and are often left out or abbreviated
    private val LEGAL_WORDS = setOf("limited", "ltd", "pvt", "private", "llp", "inc", "co")

    // Different words for the same kind of place, folded to one
    private val SYNONYMS = listOf(
        "fillingstation" to "fuelstation",
        "petrolstation" to "fuelstation",
        "petrolpump" to "fuelstation"
    )

    fun normalize(name: String): String {
        val lower = name.trim().lowercase()
        // A UPI address is its handle plus the bank; the bank part and trailing digits are not the vendor
        val handle = if (lower.contains('@')) lower.substringBefore('@').trimEnd { it.isDigit() || it == '-' } else lower
        val tokens = handle.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() && it !in LEGAL_WORDS }
        var key = tokens.joinToString("")
        for ((from, to) in SYNONYMS) key = key.replace(from, to)
        return key
    }
}
