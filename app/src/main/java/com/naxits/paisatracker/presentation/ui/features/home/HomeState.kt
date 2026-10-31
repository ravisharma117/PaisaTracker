package com.naxits.paisatracker.presentation.ui.features.home

import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.naxits.paisatracker.data.database.entity.AccountBalanceEntity
import com.naxits.paisatracker.data.database.entity.SubscriptionEntity
import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.data.repository.BudgetWithSpending
import com.naxits.paisatracker.domain.model.PersonInfo
import com.naxits.paisatracker.presentation.ui.components.BalancePoint
import java.math.BigDecimal

data class HomeUiState(
    val currentMonthTotal: BigDecimal = BigDecimal.ZERO,
    val currentYearTotal: BigDecimal = BigDecimal.ZERO,
    val currentMonthIncome: BigDecimal = BigDecimal.ZERO,
    val currentMonthExpenses: BigDecimal = BigDecimal.ZERO,
    /** This month's biggest expense categories, biggest first. */
    val topCategories: List<com.naxits.paisatracker.domain.usecase.CategoryRank> = emptyList(),
    /** The currency [topCategories] are in (the base currency, as in Analysis). */
    val topCategoriesCurrency: String = "INR",
    val currentYearExpenses: BigDecimal = BigDecimal.ZERO,
    val currentMonthCreditCard: BigDecimal = BigDecimal.ZERO,
    val currentMonthTransfer: BigDecimal = BigDecimal.ZERO,
    val currentMonthInvestment: BigDecimal = BigDecimal.ZERO,
    val lastMonthTotal: BigDecimal = BigDecimal.ZERO,
    val lastMonthIncome: BigDecimal = BigDecimal.ZERO,
    val lastMonthExpenses: BigDecimal = BigDecimal.ZERO,
    val monthlyChange: BigDecimal = BigDecimal.ZERO,
    val monthlyChangePercent: Int = 0,
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val upcomingSubscriptions: List<SubscriptionEntity> = emptyList(),
    /** The next three payments, soonest due first (overdue ones lead). */
    val upcomingPayments: List<com.naxits.paisatracker.domain.service.UpcomingItem> = emptyList(),
    val upcomingSubscriptionsTotal: BigDecimal = BigDecimal.ZERO,
    val upcomingSubscriptionsCurrency: String = "INR",
    /** How many of the upcoming payments are past due and unpaid. */
    val upcomingOverdueCount: Int = 0,
    /** The next few recurring transactions that are due. */
    val upcomingRecurring: List<com.naxits.paisatracker.data.database.entity.RecurringTransactionEntity> = emptyList(),
    val accountBalances: List<AccountBalanceEntity> = emptyList(),
    val creditCards: List<AccountBalanceEntity> = emptyList(),
    /** The Cash account, shown on its own above the transactions; set even when its balance is zero. */
    val cashAccount: AccountBalanceEntity? = null,
    /** Net worth, calculated as on the Net Worth screen. */
    val totalBalance: BigDecimal = BigDecimal.ZERO,
    val totalAvailableCredit: BigDecimal = BigDecimal.ZERO,
    val selectedCurrency: String = "INR",
    val availableCurrencies: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isScanning: Boolean = false,
    val showBreakdownDialog: Boolean = false,
    val userName: String = "User",
    val profileImageUri: Uri? = null,
    val profileBackgroundColor: Color = Color.Transparent,
    val unreadUpdatesCount: Int = 0,
    val bannerImageUri: Uri? = null,
    val showBannerImage: Boolean = false,
    val activeBudgets: List<BudgetWithSpending> = emptyList(),
    val balanceHistory: List<BalancePoint> = emptyList(),
    val transactionHeatmap: Map<java.time.LocalDate, Int> = emptyMap(),
    val convertedAmounts: Map<Long, BigDecimal> = emptyMap(),
    val baseCurrency: String = "INR",
    val lendBorrowSummary: com.naxits.paisatracker.domain.model.LendBorrowSummary = com.naxits.paisatracker.domain.model.LendBorrowSummary(),
    val transactionPersonMapping: Map<Long, PersonInfo> = emptyMap()
)
