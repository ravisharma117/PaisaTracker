package com.naxits.paisatracker.data.repository

import com.naxits.paisatracker.data.database.dao.AccountMetaDao
import com.naxits.paisatracker.data.database.entity.AccountMetaEntity
import com.naxits.paisatracker.domain.model.AccountMeta
import com.naxits.paisatracker.domain.model.AccountOrdering
import com.naxits.paisatracker.domain.model.AccountTags
import com.naxits.paisatracker.domain.model.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountMetaRepository @Inject constructor(
    private val accountMetaDao: AccountMetaDao
) {
    /** Every account that has notes, keyed like [AccountOrdering.keyOf]. */
    fun observeAll(): Flow<Map<String, AccountMeta>> = accountMetaDao.observeAll().map { rows ->
        rows.associate { AccountOrdering.keyOf(it.bankName, it.accountLast4) to it.toDomain() }
    }

    suspend fun get(bankName: String, accountLast4: String): AccountMeta =
        accountMetaDao.get(bankName, accountLast4)?.toDomain() ?: AccountMeta.NONE

    /** Stores [meta]. An account with nothing set keeps no row. */
    suspend fun save(bankName: String, accountLast4: String, meta: AccountMeta, now: LocalDateTime = LocalDateTime.now()) {
        val clean = meta.copy(
            customIconName = meta.customIconName?.takeIf { it.isNotBlank() },
            tags = AccountTags.normalize(meta.tags),
            description = meta.description.trim()
        )
        if (clean.isEmpty) {
            accountMetaDao.delete(bankName, accountLast4)
            return
        }
        accountMetaDao.upsert(
            AccountMetaEntity(
                bankName = bankName,
                accountLast4 = accountLast4,
                customIconName = clean.customIconName,
                tags = AccountTags.join(clean.tags),
                description = clean.description,
                showOnHome = clean.showOnHome,
                updatedAt = now
            )
        )
    }

    suspend fun delete(bankName: String, accountLast4: String) = accountMetaDao.delete(bankName, accountLast4)

    suspend fun getAll(): List<AccountMetaEntity> = accountMetaDao.getAll()

    suspend fun deleteAll() = accountMetaDao.deleteAll()

    suspend fun replaceAll(rows: List<AccountMetaEntity>) {
        accountMetaDao.deleteAll()
        accountMetaDao.upsertAll(rows)
    }

    suspend fun upsertAll(rows: List<AccountMetaEntity>) = accountMetaDao.upsertAll(rows)
}
