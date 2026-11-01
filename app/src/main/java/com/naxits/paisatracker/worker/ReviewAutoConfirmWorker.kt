package com.naxits.paisatracker.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.naxits.paisatracker.domain.service.ReviewService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Once a day, confirms the reviews nobody looked at for three months. */
@HiltWorker
class ReviewAutoConfirmWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val reviewService: ReviewService
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        val confirmed = reviewService.autoConfirmOldReviews()
        if (confirmed > 0) Log.d(TAG, "Confirmed $confirmed reviews after three months")
        Result.success()
    } catch (e: Exception) {
        Log.e(TAG, "Could not auto-confirm old reviews", e)
        Result.retry()
    }

    private companion object {
        const val TAG = "ReviewAutoConfirmWorker"
    }
}
