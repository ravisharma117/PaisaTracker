package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.dao.CategoryPatternDao
import com.naxits.paisatracker.data.database.dao.HistoryRow
import com.naxits.paisatracker.data.database.entity.CategoryPatternEntity
import com.naxits.paisatracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime

/** An earlier transaction as the history queries see it. */
data class FakeHistoryTx(
    val id: Long,
    val type: TransactionType,
    val dateTime: LocalDateTime,
    val amount: BigDecimal,
    val merchant: String,
    val description: String? = null,
    val partyId: Long? = null,
    val smsBody: String? = null,
    val deleted: Boolean = false
)

/** [CategoryPatternDao] over lists, with the three history queries and `setCategory` working on [transactions]. */
class FakePatternDao : CategoryPatternDao {
    val patterns = mutableListOf<CategoryPatternEntity>()
    val transactions = mutableListOf<FakeHistoryTx>()

    /** The categories written by [setCategory], by transaction id. */
    val categoryWrites = mutableMapOf<Long, Pair<String, String?>>()

    override suspend fun get(key: String, type: String) =
        patterns.firstOrNull { it.patternKey == key && it.type == type }

    override suspend fun upsert(pattern: CategoryPatternEntity) {
        patterns.removeAll { it.patternKey == pattern.patternKey && it.type == pattern.type }
        patterns += pattern
    }

    override suspend fun upsertAll(patterns: List<CategoryPatternEntity>) = patterns.forEach { upsert(it) }
    override suspend fun getAll() = patterns.toList()
    override suspend fun deleteAll() {
        patterns.clear()
    }

    override suspend fun setCategory(transactionId: Long, category: String, subcategory: String?) {
        categoryWrites[transactionId] = category to subcategory
    }

    private fun rows(
        type: TransactionType, excluding: Long, before: LocalDateTime, limit: Int, match: (FakeHistoryTx) -> Boolean
    ) = transactions
        .filter { it.type == type && !it.deleted && it.id != excluding && it.dateTime.isBefore(before) && match(it) }
        .sortedByDescending { it.dateTime }
        .take(limit)
        .map { HistoryRow(it.id, it.dateTime, it.amount, it.merchant, it.description) }

    override suspend fun historyForParty(
        partyId: Long, type: TransactionType, excludingTransactionId: Long, before: LocalDateTime, limit: Int
    ) = rows(type, excludingTransactionId, before, limit) { it.partyId == partyId }

    override suspend fun historyForMerchant(
        merchant: String, type: TransactionType, excludingTransactionId: Long, before: LocalDateTime, limit: Int
    ) = rows(type, excludingTransactionId, before, limit) { it.merchant.equals(merchant, ignoreCase = true) }

    override suspend fun historyForMessageText(
        pattern: String, type: TransactionType, excludingTransactionId: Long, before: LocalDateTime, limit: Int
    ): List<HistoryRow> {
        // Undo the escaping the real query's LIKE ... ESCAPE '\' expects
        val text = pattern.trim('%').replace("\\%", "%").replace("\\_", "_").replace("\\\\", "\\")
        return rows(type, excludingTransactionId, before, limit) { it.smsBody?.contains(text) == true }
    }
}
