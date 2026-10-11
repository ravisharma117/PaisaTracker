package com.naxits.paisatracker.utils

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Short forms of an amount for tight table cells and chart labels: 80K, 1.2L, 3Cr for rupees
 * (lakh and crore are how amounts are read in India) and 80K, 1.2M, 3B for other currencies.
 * The full figure stays one tap away, so rounding here never hides a value.
 */
object CompactAmount {

    private data class Step(val size: BigDecimal, val suffix: String)

    private val INDIAN = listOf(Step(BigDecimal("10000000"), "Cr"), Step(BigDecimal("100000"), "L"), Step(BigDecimal("1000"), "K"))
    private val INTERNATIONAL = listOf(Step(BigDecimal("1000000000"), "B"), Step(BigDecimal("1000000"), "M"), Step(BigDecimal("1000"), "K"))

    /** "80K", "-1.2L", "950". No currency symbol: callers put it where the layout wants it. */
    fun format(amount: BigDecimal, currencyCode: String = "INR"): String {
        val steps = if (currencyCode == "INR" || currencyCode == "NPR") INDIAN else INTERNATIONAL
        val abs = amount.abs()
        val step = steps.firstOrNull { abs >= it.size }
        val sign = if (amount.signum() < 0) "-" else ""
        if (step == null) return sign + abs.setScale(0, RoundingMode.HALF_UP).toPlainString()

        val scaled = abs.divide(step.size, 1, RoundingMode.HALF_UP).stripTrailingZeros()
        return sign + scaled.toPlainString() + step.suffix
    }
}
