package com.naxits.paisatracker.data.export

import com.naxits.paisatracker.data.database.entity.AccountBalanceEntity
import com.naxits.paisatracker.data.database.entity.AccountMetaEntity
import com.naxits.paisatracker.data.database.entity.CardEntity
import com.naxits.paisatracker.data.database.entity.CardType
import com.naxits.paisatracker.data.database.entity.LendBorrowPersonEntity
import com.naxits.paisatracker.data.database.entity.LendBorrowTransactionEntity
import com.naxits.paisatracker.data.database.entity.LendBorrowType
import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.data.database.entity.TransactionType
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ArchiveCsvTest {

    private val at = LocalDateTime.of(2016, 7, 11, 12, 23, 19)

    private fun tx(
        id: Long = 1,
        type: TransactionType = TransactionType.EXPENSE,
        amount: String = "2500.00",
        date: LocalDateTime = at,
        bank: String? = "Test Bank",
        account: String? = "1234",
        sms: String? = "Sent Rs.2500 from A/c 1234 by UPI",
        balance: String? = null,
        toAccount: String? = null,
        deleted: Boolean = false,
        sample: Boolean = false
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal(amount),
        merchantName = "Sample Shop",
        category = "Shopping",
        transactionType = type,
        dateTime = date,
        smsBody = sms,
        bankName = bank,
        accountNumber = account,
        balanceAfter = balance?.let { BigDecimal(it) },
        transactionHash = "hash-$id",
        toAccount = toAccount,
        isDeleted = deleted,
        isSample = sample
    )

    private fun entry(
        id: Long = 7,
        type: LendBorrowType = LendBorrowType.LENT,
        transactionId: Long? = null,
        date: LocalDateTime = at
    ) = LendBorrowTransactionEntity(
        id = id,
        personId = 3,
        transactionId = transactionId,
        type = type,
        amount = BigDecimal("500.00"),
        title = "For the trip",
        date = date,
        rememberOnly = true
    )

    private val person = LendBorrowPersonEntity(id = 3, name = "Sample Person")

    private fun balance(
        last4: String = "1234",
        amount: String,
        transactionId: Long? = null,
        creditCard: Boolean = false,
        limit: String? = null
    ) = AccountBalanceEntity(
        bankName = "Test Bank",
        accountLast4 = last4,
        balance = BigDecimal(amount),
        timestamp = at,
        transactionId = transactionId,
        isCreditCard = creditCard,
        creditLimit = limit?.let { BigDecimal(it) }
    )

    private fun rows(input: ArchiveCsv.Input) = ArchiveCsv.rows(input)

    private fun Array<String>.column(name: String): String = this[ArchiveCsv.HEADER.indexOf(name)]

    private fun single(input: ArchiveCsv.Input) = rows(input).single()

    @Test
    fun theFirstColumnsMatchTheOlderExportsExactly() {
        val older = "Date,Type,SubType,Txn Type, Payment Type,Business/Personal,Merchant/Receiver/Sender,Category," +
            "Bank Name,Account Id,Account Type,Credit,Debit,Balance,Outstanding,Available Limit,Notes"
        assertEquals(older.split(","), ArchiveCsv.HEADER.take(17))
    }

    @Test
    fun everyRowHasOneValuePerColumn() {
        val all = rows(ArchiveCsv.Input(listOf(tx()), listOf(entry()), listOf(person)))
        assertEquals(2, all.size)
        all.forEach { assertEquals(ArchiveCsv.HEADER.size, it.size) }
    }

    @Test
    fun theDateIsInTheOlderFormatWhateverThePhonesLanguage() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("hi-IN"))
            assertEquals("2016/Jul/11 12:23:19", single(ArchiveCsv.Input(listOf(tx()))).column("Date"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun moneyGoingOutIsADebit() {
        for (type in listOf(TransactionType.EXPENSE, TransactionType.CREDIT, TransactionType.TRANSFER, TransactionType.INVESTMENT, TransactionType.LENT)) {
            val row = single(ArchiveCsv.Input(listOf(tx(type = type))))
            assertEquals("debit-transaction", row.column("Type"), type.name)
            assertEquals("2500.00", row.column("Debit"), type.name)
            assertEquals("0.0", row.column("Credit"), type.name)
        }
    }

    @Test
    fun moneyComingInIsACredit() {
        for (type in listOf(TransactionType.INCOME, TransactionType.BORROWED)) {
            val row = single(ArchiveCsv.Input(listOf(tx(type = type))))
            assertEquals("credit-transaction", row.column("Type"), type.name)
            assertEquals("2500.00", row.column("Credit"), type.name)
            assertEquals("0.0", row.column("Debit"), type.name)
        }
    }

    @Test
    fun aBalanceCorrectionIsNeitherButItsAmountIsKept() {
        val row = single(ArchiveCsv.Input(listOf(tx(type = TransactionType.BALANCE_UPDATE))))
        assertEquals("", row.column("Type"))
        assertEquals("0.0", row.column("Credit"))
        assertEquals("0.0", row.column("Debit"))
        assertEquals("2500.00", row.column("Amount"))
    }

    @Test
    fun aTransferToCashIsACashWithdrawal() {
        val row = single(ArchiveCsv.Input(listOf(tx(type = TransactionType.TRANSFER, toAccount = "wallet"))))
        assertEquals("transfer-out", row.column("SubType"))
        assertEquals("cash-withdrawal", row.column("Txn Type"))
    }

    @Test
    fun aLendingEntryLinkedToATransactionJoinsItsRowSoTheMoneyIsNotCountedTwice() {
        val all = rows(ArchiveCsv.Input(listOf(tx(id = 1)), listOf(entry(transactionId = 1)), listOf(person)))

        val row = all.single()
        assertEquals("lent", row.column("SubType"))
        assertEquals("T-1", row.column("Record Id"))
        assertEquals("Sample Person", row.column("Lending Person"))
        assertEquals("LENT", row.column("Lending Type"))
        assertEquals("For the trip", row.column("Lending Note"))
        assertEquals("yes", row.column("Remember Only"))
    }

    @Test
    fun aLendingEntryWithNoTransactionGetsItsOwnRow() {
        val row = single(ArchiveCsv.Input(emptyList(), listOf(entry()), listOf(person)))

        assertEquals("L-7", row.column("Record Id"))
        assertEquals("manual", row.column("Source"))
        assertEquals("Sample Person", row.column("Merchant/Receiver/Sender"))
        assertEquals("500.00", row.column("Debit"))
    }

    @Test
    fun settlementsLandOnTheRightSide() {
        val received = single(ArchiveCsv.Input(emptyList(), listOf(entry(type = LendBorrowType.SETTLEMENT_LENT)), listOf(person)))
        assertEquals("settlement-received", received.column("SubType"))
        assertEquals("500.00", received.column("Credit"))

        val paid = single(ArchiveCsv.Input(emptyList(), listOf(entry(type = LendBorrowType.SETTLEMENT_BORROWED)), listOf(person)))
        assertEquals("settlement-paid", paid.column("SubType"))
        assertEquals("500.00", paid.column("Debit"))

        val borrowed = single(ArchiveCsv.Input(emptyList(), listOf(entry(type = LendBorrowType.BORROWED)), listOf(person)))
        assertEquals("credit-transaction", borrowed.column("Type"))
    }

    @Test
    fun aDeletedTransactionIsKeptAndMarked() {
        assertEquals("yes", single(ArchiveCsv.Input(listOf(tx(deleted = true)))).column("Deleted"))
        assertEquals("no", single(ArchiveCsv.Input(listOf(tx()))).column("Deleted"))
    }

    @Test
    fun sampleEntriesAreLeftOut() {
        assertTrue(rows(ArchiveCsv.Input(listOf(tx(sample = true)))).isEmpty())
    }

    @Test
    fun theOldestComesFirstAcrossBothKinds() {
        val all = rows(
            ArchiveCsv.Input(
                listOf(tx(id = 1, date = at.plusDays(2)), tx(id = 2, date = at)),
                listOf(entry(date = at.plusDays(1))),
                listOf(person)
            )
        )
        assertEquals(listOf("T-2", "L-7", "T-1"), all.map { it.column("Record Id") })
    }

    @Test
    fun anUnknownBalanceIsBlankNotZero() {
        assertEquals("", single(ArchiveCsv.Input(listOf(tx()))).column("Balance"))
    }

    @Test
    fun theBalanceComesFromTheEntryOrFromItsLinkedRow() {
        assertEquals("900.50", single(ArchiveCsv.Input(listOf(tx(balance = "900.50")))).column("Balance"))
        val linked = ArchiveCsv.Input(listOf(tx(id = 1)), balances = listOf(balance(amount = "750.00", transactionId = 1)))
        assertEquals("750.00", single(linked).column("Balance"))
    }

    @Test
    fun aCreditCardShowsOutstandingAndAvailableInsteadOfBalance() {
        val row = single(
            ArchiveCsv.Input(
                listOf(tx(id = 1, type = TransactionType.CREDIT)),
                balances = listOf(balance(amount = "1000.00", transactionId = 1, creditCard = true, limit = "5000.00"))
            )
        )
        assertEquals("", row.column("Balance"))
        assertEquals("1000.00", row.column("Outstanding"))
        assertEquals("4000.00", row.column("Available Limit"))
        assertEquals("credit-card", row.column("Account Type"))
        assertEquals("credit-card", row.column(" Payment Type"))
    }

    @Test
    fun accountIdKeepsLeadingZeros() {
        assertEquals("x0055", single(ArchiveCsv.Input(listOf(tx(account = "0055")))).column("Account Id"))
    }

    @Test
    fun theAccountTypeFollowsWhatTheAppKnows() {
        assertEquals("bank", single(ArchiveCsv.Input(listOf(tx()))).column("Account Type"))
        assertEquals(
            "debit-card",
            single(
                ArchiveCsv.Input(
                    listOf(tx()),
                    cards = listOf(CardEntity(cardLast4 = "1234", cardType = CardType.DEBIT, bankName = "Test Bank"))
                )
            ).column("Account Type")
        )
        assertEquals("cash", single(ArchiveCsv.Input(listOf(tx(bank = "Cash", account = "wallet")))).column("Account Type"))
        assertEquals(
            "wallet",
            single(
                ArchiveCsv.Input(listOf(tx()), balances = listOf(balance(amount = "10.00").copy(isWallet = true)))
            ).column("Account Type")
        )
    }

    @Test
    fun aBusinessTaggedAccountIsBusiness() {
        val meta = AccountMetaEntity(bankName = "Test Bank", accountLast4 = "1234", tags = "Current,Business")
        assertEquals("business", single(ArchiveCsv.Input(listOf(tx()), accountMeta = listOf(meta))).column("Business/Personal"))
        assertEquals("personal", single(ArchiveCsv.Input(listOf(tx()))).column("Business/Personal"))
    }

    @Test
    fun anEntryWithNoSmsIsMarkedManual() {
        assertEquals("manual", single(ArchiveCsv.Input(listOf(tx(sms = null)))).column("Source"))
        assertEquals("sms", single(ArchiveCsv.Input(listOf(tx()))).column("Source"))
    }

    @Test
    fun paymentTypeIsReadFromTheMessageAndBlankWhenItDoesNotSay() {
        assertEquals("upi", PaymentTypeDetector.detect("Sent Rs.10 by UPI"))
        assertEquals("neft", PaymentTypeDetector.detect("credited through NEFT with UTR"))
        assertEquals("imps", PaymentTypeDetector.detect("For IMPS from a friend"))
        assertEquals("rtgs", PaymentTypeDetector.detect("RTGS received"))
        assertEquals("nach", PaymentTypeDetector.detect("has a debit by NACH of Rs 10"))
        assertEquals("cheque", PaymentTypeDetector.detect("Chq 1234 recd in clearing"))
        assertEquals("debit-card", PaymentTypeDetector.detect("Withdrawn Rs.500 from Card x1234"))
        assertEquals("debit-card", PaymentTypeDetector.detect("spent by SBI Debit Card 1234"))
        assertEquals("credit-card", PaymentTypeDetector.detect("spent on your Credit Card"))
        assertEquals("", PaymentTypeDetector.detect("Your account was updated"))
        assertEquals("", PaymentTypeDetector.detect(null))
        // Not the word on its own
        assertEquals("", PaymentTypeDetector.detect("The cupid batch at the plant"))
    }

    @Test
    fun theFileSurvivesCommasQuotesAndLineBreaksInAMessage() {
        val message = "Sent Rs.10, to \"Sample Shop\"\non 01/01/26 ₹ नमस्ते"
        val out = ByteArrayOutputStream()

        ArchiveCsv.write(rows(ArchiveCsv.Input(listOf(tx(sms = message)))), out)

        val bytes = out.toByteArray()
        assertEquals(listOf(0xEF, 0xBB, 0xBF), bytes.take(3).map { it.toInt() and 0xFF })

        val text = String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        val parser = CSVParserBuilder().withEscapeChar('\u0000').build()
        val lines = CSVReaderBuilder(StringReader(text)).withCSVParser(parser).build().use { it.readAll() }

        assertEquals(2, lines.size)
        assertEquals(ArchiveCsv.HEADER.toList(), lines[0].toList())
        assertEquals(ArchiveCsv.HEADER.size, lines[1].size)
        assertEquals(message, lines[1].column("SMS Body"))
    }

    @Test
    fun amountsAreNeverWrittenInScientificNotation() {
        val row = single(ArchiveCsv.Input(listOf(tx(amount = "1E+5"))))
        assertEquals("100000", row.column("Debit"))
        assertEquals("100000", row.column("Amount"))
    }
}
