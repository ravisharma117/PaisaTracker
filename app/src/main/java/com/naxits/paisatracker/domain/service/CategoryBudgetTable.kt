package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.BudgetCategoryLimitEntity
import com.naxits.paisatracker.data.database.entity.BudgetEntity
import com.naxits.paisatracker.data.database.entity.BudgetPeriod
import com.naxits.paisatracker.data.database.entity.BudgetType
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/** One category line: what was planned, spent and is left. Budget and remaining are null without a limit. */
data class CategoryBudgetRow(
    val category: String,
    val budget: BigDecimal?,
    val spent: BigDecimal
) {
    val remaining: BigDecimal? get() = budget?.subtract(spent)
    val isOverBudget: Boolean get() = budget != null && spent > budget
}

data class CategoryBudgetTable(
    val rows: List<CategoryBudgetRow>,
    /** Sum of the category limits. */
    val totalBudget: BigDecimal,
    /** Everything spent, including categories without a limit. */
    val totalSpent: BigDecimal
) {
    val hasBudget: Boolean get() = rows.any { it.budget != null }

    /** What is left of the limits, counting only the categories that have one. */
    val totalRemaining: BigDecimal
        get() = totalBudget.subtract(rows.filter { it.budget != null }.fold(BigDecimal.ZERO) { a, r -> a.add(r.spent) })
}

/** The planned amounts for one month, in the display currency. */
data class MonthBudget(
    /** Sum of the budgets' overall amounts, or null when no budget applies to the month. */
    val overall: BigDecimal?,
    val categoryLimits: Map<String, BigDecimal>
) {
    companion object {
        val NONE = MonthBudget(null, emptyMap())
    }
}

object CategoryBudgetCalculator {

    /**
     * Rows for every category that has a limit or any spending: biggest spender first, then the
     * categories with a limit and nothing spent.
     */
    fun table(spent: Map<String, BigDecimal>, limits: Map<String, BigDecimal>): CategoryBudgetTable {
        val names = (spent.filterValues { it.signum() != 0 }.keys + limits.keys)
        val rows = names
            .map { CategoryBudgetRow(it, limits[it], spent[it] ?: BigDecimal.ZERO) }
            .sortedWith(compareByDescending<CategoryBudgetRow> { it.spent }.thenBy { it.category })
        return CategoryBudgetTable(
            rows = rows,
            totalBudget = limits.values.fold(BigDecimal.ZERO) { a, b -> a.add(b) },
            totalSpent = spent.values.fold(BigDecimal.ZERO) { a, b -> a.add(b) }
        )
    }

    /**
     * The budgets that apply to [month]: active expense budgets whose period overlaps it, leaving
     * out yearly ones because their amount is for the whole year. [factorOf] converts from a
     * budget's currency into the display currency.
     */
    fun budgetFor(
        month: YearMonth,
        budgets: List<BudgetEntity>,
        limits: List<BudgetCategoryLimitEntity>,
        factorOf: (String) -> BigDecimal
    ): MonthBudget {
        val start: LocalDateTime = month.atDay(1).atStartOfDay()
        val end: LocalDateTime = month.atEndOfMonth().atTime(LocalTime.MAX)
        val applicable = budgets.filter {
            it.isActive && it.budgetType == BudgetType.EXPENSE && it.periodType != BudgetPeriod.YEARLY &&
                !it.startDate.isAfter(end) && !it.endDate.isBefore(start)
        }
        if (applicable.isEmpty()) return MonthBudget.NONE

        val ids = applicable.associateBy { it.id }
        val perCategory = linkedMapOf<String, BigDecimal>()
        limits.filter { it.budgetId in ids }.forEach { limit ->
            val currency = ids.getValue(limit.budgetId).currency
            val amount = limit.limitAmount.multiply(factorOf(currency))
            perCategory[limit.categoryName] = (perCategory[limit.categoryName] ?: BigDecimal.ZERO).add(amount)
        }
        val overall = applicable.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.amount.multiply(factorOf(b.currency))) }
        return MonthBudget(overall, perCategory)
    }
}
