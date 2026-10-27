package com.naxits.paisatracker.data.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.naxits.paisatracker.data.database.entity.TransactionType
import com.naxits.paisatracker.data.mapper.toEntityType
import com.naxits.paisatracker.data.preferences.UserPreferencesRepository
import com.naxits.paisatracker.data.repository.TransactionRepository
import com.naxits.paisatracker.worker.OptimizedSmsReaderWorker
import com.ritesh.parser.core.bank.BankParserFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs once, after the SBI parser learned that a "Deposit by transfer" credit is money coming in
 * and how to read a standing-instruction message.
 *
 * Deposits saved earlier as expenses are turned into income, then the messages on the phone are
 * scanned again so the ones the parser could not read before are picked up. The scan skips every
 * message already saved, so nothing is duplicated and nothing the user edited is touched.
 */
@Singleton
class SbiDepositFixBackfill @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    companion object {
        private const val TAG = "SbiDepositFixBackfill"
    }

    suspend fun runOnce() {
        if (userPreferencesRepository.isSbiDepositFixDone()) return

        val fixed = fixDepositTypes()
        val scanQueued = queueRescan()
        // Without the SMS permission the scan cannot run, so try again on a later start
        if (!scanQueued) {
            Log.d(TAG, "Fixed $fixed deposits; the message scan waits for the SMS permission")
            return
        }
        userPreferencesRepository.markSbiDepositFixDone()
        Log.d(TAG, "Fixed $fixed deposits saved as expenses and queued a scan of the messages")
    }

    private suspend fun fixDepositTypes(): Int {
        var fixed = 0
        for (saved in transactionRepository.getAllTransactionsList()) {
            if (saved.transactionType != TransactionType.EXPENSE || saved.isDeleted) continue
            val body = saved.smsBody ?: continue
            val sender = saved.smsSender ?: continue
            if (!body.contains("deposit by transfer", ignoreCase = true)) continue

            val timestamp = saved.dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val parsed = BankParserFactory.getParsers(sender)
                .firstNotNullOfOrNull { it.parse(body, sender, timestamp) } ?: continue
            if (parsed.type.toEntityType() != TransactionType.INCOME) continue

            transactionRepository.updateTransaction(
                saved.copy(transactionType = TransactionType.INCOME, updatedAt = LocalDateTime.now())
            )
            fixed++
        }
        return fixed
    }

    /** An ordinary scan of the usual period; the saved period is reset so it covers the whole period, not only recent days. */
    private suspend fun queueRescan(): Boolean {
        val canReadSms = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
        if (!canReadSms) return false

        userPreferencesRepository.setLastScanPeriod(0)
        WorkManager.getInstance(context).enqueueUniqueWork(
            OptimizedSmsReaderWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<OptimizedSmsReaderWorker>().addTag(OptimizedSmsReaderWorker.WORK_NAME).build()
        )
        return true
    }
}
