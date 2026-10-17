package com.naxits.paisatracker.data.manager

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.naxits.paisatracker.worker.ReviewAutoConfirmWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the daily check for old reviews scheduled. Safe to call on every start. */
@Singleton
class ReviewAutoConfirmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val WORK_NAME = "review_auto_confirm"
    }

    fun schedule() {
        val request = PeriodicWorkRequestBuilder<ReviewAutoConfirmWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
