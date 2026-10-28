package com.naxits.paisatracker.presentation.ui.features.home

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The week Home lists transactions for: the calendar week, Monday to today. */
object HomeWeek {

    /** The Monday on or before [today]. */
    fun start(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
