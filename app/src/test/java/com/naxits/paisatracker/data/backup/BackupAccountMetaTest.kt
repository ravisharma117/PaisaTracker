package com.naxits.paisatracker.data.backup

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.naxits.paisatracker.data.database.entity.AccountMetaEntity
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupAccountMetaTest {

    // Same adapters the exporter and importer use
    private val gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
        .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
        .registerTypeAdapter(BigDecimal::class.java, BigDecimalTypeAdapter())
        .create()

    @Test
    fun accountNotesSurviveAnExportAndImport() {
        val notes = listOf(
            AccountMetaEntity(
                bankName = "Test Bank",
                accountLast4 = "1234",
                customIconName = "ic_brand_test",
                tags = "Savings,Business",
                description = "Household expenses",
                updatedAt = LocalDateTime.of(2026, 3, 4, 5, 6, 7)
            ),
            AccountMetaEntity(bankName = "Other Bank", accountLast4 = "5678", tags = "FD")
        )

        val json = gson.toJson(notes)
        val restored: List<AccountMetaEntity> =
            gson.fromJson(json, object : TypeToken<List<AccountMetaEntity>>() {}.type)

        assertEquals(notes[0], restored[0])
        assertEquals("FD", restored[1].tags)
        assertEquals(null, restored[1].customIconName)
    }

    @Test
    fun aBackupMadeBeforeAccountNotesExistedImportsAsNoNotes() {
        // Such a backup has no "account_meta" key at all
        val snapshot = gson.fromJson("{}", DatabaseSnapshot::class.java)

        assertTrue(snapshot.accountMeta.orEmpty().isEmpty())
    }

    @Test
    fun theSnapshotUsesTheDocumentedKey() {
        val field = DatabaseSnapshot::class.java.getDeclaredField("accountMeta")
        val name = field.getAnnotation(com.google.gson.annotations.SerializedName::class.java)?.value
        assertEquals("account_meta", name)
    }
}
