package com.naxits.paisatracker.domain.model

import com.naxits.paisatracker.data.database.entity.AccountMetaEntity

/** The user's own notes on an account: a custom icon, tags, a description and whether to list it on Home. */
data class AccountMeta(
    val customIconName: String? = null,
    val tags: List<String> = emptyList(),
    val description: String = "",
    val showOnHome: Boolean = false
) {
    val isBusiness: Boolean get() = AccountTags.hasBusiness(tags)
    val isEmpty: Boolean get() = customIconName == null && tags.isEmpty() && description.isBlank() && !showOnHome

    companion object {
        val NONE = AccountMeta()
    }
}

/**
 * Tags describe the type or purpose of an account. They stay a plain list of labels: there is no
 * separate personal/business field, "Business" is simply one of the tags.
 */
object AccountTags {
    const val SAVINGS = "Savings"
    const val CURRENT = "Current"
    const val FD = "FD"
    const val RD = "RD"
    const val BUSINESS = "Business"

    val PRESETS = listOf(SAVINGS, CURRENT, FD, RD, BUSINESS)

    private const val SEPARATOR = ","

    /** Splits stored tags. Blank entries and repeats (ignoring case) are dropped. */
    fun parse(stored: String?): List<String> = normalize(stored.orEmpty().split(SEPARATOR))

    /** Cleans a list of tags the way storing and re-reading them would. */
    fun normalize(tags: List<String>): List<String> {
        val seen = HashSet<String>()
        return tags.map { it.trim().replace(SEPARATOR, " ") }
            .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
    }

    fun join(tags: List<String>): String = normalize(tags).joinToString(SEPARATOR)

    fun hasBusiness(tags: List<String>): Boolean = tags.any { it.equals(BUSINESS, ignoreCase = true) }
}

fun AccountMetaEntity.toDomain() = AccountMeta(
    customIconName = customIconName?.takeIf { it.isNotBlank() },
    tags = AccountTags.parse(tags),
    description = description,
    showOnHome = showOnHome
)
