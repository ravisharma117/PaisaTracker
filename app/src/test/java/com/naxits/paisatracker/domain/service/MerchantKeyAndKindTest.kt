package com.naxits.paisatracker.domain.service

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MerchantKeyTest {

    @Test
    fun spellingVariantsOfOneVendorShareAKey() {
        val keys = listOf("Sample Fuel Station", "SAMPLE FILLING STATION", "Samplefillingstation", "Sample Petrol Pump")
            .map { MerchantKey.normalize(it) }
        assertEquals(1, keys.toSet().size, keys.toString())
    }

    @Test
    fun legalSuffixesAndPunctuationAreIgnored() {
        assertEquals(MerchantKey.normalize("Sample Traders Pvt. Ltd."), MerchantKey.normalize("sample traders"))
    }

    @Test
    fun aUpiHandleKeepsOnlyTheVendorPart() {
        assertEquals(MerchantKey.normalize("samplevendor"), MerchantKey.normalize("samplevendor123@testbank"))
    }

    @Test
    fun differentVendorsDoNotShareAKey() {
        assertNotEquals(MerchantKey.normalize("Sample Traders"), MerchantKey.normalize("Other Traders"))
    }
}

class MerchantKindTest {

    // The brand list lives in the UI layer; these tests give it a fixed answer
    private fun business(name: String, brand: Boolean = false) = MerchantKind.isBusiness(name) { brand }

    @Test
    fun namesWithABusinessWordAreBusinesses() {
        assertTrue(business("Sample Traders"))
        assertTrue(business("Sample Filling Station"))
        assertTrue(business("Sample Enterprises"))
        assertTrue(business("Sample Technologies Pvt Ltd"))
    }

    @Test
    fun aKnownBrandIsABusinessEvenWithoutAMarkerWord() {
        assertTrue(business("Samplebrand", brand = true))
    }

    @Test
    fun aKeywordNameIsABusiness() {
        assertTrue(business("Sample Pizza"))
    }

    @Test
    fun plainPersonalNamesArePeople() {
        assertFalse(business("Sample Person"))
        assertFalse(business("Sample Middle Person"))
    }

    @Test
    fun honorificsAndRelationFormsArePeople() {
        assertFalse(business("Mr Sample Person"))
        assertFalse(business("Smt Sample Person"))
        assertFalse(business("Sample Person S/o Other Person"))
    }

    @Test
    fun aUpiHandleIsAPerson() {
        assertFalse(business("sampleperson123@testbank"))
    }

    @Test
    fun placeholdersAreNeverRemembered() {
        assertFalse(business("Unknown Merchant"))
        assertFalse(business("Transferred to Mr"))
        assertFalse(business(""))
    }

    @Test
    fun anUnclearNameCountsAsAPerson() {
        assertFalse(business("Xyzzy"))
    }
}
