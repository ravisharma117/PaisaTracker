package com.naxits.paisatracker.data.repository

import com.naxits.paisatracker.data.database.dao.AccountMetaDao
import com.naxits.paisatracker.data.database.entity.AccountMetaEntity
import com.naxits.paisatracker.domain.model.AccountMeta
import com.naxits.paisatracker.domain.model.AccountOrdering
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountMetaRepositoryTest {

    private class FakeAccountMetaDao : AccountMetaDao {
        val rows = MutableStateFlow<List<AccountMetaEntity>>(emptyList())

        private fun same(r: AccountMetaEntity, bank: String, last4: String) =
            r.bankName == bank && r.accountLast4 == last4

        override fun observeAll(): Flow<List<AccountMetaEntity>> = rows
        override suspend fun getAll() = rows.value
        override suspend fun get(bankName: String, accountLast4: String) =
            rows.value.firstOrNull { same(it, bankName, accountLast4) }

        override suspend fun upsert(meta: AccountMetaEntity) {
            rows.value = rows.value.filterNot { same(it, meta.bankName, meta.accountLast4) } + meta
        }

        override suspend fun upsertAll(metas: List<AccountMetaEntity>) = metas.forEach { upsert(it) }
        override suspend fun delete(bankName: String, accountLast4: String) {
            rows.value = rows.value.filterNot { same(it, bankName, accountLast4) }
        }

        override suspend fun deleteAll() {
            rows.value = emptyList()
        }
    }

    private val dao = FakeAccountMetaDao()
    private val repository = AccountMetaRepository(dao)

    @Test
    fun savedNotesAreReadBackByTheAccountKey() = runTest {
        repository.save(
            "Test Bank", "1234",
            AccountMeta(customIconName = "ic_brand_test", tags = listOf("Savings", "Business"), description = " Salary ")
        )

        val all = repository.observeAll().first()
        val meta = all.getValue(AccountOrdering.keyOf("Test Bank", "1234"))
        assertEquals("ic_brand_test", meta.customIconName)
        assertEquals(listOf("Savings", "Business"), meta.tags)
        assertEquals("Salary", meta.description)
        assertTrue(meta.isBusiness)
    }

    @Test
    fun notesOnOneAccountDoNotShowOnAnother() = runTest {
        repository.save("Test Bank", "1234", AccountMeta(tags = listOf("FD")))

        assertEquals(AccountMeta.NONE, repository.get("Test Bank", "5678"))
        assertEquals(AccountMeta.NONE, repository.get("Other Bank", "1234"))
    }

    @Test
    fun clearingEverythingRemovesTheRow() = runTest {
        repository.save("Test Bank", "1234", AccountMeta(description = "Household"))
        repository.save("Test Bank", "1234", AccountMeta(tags = emptyList(), description = "  "))

        assertNull(dao.get("Test Bank", "1234"))
    }

    @Test
    fun tagsAreCleanedBeforeTheyAreStored() = runTest {
        repository.save("Test Bank", "1234", AccountMeta(tags = listOf(" RD ", "rd", "")))

        assertEquals("RD", dao.get("Test Bank", "1234")?.tags)
    }
}
