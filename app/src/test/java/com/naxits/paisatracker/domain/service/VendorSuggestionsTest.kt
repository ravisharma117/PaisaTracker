package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.dao.VendorGroup
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VendorSuggestionsTest {

    private fun group(name: String, category: String, sub: String? = null, n: Int) = VendorGroup(name, category, sub, n)

    private fun build(
        groups: List<VendorGroup>,
        remembered: Set<String> = emptySet(),
        ignored: Set<String> = emptySet(),
        business: (String) -> Boolean = { true }
    ) = VendorSuggestions.build(groups, remembered, ignored, business)

    @Test
    fun aBusinessWithSeveralCategorisedEntriesIsSuggested() {
        val result = build(listOf(group("Sample Traders", "Groceries", n = 3)))

        assertEquals(listOf(VendorSuggestion("Sample Traders", "Groceries", null, 3)), result)
    }

    @Test
    fun aSingleEntryIsNotEnough() {
        assertTrue(build(listOf(group("Sample Traders", "Groceries", n = 1))).isEmpty())
    }

    @Test
    fun spellingsOfOneVendorAreCountedTogetherAndTheCommonestIsShown() {
        val result = build(
            listOf(
                group("Sample Fuel Station", "Transport", "Fuel", 1),
                group("Samplefillingstation", "Transport", "Fuel", 2)
            )
        )

        assertEquals(1, result.size)
        assertEquals("Samplefillingstation", result.single().merchantName)
        assertEquals(3, result.single().entryCount)
    }

    @Test
    fun theCategoryMostEntriesAreInIsOffered() {
        val result = build(
            listOf(
                group("Sample Traders", "Groceries", "Staples", 4),
                group("Sample Traders", "Shopping", null, 1)
            )
        )

        assertEquals("Groceries", result.single().category)
        assertEquals("Staples", result.single().subcategory)
    }

    @Test
    fun aVendorAlreadyRememberedIsNotSuggested() {
        val key = MerchantKey.normalize("Sample Traders")
        assertTrue(build(listOf(group("Sample Traders", "Groceries", n = 3)), remembered = setOf(key)).isEmpty())
    }

    @Test
    fun aVendorTheUserRemovedIsNotSuggestedAgain() {
        val key = MerchantKey.normalize("Sample Traders")
        assertTrue(build(listOf(group("Sample Traders", "Groceries", n = 3)), ignored = setOf(key)).isEmpty())
    }

    @Test
    fun aPersonIsNeverSuggested() {
        assertTrue(build(listOf(group("Sample Person", "Groceries", n = 5)), business = { false }).isEmpty())
    }

    @Test
    fun theMostFrequentVendorsComeFirst() {
        val result = build(
            listOf(
                group("Sample Traders", "Groceries", n = 2),
                group("Other Store", "Shopping", n = 6)
            )
        )

        assertEquals(listOf("Other Store", "Sample Traders"), result.map { it.merchantName })
    }
}
