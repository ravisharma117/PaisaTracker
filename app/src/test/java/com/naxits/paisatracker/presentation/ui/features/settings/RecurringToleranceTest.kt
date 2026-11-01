package com.naxits.paisatracker.presentation.ui.features.settings

import com.naxits.paisatracker.domain.service.RecurringPatternDetector
import org.junit.Test
import kotlin.test.assertEquals

class RecurringToleranceTest {

    @Test
    fun tolerancesAreShownAsPercentages() {
        assertEquals("5%", formatToleranceTenths(50))
        assertEquals("7.5%", formatToleranceTenths(75))
        assertEquals("10%", formatToleranceTenths(100))
    }

    @Test
    fun theChoicesCoverFiveToTenPercentAndTheDefaultIsTen() {
        assertEquals(listOf(50, 75, 100), RecurringPatternDetector.TOLERANCE_CHOICES_TENTHS)
        assertEquals(100, RecurringPatternDetector.DEFAULT_TOLERANCE_TENTHS)
    }
}
