package com.naxits.paisatracker.data.manager

import android.util.Log
import com.naxits.paisatracker.data.database.dao.AccountRenameDao
import com.naxits.paisatracker.data.preferences.UserPreferencesRepository
import com.naxits.paisatracker.domain.service.AccountMatcher
import com.ritesh.parser.core.bank.BankParserFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks the account a new message belongs to. The message names the bank its own way ("HDFC
 * Bank"); the user may have given the account another name ("HDFC Bank Salary"). The digits and a
 * related name find the account the user already has, so the message joins it instead of
 * starting a second one.
 */
@Singleton
class AccountResolver @Inject constructor(
    private val renameDao: AccountRenameDao
) {
    /** The bank name to save the message under: the user's own name for the account, or [parsedBank] when none fits. */
    suspend fun bankFor(parsedBank: String, accountLast4: String?): String {
        val digits = accountLast4?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }?.takeLast(4) ?: return parsedBank
        return AccountMatcher.resolve(parsedBank, renameDao.bankNamesWithLast4(digits)) ?: parsedBank
    }
}

/**
 * Moves messages that were saved under a bank name no account carries any more (after the account
 * was renamed) onto the account they belong to. A group is only moved when the sender's own bank
 * and the digits point clearly at exactly one existing account.
 */
class AccountRepair(
    private val renameDao: AccountRenameDao,
    private val bankForSender: (sender: String) -> String?
) {
    /** Returns how many groups of messages were moved. */
    suspend fun repair(): Int {
        var moved = 0
        for (group in renameDao.orphanGroups()) {
            val senderBank = bankForSender(group.sender) ?: continue
            val target = AccountMatcher.resolve(senderBank, renameDao.bankNamesWithLast4(group.accountNumber)) ?: continue
            if (target.equals(group.bankName, ignoreCase = true)) continue
            renameDao.moveOrphans(group.bankName, group.accountNumber, group.sender, target)
            moved++
        }
        return moved
    }
}

/** Runs [AccountRepair] once per install. */
@Singleton
class AccountRepairBackfill @Inject constructor(
    private val renameDao: AccountRenameDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    companion object {
        private const val TAG = "AccountRepairBackfill"
    }

    suspend fun runOnce() {
        if (userPreferencesRepository.isAccountRepairBackfillDone()) return
        val moved = AccountRepair(renameDao) { sender ->
            BankParserFactory.getParsers(sender).firstOrNull()?.getBankName()
        }.repair()
        userPreferencesRepository.markAccountRepairBackfillDone()
        Log.d(TAG, "Moved $moved groups of messages onto the account they belong to")
    }
}
