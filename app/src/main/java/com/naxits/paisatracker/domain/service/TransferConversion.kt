package com.naxits.paisatracker.domain.service

import java.math.BigDecimal

/**
 * Balance arithmetic for turning an income or expense entry into an account-to-account transfer,
 * free of Android and the database so it can be tested with fixed data.
 */
object TransferConversion {

    /**
     * Net balance change per account (keyed by its last four digits) when an entry that already
     * moved [oldAccount] by [oldEffect] becomes a transfer of [amount] from [source] to [target].
     * Accounts whose balance ends up unchanged are left out.
     */
    fun netChanges(
        oldAccount: String?,
        oldEffect: BigDecimal,
        source: String?,
        target: String?,
        amount: BigDecimal
    ): Map<String, BigDecimal> {
        val changes = LinkedHashMap<String, BigDecimal>()
        fun add(account: String?, delta: BigDecimal) {
            if (account != null) changes[account] = (changes[account] ?: BigDecimal.ZERO).add(delta)
        }
        add(oldAccount, oldEffect.negate())
        add(source, amount.negate())
        add(target, amount)
        return changes.filterValues { it.signum() != 0 }
    }
}
