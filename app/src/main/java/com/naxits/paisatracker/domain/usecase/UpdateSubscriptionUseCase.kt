package com.naxits.paisatracker.domain.usecase

import android.util.Log
import com.naxits.paisatracker.data.database.entity.SubscriptionEntity
import com.naxits.paisatracker.data.repository.SubscriptionRepository
import javax.inject.Inject

class UpdateSubscriptionUseCase
@Inject
constructor(private val subscriptionRepository: SubscriptionRepository) {
    suspend fun execute(subscription: SubscriptionEntity) {
        Log.d("UpdateSubscriptionUseCase", "Updating subscription entity: ${subscription.id}")
        subscriptionRepository.updateSubscription(subscription)
        Log.d("UpdateSubscriptionUseCase", "Subscription updated successfully.")
    }
}
