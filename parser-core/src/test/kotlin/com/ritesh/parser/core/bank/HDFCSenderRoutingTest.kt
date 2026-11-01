package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/** A message that names HDFC Bank must be read as HDFC Bank, whatever else the sender id resembles. */
class HDFCSenderRoutingTest {

    private val upiMessage =
        "Sent Rs.2800.00\nFrom HDFC Bank A/C *1234\nTo TEST TRADERS\nOn 17/10/26\nRef 712345678901\nNot You?\nCall 18000000000/SMS BLOCK UPI to 7000000000"

    @Test
    fun `senders that start with HDFC are read by the HDFC parser first`() {
        listOf("BP-HDFCBN-S", "AD-HDFCBK-S", "VM-HDFCBK-T", "HDFCBK").forEach { sender ->
            assertEquals("HDFC Bank", BankParserFactory.getParsers(sender).firstOrNull()?.getBankName(), sender)
        }
    }

    @Test
    fun `a UPI payment from the HDFC sender is saved to the HDFC account in the message`() {
        val parsed = BankParserFactory.getParsers("BP-HDFCBN-S")
            .firstNotNullOfOrNull { it.parse(upiMessage, "BP-HDFCBN-S", 0L) }

        assertNotNull(parsed)
        assertEquals("HDFC Bank", parsed!!.bankName)
        assertEquals("1234", parsed.accountLast4)
        assertEquals(BigDecimal("2800.00"), parsed.amount)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("Test Traders".uppercase(), parsed.merchant?.uppercase())
    }
}
