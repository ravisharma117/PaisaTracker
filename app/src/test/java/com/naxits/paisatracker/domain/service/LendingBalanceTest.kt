package com.naxits.paisatracker.domain.service

import java.math.BigDecimal
import org.junit.Test
import kotlin.test.assertEquals

class LendingBalanceTest {

    private fun bd(v: String) = BigDecimal(v)

    @Test
    fun rememberOnlyMoneyIsKeptApartFromWhatCounts() {
        assertEquals(Outstanding(bd("5000"), bd("2000")), LendingBalance.outstanding(bd("5000"), bd("2000"), bd("0")))
    }

    @Test
    fun aRepaymentPaysTheCountedEntriesFirst() {
        assertEquals(Outstanding(bd("2000"), bd("2000")), LendingBalance.outstanding(bd("5000"), bd("2000"), bd("3000")))
    }

    @Test
    fun whatIsRepaidBeyondTheCountedEntriesReducesTheRememberOnlyOnes() {
        assertEquals(Outstanding(bd("0"), bd("1000")), LendingBalance.outstanding(bd("5000"), bd("2000"), bd("6000")))
    }

    @Test
    fun nothingGoesBelowZero() {
        assertEquals(Outstanding(bd("0"), bd("0")), LendingBalance.outstanding(bd("1000"), bd("500"), bd("9000")))
    }
}
