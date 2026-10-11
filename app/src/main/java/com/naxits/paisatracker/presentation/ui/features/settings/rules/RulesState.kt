package com.naxits.paisatracker.presentation.ui.features.settings.rules

import com.naxits.paisatracker.domain.usecase.BatchApplyResult

data class RulesUiState(
    val isLoading: Boolean = false,
    val batchApplyProgress: Pair<Int, Int>? = null,
    val batchApplyResult: BatchApplyResult? = null
)
