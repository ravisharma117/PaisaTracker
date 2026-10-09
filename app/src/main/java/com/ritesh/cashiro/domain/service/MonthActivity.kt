package com.ritesh.cashiro.domain.service

import com.ritesh.cashiro.data.database.entity.BillPaymentEntity
import com.ritesh.cashiro.data.database.entity.BillPaymentStatus
import com.ritesh.cashiro.data.database.entity.OccurrenceStatus
import com.ritesh.cashiro.data.database.entity.RecurringOccurrenceEntity
import com.ritesh.cashiro.data.database.entity.RecurringState
import com.ritesh.cashiro.data.database.entity.RecurringTransactionEntity
import com.ritesh.cashiro.data.database.entity.SubscriptionEntity
import com.ritesh.cashiro.data.database.entity.SubscriptionState
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

data class RecurringDue(val scheduleId: Long, val title: String, val date: LocalDate, val amount: BigDecimal)

/** What the recurring schedules did, and still will do, in one month. */
data class RecurringMonth(
    val createdCount: Int,
    val createdTotal: BigDecimal,
    val skippedCount: Int,
    /** Remind-only dates still waiting for the user. */
    val waitingCount: Int,
    val upcoming: List<RecurringDue>
) {
    val upcomingTotal: BigDecimal get() = upcoming.fold(BigDecimal.ZERO) { a, d -> a.add(d.amount) }
    val isEmpty: Boolean get() = createdCount == 0 && skippedCount == 0 && waitingCount == 0 && upcoming.isEmpty()
}

data class BillLine(val subscriptionId: Long, val name: String, val dueDate: LocalDate, val amount: BigDecimal)

/** The bills and subscriptions that fall due in one month. */
data class BillsMonth(val paid: List<BillLine>, val unpaid: List<BillLine>) {
    val paidTotal: BigDecimal get() = paid.fold(BigDecimal.ZERO) { a, b -> a.add(b.amount) }
    val unpaidTotal: BigDecimal get() = unpaid.fold(BigDecimal.ZERO) { a, b -> a.add(b.amount) }
    val total: BigDecimal get() = paidTotal.add(unpaidTotal)
    val isEmpty: Boolean get() = paid.isEmpty() && unpaid.isEmpty()
}

object MonthActivityCalculator {

    private const val MAX_DATES_PER_SCHEDULE = 62

    /**
     * Created, skipped and waiting dates come from what the schedules recorded. Upcoming dates are
     * the ones still to come in the month (today onwards) that no record covers yet.
     */
    fun recurring(
        month: YearMonth,
        today: LocalDate,
        schedules: List<RecurringTransactionEntity>,
        occurrences: List<RecurringOccurrenceEntity>,
        amountOf: (RecurringTransactionEntity) -> BigDecimal
    ): RecurringMonth {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        val byId = schedules.associateBy { it.id }
        val inMonth = occurrences.filter { !it.dueDate.isBefore(start) && !it.dueDate.isAfter(end) }

        val created = inMonth.filter { it.status == OccurrenceStatus.CREATED }
        val createdTotal = created.fold(BigDecimal.ZERO) { acc, o ->
            acc.add(byId[o.recurringId]?.let(amountOf) ?: BigDecimal.ZERO)
        }

        val recorded = inMonth.map { it.recurringId to it.dueDate }.toSet()
        val upcoming = schedules.filter { it.state == RecurringState.ACTIVE }.flatMap { schedule ->
            val rule = schedule.toRule()
            val from = maxOf(start, today, schedule.nextRunDate)
            val dates = mutableListOf<RecurringDue>()
            var date = RecurrenceCalculator.firstOnOrAfter(rule, from)
            while (date != null && !date.isAfter(end) && dates.size < MAX_DATES_PER_SCHEDULE) {
                if ((schedule.id to date) !in recorded) {
                    dates += RecurringDue(schedule.id, schedule.title, date, amountOf(schedule))
                }
                date = RecurrenceCalculator.nextAfter(rule, date)
            }
            dates
        }.sortedWith(compareBy({ it.date }, { it.title }))

        return RecurringMonth(
            createdCount = created.size,
            createdTotal = createdTotal,
            skippedCount = inMonth.count { it.status == OccurrenceStatus.SKIPPED },
            waitingCount = inMonth.count { it.status == OccurrenceStatus.PENDING },
            upcoming = upcoming
        )
    }

    /**
     * Paid cycles are the recorded payments. A cycle is unpaid when its payment is recorded as
     * unpaid, or when an active bill is due in the month and nothing records it as paid or skipped.
     */
    fun bills(
        month: YearMonth,
        subscriptions: List<SubscriptionEntity>,
        payments: List<BillPaymentEntity>,
        amountOf: (SubscriptionEntity, BigDecimal) -> BigDecimal
    ): BillsMonth {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        val bySub = subscriptions.associateBy { it.id }
        val inMonth = payments.filter { !it.dueDate.isBefore(start) && !it.dueDate.isAfter(end) && it.subscriptionId in bySub }

        fun line(sub: SubscriptionEntity, due: LocalDate, amount: BigDecimal) =
            BillLine(sub.id, sub.merchantName, due, amountOf(sub, amount))

        val paid = inMonth.filter { it.status == BillPaymentStatus.PAID }
            .map { line(bySub.getValue(it.subscriptionId), it.paidDate ?: it.dueDate, it.amount) }

        val settled = inMonth.filter { it.status != BillPaymentStatus.UNPAID }.map { it.subscriptionId to it.dueDate }.toSet()
        val recordedUnpaid = inMonth.filter { it.status == BillPaymentStatus.UNPAID }
            .map { line(bySub.getValue(it.subscriptionId), it.dueDate, it.amount) }
        val recordedUnpaidKeys = recordedUnpaid.map { it.subscriptionId to it.dueDate }.toSet()

        val expected = subscriptions
            .filter { it.state == SubscriptionState.ACTIVE }
            .mapNotNull { sub ->
                val due = sub.nextPaymentDate ?: return@mapNotNull null
                val key = sub.id to due
                val alreadyPaid = sub.lastPaidDate != null && !sub.lastPaidDate.isBefore(due)
                if (due.isBefore(start) || due.isAfter(end) || alreadyPaid || key in settled || key in recordedUnpaidKeys) null
                else line(sub, due, sub.amount)
            }

        return BillsMonth(
            paid = paid.sortedBy { it.dueDate },
            unpaid = (recordedUnpaid + expected).sortedBy { it.dueDate }
        )
    }
}
