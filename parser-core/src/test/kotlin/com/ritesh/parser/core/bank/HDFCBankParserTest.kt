package com.ritesh.parser.core.bank

import com.ritesh.parser.core.test.ExpectedTransaction
import com.ritesh.parser.core.test.ParserTestCase
import com.ritesh.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class HDFCBankParserTest {
    @TestFactory
    fun `test HDFC Bank Parser comprehensive test suite`(): List<DynamicTest> {
        val parser = HDFCBankParser()

        ParserTestUtils.printTestHeader(
            parserName = "HDFC Bank",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Bill Alert - should NOT parse as transaction
            ParserTestCase(
                name = "Bill Alert Notification - Should Not Parse",
                message = """New Bill Alert:
Your AUBA00000NAT3Q Bill 8078064625 of Rs.3953.72 is due on 05-Nov-2025. To pay, login to HDFC Bank Net/Mobile Banking>BillPay
T&C. Ignore if paid""",
                sender = "CP-HDFCBK-S",
                shouldParse = false
            ),

            // Actual transaction examples that SHOULD parse
            ParserTestCase(
                name = "UPI Debit Transaction",
                message = "Rs.500.00 debited from A/c XX1234 on 20-Oct-25 to merchant@upi (UPI Ref No 123456789012)",
                sender = "CP-HDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = com.ritesh.parser.core.TransactionType.EXPENSE,
                    accountLast4 = "1234",
                    reference = "123456789012"
                )
            ),
            ParserTestCase(
                name = "Debit card ATM withdrawal",
                message = "Withdrawn Rs.5000 From HDFC Bank Card x1234 At TEST LOCATION On 2026-10-16:15:27:41 Bal Rs.12345.67 Not You? Call bank support/SMS BLOCK DC 1234",
                sender = "AD-HDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000"),
                    currency = "INR",
                    type = com.ritesh.parser.core.TransactionType.EXPENSE,
                    merchant = "ATM Withdrawal at TEST LOCATION",
                    accountLast4 = "1234",
                    balance = BigDecimal("12345.67"),
                    isFromCard = true,
                    isAtmWithdrawal = true
                )
            ),
            ParserTestCase(
                name = "Debit card ATM withdrawal at a location containing O",
                message = "Withdrawn Rs.2000 From HDFC Bank Card x1234 At OLD TOWN ROAD On 2026-10-16:09:05:00 Bal Rs.5000.00 Not You? Call bank support/SMS BLOCK DC 1234",
                sender = "AD-HDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000"),
                    currency = "INR",
                    type = com.ritesh.parser.core.TransactionType.EXPENSE,
                    merchant = "ATM Withdrawal at OLD TOWN ROAD",
                    accountLast4 = "1234",
                    isAtmWithdrawal = true
                )
            ),
            ParserTestCase(
                name = "Debit card purchase is not an ATM withdrawal",
                message = "Spent Rs.250 From HDFC Bank Card x1234 At SAMPLE STORE On 2026-10-16:10:00:00 Bal Rs.1000.00 Not You? Call bank support/SMS BLOCK DC 1234",
                sender = "AD-HDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250"),
                    currency = "INR",
                    type = com.ritesh.parser.core.TransactionType.EXPENSE,
                    merchant = "SAMPLE STORE",
                    accountLast4 = "1234",
                    isFromCard = true,
                    isAtmWithdrawal = false
                )
            ),
            ParserTestCase(
                name = "Sent UPI transaction to named payee",
                message = """Sent Rs.45.00
From HDFC Bank A/C *1234
To Sample Friend
On 23/05/26
Ref 123456789012
Not You?
Contact bank support/SMS BLOCK UPI""",
                sender = "JD-HDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.00"),
                    currency = "INR",
                    type = com.ritesh.parser.core.TransactionType.EXPENSE,
                    merchant = "Sample Friend",
                    accountLast4 = "1234",
                    reference = "123456789012"
                )
            ),
            ParserTestCase(
                name = "Sent UPI transaction to numeric VPA",
                message = """Sent Rs.70.00
From HDFC Bank A/C *1234
To 0000000000@bank
On 23/05/26
Ref 123456789013
Not You?
Contact bank support/SMS BLOCK UPI""",
                sender = "AD-HDFCBK-S",
                expected = ExpectedTransaction(
                    amount = BigDecimal("70.00"),
                    currency = "INR",
                    type = com.ritesh.parser.core.TransactionType.EXPENSE,
                    merchant = "UPI Payee",
                    accountLast4 = "1234",
                    reference = "123456789013"
                )
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "CP-HDFCBK-S" to true,
            "AX-HDFCBK-S" to true,
            "JM-HDFCBK-S" to true,
            "HDFCBANK" to true,
            "SBI" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "HDFC Bank Parser Tests")
    }
}
