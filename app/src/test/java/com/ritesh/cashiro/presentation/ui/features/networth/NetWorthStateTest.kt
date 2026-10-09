package com.ritesh.cashiro.presentation.ui.features.networth

import com.ritesh.cashiro.data.database.entity.NetWorthSnapshotEntity
import com.ritesh.cashiro.domain.model.AccountKind
import com.ritesh.cashiro.domain.service.AccountAmount
import com.ritesh.cashiro.domain.service.NetWorthCalculator
import com.ritesh.cashiro.domain.service.NetWorthInput
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class NetWorthStateTest {

    private val today = LocalDate.parse("2026-10-09")

    private fun live(amount: String) = NetWorthCalculator.calculate(
        NetWorthInput(
            listOf(AccountAmount(AccountKind.BANK, "Bank", BigDecimal(amount))),
            BigDecimal.ZERO, BigDecimal.ZERO, emptyList()
        ),
        "INR"
    )

    private fun snap(month: String, worth: String, estimate: Boolean = false) = NetWorthSnapshotEntity(
        yearMonth = month, assets = BigDecimal(worth), liabilities = BigDecimal.ZERO,
        netWorth = BigDecimal(worth), isEstimate = estimate
    )

    @Test
    fun theRunningMonthUsesTheLiveFigureNotTheStoredOne() {
        val state = NetWorthViewModel.buildState(live("5000"), listOf(snap("2026-10", "1")), today)
        assertEquals(BigDecimal("5000"), state.monthly.last().netWorth)
        assertEquals("2026-10", state.monthly.last().label)
        assertEquals(false, state.monthly.last().isEstimate)
    }

    @Test
    fun changeSinceLastMonthAndOverTheYear() {
        val snapshots = listOf(snap("2025-10", "4000"), snap("2026-09", "4500"))
        val state = NetWorthViewModel.buildState(live("5000"), snapshots, today)

        assertEquals(BigDecimal("500"), state.sinceLastMonth?.amount)
        assertEquals(BigDecimal("11.1"), state.sinceLastMonth?.percent)
        assertEquals(BigDecimal("1000"), state.overTheYear?.amount)
        assertEquals(BigDecimal("25.0"), state.overTheYear?.percent)
    }

    @Test
    fun noComparisonWhenTheEarlierMonthIsMissing() {
        val state = NetWorthViewModel.buildState(live("5000"), emptyList(), today)
        assertNull(state.sinceLastMonth)
        assertNull(state.overTheYear)
    }

    @Test
    fun aZeroStartingPointHasAnAmountButNoPercentage() {
        val state = NetWorthViewModel.buildState(live("5000"), listOf(snap("2026-09", "0")), today)
        assertEquals(BigDecimal("5000"), state.sinceLastMonth?.amount)
        assertNull(state.sinceLastMonth?.percent)
    }

    @Test
    fun theMonthlyTrendShowsAtMostTwelveMonthsOldestFirst() {
        val snapshots = (1..14).map { snap(java.time.YearMonth.of(2025, 8).plusMonths(it.toLong() - 1).toString(), "100") }
            .filter { it.yearMonth < "2026-10" }
        val state = NetWorthViewModel.buildState(live("5000"), snapshots, today)

        assertEquals(12, state.monthly.size)
        assertEquals("2026-10", state.monthly.last().label)
        assertTrue(state.monthly.map { it.label } == state.monthly.map { it.label }.sorted())
    }

    @Test
    fun theYearlyViewKeepsTheLatestMonthOfEachYear() {
        val snapshots = listOf(
            snap("2025-06", "100", estimate = true), snap("2025-12", "300", estimate = true),
            snap("2026-01", "400"), snap("2026-09", "900")
        )
        val state = NetWorthViewModel.buildState(live("1000"), snapshots, today)

        assertEquals(listOf("2025", "2026"), state.yearly.map { it.label })
        assertEquals(BigDecimal("300"), state.yearly[0].netWorth)
        assertEquals(true, state.yearly[0].isEstimate)
        // 2026's latest month is the running one, which is live
        assertEquals(BigDecimal("1000"), state.yearly[1].netWorth)
    }

    @Test
    fun aNegativeStartingPointStillGivesAMeaningfulPercentage() {
        val state = NetWorthViewModel.buildState(live("-500"), listOf(snap("2026-09", "-1000")), today)
        // Improved from -1000 to -500: up 500, which is 50% of the size of the old figure
        assertEquals(BigDecimal("500"), state.sinceLastMonth?.amount)
        assertEquals(BigDecimal("50.0"), state.sinceLastMonth?.percent)
    }
}
