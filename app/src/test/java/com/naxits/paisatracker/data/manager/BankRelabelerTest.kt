package com.naxits.paisatracker.data.manager

import com.naxits.paisatracker.data.database.dao.AccountRenameDao
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BankRelabelerTest {

    private val oldBank = "Punjab & Sind Bank"
    private val newBank = "Himachal Pradesh State Cooperative Bank"

    private data class Balance(var bank: String, val last4: String, val timestamp: Int, var icon: String = "")
    private data class Tx(var bank: String, val last4: String, val sender: String?)
    private data class Card(var bank: String, val accountLast4: String?)
    private data class Bill(var payFromBank: String?, val payFromLast4: String?)
    private data class Recurring(var bank: String?, val last4: String?)
    private data class Meta(var bank: String, val last4: String)

    /** The queries of [AccountRenameDao], run over lists. */
    private class FakeRenameDao : AccountRenameDao() {
        val balances = mutableListOf<Balance>()
        val txs = mutableListOf<Tx>()
        val cards = mutableListOf<Card>()
        val bills = mutableListOf<Bill>()
        val recurring = mutableListOf<Recurring>()
        val meta = mutableListOf<Meta>()

        override suspend fun accountsOf(bankName: String) =
            balances.filter { it.bank == bankName }.map { it.last4 }.distinct()

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

    private class FakeKeyStore : AccountKeyStore {
        val renamed = mutableListOf<Pair<String, String>>()
        override suspend fun rename(oldKey: String, newKey: String) {
            renamed += oldKey to newKey
        }
    }

    private val dao = FakeRenameDao()
    private val keys = FakeKeyStore()
    private val relabeler = BankRelabeler(dao, AccountRenamer(dao, keys))

    @Test
    fun anAccountWhoseMessagesAllComeFromTheNewBanksSenderIsRenamedEverywhere() = runTest {
        dao.balances += Balance(oldBank, "1234", 1, icon = "ic_brand_punjab_sind_bank")
        dao.balances += Balance(oldBank, "1234", 2)
        dao.txs += Tx(oldBank, "1234", "BI-HPSBNK-S")
        dao.txs += Tx(oldBank, "1234", "JK-HPSBNK-S")
        dao.cards += Card(oldBank, "1234")
        dao.bills += Bill(oldBank, "1234")
        dao.recurring += Recurring(oldBank, "1234")
        dao.meta += Meta(oldBank, "1234")

        assertEquals(1, relabeler.relabel())

        assertTrue(dao.balances.all { it.bank == newBank })
        assertTrue(dao.txs.all { it.bank == newBank })
        assertEquals(newBank, dao.cards.single().bank)
        assertEquals(newBank, dao.bills.single().payFromBank)
        assertEquals(newBank, dao.recurring.single().bank)
        assertEquals(newBank, dao.meta.single().bank)
        assertEquals("ic_brand_himachal_state_coop_bank", dao.balances.first().icon)
        assertEquals(listOf("${oldBank}_1234" to "${newBank}_1234"), keys.renamed)
    }

    @Test
    fun anAccountWithAnotherBanksSenderIsLeftAlone() = runTest {
        dao.balances += Balance(oldBank, "5678", 1)
        dao.txs += Tx(oldBank, "5678", "AD-PSBANK-S")

        assertEquals(0, relabeler.relabel())

        assertEquals(oldBank, dao.balances.single().bank)
        assertEquals(oldBank, dao.txs.single().bank)
        assertTrue(keys.renamed.isEmpty())
    }

    @Test
    fun anAccountWithMixedSendersIsLeftAlone() = runTest {
        dao.balances += Balance(oldBank, "5678", 1)
        dao.txs += Tx(oldBank, "5678", "BI-HPSBNK-S")
        dao.txs += Tx(oldBank, "5678", "AD-PSBANK-S")

        assertEquals(0, relabeler.relabel())

        assertEquals(oldBank, dao.balances.single().bank)
    }

    @Test
    fun anAccountWithNoSmsTransactionsIsLeftAlone() = runTest {
        dao.balances += Balance(oldBank, "9999", 1)
        dao.txs += Tx(oldBank, "9999", null)

        assertEquals(0, relabeler.relabel())

        assertEquals(oldBank, dao.balances.single().bank)
    }

    @Test
    fun onlyTheMisdetectedAccountChangesWhenSeveralShareTheOldBank() = runTest {
        dao.balances += Balance(oldBank, "1234", 1)
        dao.balances += Balance(oldBank, "5678", 1)
        dao.balances += Balance("Another Bank", "1234", 1)
        dao.txs += Tx(oldBank, "1234", "BI-HPSBNK-S")
        dao.txs += Tx(oldBank, "5678", "AD-PSBANK-S")
        dao.txs += Tx("Another Bank", "1234", "XX-OTHER-S")

        assertEquals(1, relabeler.relabel())

        assertEquals(
            listOf(newBank to "1234", oldBank to "5678", "Another Bank" to "1234"),
            dao.balances.map { it.bank to it.last4 }
        )
        assertEquals(listOf(newBank, oldBank, "Another Bank"), dao.txs.map { it.bank })
    }

    @Test
    fun anIconTheUserPickedThemselvesIsKept() = runTest {
        dao.balances += Balance(oldBank, "1234", 1, icon = "type_finance_bank")
        dao.txs += Tx(oldBank, "1234", "BI-HPSBNK-S")

        relabeler.relabel()

        assertEquals("type_finance_bank", dao.balances.single().icon)
    }

    @Test
    fun whenTheNewNameAlreadyExistsTheDuplicateMomentsAreDroppedAndTheRestKept() = runTest {
        dao.balances += Balance(oldBank, "1234", 1)
        dao.balances += Balance(oldBank, "1234", 2)
        dao.balances += Balance(newBank, "1234", 2)
        dao.txs += Tx(oldBank, "1234", "BI-HPSBNK-S")
        dao.meta += Meta(oldBank, "1234")
        dao.meta += Meta(newBank, "1234")

        relabeler.relabel()

        assertEquals(listOf(1, 2), dao.balances.map { it.timestamp }.sorted())
        assertTrue(dao.balances.all { it.bank == newBank })
        assertEquals(1, dao.meta.size, "notes already on the new name are kept")
    }

    @Test
    fun runningItAgainChangesNothing() = runTest {
        dao.balances += Balance(oldBank, "1234", 1)
        dao.txs += Tx(oldBank, "1234", "BI-HPSBNK-S")

        assertEquals(1, relabeler.relabel())
        assertEquals(0, relabeler.relabel())
    }

    @Test
    fun renamingToTheSameNameLeavesTheAccountIntact() = runTest {
        dao.balances += Balance(newBank, "1234", 1)

        AccountRenamer(dao, keys).rename(newBank, "1234", newBank)

        assertEquals(1, dao.balances.size)
        assertFalse(keys.renamed.isNotEmpty())
    }

    @Test
    fun senderMatchingIgnoresCaseAndNeedsAtLeastOneSender() {
        assertTrue(BankRelabeler.belongsTo(listOf("bi-hpsbnk-s"), "HPSBNK"))
        assertFalse(BankRelabeler.belongsTo(emptyList(), "HPSBNK"))
    }
}
