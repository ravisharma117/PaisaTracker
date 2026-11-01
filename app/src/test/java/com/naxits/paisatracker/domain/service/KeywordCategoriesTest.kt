package com.naxits.paisatracker.domain.service

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeywordCategoriesTest {

    private fun assertMatch(name: String, category: String, subcategory: String?, description: String? = null) {
        val found = KeywordCategories.match(name, description)
        assertEquals(category to subcategory, found?.let { it.category to it.subcategory }, "for \"$name\"")
    }

    @Test
    fun aPizzaShopIsFoodWithThePizzaSubcategory() = assertMatch("Sample Pizza Corner", "Food & Drinks", "Pizza")

    @Test
    fun aFillingStationIsFuelHoweverItIsSpelled() {
        assertMatch("Sample Filling Station", "Transport", "Fuel")
        assertMatch("SAMPLE FUEL STATION", "Transport", "Fuel")
        assertMatch("Samplefillingstation", "Transport", "Fuel")
    }

    @Test
    fun electronicsAndCommunicationShopsAreShoppingElectronics() {
        assertMatch("Sample Electronics", "Shopping", "Electronics")
        assertMatch("Sample Communication", "Shopping", "Electronics")
    }

    @Test
    fun clothesAndShoesGoToTheirOwnSubcategories() {
        assertMatch("Sample Garments", "Shopping", "Clothes")
        assertMatch("Sample Footwear", "Shopping", "Footwear")
    }

    @Test
    fun placesThatSellFoodAtHomeAreGroceries() {
        assertMatch("Sample General Store", "Groceries", null)
        assertMatch("Sample Dairy", "Groceries", "Dairy")
    }

    @Test
    fun charitiesAndTemplesAreDonations() {
        assertMatch("Sample Charitable Trust", "Donation", null)
        assertMatch("Sample Mandir Samiti", "Donation", null)
    }

    @Test
    fun aBankOrFinanceTrustIsNotADonation() {
        assertNull(KeywordCategories.match("Sample Trust Bank"))
        assertNull(KeywordCategories.match("Sample Trust Finance"))
    }

    @Test
    fun healthInsuranceIsInsuranceNotMedical() = assertMatch("Sample Health Insurance", "Insurance", null)

    @Test
    fun pharmaciesAndHospitalsAreMedical() {
        assertMatch("Sample Pharmacy", "Medical", "Medicines")
        assertMatch("Sample Hospital", "Medical", "Hospital")
    }

    @Test
    fun aWordInsideALongerWordDoesNotMatch() {
        // "cab" inside "cabinet", "gym" inside "gymnast", "vet" inside "velvet"
        assertNull(KeywordCategories.match("Sample Cabinet Works"))
        assertNull(KeywordCategories.match("Sample Gymnast"))
        assertNull(KeywordCategories.match("Sample Velvet"))
    }

    @Test
    fun theDescriptionIsReadToo() {
        assertMatch("Sample Person", "Food & Drinks", "Pizza", description = "pizza for me")
    }

    @Test
    fun theMatchedWordIsReported() {
        assertEquals("filling station", KeywordCategories.match("Sample Filling Station")?.keyword)
    }

    @Test
    fun aNameWithNoKnownWordMatchesNothing() {
        assertNull(KeywordCategories.match("Sample Person"))
        assertNull(KeywordCategories.match(""))
    }

    @Test
    fun theMoreSpecificRuleWins() {
        // A medical store that also has "store" in the name is medicines, not groceries
        assertMatch("Sample Medical Store", "Medical", "Medicines")
    }
}
