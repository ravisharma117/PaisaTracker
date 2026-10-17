package com.naxits.paisatracker.data.repository

import com.naxits.paisatracker.data.database.dao.PartyDao
import com.naxits.paisatracker.data.database.entity.PartyEntity
import com.naxits.paisatracker.data.database.entity.PartyKind
import com.naxits.paisatracker.domain.model.Party
import com.naxits.paisatracker.domain.model.PartyText
import com.naxits.paisatracker.domain.model.toDomain
import com.naxits.paisatracker.domain.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PartyRepository @Inject constructor(
    private val partyDao: PartyDao
) {
    fun observeActive(): Flow<List<Party>> = partyDao.observeActive().map { rows -> rows.map { it.toDomain() } }

    suspend fun getById(id: Long): Party? = partyDao.getById(id)?.toDomain()

    /** Active parties whose name or alias contains [query], for suggestions while typing. */
    suspend fun search(query: String, limit: Int = 6): List<Party> {
        // % and _ are wildcards in LIKE; typed text must match literally
        val text = query.trim().replace("%", "").replace("_", "")
        if (text.isEmpty()) return partyDao.getActive().take(limit).map { it.toDomain() }
        return partyDao.search("%$text%", limit).map { it.toDomain() }
    }

    /** The active party called [name] (or having it as an alias), or a new one. */
    suspend fun findOrCreate(name: String, kind: String = PartyKind.CUSTOMER): Party {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "A customer or vendor needs a name" }
        partyDao.getActive().map { it.toDomain() }.firstOrNull { party ->
            party.name.equals(clean, ignoreCase = true) || party.aliases.any { it.equals(clean, ignoreCase = true) }
        }?.let { return it }
        val created = PartyEntity(name = clean, kind = kind)
        return created.copy(id = partyDao.insert(created)).toDomain()
    }

    suspend fun update(party: Party) {
        val existing = partyDao.getById(party.id) ?: return
        partyDao.update(party.toEntity(createdAt = existing.createdAt))
    }

    suspend fun archive(id: Long) {
        val existing = partyDao.getById(id) ?: return
        partyDao.update(existing.copy(isArchived = true))
    }

    /**
     * Folds [fromId] into [toId]: its transactions, name, phone, identifiers and aliases move
     * across, and it is archived.
     */
    suspend fun merge(fromId: Long, toId: Long) {
        if (fromId == toId) return
        val from = partyDao.getById(fromId)?.toDomain() ?: return
        val to = partyDao.getById(toId) ?: return
        partyDao.moveTransactions(fromId, toId)
        val target = to.toDomain()
        val merged = target.copy(
            phone = target.phone ?: from.phone,
            identifiers = (target.identifiers + from.identifiers).distinct(),
            aliases = PartyText.lines(PartyText.join(target.aliases + from.aliases + from.name))
                .filterNot { it.equals(target.name, ignoreCase = true) }
        )
        partyDao.update(merged.toEntity(createdAt = to.createdAt))
        archive(fromId)
    }

    suspend fun setPartyForTransaction(transactionId: Long, partyId: Long?) =
        partyDao.setPartyForTransaction(transactionId, partyId)

    suspend fun transactionCount(partyId: Long): Int = partyDao.transactionCount(partyId)

    suspend fun getAllEntities(): List<PartyEntity> = partyDao.getAll()

    suspend fun deleteAll() = partyDao.deleteAll()
}
