package com.naxits.paisatracker.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Everything that has to change together when an account gets a new bank name.
 *
 * An account has no table of its own: its bank name and last 4 digits are copied as plain text
 * into several tables, so a rename must reach all of them or the pieces drift apart.
 */
@Dao
abstract class AccountRenameDao {

    /** Last 4 digits of every account that currently carries [bankName]. */
    @Query("SELECT DISTINCT account_last4 FROM account_balances WHERE bank_name = :bankName")
    abstract suspend fun accountsOf(bankName: String): List<String>

    /** Sender of every SMS-sourced transaction on the account. Manual entries have none. */
    @Query(
        """
        SELECT DISTINCT sms_sender FROM transactions
        WHERE bank_name = :bankName AND account_number = :accountLast4
          AND sms_sender IS NOT NULL AND sms_sender != ''
        """
    )
    abstract suspend fun smsSendersOf(bankName: String, accountLast4: String): List<String>

    // OR IGNORE: a row can only collide with the same moment on an account that already carries the new name
    @Query("UPDATE OR IGNORE account_balances SET bank_name = :new WHERE bank_name = :old AND account_last4 = :last4")
    protected abstract suspend fun renameBalances(old: String, last4: String, new: String)

    /** Rows left behind after [renameBalances] are exact duplicates of rows the new account already has. */
    @Query("DELETE FROM account_balances WHERE bank_name = :old AND account_last4 = :last4")
    protected abstract suspend fun dropLeftoverBalances(old: String, last4: String)

    @Query("UPDATE transactions SET bank_name = :new WHERE bank_name = :old AND account_number = :last4")
    protected abstract suspend fun renameTransactions(old: String, last4: String, new: String)

    @Query("UPDATE OR IGNORE cards SET bank_name = :new WHERE bank_name = :old AND account_last4 = :last4")
    protected abstract suspend fun renameLinkedCards(old: String, last4: String, new: String)

    @Query("UPDATE subscriptions SET pay_from_bank = :new WHERE pay_from_bank = :old AND pay_from_last4 = :last4")
    protected abstract suspend fun renameSubscriptionPayFrom(old: String, last4: String, new: String)

    @Query("UPDATE recurring_transactions SET bank_name = :new WHERE bank_name = :old AND account_last4 = :last4")
    protected abstract suspend fun renameRecurring(old: String, last4: String, new: String)

    /** The account's notes move with it. If the new name already has notes, those are kept. */
    @Query("UPDATE OR IGNORE account_meta SET bank_name = :new WHERE bank_name = :old AND account_last4 = :last4")
    protected abstract suspend fun renameMeta(old: String, last4: String, new: String)

    @Query("DELETE FROM account_meta WHERE bank_name = :old AND account_last4 = :last4")
    protected abstract suspend fun dropLeftoverMeta(old: String, last4: String)

    /** Swaps one brand icon the user may have stored on the account for another. */
    @Query(
        """
        UPDATE account_balances SET icon_name = :newIcon, icon_res_id = 0
        WHERE bank_name = :bank AND account_last4 = :last4 AND icon_name = :oldIcon
        """
    )
    abstract suspend fun replaceStoredIcon(bank: String, last4: String, oldIcon: String, newIcon: String)

    @Transaction
    open suspend fun rename(old: String, last4: String, new: String) {
        // The leftover cleanup below would wipe the account if the name did not change
        if (old == new) return
        renameBalances(old, last4, new)
        dropLeftoverBalances(old, last4)
        renameTransactions(old, last4, new)
        renameLinkedCards(old, last4, new)
        renameSubscriptionPayFrom(old, last4, new)
        renameRecurring(old, last4, new)
        renameMeta(old, last4, new)
        dropLeftoverMeta(old, last4)
    }
}
