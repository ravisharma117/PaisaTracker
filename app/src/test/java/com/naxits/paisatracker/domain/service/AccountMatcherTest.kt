package com.naxits.paisatracker.domain.service

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountMatcherTest {

    @Test
    fun aMessageNamingTheBankMatchesAnAccountTheUserCalledSomethingLonger() {
        assertEquals("HDFC Bank Salary", AccountMatcher.resolve("HDFC Bank", listOf("HDFC Bank Salary")))
    }

    @Test
    fun anExactNameAlwaysWins() {
        assertEquals("HDFC Bank", AccountMatcher.resolve("HDFC Bank", listOf("HDFC Bank Salary", "HDFC Bank")))
        assertEquals("HDFC Bank", AccountMatcher.resolve("hdfc bank", listOf("HDFC Bank")))
    }

    @Test
    fun twoRelatedAccountsWithTheSameDigitsAreTooUnclearToChoose() {
        assertNull(AccountMatcher.resolve("HDFC Bank", listOf("HDFC Bank Salary", "HDFC Bank Savings")))
    }

    @Test
    fun anUnrelatedBankWithTheSameDigitsIsNotMatched() {
        assertNull(AccountMatcher.resolve("HDFC Bank", listOf("State Bank of India")))
        assertNull(AccountMatcher.resolve("State Bank of India", listOf("HDFC Bank Salary")))
    }

    @Test
    fun noAccountMeansNoMatch() {
        assertNull(AccountMatcher.resolve("HDFC Bank", emptyList()))
    }

    @Test
    fun relatedIgnoresCaseSpacesAndPunctuation() {
        assertTrue(AccountMatcher.related("HDFC Bank", "hdfc-bank (salary)"))
        assertTrue(AccountMatcher.related("Punjab & Sind Bank", "Punjab and Sind Bank".replace("and", "&")))
    }

    @Test
    fun veryShortNamesAreNeverRelatedByAccident() {
        assertFalse(AccountMatcher.related("SBI", "SBI Savings Plus"))
        assertFalse(AccountMatcher.related("", "HDFC Bank"))
    }
}
