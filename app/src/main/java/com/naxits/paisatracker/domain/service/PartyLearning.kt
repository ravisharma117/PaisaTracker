package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.domain.model.Party
import com.naxits.paisatracker.domain.model.PartyIdentifier

/** What a confirmed transaction teaches the app about the party it was with. */
object PartyLearning {

    /**
     * [party] plus whatever [counterparty] and [merchantName] add: a UPI id, an account number, a
     * phone number (only when the party has none), and the merchant text as another name. Nothing
     * already known is changed or removed.
     */
    fun learn(party: Party, counterparty: Counterparty, merchantName: String): Party {
        val identifiers = party.identifiers.toMutableList()
        counterparty.upiId?.let { id ->
            if (identifiers.none { it is PartyIdentifier.Upi && it.value.equals(id, ignoreCase = true) }) {
                identifiers += PartyIdentifier.Upi(id.lowercase())
            }
        }
        counterparty.account?.let { number ->
            if (identifiers.none { it is PartyIdentifier.Account && it.value == number }) {
                identifiers += PartyIdentifier.Account(number)
            }
        }

        val aliases = party.aliases.toMutableList()
        val text = counterparty.name ?: merchantName
        val isUseful = text.isNotBlank() && !text.startsWith("Unknown", ignoreCase = true)
        if (isUseful && !text.equals(party.name, ignoreCase = true) && aliases.none { it.equals(text, ignoreCase = true) }) {
            aliases += text
        }

        return party.copy(
            phone = party.phone ?: counterparty.phone,
            identifiers = identifiers,
            aliases = aliases
        )
    }
}
