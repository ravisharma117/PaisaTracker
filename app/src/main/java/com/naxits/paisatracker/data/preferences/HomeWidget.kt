package com.naxits.paisatracker.data.preferences

import androidx.annotation.StringRes
import com.naxits.paisatracker.R

/**
 * The cards of the Home screen. [defaultOrder] is where a card sits on a fresh install, and where
 * a card that is new to an existing layout is slotted in (after the card that precedes it).
 */
enum class HomeWidget(@StringRes val labelRes: Int, @StringRes val descRes: Int, val defaultOrder: Int) {
    CASH(R.string.home_widget_cash, R.string.home_widget_cash_desc, 0),
    RECENT_TRANSACTIONS(R.string.home_widget_recent, R.string.home_widget_recent_desc, 1),
    NETWORTH_SUMMARY(R.string.home_widget_networth, R.string.home_widget_networth_desc, 2),
    MONTHLY_SUMMARY(R.string.home_widget_monthly_summary, R.string.home_widget_monthly_summary_desc, 3),
    TOP_CATEGORIES(R.string.home_widget_top_categories, R.string.home_widget_top_categories_desc, 4),
    ACCOUNT_CAROUSEL(R.string.home_widget_accounts, R.string.home_widget_accounts_desc, 5),
    UPCOMING_SUBSCRIPTIONS(R.string.home_widget_upcoming_payments, R.string.home_widget_upcoming_payments_desc, 6),
    RECURRING(R.string.home_widget_recurring, R.string.home_widget_recurring_desc, 7),
    BUDGET_CAROUSEL(R.string.home_widget_budgets, R.string.home_widget_budgets_desc, 8),
    TRANSACTION_HEATMAP(R.string.home_widget_heatmap, R.string.home_widget_heatmap_desc, 9);

    companion object {
        fun fromName(name: String): HomeWidget? {
            return entries.find { it.name == name }
        }
    }
}
