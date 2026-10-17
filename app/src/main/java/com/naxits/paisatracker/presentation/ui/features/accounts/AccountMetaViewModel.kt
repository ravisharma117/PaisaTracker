package com.naxits.paisatracker.presentation.ui.features.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naxits.paisatracker.data.repository.AccountMetaRepository
import com.naxits.paisatracker.domain.model.AccountMeta
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Holds the user's notes on every account for the whole app. */
@HiltViewModel
class AccountMetaViewModel @Inject constructor(
    accountMetaRepository: AccountMetaRepository
) : ViewModel() {

    val meta: StateFlow<Map<String, AccountMeta>> = accountMetaRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
}
