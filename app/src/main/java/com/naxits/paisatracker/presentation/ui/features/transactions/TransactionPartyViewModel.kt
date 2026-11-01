package com.naxits.paisatracker.presentation.ui.features.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naxits.paisatracker.data.database.entity.TransactionReviewEntity
import com.naxits.paisatracker.data.repository.PartyRepository
import com.naxits.paisatracker.data.repository.TransactionRepository
import com.naxits.paisatracker.domain.model.Party
import com.naxits.paisatracker.domain.service.ReviewService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Who a transaction was with and what the app suggested about it, for the detail screen. Every
 * change is saved straight away and reported through the callback so the screen can keep its own
 * copy of the transaction in step.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TransactionPartyViewModel @Inject constructor(
    private val partyRepository: PartyRepository,
    private val reviewService: ReviewService,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val transactionId = MutableStateFlow<Long?>(null)
    private val currentPartyId = MutableStateFlow<Long?>(null)
    private val query = MutableStateFlow("")

    val reviews: StateFlow<List<TransactionReviewEntity>> = transactionId
        .flatMapLatest { id -> if (id == null) emptyFlow() else reviewService.observeForTransaction(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentParty: StateFlow<Party?> = currentPartyId
        .flatMapLatest { id -> flow { emit(id?.let { partyRepository.getById(it) }) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val suggestions: StateFlow<List<Party>> = query
        .debounce(150)
        .mapLatest { partyRepository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun bind(id: Long, partyId: Long?) {
        transactionId.value = id
        currentPartyId.value = partyId
    }

    fun search(text: String) {
        query.value = text
    }

    /** Confirms what was suggested for [reviewId]. */
    fun confirm(reviewId: Long, onPartyChanged: (Long?) -> Unit) {
        viewModelScope.launch {
            reviewService.confirmParty(reviewId)
            publish(onPartyChanged)
        }
    }

    /** Confirms the category the app suggested for [reviewId]. */
    fun confirmCategory(reviewId: Long) {
        viewModelScope.launch { reviewService.confirmCategory(reviewId) }
    }

    fun dismiss(reviewId: Long) {
        viewModelScope.launch { reviewService.dismiss(reviewId) }
    }

    /**
     * Sets the party to [existing], or to the party called [typedName] (created when new). A
     * pending review is confirmed with that party; otherwise the party is simply saved.
     */
    fun changeParty(
        reviewId: Long?,
        existing: Party?,
        typedName: String,
        onPartyChanged: (Long?) -> Unit
    ) {
        val id = transactionId.value ?: return
        viewModelScope.launch {
            val party = existing ?: typedName.takeIf { it.isNotBlank() }?.let { partyRepository.findOrCreate(it) }
            if (reviewId != null) {
                reviewService.confirmParty(reviewId, party?.id)
            } else {
                partyRepository.setPartyForTransaction(id, party?.id)
            }
            publish(onPartyChanged)
        }
    }

    private suspend fun publish(onPartyChanged: (Long?) -> Unit) {
        val id = transactionId.value ?: return
        val partyId = transactionRepository.getTransactionById(id)?.partyId
        currentPartyId.value = partyId
        onPartyChanged(partyId)
    }
}
