package com.naxits.paisatracker.data.backup

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.naxits.paisatracker.data.database.entity.PartyEntity
import com.naxits.paisatracker.data.database.entity.PartyKind
import com.naxits.paisatracker.data.database.entity.ReviewKind
import com.naxits.paisatracker.data.database.entity.ReviewResolvedBy
import com.naxits.paisatracker.data.database.entity.ReviewStatus
import com.naxits.paisatracker.data.database.entity.TransactionEntity
import com.naxits.paisatracker.data.database.entity.TransactionReviewEntity
import com.naxits.paisatracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupPartiesTest {

    // Same adapters the exporter and importer use
    private val gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
        .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
        .registerTypeAdapter(BigDecimal::class.java, BigDecimalTypeAdapter())
        .create()

    @Test
    fun partiesSurviveAnExportAndImport() {
        val parties = listOf(
            PartyEntity(
                id = 3, name = "Acme Traders", kind = PartyKind.VENDOR, phone = "7000000001",
                identifiers = "upi:client@testbank\nacct:5678", aliases = "ACME TRD", notes = "Net 30",
                isArchived = true, createdAt = LocalDateTime.of(2026, 1, 2, 3, 4, 5)
            )
        )

        val restored: List<PartyEntity> =
            gson.fromJson(gson.toJson(parties), object : TypeToken<List<PartyEntity>>() {}.type)

        assertEquals(parties, restored)
    }

    @Test
    fun reviewsKeepTheirWholeHistory() {
        val reviews = listOf(
            TransactionReviewEntity(
                id = 9, transactionId = 4, kind = ReviewKind.CATEGORY, suggestedValue = "Salary",
                suggestedSubcategory = "Monthly", suggestedPartyId = 3, reason = "3 earlier payments",
                confidence = 88, status = ReviewStatus.AUTO_CONFIRMED,
                createdAt = LocalDateTime.of(2025, 11, 1, 9, 0),
                resolvedAt = LocalDateTime.of(2026, 2, 1, 9, 0),
                resolvedBy = ReviewResolvedBy.AUTO_THREE_MONTHS, resolvedValue = "Salary"
            )
        )

        val restored: List<TransactionReviewEntity> =
            gson.fromJson(gson.toJson(reviews), object : TypeToken<List<TransactionReviewEntity>>() {}.type)

        assertEquals(reviews, restored)
    }

    @Test
    fun theCustomerOfATransactionSurvivesTheBackup() {
        val tx = TransactionEntity(
            id = 1, amount = BigDecimal("10.00"), merchantName = "Test Shop", category = "Misc",
            transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 1, 1, 0, 0),
            transactionHash = "hash-1", partyId = 3
        )

        val restored = gson.fromJson(gson.toJson(tx), TransactionEntity::class.java)

        assertEquals(3L, restored.partyId)
        assertEquals(3L, restored.sanitize().partyId)
    }

    @Test
    fun aBackupMadeBeforeCustomersExistedImportsAsNoParties() {
        val snapshot = gson.fromJson("{}", DatabaseSnapshot::class.java)

        assertTrue(snapshot.parties.orEmpty().isEmpty())
        assertTrue(snapshot.transactionReviews.orEmpty().isEmpty())
    }

    @Test
    fun theSnapshotUsesTheDocumentedKeys() {
        fun key(field: String) =
            DatabaseSnapshot::class.java.getDeclaredField(field).getAnnotation(SerializedName::class.java)?.value
        assertEquals("parties", key("parties"))
        assertEquals("transaction_reviews", key("transactionReviews"))
    }

    @Test
    fun learnedCategoriesSurviveAnExportAndImport() {
        val patterns = listOf(
            com.naxits.paisatracker.data.database.entity.CategoryPatternEntity(
                patternKey = "party:3", type = "INCOME", category = "Salary", subcategory = "Monthly", count = 4,
                lastAmount = BigDecimal("50000.00"), lastDate = LocalDateTime.of(2026, 3, 1, 9, 0),
                updatedAt = LocalDateTime.of(2026, 3, 2, 10, 0)
            )
        )

        val restored: List<com.naxits.paisatracker.data.database.entity.CategoryPatternEntity> =
            gson.fromJson(
                gson.toJson(patterns),
                object : TypeToken<List<com.naxits.paisatracker.data.database.entity.CategoryPatternEntity>>() {}.type
            )

        assertEquals(patterns, restored)
    }

    @Test
    fun aBackupMadeBeforeSuggestionsExistedImportsAsNoPatterns() {
        val snapshot = gson.fromJson("{}", DatabaseSnapshot::class.java)

        assertTrue(snapshot.categoryPatterns.orEmpty().isEmpty())
        assertEquals(
            "category_patterns",
            DatabaseSnapshot::class.java.getDeclaredField("categoryPatterns")
                .getAnnotation(SerializedName::class.java)?.value
        )
    }
}
