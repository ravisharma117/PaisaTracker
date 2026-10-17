package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.data.database.entity.TransactionType
import com.naxits.paisatracker.domain.model.CashAccount

/**
 * An ATM withdrawal moves money from a bank account into Cash, so it is recorded as a transfer
 * to the Cash account rather than an expense. Spending that cash is entered later from Cash.
 */
object AtmWithdrawal {
    const val CATEGORY = "Cash Withdrawal"
    private const val TITLE = "ATM Withdrawal"

    /** Keeps a parser title such as "ATM Withdrawal at <location>", otherwise uses the plain title. */
    fun title(merchant: String?): String =
        merchant?.trim()?.takeIf { it.startsWith(TITLE, ignoreCase = true) } ?: TITLE

    /**
     * The withdrawal as a transfer from its own account (or card) to Cash. The source account
     * keeps the debit it already had as an expense, so only the Cash side is new.
     */
    fun toCashTransfer(entity: TransactionEntity, merchant: String? = entity.merchantName): TransactionEntity =
        entity.copy(
            transactionType = TransactionType.TRANSFER,
            merchantName = title(merchant),
            category = CATEGORY,
            subcategory = null,
            fromAccount = entity.accountNumber,
            toAccount = CashAccount.WALLET_LAST4,
            excludeFromIncome = false,
            isCashEntry = false
        )
}
