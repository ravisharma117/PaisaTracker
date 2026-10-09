package com.ritesh.cashiro.presentation.ui.features.analytics

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ritesh.cashiro.presentation.ui.features.tracker.AnalysisMode
import com.ritesh.cashiro.presentation.ui.features.tracker.AnalysisModeSwitch
import com.ritesh.cashiro.presentation.ui.features.tracker.MonthlyTrackerScreen
import com.ritesh.cashiro.presentation.ui.features.tracker.YearlyTrackerScreen
import java.time.YearMonth

/**
 * The Analysis tab: the existing overview, plus the Month and Year trackers behind a switch at
 * the top so the bottom bar keeps its three items.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AnalysisHost(
    onNavigateToTransactions: (category: String?, merchant: String?, period: String?, currency: String?) -> Unit,
    onOpenCategory: (name: String, month: YearMonth) -> Unit,
    onOpenAccount: (bankName: String, last4: String) -> Unit,
    onOpenBills: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
    animatedContentScope: AnimatedContentScope?,
    blurEffects: Boolean
) {
    var mode by rememberSaveable { mutableStateOf(AnalysisMode.OVERVIEW) }
    val switch: @Composable () -> Unit = { AnalysisModeSwitch(selected = mode, onSelect = { mode = it }) }

    when (mode) {
        AnalysisMode.OVERVIEW -> AnalyticsScreen(
            onNavigateToTransactions = onNavigateToTransactions,
            animatedContentScope = animatedContentScope,
            blurEffects = blurEffects,
            modeSwitch = switch
        )
        AnalysisMode.MONTH -> MonthlyTrackerScreen(
            initialMonth = null,
            onNavigateBack = null,
            onOpenCategory = onOpenCategory,
            onOpenAccount = onOpenAccount,
            onOpenBills = onOpenBills,
            onOpenRecurring = onOpenRecurring,
            modeSwitch = switch
        )
        AnalysisMode.YEAR -> YearlyTrackerScreen(
            initialStartYear = null,
            onNavigateBack = null,
            onOpenMonth = onOpenMonth,
            modeSwitch = switch
        )
    }
}
