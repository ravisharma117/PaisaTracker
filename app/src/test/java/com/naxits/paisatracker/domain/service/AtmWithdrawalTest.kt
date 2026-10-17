package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.data.database.entity.TransactionType
import com.naxits.paisatracker.data.database.entity.countsAsExpense
import com.naxits.paisatracker.domain.model.CashAccount
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.Test

class AtmWithdrawalTest {

    private val withdrawal = TransactionEntity(
        amount = BigDecimal("5000"),
        merchantName = "ATM Withdrawal at TEST LOCATION",
        category = "Miscellaneous",
        subcategory = "Other",
        transactionType = TransactionType.EXPENSE,
        dateTime = LocalDateTime.of(2026, 10, 16, 15, 27),
        bankName = "Test Bank",
        accountNumber = "1234",
        balanceAfter = BigDecimal("12345.67"),
        transactionHash = "hash-atm",
        isCashEntry = true
    )

    @Test
    fun `withdrawal becomes a transfer from its account to Cash`() {
        val transfer = AtmWithdrawal.toCashTransfer(withdrawal)

        assertEquals(TransactionType.TRANSFER, transfer.transactionType)
        assertEquals("1234", transfer.accountNumber)
        assertEquals("1234", transfer.fromAccount)
        assertEquals(CashAccount.WALLET_LAST4, transfer.toAccount)
        assertEquals(AtmWithdrawal.CATEGORY, transfer.category)
        assertNull(transfer.subcategory)
        assertEquals("ATM Withdrawal at TEST LOCATION", transfer.merchantName)
        assertFalse(transfer.isCashEntry, "the Cash side is a transfer, not a Cash mark")
        assertFalse(transfer.countsAsExpense)
    }

    @Test
    fun `amount, date, bank and SMS balance are kept`() {
        val transfer = AtmWithdrawal.toCashTransfer(withdrawal)

        assertEquals(withdrawal.amount, transfer.amount)
        assertEquals(withdrawal.dateTime, transfer.dateTime)
        assertEquals(withdrawal.bankName, transfer.bankName)
        assertEquals(withdrawal.balanceAfter, transfer.balanceAfter)
        assertEquals(withdrawal.transactionHash, transfer.transactionHash)
    }

    @Test
    fun `title keeps a parser title and falls back to the plain title`() {
        assertEquals("ATM Withdrawal at TEST LOCATION", AtmWithdrawal.title("ATM Withdrawal at TEST LOCATION"))
        assertEquals("ATM Withdrawal", AtmWithdrawal.title("TEST LOCATION"))
        assertEquals("ATM Withdrawal", AtmWithdrawal.title("ATM"))
        assertEquals("ATM Withdrawal", AtmWithdrawal.title(null))
    }

    @Test
    fun `a merchant passed in replaces the stored one`() {
        val transfer = AtmWithdrawal.toCashTransfer(
            withdrawal.copy(merchantName = "Test Location"),
            merchant = "ATM Withdrawal at TEST LOCATION"
        )
        assertEquals("ATM Withdrawal at TEST LOCATION", transfer.merchantName)
    }
}
