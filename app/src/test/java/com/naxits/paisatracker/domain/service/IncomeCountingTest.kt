package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.data.database.entity.TransactionType
import com.naxits.paisatracker.data.database.entity.countsAsIncome
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class IncomeCountingTest {

    private fun entry(type: TransactionType, excluded: Boolean = false) = TransactionEntity(
        amount = BigDecimal("100"),
        merchantName = "Test Merchant",
        category = "Income",
        transactionType = type,
        dateTime = LocalDateTime.of(2025, 1, 15, 10, 30),
        transactionHash = "hash-$type-$excluded",
        excludeFromIncome = excluded
    )

    @Test
    fun `income counts unless it is marked as not income`() {
        assertTrue(entry(TransactionType.INCOME).countsAsIncome)
        assertFalse(entry(TransactionType.INCOME, excluded = true).countsAsIncome)
    }

    @Test
    fun `other types never count as income`() {
        TransactionType.entries.filter { it != TransactionType.INCOME }.forEach {
            assertFalse(entry(it).countsAsIncome, "$it must not count as income")
        }
    }

    @Test
    fun `expense turned into a transfer only credits the destination`() {
        // The source was already debited when the expense was recorded
        val changes = TransferConversion.netChanges(
            oldAccount = "1111",
            oldEffect = BigDecimal("-500"),
            source = "1111",
            target = "2222",
            amount = BigDecimal("500")
        )
        assertEquals(mapOf("2222" to BigDecimal("500")), changes)
    }

    @Test
    fun `income turned into a transfer only debits the source`() {
        // The destination was already credited when the income was recorded
        val changes = TransferConversion.netChanges(
            oldAccount = "2222",
            oldEffect = BigDecimal("500"),
            source = "1111",
            target = "2222",
            amount = BigDecimal("500")
        )
        assertEquals(mapOf("1111" to BigDecimal("-500")), changes)
    }

    @Test
    fun `amount edited during the conversion adjusts both accounts`() {
        val changes = TransferConversion.netChanges(
            oldAccount = "1111",
            oldEffect = BigDecimal("-500"),
            source = "1111",
            target = "2222",
            amount = BigDecimal("700")
        )
        assertEquals(mapOf("1111" to BigDecimal("-200"), "2222" to BigDecimal("700")), changes)
    }

    @Test
    fun `entry without an account only moves the transfer accounts`() {
        val changes = TransferConversion.netChanges(
            oldAccount = null,
            oldEffect = BigDecimal("-500"),
            source = "1111",
            target = "2222",
            amount = BigDecimal("500")
        )
        assertEquals(mapOf("1111" to BigDecimal("-500"), "2222" to BigDecimal("500")), changes)
    }
}
