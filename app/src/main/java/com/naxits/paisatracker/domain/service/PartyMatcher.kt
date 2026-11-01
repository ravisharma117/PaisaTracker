package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.domain.model.Party
import com.naxits.paisatracker.domain.model.PartyIdentifier

enum class MatchSource { PHONE, UPI_ID, ACCOUNT, HISTORY, NAME, NONE }

/**
 * The outcome of looking for the customer or vendor behind a transaction. [partyId] is null when
 * no party matched reliably: the transaction then stays Unknown instead of guessing.
 */
data class PartyMatch(
    val partyId: Long?,
    val partyName: String?,
    val confidence: Int,
    /** In plain words, how it was worked out. */
    val reason: String,
    val source: MatchSource
) {
    val isUnknown: Boolean get() = partyId == null
}

object PartyMatcher {
    /** Below this a match is not reliable enough to suggest. */
    const val RELIABLE = 80

    private const val PHONE_OR_UPI = 95
    private const val ACCOUNT_NUMBER = 90
    private const val HISTORY = 85

    const val NO_MATCH_REASON = "No known customer or vendor matches"
    const val AMBIGUOUS_REASON = "More than one customer or vendor fits equally well"

    private class Candidate(val party: Party, val confidence: Int, val reason: String, val source: MatchSource)

    /**
     * @param historyPartyId the party of an earlier, settled transaction with the same merchant
     */
    fun match(
        counterparty: Counterparty,
        merchantName: String,
        parties: List<Party>,
        historyPartyId: Long? = null
    ): PartyMatch {
        val best = HashMap<Long, Candidate>()
        fun offer(candidate: Candidate) {
            val current = best[candidate.party.id]
            if (current == null || candidate.confidence > current.confidence) best[candidate.party.id] = candidate
        }

        val nameToCompare = counterparty.name
            ?: merchantName.takeIf { it.isNotBlank() && !it.startsWith("Unknown", ignoreCase = true) }

        for (party in parties) {
            if (party.isArchived) continue

            if (counterparty.phone != null && PhoneNumbers.same(party.phone, counterparty.phone)) {
                offer(Candidate(party, PHONE_OR_UPI, "Matches the phone number ending ${counterparty.phone.takeLast(4)}", MatchSource.PHONE))
            }
            if (counterparty.upiId != null &&
                party.identifiers.any { it is PartyIdentifier.Upi && it.value.equals(counterparty.upiId, ignoreCase = true) }
            ) {
                offer(Candidate(party, PHONE_OR_UPI, "Matches the UPI id saved for ${party.name}", MatchSource.UPI_ID))
            }
            if (counterparty.account != null &&
                party.identifiers.any { it is PartyIdentifier.Account && it.value == counterparty.account }
            ) {
                offer(Candidate(party, ACCOUNT_NUMBER, "Matches the known account ending ${counterparty.account.takeLast(4)}", MatchSource.ACCOUNT))
            }
            if (nameToCompare != null) {
                val score = NameMatcher.bestScore(nameToCompare, listOf(party.name) + party.aliases)
                if (score >= RELIABLE) {
                    offer(Candidate(party, score, "The name \"$nameToCompare\" looks like ${party.name}", MatchSource.NAME))
                }
            }
            if (historyPartyId != null && party.id == historyPartyId) {
                offer(Candidate(party, HISTORY, "Same payee as an earlier transaction with ${party.name}", MatchSource.HISTORY))
            }
        }

        val ranked = best.values.sortedByDescending { it.confidence }
        val top = ranked.firstOrNull()
        if (top == null || top.confidence < RELIABLE) return unknown(NO_MATCH_REASON)
        if (ranked.size > 1 && ranked[1].confidence == top.confidence) return unknown(AMBIGUOUS_REASON)
        return PartyMatch(top.party.id, top.party.name, top.confidence, top.reason, top.source)
    }

    private fun unknown(reason: String) = PartyMatch(null, null, 0, reason, MatchSource.NONE)
}
