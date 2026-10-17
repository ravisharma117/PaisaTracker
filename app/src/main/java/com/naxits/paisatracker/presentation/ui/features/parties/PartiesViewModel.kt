package com.naxits.paisatracker.presentation.ui.features.parties

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naxits.paisatracker.data.repository.PartyRepository
import com.naxits.paisatracker.domain.model.Party
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PartyRow(val party: Party, val transactionCount: Int)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PartiesViewModel @Inject constructor(
    private val partyRepository: PartyRepository
) : ViewModel() {

    val parties: StateFlow<List<PartyRow>> = partyRepository.observeActive()
        .mapLatest { list -> list.map { PartyRow(it, partyRepository.transactionCount(it.id)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(name: String, kind: String, phone: String?, notes: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val party = partyRepository.findOrCreate(name, kind)
            // findOrCreate reuses a party of that name, so fill in the extras on whichever it returned
            partyRepository.update(party.copy(kind = kind, phone = phone ?: party.phone, notes = notes ?: party.notes))
        }
    }

    fun update(party: Party) {
        if (party.name.isBlank()) return
        viewModelScope.launch { partyRepository.update(party.copy(name = party.name.trim())) }
    }

    fun archive(id: Long) {
        viewModelScope.launch { partyRepository.archive(id) }
    }

    /** Folds [fromId] into [toId]: its transactions and what is known about it move across. */
    fun merge(fromId: Long, toId: Long) {
        viewModelScope.launch { partyRepository.merge(fromId, toId) }
    }
}
