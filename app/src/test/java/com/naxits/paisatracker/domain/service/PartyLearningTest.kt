package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.PartyKind
import com.naxits.paisatracker.domain.model.Party
import com.naxits.paisatracker.domain.model.PartyIdentifier
import com.naxits.paisatracker.domain.model.PartyText
import org.junit.Test
import kotlin.test.assertEquals

class PartyLearningTest {

    private val base = Party(1, "Acme Traders", PartyKind.CUSTOMER, null, emptyList(), emptyList(), null, false)

    @Test
    fun aConfirmedTransactionTeachesTheUpiIdAccountPhoneAndName() {
        val learned = PartyLearning.learn(
            base,
            Counterparty(name = "ACME TRD", phone = "7000000001", upiId = "client@testbank", account = "5678"),
            "ACME TRD"
        )
        assertEquals("7000000001", learned.phone)
        assertEquals(
            listOf(PartyIdentifier.Upi("client@testbank"), PartyIdentifier.Account("5678")),
            learned.identifiers
        )
        assertEquals(listOf("ACME TRD"), learned.aliases)
    }

    @Test
    fun anExistingPhoneIsNeverReplaced() {
        val learned = PartyLearning.learn(base.copy(phone = "7000000009"), Counterparty(phone = "7000000001"), "x")
        assertEquals("7000000009", learned.phone)
    }

    @Test
    fun nothingIsAddedTwice() {
        val once = PartyLearning.learn(base, Counterparty(upiId = "client@testbank", name = "Acme Co"), "Acme Co")
        val twice = PartyLearning.learn(once, Counterparty(upiId = "Client@TestBank", name = "acme co"), "acme co")
        assertEquals(once, twice)
    }

    @Test
    fun theSameNameAsThePartyIsNotAnAlias() {
        val learned = PartyLearning.learn(base, Counterparty(name = "acme traders"), "acme traders")
        assertEquals(emptyList(), learned.aliases)
    }

    @Test
    fun anUnknownMerchantIsNotLearnedAsAName() {
        val learned = PartyLearning.learn(base, Counterparty(), "Unknown Merchant")
        assertEquals(emptyList(), learned.aliases)
    }

    @Test
    fun identifiersSurviveTheStoredTextRoundTrip() {
        val ids = listOf(PartyIdentifier.Upi("client@testbank"), PartyIdentifier.Account("5678"))
        assertEquals(ids, PartyText.identifiers(PartyText.joinIdentifiers(ids)))
    }
}
