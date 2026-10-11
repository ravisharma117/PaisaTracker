package com.naxits.paisatracker.domain.service

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * Repayment maths for a loan paid in equal monthly instalments (EMI).
 *
 * The figures are estimates: real lenders round, change rates and apply fees. That is why the
 * app lets the user type in the real remaining balance, which becomes the new starting point.
 */
object LoanMath {

    private val MC = MathContext(20, RoundingMode.HALF_UP)
    private val TWELVE_HUNDRED = BigDecimal(1200)
    private const val MAX_MONTHS = 1200

    /** The monthly instalment that repays [principal] over [months] at [annualRatePercent]. */
    fun emi(principal: BigDecimal, annualRatePercent: BigDecimal, months: Int): BigDecimal {
        require(months > 0) { "months must be positive" }
        if (principal.signum() <= 0) return BigDecimal.ZERO.setScale(2)
        val r = annualRatePercent.divide(TWELVE_HUNDRED, MC)
        if (r.signum() == 0) return principal.divide(BigDecimal(months), 2, RoundingMode.HALF_UP)
        val growth = BigDecimal.ONE.add(r).pow(months, MC)
        return principal.multiply(r).multiply(growth)
            .divide(growth.subtract(BigDecimal.ONE), MC)
            .setScale(2, RoundingMode.HALF_UP)
    }

    /** [day] of the month, or the last day of shorter months. */
    fun paymentDate(month: YearMonth, day: Int): LocalDate = month.atDay(day.coerceIn(1, month.lengthOfMonth()))

    /**
     * What is still owed on [date], carrying [anchorValue] (owed on [anchorDate]) forward through
     * every EMI paid after the anchor up to and including [date]. Each EMI first covers the
     * month's interest and the rest reduces the balance. Without an EMI the balance stays as it is.
     */
    fun balanceOn(
        anchorValue: BigDecimal,
        anchorDate: LocalDate,
        annualRatePercent: BigDecimal?,
        emi: BigDecimal?,
        emiDay: Int?,
        date: LocalDate
    ): BigDecimal {
        if (emi == null || emi.signum() <= 0 || emiDay == null || !date.isAfter(anchorDate)) return anchorValue
        val monthlyRate = (annualRatePercent ?: BigDecimal.ZERO).divide(TWELVE_HUNDRED, MC)

        var balance = anchorValue
        var month = YearMonth.from(anchorDate)
        var steps = 0
        while (steps++ < MAX_MONTHS && balance.signum() > 0) {
            val paid = paymentDate(month, emiDay)
            month = month.plusMonths(1)
            if (!paid.isAfter(anchorDate)) continue
            if (paid.isAfter(date)) break
            val interest = balance.multiply(monthlyRate)
            val principalPart = emi.subtract(interest)
            balance = balance.subtract(principalPart).max(BigDecimal.ZERO)
        }
        return balance.setScale(2, RoundingMode.HALF_UP)
    }
}
