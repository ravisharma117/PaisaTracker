package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.NetWorthItemType
import com.naxits.paisatracker.domain.model.AccountKind
import java.math.BigDecimal

enum class AssetGroup { BANK, CASH, WALLET, INVESTMENTS, MONEY_LENT, OTHER_ASSETS }

enum class LiabilityGroup { CREDIT_CARDS, OVERDRAFT, MONEY_BORROWED, LOANS, OTHER_LIABILITIES }

/** One row on the Net Worth screen. [itemId] is set for rows the user entered by hand. */
data class NetWorthLine(
    val name: String,
    val amount: BigDecimal,
    val itemId: Long? = null,
    val itemType: NetWorthItemType? = null,
    /** A loan balance carried forward by the repayment schedule rather than typed in. */
    val isEstimate: Boolean = false
)

data class NetWorthGroup<G>(val group: G, val lines: List<NetWorthLine>) {
    val total: BigDecimal get() = lines.fold(BigDecimal.ZERO) { acc, line -> acc.add(line.amount) }
}

data class NetWorthBreakdown(
    val currency: String,
    val assets: List<NetWorthGroup<AssetGroup>>,
    val liabilities: List<NetWorthGroup<LiabilityGroup>>
) {
    val totalAssets: BigDecimal get() = assets.fold(BigDecimal.ZERO) { acc, g -> acc.add(g.total) }
    val totalLiabilities: BigDecimal get() = liabilities.fold(BigDecimal.ZERO) { acc, g -> acc.add(g.total) }
    val netWorth: BigDecimal get() = totalAssets.subtract(totalLiabilities)
}

data class AccountAmount(val kind: AccountKind, val name: String, val balance: BigDecimal)

data class ItemAmount(
    val id: Long,
    val name: String,
    val type: NetWorthItemType,
    val value: BigDecimal,
    val isEstimate: Boolean
)

/** Everything net worth is made of, already in one currency. */
data class NetWorthInput(
    val accounts: List<AccountAmount>,
    /** Still owed to the user by other people. */
    val moneyLent: BigDecimal,
    /** Still owed by the user to other people. */
    val moneyBorrowed: BigDecimal,
    val items: List<ItemAmount>
)

/**
 * The one net-worth calculation: assets minus liabilities.
 *
 * - Bank, cash and wallet balances are assets; a negative one (overdraft) is a liability.
 * - A credit card's stored balance is what is owed on it, so it is a liability.
 * - Money lent is an asset and money borrowed a liability. The cash for a loan already left or
 *   entered an account, so counting the receivable or payable is not counting it twice.
 * - Manually entered holdings are assets (investments, gold, property, vehicles) and loans and
 *   other debts are liabilities.
 */
object NetWorthCalculator {

    fun calculate(input: NetWorthInput, currency: String): NetWorthBreakdown {
        val assets = linkedMapOf<AssetGroup, MutableList<NetWorthLine>>()
        val liabilities = linkedMapOf<LiabilityGroup, MutableList<NetWorthLine>>()
        fun asset(group: AssetGroup, line: NetWorthLine) = assets.getOrPut(group) { mutableListOf() }.add(line)
        fun liability(group: LiabilityGroup, line: NetWorthLine) = liabilities.getOrPut(group) { mutableListOf() }.add(line)

        for (account in input.accounts) {
            when {
                account.kind == AccountKind.CREDIT_CARD ->
                    liability(LiabilityGroup.CREDIT_CARDS, NetWorthLine(account.name, account.balance))
                account.balance.signum() < 0 ->
                    liability(LiabilityGroup.OVERDRAFT, NetWorthLine(account.name, account.balance.negate()))
                else -> asset(
                    when (account.kind) {
                        AccountKind.CASH -> AssetGroup.CASH
                        AccountKind.WALLET -> AssetGroup.WALLET
                        else -> AssetGroup.BANK
                    },
                    NetWorthLine(account.name, account.balance)
                )
            }
        }

        if (input.moneyLent.signum() > 0) asset(AssetGroup.MONEY_LENT, NetWorthLine(MONEY_LENT, input.moneyLent))
        if (input.moneyBorrowed.signum() > 0) {
            liability(LiabilityGroup.MONEY_BORROWED, NetWorthLine(MONEY_BORROWED, input.moneyBorrowed))
        }

        for (item in input.items) {
            val line = NetWorthLine(item.name, item.value, item.id, item.type, item.isEstimate)
            when {
                item.type.isLoan -> liability(LiabilityGroup.LOANS, line)
                item.type.isLiability -> liability(LiabilityGroup.OTHER_LIABILITIES, line)
                item.type.isInvestment -> asset(AssetGroup.INVESTMENTS, line)
                else -> asset(AssetGroup.OTHER_ASSETS, line)
            }
        }

        return NetWorthBreakdown(
            currency = currency,
            assets = AssetGroup.entries.mapNotNull { g -> assets[g]?.let { NetWorthGroup(g, it) } },
            liabilities = LiabilityGroup.entries.mapNotNull { g -> liabilities[g]?.let { NetWorthGroup(g, it) } }
        )
    }

    // Row names for the two lending lines; the screen shows these as given.
    const val MONEY_LENT = "Money lent"
    const val MONEY_BORROWED = "Money borrowed"
}
