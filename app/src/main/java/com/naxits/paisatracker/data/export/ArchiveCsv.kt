package com.naxits.paisatracker.data.export

import com.naxits.paisatracker.data.database.entity.AccountBalanceEntity
import com.naxits.paisatracker.data.database.entity.AccountMetaEntity
import com.naxits.paisatracker.data.database.entity.CardEntity
import com.naxits.paisatracker.data.database.entity.CardType
import com.naxits.paisatracker.data.database.entity.LendBorrowPersonEntity
import com.naxits.paisatracker.data.database.entity.LendBorrowTransactionEntity
import com.naxits.paisatracker.data.database.entity.LendBorrowType
import com.naxits.paisatracker.data.database.entity.PartyEntity
import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.data.database.entity.TransactionType
import com.naxits.paisatracker.domain.model.AccountTags
import com.naxits.paisatracker.domain.model.CashAccount
import com.opencsv.CSVWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How a payment was made, going by the words of the bank's message. Blank when the message does not say. */
object PaymentTypeDetector {

    private val WORDS = listOf(
        "nach" to "nach",
        "ecs" to "nach",
        "neft" to "neft",
        "imps" to "imps",
        "rtgs" to "rtgs",
        "upi" to "upi",
        "cheque" to "cheque",
        "chq" to "cheque"
    ).map { (word, type) -> Regex("(?<![a-z0-9])$word(?![a-z0-9])") to type }

    fun detect(smsBody: String?, isCreditCardEntry: Boolean = false): String {
        if (isCreditCardEntry) return "credit-card"
        val text = smsBody?.lowercase() ?: return ""
        WORDS.firstOrNull { (word, _) -> word.containsMatchIn(text) }?.let { return it.second }
        return when {
            "credit card" in text -> "credit-card"
            "debit card" in text || "atm" in text.split(Regex("[^a-z0-9]+")) || "withdrawn" in text -> "debit-card"
            else -> ""
        }
    }
}

/**
 * The long-term record of the user's money: one CSV row per movement of money, kept readable for
 * years without the app. It is written once and never read back by the app.
 *
 * A lending entry linked to a transaction describes the same money, so it adds its details to that
 * row; a second row would count the money twice when the columns are added up. Blank always means
 * "not recorded": nothing here is guessed, and a missing balance is not written as zero.
 */
object ArchiveCsv {

    /**
     * The first columns match the format of the user's older exports, in name and order, so the
     * files can be combined. That includes the leading space in " Payment Type".
     */
    val HEADER = arrayOf(
        "Date", "Type", "SubType", "Txn Type", " Payment Type", "Business/Personal",
        "Merchant/Receiver/Sender", "Category", "Bank Name", "Account Id", "Account Type",
        "Credit", "Debit", "Balance", "Outstanding", "Available Limit", "Notes",
        "Record Id", "Source", "Subcategory", "Amount", "Currency", "Reference",
        "From Account", "To Account", "Recurring", "Left Out Of Reports", "Counted As Cash",
        "Customer/Vendor", "Lending Person", "Lending Type", "Lending Note", "Due Date",
        "Settled", "Remember Only", "Attachments", "SMS Sender", "SMS Body",
        "Transaction Hash", "Created At", "Updated At", "Deleted"
    )

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy/MMM/dd HH:mm:ss", Locale.ENGLISH)
    private const val NOTHING = "0.0"

    private enum class Direction { CREDIT, DEBIT, NEITHER }

    /** Everything a row is built from. */
    data class Input(
        val transactions: List<TransactionEntity>,
        val lendingEntries: List<LendBorrowTransactionEntity> = emptyList(),
        val persons: List<LendBorrowPersonEntity> = emptyList(),
        val balances: List<AccountBalanceEntity> = emptyList(),
        val cards: List<CardEntity> = emptyList(),
        val accountMeta: List<AccountMetaEntity> = emptyList(),
        val parties: List<PartyEntity> = emptyList()
    )

    private data class Dated(val date: LocalDateTime, val recordId: String, val row: Array<String>)

    /** One row per movement of money, oldest first. Sample entries are left out. */
    fun rows(input: Input): List<Array<String>> {
        val transactions = input.transactions.filter { !it.isSample }
        val transactionIds = transactions.map { it.id }.toSet()
        val entries = input.lendingEntries.filter { !it.isSample }
        val personNames = input.persons.associate { it.id to it.name }
        val partyNames = input.parties.associate { it.id to it.name }
        val businessAccounts = input.accountMeta
            .filter { AccountTags.hasBusiness(AccountTags.parse(it.tags)) }
            .map { it.bankName to it.accountLast4 }
            .toSet()

        // Entries on a transaction this file holds; should two ever share one, the rest get rows of their own
        val linkedEntries = entries.filter { it.transactionId in transactionIds }
            .groupBy { it.transactionId }
            .mapValues { (_, list) -> list.first() }
        val merged = linkedEntries.values.map { it.id }.toSet()

        val fromTransactions = transactions.map { tx ->
            val entry = linkedEntries[tx.id]
            Dated(
                tx.dateTime,
                "T-${tx.id}",
                transactionRow(tx, entry, entry?.let { personNames[it.personId] }, input, partyNames, businessAccounts)
            )
        }
        val fromLending = entries.filter { it.id !in merged }.map { entry ->
            Dated(entry.date, "L-${entry.id}", lendingRow(entry, personNames[entry.personId]))
        }

        return (fromTransactions + fromLending)
            .sortedWith(compareBy<Dated> { it.date }.thenBy { it.recordId })
            .map { it.row }
    }

    /** Writes the file: UTF-8 with a BOM, so spreadsheets show the rupee sign and Hindi correctly. */
    fun write(rows: List<Array<String>>, out: OutputStream) {
        out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
            CSVWriter(writer).use { csv ->
                csv.writeNext(HEADER)
                rows.forEach { csv.writeNext(it) }
            }
        }
    }

    private fun transactionRow(
        tx: TransactionEntity,
        entry: LendBorrowTransactionEntity?,
        person: String?,
        input: Input,
        partyNames: Map<Long, String>,
        businessAccounts: Set<Pair<String, String>>
    ): Array<String> {
        val direction = directionOf(tx.transactionType)
        val account = tx.accountNumber
        val accountRows = input.balances.filter { it.bankName == tx.bankName && it.accountLast4 == account }
        val linkedBalance = accountRows.firstOrNull { it.transactionId == tx.id }
        val isCreditCard = tx.transactionType == TransactionType.CREDIT ||
            accountRows.any { it.isCreditCard } ||
            input.cards.any { it.cardLast4 == account && it.cardType == CardType.CREDIT }
        val available = linkedBalance?.creditLimit?.takeIf { isCreditCard }?.subtract(linkedBalance.balance)
        val isBusiness = tx.bankName != null && account != null && (tx.bankName to account) in businessAccounts

        return arrayOf(
            tx.dateTime.format(dateFormat),
            typeOf(direction),
            entry?.let { lendingSubType(it.type) } ?: subTypeOf(tx.transactionType),
            if (tx.toAccount == CashAccount.WALLET_LAST4) "cash-withdrawal" else "",
            PaymentTypeDetector.detect(tx.smsBody, tx.transactionType == TransactionType.CREDIT),
            if (isBusiness) "business" else "personal",
            tx.merchantName,
            tx.category,
            tx.bankName.orEmpty(),
            accountId(account),
            accountTypeOf(tx, accountRows, input.cards, isCreditCard),
            if (direction == Direction.CREDIT) tx.amount.toPlainString() else NOTHING,
            if (direction == Direction.DEBIT) tx.amount.toPlainString() else NOTHING,
            if (isCreditCard) "" else (tx.balanceAfter ?: linkedBalance?.balance)?.toPlainString().orEmpty(),
            if (isCreditCard) linkedBalance?.balance?.toPlainString().orEmpty() else "",
            available?.toPlainString().orEmpty(),
            tx.description.orEmpty(),
            "T-${tx.id}",
            if (tx.smsBody.isNullOrBlank()) "manual" else "sms",
            tx.subcategory.orEmpty(),
            tx.amount.toPlainString(),
            tx.currency,
            tx.reference.orEmpty(),
            tx.fromAccount.orEmpty(),
            tx.toAccount.orEmpty(),
            yesNo(tx.isRecurring),
            yesNo(tx.excludeFromIncome),
            yesNo(tx.isCashEntry),
            tx.partyId?.let { partyNames[it] }.orEmpty(),
            person.orEmpty(),
            entry?.type?.name.orEmpty(),
            entry?.title.orEmpty(),
            entry?.dueDate?.format(dateFormat).orEmpty(),
            entry?.let { yesNo(it.isSettled) }.orEmpty(),
            entry?.let { yesNo(it.rememberOnly) }.orEmpty(),
            tx.attachments,
            tx.smsSender.orEmpty(),
            tx.smsBody.orEmpty(),
            tx.transactionHash,
            tx.createdAt.format(dateFormat),
            tx.updatedAt.format(dateFormat),
            yesNo(tx.isDeleted)
        )
    }

    /** A lending entry with no transaction behind it, such as one typed in by hand. */
    private fun lendingRow(entry: LendBorrowTransactionEntity, person: String?): Array<String> {
        val direction = when (entry.type) {
            LendBorrowType.LENT, LendBorrowType.SETTLEMENT_BORROWED -> Direction.DEBIT
            LendBorrowType.BORROWED, LendBorrowType.SETTLEMENT_LENT -> Direction.CREDIT
        }
        return arrayOf(
            entry.date.format(dateFormat),
            typeOf(direction),
            lendingSubType(entry.type),
            "",
            "",
            "personal",
            entry.merchant?.takeIf { it.isNotBlank() } ?: person.orEmpty(),
            entry.category.orEmpty(),
            "",
            "",
            "",
            if (direction == Direction.CREDIT) entry.amount.toPlainString() else NOTHING,
            if (direction == Direction.DEBIT) entry.amount.toPlainString() else NOTHING,
            "",
            "",
            "",
            "",
            "L-${entry.id}",
            "manual",
            "",
            entry.amount.toPlainString(),
            entry.currency,
            "",
            "",
            "",
            yesNo(false),
            yesNo(false),
            yesNo(false),
            "",
            person.orEmpty(),
            entry.type.name,
            entry.title,
            entry.dueDate?.format(dateFormat).orEmpty(),
            yesNo(entry.isSettled),
            yesNo(entry.rememberOnly),
            entry.attachments.joinToString(","),
            "",
            "",
            "",
            entry.createdAt.format(dateFormat),
            entry.updatedAt.format(dateFormat),
            yesNo(false)
        )
    }

    private fun directionOf(type: TransactionType): Direction = when (type) {
        TransactionType.INCOME, TransactionType.BORROWED -> Direction.CREDIT
        TransactionType.EXPENSE, TransactionType.CREDIT, TransactionType.TRANSFER,
        TransactionType.INVESTMENT, TransactionType.LENT -> Direction.DEBIT
        // A balance correction moves no money
        TransactionType.BALANCE_UPDATE -> Direction.NEITHER
    }

    private fun typeOf(direction: Direction): String = when (direction) {
        Direction.CREDIT -> "credit-transaction"
        Direction.DEBIT -> "debit-transaction"
        Direction.NEITHER -> ""
    }

    private fun subTypeOf(type: TransactionType): String = when (type) {
        TransactionType.INCOME -> "income"
        TransactionType.EXPENSE, TransactionType.CREDIT -> "expense"
        TransactionType.TRANSFER -> "transfer-out"
        TransactionType.INVESTMENT -> "investment"
        TransactionType.BALANCE_UPDATE -> "balance-update"
        TransactionType.LENT -> "lent"
        TransactionType.BORROWED -> "borrowed"
    }

    private fun lendingSubType(type: LendBorrowType): String = when (type) {
        LendBorrowType.LENT -> "lent"
        LendBorrowType.BORROWED -> "borrowed"
        LendBorrowType.SETTLEMENT_LENT -> "settlement-received"
        LendBorrowType.SETTLEMENT_BORROWED -> "settlement-paid"
    }

    private fun accountTypeOf(
        tx: TransactionEntity,
        accountRows: List<AccountBalanceEntity>,
        cards: List<CardEntity>,
        isCreditCard: Boolean
    ): String {
        val account = tx.accountNumber
        return when {
            CashAccount.isCash(tx.bankName, account) -> "cash"
            isCreditCard -> "credit-card"
            account.isNullOrBlank() -> ""
            accountRows.any { it.isWallet } -> "wallet"
            cards.any { it.cardLast4 == account && it.cardType == CardType.DEBIT } -> "debit-card"
            else -> "bank"
        }
    }

    /** The "x" keeps a spreadsheet from turning digits like 0055 into the number 55. */
    private fun accountId(account: String?): String = when {
        account.isNullOrBlank() -> ""
        account.all { it.isDigit() } -> "x$account"
        else -> account
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"
}
