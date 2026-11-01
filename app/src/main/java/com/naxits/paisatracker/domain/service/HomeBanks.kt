package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.AccountBalanceEntity
import com.naxits.paisatracker.domain.model.AccountMeta
import com.naxits.paisatracker.domain.model.AccountOrdering
import com.naxits.paisatracker.domain.model.CashAccount

/** Picks the bank accounts the Show Banks card on Home lists. */
object HomeBanks {

    /**
     * The plain bank accounts in [accounts] (kept in the order given) that were ticked "Show on
     * homepage" in [meta], leaving out credit cards, wallets, Cash and any account in [hidden].
     * A ticked account is listed even when its balance is zero.
     *
     * [meta] and [hidden] use the keys of [AccountOrdering.keyOf].
     */
    fun select(
        accounts: List<AccountBalanceEntity>,
        meta: Map<String, AccountMeta>,
        hidden: Set<String>
    ): List<AccountBalanceEntity> = accounts.filter { account ->
        val key = AccountOrdering.keyOf(account)
        !account.isCreditCard &&
            !account.isWallet &&
            !CashAccount.isCash(account.bankName, account.accountLast4) &&
            key !in hidden &&
            meta[key]?.showOnHome == true
    }
}
