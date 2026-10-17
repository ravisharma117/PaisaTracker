package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import com.ritesh.parser.core.test.ExpectedTransaction
import com.ritesh.parser.core.test.ParserTestCase
import com.ritesh.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

class HimachalStateCoopBankParserTest {

    private val bankName = "Himachal Pradesh State Cooperative Bank"

    @TestFactory
    fun `himachal state coop bank parser handles its formats`(): List<DynamicTest> {
        val parser = HimachalStateCoopBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "Himachal State Coop Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            ParserTestCase(
                name = "Debit with short mask and slash date",
                message = "Dear Customer, Your A/c XXXX1234 has been debited with Rs.1500.50 on 01/02/2026. Now your A/c balance is Rs. 12345.67. THE HP STATE COOP BANK LTD.",
                sender = "BI-HPSBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.50"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = bankName,
                    accountLast4 = "1234",
                    balance = BigDecimal("12345.67")
                )
            ),
            ParserTestCase(
                name = "Credit with short mask and slash date",
                message = "Dear Customer, Your A/c XXXX1234 has been credited with Rs.25000.00 on 01/02/2026. Now your A/c balance is Rs. 37345.67. THE HP STATE COOP BANK LTD.",
                sender = "JK-HPSBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("25000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = bankName,
                    accountLast4 = "1234",
                    balance = BigDecimal("37345.67")
                )
            ),
            ParserTestCase(
                name = "Debit with long mask, time stamp and CR balance",
                message = "Dear Customer, Your A/c ********1234 has been Debited with Rs.100000.00 on 01-02-2026 10:20:30. Now your A/c balance is Rs. 98765.43CR. THE HP STATE COOP BANK LTD.",
                sender = "JX-HPSBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE,
                    merchant = bankName,
                    accountLast4 = "1234",
                    balance = BigDecimal("98765.43")
                )
            ),
            ParserTestCase(
                name = "Credit with long mask and a balance over one lakh",
                message = "Dear Customer, Your A/c ********1234 has been Credited with Rs.999.99 on 01-02-2026 10:20:30. Now your A/c balance is Rs. 123456.78CR. THE HP STATE COOP BANK LTD.",
                sender = "BI-HPSBNK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("999.99"),
                    currency = "INR",
                    type = TransactionType.INCOME,
                    merchant = bankName,
                    accountLast4 = "1234",
                    balance = BigDecimal("123456.78")
                )
            )
        )

        val handleChecks = listOf(
            "BI-HPSBNK-S" to true,
            "JK-HPSBNK-S" to true,
            "hpsbnk" to true,
            "AD-PSBANK-S" to false,
            "HDFCBK" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Himachal State Coop Bank Parser"
        )
    }

    @Test
    fun `factory sends HPSBNK to Himachal and PSB senders to Punjab and Sind`() {
        assertEquals(bankName, BankParserFactory.getParser("BI-HPSBNK-S")?.getBankName())
        assertEquals("Punjab & Sind Bank", BankParserFactory.getParser("AD-PSBANK-S")?.getBankName())
        assertEquals("Punjab & Sind Bank", BankParserFactory.getParser("PUNJAB SIND")?.getBankName())
    }

    @Test
    fun `parsed message through the factory is labelled with the Himachal bank`() {
        val parsed = BankParserFactory.getParser("BI-HPSBNK-S")?.parse(
            "Dear Customer, Your A/c XXXX1234 has been debited with Rs.10.00 on 01/02/2026. Now your A/c balance is Rs. 90.00. THE HP STATE COOP BANK LTD.",
            "BI-HPSBNK-S",
            0L
        )
        assertNotNull(parsed)
        assertEquals(bankName, parsed!!.bankName)
    }
}
