package com.naxits.paisatracker.domain.model

import com.naxits.paisatracker.data.database.entity.AccountMetaEntity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountMetaTest {

    @Test
    fun tagsRoundTripThroughTheStoredString() {
        val tags = listOf("Savings", "Business", "Family")
        assertEquals(tags, AccountTags.parse(AccountTags.join(tags)))
    }

    @Test
    fun blankAndRepeatedTagsAreDropped() {
        assertEquals(listOf("FD", "RD"), AccountTags.normalize(listOf(" FD ", "", "fd", "RD", "  ")))
        assertEquals(emptyList(), AccountTags.parse(null))
        assertEquals(emptyList(), AccountTags.parse(""))
    }

    @Test
    fun aCommaInsideATagCannotSplitItInTwo() {
        assertEquals(listOf("Rent income"), AccountTags.parse(AccountTags.join(listOf("Rent,income"))))
    }

    @Test
    fun businessIsRecognisedIgnoringCaseAndIsOnlyATag() {
        assertTrue(AccountTags.hasBusiness(listOf("Savings", "business")))
        assertFalse(AccountTags.hasBusiness(listOf("Savings", "Current")))
        assertTrue(AccountMeta(tags = listOf(AccountTags.BUSINESS)).isBusiness)
        assertFalse(AccountMeta.NONE.isBusiness)
    }

    @Test
    fun presetsCoverTheTypesAskedFor() {
        assertEquals(
            listOf("Savings", "Current", "FD", "RD", "Business"),
            AccountTags.PRESETS
        )
    }

    @Test
    fun emptyMetaIsEmptyAndAnyFieldMakesItNot() {
        assertTrue(AccountMeta.NONE.isEmpty)
        assertFalse(AccountMeta(customIconName = "ic_brand_test").isEmpty)
        assertFalse(AccountMeta(tags = listOf("FD")).isEmpty)
        assertFalse(AccountMeta(description = "Household").isEmpty)
        assertFalse(AccountMeta(showOnHome = true).isEmpty)
        assertTrue(AccountMeta(description = "   ").let { it.description.isBlank() && it.tags.isEmpty() })
    }

    @Test
    fun entityMapsToDomainWithABlankIconTreatedAsNoOverride() {
        val meta = AccountMetaEntity(
            bankName = "Test Bank",
            accountLast4 = "1234",
            customIconName = "",
            tags = "Savings,Business",
            description = "Salary"
        ).toDomain()
        assertNull(meta.customIconName)
        assertEquals(listOf("Savings", "Business"), meta.tags)
        assertEquals("Salary", meta.description)
    }
}
