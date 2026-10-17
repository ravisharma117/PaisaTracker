package com.naxits.paisatracker.domain.service

/** Who the other side of a transaction is, as far as its message says. */
data class Counterparty(
    val name: String? = null,
    /** Ten-digit mobile number. */
    val phone: String? = null,
    /** UPI address, lower case. */
    val upiId: String? = null,
    /** Digits of the other side's account number, at least four. */
    val account: String? = null
) {
    val isEmpty: Boolean get() = name.isNullOrBlank() && phone == null && upiId == null && account == null
}

/**
 * Reads the other side of a transaction out of its message and merchant text. The bank parsers
 * keep none of this, so it is worked out here from the stored message.
 */
object CounterpartyExtractor {

    // someone@bank, but not an e-mail address (a dot and letters after the part behind the @)
    private val UPI = Regex("""(?<![A-Za-z0-9._\-])([A-Za-z0-9._\-]{2,})@([A-Za-z][A-Za-z0-9]{1,})(?!\.[A-Za-z])""")

    // A mobile number standing on its own: not part of a longer number or an amount
    private val PHONE = Regex("""(?<![\d.,])[6-9]\d{9}(?!\d)""")

    // "to A/c 12345678", "from account no. XXXX1234": the other side's account, not the user's own
    private val ACCOUNT = Regex(
        """(?:to|from|beneficiary|by)\s+(?:a/c|ac|account|acct)\.?\s*(?:no\.?|number)?\s*[:\-]?\s*[xX*]*(\d{4,})""",
        RegexOption.IGNORE_CASE
    )

    /**
     * @param ownAccountLast4 the user's own account, so it is never mistaken for the other side
     */
    fun extract(smsBody: String?, merchantName: String?, ownAccountLast4: String? = null): Counterparty {
        val text = smsBody.orEmpty()
        val merchant = merchantName.orEmpty()

        val upiMatch = UPI.find(text) ?: UPI.find(merchant)
        val upiId = upiMatch?.value?.lowercase()
        val handle = upiMatch?.groupValues?.get(1)

        val phone = handle?.let { PHONE.find(it)?.value }
            ?: PHONE.find(merchant)?.value
            ?: PHONE.find(text)?.value

        val account = ACCOUNT.find(text)?.groupValues?.get(1)
            ?.takeIf { ownAccountLast4 == null || !it.endsWith(ownAccountLast4) }

        val name = merchant.takeIf { it.isNotBlank() && !it.startsWith("Unknown", ignoreCase = true) }
        return Counterparty(name = name, phone = phone, upiId = upiId, account = account)
    }
}
