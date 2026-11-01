package com.naxits.paisatracker.data.manager

import com.naxits.paisatracker.data.database.dao.AccountRenameDao
import com.naxits.paisatracker.data.database.dao.OrphanGroup

data class FakeBalance(var bank: String, val last4: String, val timestamp: Int, var icon: String = "")
data class FakeTx(var bank: String?, val last4: String, val sender: String?)
data class FakeCard(var bank: String, val accountLast4: String?)
data class FakeBill(var payFromBank: String?, val payFromLast4: String?)
data class FakeRecurring(var bank: String?, val last4: String?)
data class FakeMeta(var bank: String, val last4: String)

/** The queries of [AccountRenameDao], run over lists. */
class FakeAccountRenameDao : AccountRenameDao() {
    val balances = mutableListOf<FakeBalance>()
    val txs = mutableListOf<FakeTx>()
    val cards = mutableListOf<FakeCard>()
    val bills = mutableListOf<FakeBill>()
    val recurring = mutableListOf<FakeRecurring>()
    val meta = mutableListOf<FakeMeta>()

    override suspend fun accountsOf(bankName: String) =
        (balances.filter { it.bank == bankName }.map { it.last4 } +
            txs.filter { it.bank == bankName }.map { it.last4 }).distinct()

    override suspend fun bankNamesWithLast4(accountLast4: String) =
        balances.filter { it.last4 == accountLast4 }.map { it.bank }.distinct()

    override suspend fun orphanGroups(): List<OrphanGroup> = txs
        .filter { !it.sender.isNullOrEmpty() && it.last4.isNotEmpty() }
        .filter { tx -> balances.none { it.bank == tx.bank && it.last4 == tx.last4 } }
        .map { OrphanGroup(it.bank, it.last4, it.sender!!) }
        .distinct()

    override suspend fun moveOrphans(oldBank: String?, accountLast4: String, sender: String, newBank: String) {
        txs.filter { it.bank == oldBank && it.last4 == accountLast4 && it.sender == sender }.forEach { it.bank = newBank }
    }

    override suspend fun smsSendersOf(bankName: String, accountLast4: String) =
        txs.filter { it.bank == bankName && it.last4 == accountLast4 && !it.sender.isNullOrEmpty() }
            .mapNotNull { it.sender }.distinct()

    override suspend fun renameBalances(old: String, last4: String, new: String) {
        balances.filter { it.bank == old && it.last4 == last4 }.forEach { row ->
            // OR IGNORE: skip a row whose new name already has the same moment
            if (balances.none { it.bank == new && it.last4 == last4 && it.timestamp == row.timestamp }) row.bank = new
        }
    }

    override suspend fun dropLeftoverBalances(old: String, last4: String) {
        balances.removeAll { it.bank == old && it.last4 == last4 }
    }

    override suspend fun renameTransactions(old: String, last4: String, new: String) {
        txs.filter { it.bank == old && it.last4 == last4 }.forEach { it.bank = new }
    }

    override suspend fun renameLinkedCards(old: String, last4: String, new: String) {
        cards.filter { it.bank == old && it.accountLast4 == last4 }.forEach { it.bank = new }
    }

    override suspend fun renameSubscriptionPayFrom(old: String, last4: String, new: String) {
        bills.filter { it.payFromBank == old && it.payFromLast4 == last4 }.forEach { it.payFromBank = new }
    }

    override suspend fun renameRecurring(old: String, last4: String, new: String) {
        recurring.filter { it.bank == old && it.last4 == last4 }.forEach { it.bank = new }
    }

    override suspend fun renameMeta(old: String, last4: String, new: String) {
        meta.filter { it.bank == old && it.last4 == last4 }.forEach { row ->
            if (meta.none { it.bank == new && it.last4 == last4 }) row.bank = new
        }
    }

    override suspend fun dropLeftoverMeta(old: String, last4: String) {
        meta.removeAll { it.bank == old && it.last4 == last4 }
    }

    override suspend fun replaceStoredIcon(bank: String, last4: String, oldIcon: String, newIcon: String) {
        balances.filter { it.bank == bank && it.last4 == last4 && it.icon == oldIcon }.forEach { it.icon = newIcon }
    }
}
