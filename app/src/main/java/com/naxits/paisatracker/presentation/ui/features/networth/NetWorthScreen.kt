package com.naxits.paisatracker.presentation.ui.features.networth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naxits.paisatracker.R
import com.naxits.paisatracker.data.database.entity.NetWorthItemEntity
import com.naxits.paisatracker.domain.service.AssetGroup
import com.naxits.paisatracker.domain.service.LiabilityGroup
import com.naxits.paisatracker.domain.service.NetWorthBreakdown
import com.naxits.paisatracker.domain.service.NetWorthGroup
import com.naxits.paisatracker.presentation.ui.components.CashiroCard
import com.naxits.paisatracker.presentation.ui.components.CustomTitleTopAppBar
import com.naxits.paisatracker.presentation.ui.components.SectionHeader
import com.naxits.paisatracker.presentation.ui.features.categories.NavigationContent
import com.naxits.paisatracker.presentation.ui.theme.Dimensions
import com.naxits.paisatracker.presentation.ui.theme.Spacing
import com.naxits.paisatracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.math.BigDecimal

/**
 * Everything the user owns and owes, in one figure. Every amount here is a total, so all of them
 * follow the hide-totals setting.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NetWorthScreen(
    onNavigateBack: () -> Unit,
    viewModel: NetWorthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val currency by viewModel.baseCurrency.collectAsStateWithLifecycle()

    var yearly by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<NetWorthItemEntity?>(null) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CustomTitleTopAppBar(
                title = stringResource(R.string.net_worth),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehavior,
                hazeState = hazeState,
                hasBackButton = true,
                navigationContent = { NavigationContent(onNavigateBack) }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; showSheet = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.nw_add)) }
            )
        }
    ) { paddingValues ->
        val breakdown = state.breakdown
        LazyColumn(
            modifier = Modifier.fillMaxSize().hazeSource(state = hazeState),
            contentPadding = PaddingValues(
                start = Dimensions.Padding.content,
                end = Dimensions.Padding.content,
                top = Dimensions.Padding.content + paddingValues.calculateTopPadding(),
                bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (breakdown != null) {
                item { Headline(breakdown, state) }
                item {
                    TrendCard(
                        points = if (yearly) state.yearly else state.monthly,
                        currency = breakdown.currency,
                        yearly = yearly,
                        onToggle = { yearly = it }
                    )
                }
                if (breakdown.assets.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.nw_assets),
                            modifier = Modifier.padding(start = 8.dp, top = Spacing.sm),
                            action = { Amount(breakdown.totalAssets, breakdown.currency, bold = true) }
                        )
                    }
                    breakdown.assets.forEach { group ->
                        item(key = "asset-${group.group}") {
                            GroupCard(
                                title = stringResource(assetGroupLabel(group.group)),
                                group = group,
                                currency = breakdown.currency,
                                lineNameOverride = if (group.group == AssetGroup.MONEY_LENT) R.string.nw_group_lent else null,
                                onLineClick = { id -> editing = items.firstOrNull { it.id == id }; showSheet = true }
                            )
                        }
                    }
                }
                if (breakdown.liabilities.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.nw_liabilities),
                            modifier = Modifier.padding(start = 8.dp, top = Spacing.sm),
                            action = { Amount(breakdown.totalLiabilities, breakdown.currency, bold = true) }
                        )
                    }
                    breakdown.liabilities.forEach { group ->
                        item(key = "liability-${group.group}") {
                            GroupCard(
                                title = stringResource(liabilityGroupLabel(group.group)),
                                group = group,
                                currency = breakdown.currency,
                                lineNameOverride = if (group.group == LiabilityGroup.MONEY_BORROWED) R.string.nw_group_borrowed else null,
                                onLineClick = { id -> editing = items.firstOrNull { it.id == id }; showSheet = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            NetWorthItemForm(
                item = editing,
                currency = currency,
                onSave = { item, value, asOf ->
                    if (editing == null) viewModel.add(item) else viewModel.save(item, value, asOf)
                },
                onDelete = { id -> viewModel.delete(id) },
                onDismiss = { showSheet = false }
            )
        }
    }
}

@Composable
private fun Amount(amount: BigDecimal, currency: String, bold: Boolean = false, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    Text(
        text = CurrencyFormatter.formatTotal(amount, currency),
        style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
        color = color
    )
}

@Composable
private fun Headline(breakdown: NetWorthBreakdown, state: NetWorthUiState) {
    CashiroCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.net_worth),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = CurrencyFormatter.formatTotal(breakdown.netWorth, breakdown.currency),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        state.sinceLastMonth?.let { ChangeLine(it, breakdown.currency, R.string.nw_change_month) }
        state.overTheYear?.let { ChangeLine(it, breakdown.currency, R.string.nw_change_year) }
    }
}

@Composable
private fun ChangeLine(change: NetWorthChange, currency: String, label: Int) {
    val up = change.amount.signum() >= 0
    val sign = if (up) "+" else "-"
    val amount = CurrencyFormatter.formatTotal(change.amount.abs(), currency)
    val percent = change.percent?.let { " (${it.abs().toPlainString()}%)" } ?: ""
    Text(
        text = stringResource(label, "$sign$amount$percent"),
        style = MaterialTheme.typography.bodyMedium,
        color = if (up) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    )
}

@Composable
private fun TrendCard(points: List<TrendPoint>, currency: String, yearly: Boolean, onToggle: (Boolean) -> Unit) {
    CashiroCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.nw_trend),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            FilterChip(selected = !yearly, onClick = { onToggle(false) }, label = { Text(stringResource(R.string.nw_trend_monthly)) })
            Spacer(Modifier.padding(horizontal = 2.dp))
            FilterChip(selected = yearly, onClick = { onToggle(true) }, label = { Text(stringResource(R.string.nw_trend_yearly)) })
        }
        Spacer(Modifier.height(Spacing.sm))
        if (points.size < 2) {
            Text(
                stringResource(R.string.nw_trend_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@CashiroCard
        }
        val maxAbs = points.maxOf { it.netWorth.abs() }.takeIf { it.signum() > 0 } ?: BigDecimal.ONE
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.Bottom
        ) {
            points.forEach { point ->
                val fraction = (point.netWorth.abs().toFloat() / maxAbs.toFloat()).coerceIn(0f, 1f)
                val base = if (point.netWorth.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((88 * fraction).dp.coerceAtLeast(2.dp))
                            .background(
                                color = base.copy(alpha = if (point.isEstimate) 0.35f else 1f),
                                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                            )
                    )
                    Text(
                        text = if (yearly) point.label else point.label.takeLast(2),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        if (points.any { it.isEstimate }) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                stringResource(R.string.nw_trend_estimate_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun <G> GroupCard(
    title: String,
    group: NetWorthGroup<G>,
    currency: String,
    lineNameOverride: Int?,
    onLineClick: (Long) -> Unit
) {
    CashiroCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Amount(group.total, currency, bold = true)
        }
        group.lines.forEach { line ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (line.itemId != null) Modifier.clickable { onLineClick(line.itemId) } else Modifier)
                    .padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lineNameOverride?.let { stringResource(it) } ?: line.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (line.isEstimate) {
                        Text(
                            stringResource(R.string.nw_estimate_tag),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Amount(line.amount, currency)
            }
        }
    }
}

private fun assetGroupLabel(group: AssetGroup): Int = when (group) {
    AssetGroup.BANK -> R.string.nw_group_bank
    AssetGroup.CASH -> R.string.nw_group_cash
    AssetGroup.WALLET -> R.string.nw_group_wallet
    AssetGroup.INVESTMENTS -> R.string.nw_group_investments
    AssetGroup.MONEY_LENT -> R.string.nw_group_lent
    AssetGroup.OTHER_ASSETS -> R.string.nw_group_other_assets
}

private fun liabilityGroupLabel(group: LiabilityGroup): Int = when (group) {
    LiabilityGroup.CREDIT_CARDS -> R.string.nw_group_cards
    LiabilityGroup.OVERDRAFT -> R.string.nw_group_overdraft
    LiabilityGroup.MONEY_BORROWED -> R.string.nw_group_borrowed
    LiabilityGroup.LOANS -> R.string.nw_group_loans
    LiabilityGroup.OTHER_LIABILITIES -> R.string.nw_group_other_liabilities
}
