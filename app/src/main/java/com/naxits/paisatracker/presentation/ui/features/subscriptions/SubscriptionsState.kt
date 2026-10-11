package com.naxits.paisatracker.presentation.ui.features.subscriptions

import com.naxits.paisatracker.data.database.entity.SubscriptionEntity
import com.naxits.paisatracker.data.database.entity.SubscriptionKind
import com.naxits.paisatracker.domain.service.UpcomingPayments
import java.math.BigDecimal

enum class SubscriptionKindFilter { ALL, BILLS, SUBSCRIPTIONS }

data class SubscriptionsUiState(
    val activeSubscriptions: List<SubscriptionEntity> = emptyList(),
    val totalMonthlyAmount: BigDecimal = BigDecimal.ZERO,
    val totalYearlyAmount: BigDecimal = BigDecimal.ZERO,
    val targetCurrency: String = "INR",
    val isLoading: Boolean = true,
    val lastHiddenSubscription: SubscriptionEntity? = null,
    val selectedSubscription: SubscriptionEntity? = null,
    val convertedAmounts: Map<Long, BigDecimal> = emptyMap(),
    val conversionFailureCount: Int = 0,
    val kindFilter: SubscriptionKindFilter = SubscriptionKindFilter.ALL,
    /** Overdue items and everything due in the next 30 days, grouped. */
    val upcoming: UpcomingPayments = UpcomingPayments(emptyList(), emptyList(), emptyList(), BigDecimal.ZERO),
    /** The bill the "mark paid" question is open for. */
    val markPaidTarget: SubscriptionEntity? = null
) {
    val visibleSubscriptions: List<SubscriptionEntity>
        get() = when (kindFilter) {
            SubscriptionKindFilter.ALL -> activeSubscriptions
            SubscriptionKindFilter.BILLS -> activeSubscriptions.filter { it.kind == SubscriptionKind.BILL }
            SubscriptionKindFilter.SUBSCRIPTIONS -> activeSubscriptions.filter { it.kind == SubscriptionKind.SUBSCRIPTION }
        }
}
