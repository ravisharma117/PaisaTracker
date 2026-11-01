package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.AccountBalanceEntity
import com.naxits.paisatracker.domain.model.AccountMeta
import com.naxits.paisatracker.domain.model.AccountOrdering
import com.naxits.paisatracker.domain.model.CashAccount
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class HomeBanksTest {

    private fun account(
        bank: String,
        last4: String,
        balance: String = "100",
        creditCard: Boolean = false,
        wallet: Boolean = false
    ) = AccountBalanceEntity(
        bankName = bank,
        accountLast4 = last4,
        balance = BigDecimal(balance),
        timestamp = LocalDateTime.of(2026, 1, 1, 0, 0),
        isCreditCard = creditCard,
        isWallet = wallet
    )

    private val ticked = AccountMeta(showOnHome = true)

    private fun metaFor(vararg accounts: AccountBalanceEntity) =
        accounts.associate { AccountOrdering.keyOf(it) to ticked }

    @Test
    fun onlyTickedBankAccountsAreListedInTheOrderGiven() {
        val a = account("Bank A", "1111")
        val b = account("Bank B", "2222")
        val c = account("Bank C", "3333")

        val result = HomeBanks.select(listOf(c, a, b), metaFor(a, c), emptySet())

        assertEquals(listOf(c, a), result)
    }

    @Test
    fun anAccountWithNoNotesOrAnUntickedBoxIsLeftOut() {
        val a = account("Bank A", "1111")
        val b = account("Bank B", "2222")
        val meta = mapOf(AccountOrdering.keyOf(b) to AccountMeta(description = "Household"))

        assertTrue(HomeBanks.select(listOf(a, b), meta, emptySet()).isEmpty())
    }

    @Test
    fun creditCardsWalletsAndCashAreNeverListed() {
        val card = account("Bank A", "1111", creditCard = true)
        val wallet = account("Wallet X", "wallet", wallet = true)
        val cash = account(CashAccount.BANK_NAME, CashAccount.WALLET_LAST4, wallet = true)

        assertTrue(HomeBanks.select(listOf(card, wallet, cash), metaFor(card, wallet, cash), emptySet()).isEmpty())
    }

    @Test
    fun aHiddenAccountIsLeftOut() {
        val a = account("Bank A", "1111")
        val b = account("Bank B", "2222")

        val result = HomeBanks.select(listOf(a, b), metaFor(a, b), setOf(AccountOrdering.keyOf(a)))

        assertEquals(listOf(b), result)
    }

    @Test
    fun aTickedAccountWithZeroBalanceIsStillListed() {
        val a = account("Bank A", "1111", balance = "0")

        assertEquals(listOf(a), HomeBanks.select(listOf(a), metaFor(a), emptySet()))
    }
}
