package com.naxits.paisatracker.data.repository

import com.naxits.paisatracker.data.database.entity.PartyEntity
import com.naxits.paisatracker.data.database.entity.PartyKind
import com.naxits.paisatracker.domain.model.PartyIdentifier
import com.naxits.paisatracker.domain.service.FakePartyDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PartyRepositoryTest {

    private val dao = FakePartyDao()
    private val repository = PartyRepository(dao)

    @Test
    fun searchFindsByNameOrAliasWhileTyping() = runTest {
        dao.insert(PartyEntity(name = "Acme Traders"))
        dao.insert(PartyEntity(name = "Sample Vendor", aliases = "Tester Corp"))

        assertEquals(listOf("Acme Traders"), repository.search("acm").map { it.name })
        assertEquals(listOf("Sample Vendor"), repository.search("tester").map { it.name })
        assertEquals(emptyList(), repository.search("zzz"))
    }

    @Test
    fun searchTreatsWildcardsAsPlainText() = runTest {
        dao.insert(PartyEntity(name = "Acme Traders"))

        assertEquals(emptyList(), repository.search("%x_"))
    }

    @Test
    fun emptySearchListsSomeActiveParties() = runTest {
        dao.insert(PartyEntity(name = "Acme Traders"))
        dao.insert(PartyEntity(name = "Old Client", isArchived = true))

        assertEquals(listOf("Acme Traders"), repository.search("").map { it.name })
    }

    @Test
    fun findOrCreateReusesAnExistingNameIgnoringCase() = runTest {
        val first = repository.findOrCreate("Acme Traders")
        val again = repository.findOrCreate("  acme traders ")

        assertEquals(first.id, again.id)
        assertEquals(1, dao.getAll().size)
    }

    @Test
    fun findOrCreateReusesAnAlias() = runTest {
        val id = dao.insert(PartyEntity(name = "Acme Traders", aliases = "ACME TRD"))

        assertEquals(id, repository.findOrCreate("acme trd").id)
    }

    @Test
    fun findOrCreateAddsANewPartyWithTheGivenKind() = runTest {
        val vendor = repository.findOrCreate("Sample Vendor", PartyKind.VENDOR)

        assertTrue(vendor.id > 0)
        assertEquals(PartyKind.VENDOR, vendor.kind)
        assertEquals("Sample Vendor", repository.getById(vendor.id)?.name)
    }

    @Test
    fun aBlankNameIsRefused() = runTest {
        assertFailsWith<IllegalArgumentException> { repository.findOrCreate("   ") }
    }

    @Test
    fun anArchivedPartyDisappearsFromTheActiveList() = runTest {
        val party = repository.findOrCreate("Acme Traders")

        repository.archive(party.id)

        assertEquals(emptyList(), repository.observeActive().first())
        assertEquals(1, dao.getAll().size, "archiving keeps the record")
    }

    @Test
    fun mergingMovesTransactionsAndKnowledgeAndArchivesTheOldOne() = runTest {
        val keep = dao.insert(PartyEntity(name = "Acme Traders"))
        val fold = dao.insert(
            PartyEntity(
                name = "Acme Trading", phone = "7000000001", aliases = "ACME TRD",
                identifiers = PartyIdentifier.Upi("client@testbank").encode()
            )
        )
        dao.addTransaction(1, "Acme Trading", fold, LocalDateTime.of(2026, 1, 1, 0, 0))
        dao.addTransaction(2, "Acme Traders", keep, LocalDateTime.of(2026, 1, 2, 0, 0))

        repository.merge(fold, keep)

        assertEquals(keep, dao.transactionParty[1L])
        val merged = repository.getById(keep)!!
        assertEquals("7000000001", merged.phone)
        assertTrue(PartyIdentifier.Upi("client@testbank") in merged.identifiers)
        assertTrue("Acme Trading" in merged.aliases && "ACME TRD" in merged.aliases)
        assertTrue(dao.getById(fold)!!.isArchived)
        assertEquals(2, repository.transactionCount(keep))
    }

    @Test
    fun mergingAPartyIntoItselfChangesNothing() = runTest {
        val id = dao.insert(PartyEntity(name = "Acme Traders"))

        repository.merge(id, id)

        assertEquals(false, dao.getById(id)!!.isArchived)
    }
}
