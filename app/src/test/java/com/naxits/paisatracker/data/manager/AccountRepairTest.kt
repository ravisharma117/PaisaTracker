package com.naxits.paisatracker.data.manager

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class AccountRepairTest {

    private val dao = FakeAccountRenameDao()

    // The bank each sender belongs to, as the parsers would say
    private val repair = AccountRepair(dao) { sender ->
        when {
            "HDFC" in sender -> "HDFC Bank"
            "SBIBK" in sender -> "State Bank of India"
            else -> null
        }
    }
    private val resolver = AccountResolver(dao)

    @Test
    fun messagesSavedUnderTheOldNameOfARenamedAccountMoveOntoIt() = runTest {
        // The account was renamed, but its messages still carry the name it had before
        dao.balances += FakeBalance("HDFC Bank Salary", "1234", 1)
        dao.txs += FakeTx("State Bank of India", "1234", "BP-HDFCBN-S")
        dao.txs += FakeTx("State Bank of India", "1234", "VM-HDFCBK-T")

        assertEquals(2, repair.repair())

        assertEquals(listOf("HDFC Bank Salary", "HDFC Bank Salary"), dao.txs.map { it.bank })
    }

    @Test
    fun messagesSavedUnderTheBanksOwnNameJoinTheUsersAccount() = runTest {
        dao.balances += FakeBalance("HDFC Bank Salary", "1234", 1)
        dao.txs += FakeTx("HDFC Bank", "1234", "AD-HDFCBK-S")

        assertEquals(1, repair.repair())

        assertEquals("HDFC Bank Salary", dao.txs.single().bank)
    }

    @Test
    fun messagesFromAnotherBanksSenderAreLeftWhereTheyAre() = runTest {
        dao.balances += FakeBalance("HDFC Bank Salary", "1234", 1)
        dao.txs += FakeTx("State Bank of India", "1234", "AD-SBIBK-S")

        assertEquals(0, repair.repair())

        assertEquals("State Bank of India", dao.txs.single().bank)
    }

    @Test
    fun messagesThatAlreadyHaveTheirAccountAreNotTouched() = runTest {
        dao.balances += FakeBalance("HDFC Bank Salary", "1234", 1)
        dao.txs += FakeTx("HDFC Bank Salary", "1234", "AD-HDFCBK-S")

        assertEquals(0, repair.repair())

        assertEquals("HDFC Bank Salary", dao.txs.single().bank)
    }

    @Test
    fun differentDigitsAreNeverMovedOntoTheAccount() = runTest {
        dao.balances += FakeBalance("HDFC Bank Salary", "1234", 1)
        dao.txs += FakeTx("State Bank of India", "5678", "AD-HDFCBK-S")

        assertEquals(0, repair.repair())

        assertEquals("State Bank of India", dao.txs.single().bank)
    }

    @Test
    fun twoRelatedAccountsWithTheSameDigitsAreTooUnclearToChoose() = runTest {
        dao.balances += FakeBalance("HDFC Bank Salary", "1234", 1)
        dao.balances += FakeBalance("HDFC Bank Savings", "1234", 1)
        dao.txs += FakeTx("State Bank of India", "1234", "AD-HDFCBK-S")

        assertEquals(0, repair.repair())
    }

    @Test
    fun manualEntriesAndSendersWeDoNotKnowAreIgnored() = runTest {
        dao.balances += FakeBalance("HDFC Bank Salary", "1234", 1)
        dao.txs += FakeTx("Manual Entry", "1234", null)
        dao.txs += FakeTx("Some Bank", "1234", "XX-UNKNOWN-S")

        assertEquals(0, repair.repair())
    }

    @Test
    fun runningItAgainChangesNothing() = runTest {
        dao.balances += FakeBalance("HDFC Bank Salary", "1234", 1)
        dao.txs += FakeTx("State Bank of India", "1234", "BP-HDFCBN-S")

        assertEquals(1, repair.repair())
        assertEquals(0, repair.repair())
    }

    @Test
    fun aNewMessageJoinsTheAccountTheUserNamed() = runTest {
        dao.balances += FakeBalance("HDFC Bank Salary", "1234", 1)

        assertEquals("HDFC Bank Salary", resolver.bankFor("HDFC Bank", "1234"))
    }

    @Test
    fun aNewMessageKeepsItsOwnNameWhenNoAccountFits() = runTest {
        dao.balances += FakeBalance("State Bank of India", "1234", 1)

        assertEquals("HDFC Bank", resolver.bankFor("HDFC Bank", "1234"))
        assertEquals("HDFC Bank", resolver.bankFor("HDFC Bank", "9999"))
        assertEquals("HDFC Bank", resolver.bankFor("HDFC Bank", null))
        assertEquals("HDFC Bank", resolver.bankFor("HDFC Bank", "  "))
    }

    @Test
    fun aLongerAccountNumberIsMatchedByItsLastFourDigits() = runTest {
        dao.balances += FakeBalance("HDFC Bank Salary", "1234", 1)

        assertEquals("HDFC Bank Salary", resolver.bankFor("HDFC Bank", "000001234"))
    }
}
