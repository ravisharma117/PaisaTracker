package com.naxits.paisatracker.data.manager

import com.naxits.paisatracker.data.database.dao.AccountRenameDao
import com.naxits.paisatracker.domain.model.AccountOrdering
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gives an account a new bank name everywhere the old one was copied: balance history,
 * transactions, linked cards, bills, recurring entries, the account's notes, and the preferences
 * that remember its place, hidden state and main-account choice.
 */
@Singleton
class AccountRenamer @Inject constructor(
    private val renameDao: AccountRenameDao,
    private val keyStore: AccountKeyStore
) {
    suspend fun rename(oldBankName: String, accountLast4: String, newBankName: String) {
        if (oldBankName == newBankName) return
        renameDao.rename(oldBankName, accountLast4, newBankName)
        keyStore.rename(
            AccountOrdering.keyOf(oldBankName, accountLast4),
            AccountOrdering.keyOf(newBankName, accountLast4)
        )
    }
}
