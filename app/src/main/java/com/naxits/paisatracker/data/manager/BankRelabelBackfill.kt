package com.naxits.paisatracker.data.manager

import android.util.Log
import com.naxits.paisatracker.data.database.dao.AccountRenameDao
import com.naxits.paisatracker.data.preferences.UserPreferencesRepository
import com.ritesh.parser.core.bank.HimachalStateCoopBankParser
import javax.inject.Inject
import javax.inject.Singleton

/** Accounts saved under [oldBank] whose messages all come from [senderKey] belong to [newBank]. */
data class BankRelabelRule(
    val oldBank: String,
    val senderKey: String,
    val newBank: String,
    val oldIcon: String,
    val newIcon: String
)

/**
 * Renames accounts that were saved under the wrong bank before the SMS parser learned to tell the
 * two banks apart. An account is only renamed when every SMS-sourced transaction on it came from
 * the sender that belongs to the new bank, so accounts that really are with the old bank are left
 * alone.
 */
@Singleton
class BankRelabeler @Inject constructor(
    private val renameDao: AccountRenameDao,
    private val accountRenamer: AccountRenamer
) {
    companion object {
        val RULES = listOf(
            BankRelabelRule(
                oldBank = "Punjab & Sind Bank",
                senderKey = HimachalStateCoopBankParser.SENDER_KEY,
                newBank = HimachalStateCoopBankParser().getBankName(),
                oldIcon = "ic_brand_punjab_sind_bank",
                newIcon = "ic_brand_himachal_state_coop_bank"
            )
        )

        /** True when there is at least one sender and every one of them contains [senderKey]. */
        fun belongsTo(senders: List<String>, senderKey: String): Boolean =
            senders.isNotEmpty() && senders.all { it.uppercase().contains(senderKey.uppercase()) }
    }

    /** Applies [rules] and returns how many accounts were renamed. */
    suspend fun relabel(rules: List<BankRelabelRule> = RULES): Int {
        var renamed = 0
        for (rule in rules) {
            for (last4 in renameDao.accountsOf(rule.oldBank)) {
                val senders = renameDao.smsSendersOf(rule.oldBank, last4)
                if (!belongsTo(senders, rule.senderKey)) continue
                accountRenamer.rename(rule.oldBank, last4, rule.newBank)
                renameDao.replaceStoredIcon(rule.newBank, last4, rule.oldIcon, rule.newIcon)
                renamed++
            }
        }
        return renamed
    }
}

/** Runs [BankRelabeler] once per install. */
@Singleton
class BankRelabelBackfill @Inject constructor(
    private val relabeler: BankRelabeler,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    companion object {
        private const val TAG = "BankRelabelBackfill"
    }

    suspend fun runOnce() {
        if (userPreferencesRepository.isBankRelabelBackfillDone()) return
        val renamed = relabeler.relabel()
        userPreferencesRepository.markBankRelabelBackfillDone()
        Log.d(TAG, "Renamed $renamed accounts to the bank their messages come from")
    }
}
