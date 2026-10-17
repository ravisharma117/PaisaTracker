package com.naxits.paisatracker.domain.service

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CounterpartyExtractorTest {

    @Test
    fun aUpiAddressInTheMessageIsFoundAndLowercased() {
        val cp = CounterpartyExtractor.extract(
            "Rs.500.00 debited from A/c XXXX1234 and credited to Client.Test@OkBank on 01-02-26. UPI Ref 712345678901",
            "Client Test",
            "1234"
        )
        assertEquals("client.test@okbank", cp.upiId)
        assertEquals("Client Test", cp.name)
    }

    @Test
    fun anEmailAddressIsNotTakenForAUpiId() {
        val cp = CounterpartyExtractor.extract("Contact support@testbank.com for help", "Test Shop", "1234")
        assertNull(cp.upiId)
    }

    @Test
    fun aMobileNumberBeforeTheAtSignIsThePhone() {
        val cp = CounterpartyExtractor.extract("Paid to 7000000001@ybl via UPI", "7000000001@ybl", "1234")
        assertEquals("7000000001", cp.phone)
        assertEquals("7000000001@ybl", cp.upiId)
    }

    @Test
    fun aStandaloneMobileNumberInTheMessageIsFound() {
        val cp = CounterpartyExtractor.extract("Sent to 7000000002 on 01-02-26", "Someone", "1234")
        assertEquals("7000000002", cp.phone)
    }

    @Test
    fun aLongReferenceNumberIsNotTakenForAPhone() {
        val cp = CounterpartyExtractor.extract("UPI Ref no 712345678901 completed", "Test Shop", "1234")
        assertNull(cp.phone)
    }

    @Test
    fun anAmountIsNotTakenForAPhone() {
        val cp = CounterpartyExtractor.extract("Rs.7000000001.00 debited", "Test Shop", "1234")
        assertNull(cp.phone)
    }

    @Test
    fun theOtherSidesAccountIsFound() {
        val cp = CounterpartyExtractor.extract("Transferred to A/c XXXX5678 on 01-02-26", "Test Shop", "1234")
        assertEquals("5678", cp.account)
    }

    @Test
    fun theUsersOwnAccountIsNeverTheOtherSide() {
        val cp = CounterpartyExtractor.extract("Amount debited from A/c XXXX1234 on 01-02-26", "Test Shop", "1234")
        assertNull(cp.account)
    }

    @Test
    fun anUnknownMerchantGivesNoName() {
        val cp = CounterpartyExtractor.extract("Rs.10 debited", "Unknown Merchant", "1234")
        assertNull(cp.name)
        assertTrue(cp.isEmpty)
    }

    @Test
    fun nothingInTheTextGivesAnEmptyCounterparty() {
        assertTrue(CounterpartyExtractor.extract(null, null, null).isEmpty)
    }
}
