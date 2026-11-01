package com.naxits.paisatracker.presentation.common

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Ids of the transactions that still have a review waiting for the user. Provided once at the top
 * of the app so every transaction row can show its Review badge without each screen passing it down.
 */
val LocalPendingReviews = staticCompositionLocalOf<Set<Long>> { emptySet() }
