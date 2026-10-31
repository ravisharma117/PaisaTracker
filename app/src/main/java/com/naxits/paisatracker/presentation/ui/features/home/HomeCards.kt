package com.naxits.paisatracker.presentation.ui.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.naxits.paisatracker.R
import com.naxits.paisatracker.data.database.entity.AccountBalanceEntity
import com.naxits.paisatracker.data.database.entity.RecurringTransactionEntity
import com.naxits.paisatracker.domain.service.MonthlySavings
import com.naxits.paisatracker.presentation.ui.components.CashiroCard
import com.naxits.paisatracker.presentation.ui.features.categories.displayName
import com.naxits.paisatracker.presentation.ui.theme.Spacing
import com.naxits.paisatracker.utils.CurrencyFormatter
import com.naxits.paisatracker.utils.formatBalance
import ir.ehsannarmani.compose_charts.PieChart
import ir.ehsannarmani.compose_charts.models.Pie
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** This month's income, expenses and what was kept. Totals follow the hide-totals setting. */
@Composable
fun MonthlySummaryCard(
    income: BigDecimal,
    expenses: BigDecimal,
    currency: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = remember(income, expenses) { MonthlySavings.of(income, expenses) }
    CashiroCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.home_widget_monthly_summary),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = YearMonth.now().displayName(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Figure(stringResource(R.string.income), summary.income, currency, MaterialTheme.colorScheme.primary)
            Figure(stringResource(R.string.expense), summary.expenses, currency, MaterialTheme.colorScheme.error)
            Figure(
                label = stringResource(R.string.balance_label),
                amount = summary.savings,
                currency = currency,
                color = if (summary.savings.signum() < 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                note = stringResource(R.string.home_savings_rate, summary.ratePercent.toPlainString())
            )
        }
    }
}

@Composable
private fun Figure(
    label: String,
    amount: BigDecimal,
    currency: String,
    color: androidx.compose.ui.graphics.Color,
    note: String? = null
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            CurrencyFormatter.formatTotal(amount, currency),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        if (note != null) {
            Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The Cash balance on its own, shown above the transactions. Totals follow the hide-totals setting. */
@Composable
fun CashHomeCard(
    account: AccountBalanceEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CashiroCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.home_widget_cash),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = account.formatBalance(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** The next few recurring transactions that are due. */
@Composable
fun RecurringHomeCard(
    items: List<RecurringTransactionEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    CashiroCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Text(
            text = stringResource(R.string.home_widget_recurring),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.nextRunDate.format(formatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    CurrencyFormatter.formatCurrency(item.amount, item.currency),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * This month's expenses: a pie of the categories, then every category with its subcategories.
 * Only categories and subcategories with something spent are listed.
 */
@Composable
fun CategoryBreakdownCard(
    categories: List<com.naxits.paisatracker.domain.usecase.CategoryBreakdown>,
    currency: String,
    categoriesMap: Map<String, com.naxits.paisatracker.data.database.entity.CategoryEntity>,
    onOpenCategory: (String) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fallback = MaterialTheme.colorScheme.primary
    val colors = remember(categories, categoriesMap, fallback) {
        categories.associate { rank ->
            rank.category to (categoriesMap[rank.category]?.color?.let {
                try {
                    androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(it))
                } catch (_: IllegalArgumentException) {
                    null
                }
            } ?: fallback)
        }
    }
    CashiroCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.home_widget_top_categories),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = YearMonth.now().displayName(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        if (categories.isEmpty()) {
            Text(
                text = stringResource(R.string.home_top_categories_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@CashiroCard
        }

        val pies = remember(categories, colors) {
            categories.map { rank ->
                val color = colors.getValue(rank.category)
                Pie(
                    label = rank.category,
                    data = rank.total.toDouble(),
                    color = color,
                    selectedColor = color
                )
            }
        }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            PieChart(
                modifier = Modifier.size(160.dp),
                data = pies,
                style = Pie.Style.Stroke(width = 28.dp)
            )
        }
        Spacer(Modifier.height(Spacing.sm))

        categories.forEach { rank ->
            val color = colors.getValue(rank.category)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCategory(rank.category) }
                    .padding(vertical = Spacing.xs)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color, CircleShape)
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = rank.category,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = CurrencyFormatter.formatTotal(rank.total, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { (rank.percentOfMonth / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = color,
                    trackColor = color.copy(alpha = 0.15f)
                )
                // A category with no named subcategory has nothing more to say than its own line
                if (rank.subcategories.any { it.name != null }) {
                    rank.subcategories.forEach { sub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = Spacing.md + Spacing.sm, top = Spacing.xs)
                        ) {
                            Text(
                                text = sub.name ?: stringResource(R.string.category_no_subcategory),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = CurrencyFormatter.formatTotal(sub.total, currency),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
