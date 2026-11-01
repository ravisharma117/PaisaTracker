package com.naxits.paisatracker.presentation.ui.features.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naxits.paisatracker.domain.service.ReviewService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Holds which transactions are waiting for review, for the whole app. */
@HiltViewModel
class ReviewsViewModel @Inject constructor(
    reviewService: ReviewService
) : ViewModel() {

    val pendingTransactionIds: StateFlow<Set<Long>> = reviewService.observePendingTransactionIds()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
}
