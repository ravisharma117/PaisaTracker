package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.dao.PartyDao
import com.naxits.paisatracker.data.database.dao.TransactionReviewDao
import com.naxits.paisatracker.data.database.entity.PartyEntity
import com.naxits.paisatracker.data.database.entity.TransactionReviewEntity
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** [PartyDao] over lists. The transaction columns it touches (`party_id`, merchant, date) are kept in maps. */
class FakePartyDao(
    private val pendingTransactionIds: () -> Set<Long> = { emptySet() }
) : PartyDao {
    val parties = MutableStateFlow<List<PartyEntity>>(emptyList())
    private var nextId = 1L

    val transactionParty = mutableMapOf<Long, Long?>()
    val transactionMerchant = mutableMapOf<Long, String>()
    val transactionDate = mutableMapOf<Long, LocalDateTime>()

    /** Records an earlier transaction the way the real table would hold it. */
    fun addTransaction(id: Long, merchant: String, partyId: Long?, date: LocalDateTime) {
        transactionParty[id] = partyId
        transactionMerchant[id] = merchant
        transactionDate[id] = date
    }

    override fun observeActive(): Flow<List<PartyEntity>> =
        parties.map { rows -> rows.filterNot { it.isArchived }.sortedBy { it.name.lowercase() } }

    override suspend fun getActive() = parties.value.filterNot { it.isArchived }
    override suspend fun getAll() = parties.value
    override suspend fun getById(id: Long) = parties.value.firstOrNull { it.id == id }

    override suspend fun search(pattern: String, limit: Int): List<PartyEntity> {
        val text = pattern.trim('%').lowercase()
        return getActive()
            .filter { it.name.lowercase().contains(text) || it.aliases.lowercase().contains(text) }
            .sortedBy { it.name.lowercase() }
            .take(limit)
    }

    override suspend fun insert(party: PartyEntity): Long {
        val id = if (party.id != 0L) party.id else nextId++
        parties.value = parties.value + party.copy(id = id)
        return id
    }

    override suspend fun upsertAll(parties: List<PartyEntity>) {
        parties.forEach { p -> this.parties.value = this.parties.value.filterNot { it.id == p.id } + p }
    }

    override suspend fun update(party: PartyEntity) {
        parties.value = parties.value.map { if (it.id == party.id) party else it }
    }

    override suspend fun deleteAll() {
        parties.value = emptyList()
    }

    override suspend fun setPartyForTransaction(transactionId: Long, partyId: Long?) {
        transactionParty[transactionId] = partyId
    }

    override suspend fun moveTransactions(fromId: Long, toId: Long) {
        transactionParty.keys.toList().forEach { if (transactionParty[it] == fromId) transactionParty[it] = toId }
    }

    override suspend fun transactionCount(partyId: Long) = transactionParty.values.count { it == partyId }

    override suspend fun latestSettledPartyForMerchant(merchant: String, excludingTransactionId: Long): Long? {
        val pending = pendingTransactionIds()
        return transactionMerchant
            .filter { (id, m) -> m == merchant && id != excludingTransactionId && transactionParty[id] != null && id !in pending }
            .keys.maxByOrNull { transactionDate.getValue(it) }
            ?.let { transactionParty[it] }
    }
}

class FakeReviewDao : TransactionReviewDao {
    val reviews = MutableStateFlow<List<TransactionReviewEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(review: TransactionReviewEntity): Long {
        if (reviews.value.any { it.transactionId == review.transactionId && it.kind == review.kind }) return -1
        val id = if (review.id != 0L) review.id else nextId++
        reviews.value = reviews.value + review.copy(id = id)
        return id
    }

    override suspend fun upsertAll(reviews: List<TransactionReviewEntity>) {
        reviews.forEach { r -> this.reviews.value = this.reviews.value.filterNot { it.id == r.id } + r }
    }

    override suspend fun update(review: TransactionReviewEntity) {
        reviews.value = reviews.value.map { if (it.id == review.id) review else it }
    }

    override suspend fun getById(id: Long) = reviews.value.firstOrNull { it.id == id }
    override suspend fun get(transactionId: Long, kind: String) =
        reviews.value.firstOrNull { it.transactionId == transactionId && it.kind == kind }

    override fun observeForTransaction(transactionId: Long): Flow<List<TransactionReviewEntity>> =
        reviews.map { rows -> rows.filter { it.transactionId == transactionId } }

    override suspend fun getForTransaction(transactionId: Long) =
        reviews.value.filter { it.transactionId == transactionId }

    override fun observePendingTransactionIds(): Flow<List<Long>> =
        reviews.map { rows -> rows.filter { it.isPending }.map { it.transactionId }.distinct() }

    override suspend fun getPending() = reviews.value.filter { it.isPending }

    override suspend fun getPendingCreatedOnOrBefore(cutoff: LocalDateTime) =
        reviews.value.filter { it.isPending && !it.createdAt.isAfter(cutoff) }

    override suspend fun getAll() = reviews.value

    override suspend fun deleteAll() {
        reviews.value = emptyList()
    }

    fun pendingTransactionIds(): Set<Long> = reviews.value.filter { it.isPending }.map { it.transactionId }.toSet()
}
