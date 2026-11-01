package com.naxits.paisatracker.domain.service

/**
 * Finds the account a message belongs to when the user has given it a name of their own.
 *
 * A message says "HDFC Bank A/C *1234", but the user may have called that account "HDFC Bank
 * Salary". Matching on the exact bank name alone would then start a second account for every
 * message, so the digits and a related bank name pick out the one the user already has.
 */
object AccountMatcher {

    /** Shortest name that can count as the start of another, so "SBI" is not "SBI Savings Plus" by accident. */
    private const val MIN_RELATED_LENGTH = 4

    /**
     * The existing bank name that [parsedBank] refers to for the account ending [last4] (already
     * narrowed to [existingBanks] that carry those digits), or null when there is no clear one.
     * An exact name always wins; otherwise exactly one related name must remain.
     */
    fun resolve(parsedBank: String, existingBanks: List<String>): String? {
        existingBanks.firstOrNull { it.equals(parsedBank, ignoreCase = true) }?.let { return it }
        val related = existingBanks.filter { related(parsedBank, it) }.distinct()
        return related.singleOrNull()
    }

    /** One name starts with the other once spaces and punctuation are ignored: "HDFC Bank" and "HDFC Bank Salary". */
    fun related(a: String, b: String): Boolean {
        val x = normalize(a)
        val y = normalize(b)
        if (x.length < MIN_RELATED_LENGTH || y.length < MIN_RELATED_LENGTH) return false
        return x.startsWith(y) || y.startsWith(x)
    }

    private fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }
}
