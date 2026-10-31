package com.naxits.paisatracker.data.preferences

import androidx.annotation.StringRes
import com.naxits.paisatracker.R

/**
 * The cards of the Home screen. [defaultOrder] is where a card sits on a fresh install, and where
 * a card that is new to an existing layout is slotted in (after the card that precedes it).
 */
enum class HomeWidget(@StringRes val labelRes: Int, val defaultOrder: Int) {
    CASH(R.string.home_widget_cash, 0),
    RECENT_TRANSACTIONS(R.string.home_widget_recent, 1),
    NETWORTH_SUMMARY(R.string.home_widget_networth, 2),
    MONTHLY_SUMMARY(R.string.home_widget_monthly_summary, 3),
    TOP_CATEGORIES(R.string.home_widget_top_categories, 4),
    ACCOUNT_CAROUSEL(R.string.home_widget_accounts, 5),
    UPCOMING_SUBSCRIPTIONS(R.string.home_widget_upcoming_payments, 6),
    RECURRING(R.string.home_widget_recurring, 7),
    BUDGET_CAROUSEL(R.string.home_widget_budgets, 8),
    TRANSACTION_HEATMAP(R.string.home_widget_heatmap, 9);

    companion object {
        fun fromName(name: String): HomeWidget? {
            return entries.find { it.name == name }
        }
    }
}
