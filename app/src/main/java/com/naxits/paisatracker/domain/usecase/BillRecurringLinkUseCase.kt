package com.naxits.paisatracker.domain.usecase

import com.naxits.paisatracker.data.database.entity.RecurringTransactionEntity
import com.naxits.paisatracker.data.database.entity.SubscriptionEntity
import com.naxits.paisatracker.data.database.entity.TransactionType
import com.naxits.paisatracker.data.repository.RecurringTransactionRepository
import com.naxits.paisatracker.data.repository.SubscriptionRepository
import com.naxits.paisatracker.domain.service.BillingCycleMapper
import java.time.LocalDate
import javax.inject.Inject

/**
 * Lets a bill add its own transaction on schedule by linking it to a recurring schedule. The
 * schedule creates the transaction and that transaction settles the cycle, so one payment is
 * never recorded twice.
 */
class BillRecurringLinkUseCase @Inject constructor(
    private val subscriptions: SubscriptionRepository,
    private val recurring: RecurringTransactionRepository
) {

    /** Creates the schedule for [subscription] and returns the bill with its new link. */
    suspend fun link(subscription: SubscriptionEntity, today: LocalDate = LocalDate.now()): SubscriptionEntity {
        if (subscription.recurringId != null) return subscription
        val cycle = BillingCycleMapper.toSchedule(subscription.billingCycle)
        val start = subscription.nextPaymentDate ?: today
        val scheduleId = recurring.create(
            RecurringTransactionEntity(
                title = subscription.merchantName,
                amount = subscription.amount,
                currency = subscription.currency,
                transactionType = TransactionType.EXPENSE,
                category = subscription.category ?: "Bills",
                subcategory = subscription.subcategory,
                bankName = subscription.payFromBank ?: subscription.bankName,
                accountLast4 = subscription.payFromLast4,
                frequency = cycle.frequency,
                intervalCount = cycle.intervalCount,
                customUnit = cycle.customUnit,
                startDate = start,
                endDate = cycle.endDate,
                nextRunDate = start,
                autoCreate = true
            )
        )
        val linked = subscription.copy(recurringId = scheduleId)
        subscriptions.updateSubscription(linked)
        return linked
    }

    /** Removes the schedule and the link. Transactions it already added stay. */
    suspend fun unlink(subscription: SubscriptionEntity): SubscriptionEntity {
        val scheduleId = subscription.recurringId ?: return subscription
        recurring.delete(scheduleId)
        val unlinked = subscription.copy(recurringId = null)
        subscriptions.updateSubscription(unlinked)
        return unlinked
    }
}
