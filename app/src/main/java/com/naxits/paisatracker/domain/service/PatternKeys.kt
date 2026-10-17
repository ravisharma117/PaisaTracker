package com.naxits.paisatracker.domain.service

/**
 * The keys a transaction is remembered under in `category_patterns`, strongest first. The same
 * counterparty can be known several ways (a saved customer, a UPI address, a phone number, the
 * merchant text); any of them can recognise the next entry in a series.
 */
object PatternKeys {
    const val PARTY = "party:"
    const val UPI = "upi:"
    const val ACCOUNT = "acct:"
    const val PHONE = "phone:"
    const val MERCHANT = "merchant:"

    fun keysFor(partyId: Long?, counterparty: Counterparty, merchantName: String): List<String> = buildList {
        if (partyId != null) add("$PARTY$partyId")
        counterparty.upiId?.let { add("$UPI${it.lowercase()}") }
        counterparty.account?.let { add("$ACCOUNT$it") }
        counterparty.phone?.let { add("$PHONE$it") }
        val merchant = merchantName.trim()
        if (merchant.isNotEmpty() && !merchant.startsWith("Unknown", ignoreCase = true)) {
            add("$MERCHANT${merchant.lowercase()}")
        }
    }

    /** Everything but the merchant text names a specific counterparty. */
    fun isStrong(key: String): Boolean = !key.startsWith(MERCHANT)

    /** What the key stands for, in the words of a reason: "the same account", "the same payee". */
    fun describe(key: String): String = when {
        key.startsWith(PARTY) -> "customer or vendor"
        key.startsWith(UPI) -> "UPI id"
        key.startsWith(ACCOUNT) -> "account"
        key.startsWith(PHONE) -> "phone number"
        else -> "payee"
    }

    fun value(key: String): String = key.substringAfter(':')
}
