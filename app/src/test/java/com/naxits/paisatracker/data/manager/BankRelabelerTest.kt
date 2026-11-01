package com.naxits.paisatracker.data.manager

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BankRelabelerTest {

    private val oldBank = "Punjab & Sind Bank"
    private val newBank = "Himachal Pradesh State Cooperative Bank"

    private class FakeKeyStore : AccountKeyStore {
        val renamed = mutableListOf<Pair<String, String>>()
        override suspend fun rename(oldKey: String, newKey: String) {
            renamed += oldKey to newKey
        }
    }

    private val dao = FakeAccountRenameDao()
    private val keys = FakeKeyStore()
    private val relabeler = BankRelabeler(dao, AccountRenamer(dao, keys))

    @Test
    fun anAccountWhoseMessagesAllComeFromTheNewBanksSenderIsRenamedEverywhere() = runTest {
        dao.balances += FakeBalance(oldBank, "1234", 1, icon = "ic_brand_punjab_sind_bank")
        dao.balances += FakeBalance(oldBank, "1234", 2)
        dao.txs += FakeTx(oldBank, "1234", "BI-HPSBNK-S")
        dao.txs += FakeTx(oldBank, "1234", "JK-HPSBNK-S")
        dao.cards += FakeCard(oldBank, "1234")
        dao.bills += FakeBill(oldBank, "1234")
        dao.recurring += FakeRecurring(oldBank, "1234")
        dao.meta += FakeMeta(oldBank, "1234")

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
        dao.balances += FakeBalance(oldBank, "5678", 1)
        dao.txs += FakeTx(oldBank, "5678", "AD-PSBANK-S")

        assertEquals(0, relabeler.relabel())

        assertEquals(oldBank, dao.balances.single().bank)
        assertEquals(oldBank, dao.txs.single().bank)
        assertTrue(keys.renamed.isEmpty())
    }

    @Test
    fun anAccountWithMixedSendersIsLeftAlone() = runTest {
        dao.balances += FakeBalance(oldBank, "5678", 1)
        dao.txs += FakeTx(oldBank, "5678", "BI-HPSBNK-S")
        dao.txs += FakeTx(oldBank, "5678", "AD-PSBANK-S")

        assertEquals(0, relabeler.relabel())

        assertEquals(oldBank, dao.balances.single().bank)
    }

    @Test
    fun anAccountWithNoSmsTransactionsIsLeftAlone() = runTest {
        dao.balances += FakeBalance(oldBank, "9999", 1)
        dao.txs += FakeTx(oldBank, "9999", null)

        assertEquals(0, relabeler.relabel())

        assertEquals(oldBank, dao.balances.single().bank)
    }

    @Test
    fun onlyTheMisdetectedAccountChangesWhenSeveralShareTheOldBank() = runTest {
        dao.balances += FakeBalance(oldBank, "1234", 1)
        dao.balances += FakeBalance(oldBank, "5678", 1)
        dao.balances += FakeBalance("Another Bank", "1234", 1)
        dao.txs += FakeTx(oldBank, "1234", "BI-HPSBNK-S")
        dao.txs += FakeTx(oldBank, "5678", "AD-PSBANK-S")
        dao.txs += FakeTx("Another Bank", "1234", "XX-OTHER-S")

        assertEquals(1, relabeler.relabel())

        assertEquals(
            listOf(newBank to "1234", oldBank to "5678", "Another Bank" to "1234"),
            dao.balances.map { it.bank to it.last4 }
        )
        assertEquals(listOf(newBank, oldBank, "Another Bank"), dao.txs.map { it.bank })
    }

    @Test
    fun anIconTheUserPickedThemselvesIsKept() = runTest {
        dao.balances += FakeBalance(oldBank, "1234", 1, icon = "type_finance_bank")
        dao.txs += FakeTx(oldBank, "1234", "BI-HPSBNK-S")

        relabeler.relabel()

        assertEquals("type_finance_bank", dao.balances.single().icon)
    }

    @Test
    fun whenTheNewNameAlreadyExistsTheDuplicateMomentsAreDroppedAndTheRestKept() = runTest {
        dao.balances += FakeBalance(oldBank, "1234", 1)
        dao.balances += FakeBalance(oldBank, "1234", 2)
        dao.balances += FakeBalance(newBank, "1234", 2)
        dao.txs += FakeTx(oldBank, "1234", "BI-HPSBNK-S")
        dao.meta += FakeMeta(oldBank, "1234")
        dao.meta += FakeMeta(newBank, "1234")

        relabeler.relabel()

        assertEquals(listOf(1, 2), dao.balances.map { it.timestamp }.sorted())
        assertTrue(dao.balances.all { it.bank == newBank })
        assertEquals(1, dao.meta.size, "notes already on the new name are kept")
    }

    @Test
    fun transactionsWithoutAnAccountRowAreRelabelledToo() = runTest {
        dao.balances += FakeBalance(oldBank, "1234", 1)
        dao.txs += FakeTx(oldBank, "1234", "BI-HPSBNK-S")
        // A message saved before its account ever got a balance row
        dao.txs += FakeTx(oldBank, "5678", "JK-HPSBNK-S")
        // Another account with no balance row that really is with the old bank
        dao.txs += FakeTx(oldBank, "9999", "AD-PSBANK-S")

        assertEquals(2, relabeler.relabel())

        assertEquals(newBank, dao.txs[1].bank)
        assertEquals(oldBank, dao.txs[2].bank)
    }

    @Test
    fun runningItAgainChangesNothing() = runTest {
        dao.balances += FakeBalance(oldBank, "1234", 1)
        dao.txs += FakeTx(oldBank, "1234", "BI-HPSBNK-S")

        assertEquals(1, relabeler.relabel())
        assertEquals(0, relabeler.relabel())
    }

    @Test
    fun renamingToTheSameNameLeavesTheAccountIntact() = runTest {
        dao.balances += FakeBalance(newBank, "1234", 1)

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
