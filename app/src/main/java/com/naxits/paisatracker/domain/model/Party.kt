package com.naxits.paisatracker.domain.model

import com.naxits.paisatracker.data.database.entity.PartyEntity

/** What a party can be recognised by besides its name. */
sealed class PartyIdentifier {
    abstract val value: String

    data class Upi(override val value: String) : PartyIdentifier()
    data class Account(override val value: String) : PartyIdentifier()

    /** One line of the stored text, e.g. `upi:someone@bank`. */
    fun encode(): String = when (this) {
        is Upi -> "upi:$value"
        is Account -> "acct:$value"
    }

    companion object {
        fun decode(line: String): PartyIdentifier? {
            val text = line.trim()
            val value = text.substringAfter(':', "").trim()
            if (value.isEmpty()) return null
            return when (text.substringBefore(':')) {
                "upi" -> Upi(value.lowercase())
                "acct" -> Account(value)
                else -> null
            }
        }
    }
}

/** A customer or vendor as the matching and screens see it. */
data class Party(
    val id: Long,
    val name: String,
    val kind: String,
    val phone: String?,
    val identifiers: List<PartyIdentifier>,
    val aliases: List<String>,
    val notes: String?,
    val isArchived: Boolean
)

object PartyText {
    private const val SEPARATOR = "\n"

    fun lines(stored: String?): List<String> =
        stored.orEmpty().split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    /** Joins [values] one per line, dropping blanks and repeats (ignoring case). */
    fun join(values: List<String>): String {
        val seen = HashSet<String>()
        return values.map { it.trim().replace(SEPARATOR, " ") }
            .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
            .joinToString(SEPARATOR)
    }

    fun identifiers(stored: String?): List<PartyIdentifier> =
        lines(stored).mapNotNull { PartyIdentifier.decode(it) }.distinct()

    fun joinIdentifiers(identifiers: List<PartyIdentifier>): String =
        join(identifiers.map { it.encode() })
}

fun PartyEntity.toDomain() = Party(
    id = id,
    name = name,
    kind = kind,
    phone = phone?.takeIf { it.isNotBlank() },
    identifiers = PartyText.identifiers(identifiers),
    aliases = PartyText.lines(aliases),
    notes = notes?.takeIf { it.isNotBlank() },
    isArchived = isArchived
)

fun Party.toEntity(createdAt: java.time.LocalDateTime = java.time.LocalDateTime.now()) = PartyEntity(
    id = id,
    name = name.trim(),
    kind = kind,
    phone = phone?.takeIf { it.isNotBlank() },
    identifiers = PartyText.joinIdentifiers(identifiers),
    aliases = PartyText.join(aliases),
    notes = notes?.takeIf { it.isNotBlank() },
    isArchived = isArchived,
    createdAt = createdAt
)
