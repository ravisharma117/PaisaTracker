package com.naxits.paisatracker.data.manager

import android.util.Log
import com.naxits.paisatracker.data.database.entity.TransactionType
import com.naxits.paisatracker.data.preferences.UserPreferencesRepository
import com.naxits.paisatracker.data.repository.AccountBalanceRepository
import com.naxits.paisatracker.data.repository.TransactionRepository
import com.naxits.paisatracker.domain.service.AtmWithdrawal
import com.ritesh.parser.core.bank.BankParserFactory
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs once: ATM withdrawals from the last [DAYS] days that were saved as expenses, before
 * withdrawals became transfers, are turned into transfers to Cash. Older entries stay as they are.
 */
@Singleton
class AtmWithdrawalBackfill @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    companion object {
        private const val TAG = "AtmWithdrawalBackfill"
        const val DAYS = 7L
    }

    suspend fun runOnce() {
        if (userPreferencesRepository.isAtmTransferBackfillDone()) return

        val now = LocalDateTime.now()
        val candidates = transactionRepository.getTransactionsBetweenDatesList(now.minusDays(DAYS), now)
            .filter { it.transactionType == TransactionType.EXPENSE && !it.isDeleted }

        var converted = 0
        for (expense in candidates) {
            val body = expense.smsBody ?: continue
            val sender = expense.smsSender ?: continue
            val timestamp = expense.dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val parsed = BankParserFactory.getParsers(sender)
                .firstNotNullOfOrNull { it.parse(body, sender, timestamp) }
            if (parsed?.isAtmWithdrawal != true) continue

            // The source account already carries the debit from when this was an expense
            transactionRepository.updateTransaction(
                AtmWithdrawal.toCashTransfer(expense, parsed.merchant)
                    .copy(updatedAt = LocalDateTime.now())
            )
            accountBalanceRepository.creditCashForTransfer(
                amount = expense.amount,
                timestamp = expense.dateTime,
                transactionId = expense.id,
                currency = expense.currency
            )
            converted++
        }

        userPreferencesRepository.markAtmTransferBackfillDone()
        Log.d(TAG, "Turned $converted recent ATM withdrawals into transfers to Cash")
    }
}
