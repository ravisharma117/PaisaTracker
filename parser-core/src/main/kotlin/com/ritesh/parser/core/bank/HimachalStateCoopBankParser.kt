package com.ritesh.parser.core.bank

import java.math.BigDecimal

/**
 * The Himachal Pradesh State Cooperative Bank.
 *
 * Its sender id ("...HPSBNK...") contains "PSB", so it must be registered before
 * [PunjabSindBankParser], which also rejects this sender.
 */
class HimachalStateCoopBankParser : BankParser() {

    override fun getBankName() = "Himachal Pradesh State Cooperative Bank"

    override fun canHandle(sender: String): Boolean = SENDER_KEY in sender.uppercase()

    // The message carries no payee, so the bank itself stands in as the merchant.
    override fun extractMerchant(message: String, sender: String): String = getBankName()

    // "Now your A/c balance is Rs. 12345.67" (optionally followed by "CR")
    override fun extractBalance(message: String): BigDecimal? =
        BALANCE.find(message)?.groupValues?.get(1)?.replace(",", "")?.toBigDecimalOrNull()
            ?: super.extractBalance(message)

    companion object {
        const val SENDER_KEY = "HPSBNK"
        private val BALANCE = Regex("""balance\s+is\s+Rs\.?\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    }
}
