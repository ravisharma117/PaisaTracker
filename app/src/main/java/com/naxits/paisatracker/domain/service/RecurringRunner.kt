package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.dao.LendBorrowDao
import com.naxits.paisatracker.data.database.dao.RecurringTransactionDao
import com.naxits.paisatracker.data.database.dao.SubscriptionDao
import com.naxits.paisatracker.data.preferences.UserPreferencesRepository
import com.naxits.paisatracker.data.repository.SubscriptionRepository
import com.naxits.paisatracker.data.database.entity.OccurrenceStatus
import com.naxits.paisatracker.data.database.entity.RecurringTransactionEntity
import com.naxits.paisatracker.data.manager.RecurringAlarmScheduler
import com.naxits.paisatracker.data.manager.RecurringNotifier
import com.naxits.paisatracker.domain.usecase.AddTransactionUseCase
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one entry point the alarm, the boot receiver and app start all use: create whatever is
 * due, tell the user, and arm the next alarm. Runs are serialised, so an alarm and an app start
 * that overlap cannot interleave.
 */
@Singleton
class RecurringRunner @Inject constructor(
    private val dao: RecurringTransactionDao,
    private val addTransaction: AddTransactionUseCase,
    private val notifier: RecurringNotifier,
    private val scheduler: RecurringAlarmScheduler,
    private val subscriptionDao: SubscriptionDao,
    private val lendDao: LendBorrowDao,
    private val subscriptions: SubscriptionRepository,
    private val preferences: UserPreferencesRepository,
    private val netWorth: NetWorthService,
    private val currencyRepository: com.naxits.paisatracker.data.repository.CurrencyRepository
) {
    private val mutex = Mutex()
    private val processor = RecurringProcessor(dao, ::createTransaction)
    private val billReminders = BillReminderProcessor(subscriptionDao)
    private val lendReminders = LendReminderProcessor(lendDao)

    suspend fun run(now: LocalDateTime = LocalDateTime.now()) = mutex.withLock {
        val result = processor.process(now)
        notifier.show(result)

        val enabled = preferences.upcomingNotificationsEnabled.first()
        val disabled = preferences.disabledSubscriptionNotificationIds.first()
            .mapNotNull { it.toLongOrNull() }.toSet()
        notifier.showBills(billReminders.process(now, enabled, disabled))
        notifier.showLendReminders(lendReminders.process(now, preferences.lendRemindersEnabled.first()))

        // Keeps the net worth trend current whenever the app has work to do or is opened
        runCatching { netWorth.refreshSnapshots(currencyRepository.effectiveBaseCurrencyCode.first(), now.toLocalDate()) }

        scheduler.reschedule(now)
    }

    /** The user chose "Add now" on a remind-only date. */
    suspend fun addPending(scheduleId: Long, dueDate: LocalDate) = mutex.withLock {
        val schedule = dao.getById(scheduleId) ?: return@withLock
        val occurrence = dao.getOccurrence(scheduleId, dueDate) ?: return@withLock
        if (occurrence.status != OccurrenceStatus.PENDING) return@withLock
        val transactionId = createTransaction(schedule, dueDate)
        dao.updateOccurrence(
            occurrence.copy(status = OccurrenceStatus.CREATED, transactionId = transactionId)
        )
        notifier.dismiss(scheduleId, dueDate)
    }

    private suspend fun createTransaction(
        schedule: RecurringTransactionEntity,
        dueDate: LocalDate
    ): Long = createAndSettle(schedule, dueDate)

    private suspend fun createAndSettle(schedule: RecurringTransactionEntity, dueDate: LocalDate): Long {
        val transactionId = insertTransaction(schedule, dueDate)
        // A bill linked to this schedule is paid by this transaction. The guard leaves a cycle
        // alone that was already paid in advance.
        subscriptions.getByRecurringId(schedule.id)?.let { bill ->
            subscriptions.settleCurrentCycle(
                subscriptionId = bill.id,
                paidDate = dueDate,
                amount = schedule.amount,
                transactionId = transactionId,
                onlyIfDueOnOrBefore = dueDate
            )
        }
        return transactionId
    }

    private suspend fun insertTransaction(
        schedule: RecurringTransactionEntity,
        dueDate: LocalDate
    ): Long = addTransaction.execute(
        amount = schedule.amount,
        merchant = schedule.title,
        category = schedule.category,
        type = schedule.transactionType,
        date = dueDate.atTime(RecurringProcessor.RUN_TIME),
        notes = schedule.notes,
        subcategory = schedule.subcategory,
        isRecurring = true,
        bankName = schedule.bankName,
        accountLast4 = schedule.accountLast4,
        currency = schedule.currency,
        createSubscription = false
    )
}
