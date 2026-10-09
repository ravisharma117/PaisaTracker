package com.ritesh.cashiro.domain.service

import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

/**
 * The Indian financial year: 1 April to 31 March. A year is named by the calendar year it starts
 * in, so "2026" is April 2026 to March 2027.
 */
object FinancialYear {

    private val FIRST_MONTH = Month.APRIL

    /** The starting calendar year of the financial year that [date] falls in. */
    fun startYearOf(date: LocalDate): Int =
        if (date.month >= FIRST_MONTH) date.year else date.year - 1

    fun startYearOf(month: YearMonth): Int = if (month.month >= FIRST_MONTH) month.year else month.year - 1

    /** The twelve months of the year, April first. */
    fun months(startYear: Int): List<YearMonth> =
        (0L until 12L).map { YearMonth.of(startYear, FIRST_MONTH).plusMonths(it) }

    fun start(startYear: Int): LocalDate = LocalDate.of(startYear, FIRST_MONTH, 1)

    fun end(startYear: Int): LocalDate = LocalDate.of(startYear + 1, Month.MARCH, 31)

    /** "2026-27" */
    fun label(startYear: Int): String = "$startYear-${((startYear + 1) % 100).toString().padStart(2, '0')}"
}
