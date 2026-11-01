package com.naxits.paisatracker.domain.service

import java.math.BigDecimal

/** What is still owed in one direction, split into what counts and what is kept only as a reminder. */
data class Outstanding(val counted: BigDecimal, val rememberOnly: BigDecimal)

/**
 * Splits what is still owed between entries that count and entries marked "remember only", which
 * are kept as a reminder but left out of net worth and the Lend & Borrow totals.
 *
 * A repayment does not say which entry it pays back, so it is applied to the counted entries
 * first, and only what is left over reduces the remember-only ones.
 */
object LendingBalance {

    fun outstanding(counted: BigDecimal, rememberOnly: BigDecimal, repaid: BigDecimal): Outstanding {
        val countedLeft = counted.subtract(repaid).max(BigDecimal.ZERO)
        val repaidLeft = repaid.subtract(counted).max(BigDecimal.ZERO)
        val rememberLeft = rememberOnly.subtract(repaidLeft).max(BigDecimal.ZERO)
        return Outstanding(countedLeft, rememberLeft)
    }
}
