package com.naxits.paisatracker.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.naxits.paisatracker.R
import com.naxits.paisatracker.presentation.ui.icons.AiCommentary
import com.naxits.paisatracker.presentation.ui.icons.FavoriteChart
import com.naxits.paisatracker.presentation.ui.icons.Home
import com.naxits.paisatracker.presentation.ui.icons.ReceiptItem
import com.naxits.paisatracker.presentation.ui.icons.Iconax
import kotlin.reflect.KClass
import com.naxits.paisatracker.presentation.navigation.Home as HomeDestination
import com.naxits.paisatracker.presentation.navigation.Analytics as AnalyticsDestination
import com.naxits.paisatracker.presentation.navigation.Transactions as TransactionsDestination

sealed class BottomNavItem(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val destination: Any,
    val destinationType: KClass<*>
) {
    data object Home : BottomNavItem(
        route = "home",
        titleRes = R.string.home_title,
        icon = Iconax.Home,
        destination = HomeDestination,
        destinationType = HomeDestination::class
    )
    
    data object Analytics : BottomNavItem(
        route = "analytics",
        titleRes = R.string.analytics,
        icon = Iconax.FavoriteChart,
        destination = AnalyticsDestination,
        destinationType = AnalyticsDestination::class
    )

    data object Transactions : BottomNavItem(
        route = "transactions",
        titleRes = R.string.transactions,
        icon = Iconax.ReceiptItem,
        destination = TransactionsDestination(),
        destinationType = TransactionsDestination::class
    )
}