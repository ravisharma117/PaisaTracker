package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.PartyKind
import com.naxits.paisatracker.domain.model.Party
import com.naxits.paisatracker.domain.model.PartyIdentifier
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartyMatcherTest {

    private fun party(
        id: Long,
        name: String,
        phone: String? = null,
        identifiers: List<PartyIdentifier> = emptyList(),
        aliases: List<String> = emptyList(),
        archived: Boolean = false
    ) = Party(id, name, PartyKind.CUSTOMER, phone, identifiers, aliases, null, archived)

    private val acme = party(
        1, "Acme Traders", phone = "7000000001",
        identifiers = listOf(PartyIdentifier.Upi("client@testbank"), PartyIdentifier.Account("5678"))
    )
    private val other = party(2, "Sample Vendor", phone = "7000000002")

    @Test
    fun aMatchingPhoneNumberIsAStrongMatch() {
        val match = PartyMatcher.match(Counterparty(phone = "7000000001"), "Unknown Merchant", listOf(acme, other))
        assertEquals(1L, match.partyId)
        assertEquals(MatchSource.PHONE, match.source)
        assertEquals(95, match.confidence)
        assertTrue("0001" in match.reason, "the reason shows only the end of the number")
        assertTrue("7000000001" !in match.reason)
    }

    @Test
    fun aSavedUpiIdMatchesIgnoringCase() {
        val match = PartyMatcher.match(Counterparty(upiId = "Client@TestBank"), "x", listOf(acme, other))
        assertEquals(1L, match.partyId)
        assertEquals(MatchSource.UPI_ID, match.source)
    }

    @Test
    fun aKnownAccountNumberMatches() {
        val match = PartyMatcher.match(Counterparty(account = "5678"), "x", listOf(acme, other))
        assertEquals(1L, match.partyId)
        assertEquals(MatchSource.ACCOUNT, match.source)
        assertEquals(90, match.confidence)
    }

    @Test
    fun anEarlierSettledTransactionWithTheSameMerchantMatches() {
        val match = PartyMatcher.match(Counterparty(), "Some Merchant", listOf(acme, other), historyPartyId = 2L)
        assertEquals(2L, match.partyId)
        assertEquals(MatchSource.HISTORY, match.source)
        assertEquals(85, match.confidence)
    }

    @Test
    fun aNameThatContainsAllWordsOfThePartyNameIsReliable() {
        val match = PartyMatcher.match(Counterparty(name = "Acme Traders Pvt"), "Acme Traders Pvt", listOf(acme, other))
        assertEquals(1L, match.partyId)
        assertEquals(MatchSource.NAME, match.source)
        assertTrue(match.confidence >= PartyMatcher.RELIABLE)
    }

    @Test
    fun anAliasCountsLikeTheName() {
        val withAlias = party(3, "Test Company", aliases = listOf("Tester Corp"))
        val match = PartyMatcher.match(Counterparty(name = "Tester Corp"), "Tester Corp", listOf(withAlias))
        assertEquals(3L, match.partyId)
    }

    @Test
    fun aSingleSharedWordIsNotReliableEnough() {
        val match = PartyMatcher.match(Counterparty(name = "Acme"), "Acme", listOf(acme))
        assertTrue(match.isUnknown)
        assertEquals(PartyMatcher.NO_MATCH_REASON, match.reason)
    }

    @Test
    fun nothingKnownGivesUnknown() {
        val match = PartyMatcher.match(Counterparty(name = "Random Shop"), "Random Shop", listOf(acme, other))
        assertTrue(match.isUnknown)
        assertNull(match.partyName)
        assertEquals(MatchSource.NONE, match.source)
        assertEquals(0, match.confidence)
    }

    @Test
    fun noPartiesAtAllGivesUnknown() {
        assertTrue(PartyMatcher.match(Counterparty(phone = "7000000001"), "x", emptyList()).isUnknown)
    }

    @Test
    fun theStrongerEvidenceWinsBetweenTwoParties() {
        // A phone match (95) beats a history match (85) for a different party
        val match = PartyMatcher.match(Counterparty(phone = "7000000001"), "x", listOf(acme, other), historyPartyId = 2L)
        assertEquals(1L, match.partyId)
    }

    @Test
    fun twoPartiesFittingEquallyWellStayUnknown() {
        val a = party(10, "Twin Traders")
        val b = party(11, "Twin Traders")
        val match = PartyMatcher.match(Counterparty(name = "Twin Traders"), "Twin Traders", listOf(a, b))
        assertTrue(match.isUnknown)
        assertEquals(PartyMatcher.AMBIGUOUS_REASON, match.reason)
    }

    @Test
    fun anArchivedPartyIsIgnored() {
        val archived = acme.copy(isArchived = true)
        assertTrue(PartyMatcher.match(Counterparty(phone = "7000000001"), "x", listOf(archived)).isUnknown)
    }

    @Test
    fun aPhoneWrittenWithACountryCodeStillMatches() {
        val withCode = acme.copy(phone = "+91 70000 00001")
        val match = PartyMatcher.match(Counterparty(phone = "7000000001"), "x", listOf(withCode))
        assertEquals(1L, match.partyId)
    }
}
