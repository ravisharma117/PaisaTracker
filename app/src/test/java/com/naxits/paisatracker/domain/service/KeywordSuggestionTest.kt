package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.dao.MerchantMappingDao
import com.naxits.paisatracker.data.database.dao.VendorGroup
import com.naxits.paisatracker.data.database.entity.MerchantMappingEntity
import com.naxits.paisatracker.data.database.entity.ReviewKind
import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.data.database.entity.TransactionReviewEntity
import com.naxits.paisatracker.data.database.entity.TransactionType
import com.naxits.paisatracker.data.manager.CategoryChoice
import com.naxits.paisatracker.data.manager.UncategorisedFiller
import com.naxits.paisatracker.data.repository.MerchantMappingRepository
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun entry(
    id: Long = 10,
    merchant: String = "Sample Filling Station",
    category: String = "Miscellaneous",
    subcategory: String? = null,
    type: TransactionType = TransactionType.EXPENSE,
    description: String? = null
) = TransactionEntity(
    id = id,
    amount = BigDecimal("500.00"),
    merchantName = merchant,
    category = category,
    subcategory = subcategory,
    transactionType = type,
    dateTime = LocalDateTime.of(2026, 4, 1, 9, 0),
    description = description,
    bankName = "Test Bank",
    accountNumber = "1234",
    transactionHash = "hash-$id"
)

class KeywordCategorySuggesterTest {

    private val now = LocalDateTime.of(2026, 4, 2, 8, 0)
    private val patterns = FakePatternDao()
    private val reviews = FakeReviewDao()
    private val suggester = KeywordCategorySuggester(patterns, reviews) { now }

    @Test
    fun anUncategorisedEntryGetsTheKeywordCategoryAndAReviewSayingWhy() = runTest {
        val review = suggester.suggest(10, entry())

        assertNotNull(review)
        assertEquals(ReviewKind.CATEGORY, review.kind)
        assertEquals("Transport", review.suggestedValue)
        assertEquals("Fuel", review.suggestedSubcategory)
        assertTrue(review.reason.contains("filling station"), review.reason)
        assertEquals("Transport" to "Fuel", patterns.categoryWrites[10L])
        assertEquals(1, reviews.reviews.value.size)
    }

    @Test
    fun anEntryAlreadyInARealCategoryKeepsIt() = runTest {
        assertNull(suggester.suggest(10, entry(category = "Shopping")))

        assertTrue(patterns.categoryWrites.isEmpty())
        assertTrue(reviews.reviews.value.isEmpty())
    }

    @Test
    fun whenTheKeywordAgreesOnlyAMissingSubcategoryIsFilledInWithoutAReview() = runTest {
        assertNull(suggester.suggest(10, entry(category = "Transport")))

        assertEquals("Transport" to "Fuel", patterns.categoryWrites[10L])
        assertTrue(reviews.reviews.value.isEmpty())
    }

    @Test
    fun moneyComingInIsNeverCategorisedFromAShopWord() = runTest {
        assertNull(suggester.suggest(10, entry(type = TransactionType.INCOME)))

        assertTrue(patterns.categoryWrites.isEmpty())
    }

    @Test
    fun anEntryWithNoKeywordIsLeftAlone() = runTest {
        assertNull(suggester.suggest(10, entry(merchant = "Sample Person")))

        assertTrue(patterns.categoryWrites.isEmpty())
    }

    @Test
    fun aSecondCallDoesNotFileASecondReview() = runTest {
        suggester.suggest(10, entry())
        assertNull(suggester.suggest(10, entry()))

        assertEquals(1, reviews.reviews.value.size)
    }

    @Test
    fun theNoteIsReadAsWellAsTheName() = runTest {
        val review = suggester.suggest(10, entry(merchant = "Sample Person", description = "pizza for me"))

        assertEquals("Food & Drinks", review?.suggestedValue)
    }
}

class UncategorisedFillerTest {

    private val none: suspend (String) -> CategoryChoice? = { null }

    @Test
    fun aRememberedVendorWinsOverAKeyword() = runTest {
        val choice = UncategorisedFiller.choose(entry()) { CategoryChoice("Business", "Inventory") }

        assertEquals(CategoryChoice("Business", "Inventory"), choice)
    }

    @Test
    fun otherwiseAKeywordFilesIt() = runTest {
        assertEquals(CategoryChoice("Transport", "Fuel"), UncategorisedFiller.choose(entry(), none))
    }

    @Test
    fun anEntryInARealCategoryIsNeverTouched() = runTest {
        assertNull(UncategorisedFiller.choose(entry(category = "Home")) { CategoryChoice("Business", null) })
    }

    @Test
    fun incomeIsNotFiledFromAShopWord() = runTest {
        assertNull(UncategorisedFiller.choose(entry(type = TransactionType.INCOME), none))
    }

    @Test
    fun nothingKnownMeansNothingChanges() = runTest {
        assertNull(UncategorisedFiller.choose(entry(merchant = "Sample Person"), none))
    }
}

class MerchantMappingRepositoryTest {

    private val dao = FakeMerchantMappingDao()
    private val repository = MerchantMappingRepository(dao)

    @Test
    fun aRememberedVendorIsFoundUnderAnotherSpelling() = runTest {
        repository.setMapping("Sample Fuel Station", "Transport", "Fuel")

        val found = repository.findForVendor("SAMPLE FILLING STATION")

        assertEquals("Transport", found?.category)
        assertEquals("Fuel", found?.subcategory)
    }

    @Test
    fun rememberingAgainReplacesTheOldChoiceInsteadOfAddingAnother() = runTest {
        repository.setMapping("Sample Fuel Station", "Transport", "Fuel")
        repository.setMapping("Sample Filling Station", "Business", null)

        assertEquals(1, dao.rows.size)
        assertEquals("Business", repository.findForVendor("Sample Fuel Station")?.category)
    }

    @Test
    fun forgettingAVendorRemovesEverySpelling() = runTest {
        repository.setMapping("Sample Fuel Station", "Transport", "Fuel")

        repository.removeMapping("Samplefillingstation")

        assertNull(repository.findForVendor("Sample Fuel Station"))
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun anUnrelatedVendorIsNotAffected() = runTest {
        repository.setMapping("Sample Traders", "Groceries", null)

        assertNull(repository.findForVendor("Other Traders"))
    }

    @Test
    fun rememberingAVendorRefilesAllItsExistingEntriesUnderAnySpelling() = runTest {
        dao.entries += FakeVendorEntry("Sample Fuel Station", "Miscellaneous")
        dao.entries += FakeVendorEntry("Samplefillingstation", "Shopping", "Books")
        dao.entries += FakeVendorEntry("Other Traders", "Groceries")

        val changed = repository.rememberAndRefile("SAMPLE FILLING STATION", "Transport", "Fuel")

        assertEquals(2, changed)
        assertEquals(
            listOf("Transport" to "Fuel", "Transport" to "Fuel", "Groceries" to null),
            dao.entries.map { it.category to it.subcategory }
        )
        assertEquals("Transport", repository.findForVendor("Sample Fuel Station")?.category)
    }

    @Test
    fun deletedEntriesAreLeftAlone() = runTest {
        dao.entries += FakeVendorEntry("Sample Traders", "Miscellaneous", deleted = true)

        assertEquals(0, repository.rememberAndRefile("Sample Traders", "Groceries", null))
        assertEquals("Miscellaneous", dao.entries.single().category)
    }

    @Test
    fun aVendorWithNoEntriesYetIsStillRemembered() = runTest {
        assertEquals(0, repository.rememberAndRefile("New Firm Traders", "Business", null))

        assertEquals("Business", repository.findForVendor("New Firm Traders")?.category)
    }

    @Test
    fun anOlderMappingWithoutAKeyStillMatchesItsExactName() = runTest {
        dao.rows += MerchantMappingEntity(merchantName = "Sample Traders", category = "Groceries")

        assertEquals("Groceries", repository.findForVendor("Sample Traders")?.category)
    }
}

data class FakeVendorEntry(
    val merchant: String,
    val category: String,
    val subcategory: String? = null,
    val deleted: Boolean = false
)

/** [MerchantMappingDao] over a list, matching the real queries. */
class FakeMerchantMappingDao : MerchantMappingDao {
    val rows = mutableListOf<MerchantMappingEntity>()
    var groups: List<VendorGroup> = emptyList()

    /** Spending entries as the refile queries see them. */
    val entries = mutableListOf<FakeVendorEntry>()

    override suspend fun vendorGroups() = groups

    override suspend fun vendorNames() = entries.filter { !it.deleted }.map { it.merchant }.distinct()

    override suspend fun refile(merchantNames: List<String>, category: String, subcategory: String?): Int {
        var changed = 0
        entries.forEachIndexed { index, e ->
            if (!e.deleted && e.merchant in merchantNames) {
                entries[index] = e.copy(category = category, subcategory = subcategory)
                changed++
            }
        }
        return changed
    }

    override suspend fun getCategoryForMerchant(merchantName: String) =
        rows.firstOrNull { it.merchantName == merchantName }?.category

    override suspend fun findForVendor(merchantName: String, matchKey: String) =
        rows.filter { it.merchantName == merchantName || (matchKey != "" && it.matchKey == matchKey) }
            .sortedWith(compareByDescending<MerchantMappingEntity> { it.merchantName == merchantName }.thenByDescending { it.updatedAt })
            .firstOrNull()

    override suspend fun deleteForVendor(merchantName: String, matchKey: String) {
        rows.removeAll { it.merchantName == merchantName || (matchKey != "" && it.matchKey == matchKey) }
    }

    override suspend fun insertOrUpdateMapping(mapping: MerchantMappingEntity) {
        rows.removeAll { it.merchantName == mapping.merchantName }
        rows += mapping
    }

    override suspend fun insertMapping(mapping: MerchantMappingEntity) = insertOrUpdateMapping(mapping)

    override suspend fun deleteMapping(merchantName: String) {
        rows.removeAll { it.merchantName == merchantName }
    }

    override fun getAllMappings(): Flow<List<MerchantMappingEntity>> = flowOf(rows.toList())
    override suspend fun getMappingCount() = rows.size
    override suspend fun deleteAllMappings() = rows.clear()
}
