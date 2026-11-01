package com.naxits.paisatracker.domain.service

import com.naxits.paisatracker.data.database.entity.CategoryPatternEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** How far apart the entries of a series are. The ranges leave room for pay days that move a few days. */
enum class RecurrencePeriod(val label: String, private val minDays: Long, private val maxDays: Long) {
    WEEKLY("weekly", 6, 8),
    EVERY_TWO_WEEKS("fortnightly", 12, 16),
    MONTHLY("monthly", 25, 36),
    QUARTERLY("quarterly", 84, 98),
    YEARLY("yearly", 355, 375);

    fun matches(days: Long): Boolean = days in minDays..maxDays

    companion object {
        fun of(days: Long): RecurrencePeriod? = entries.firstOrNull { it.matches(days) }
    }
}

/** An entry in a series: when, how much, and the words around it. */
data class RecurrenceEntry(val dateTime: LocalDateTime, val amount: BigDecimal, val text: String)

data class RecurringSuggestion(
    val category: String,
    val subcategory: String?,
    /** 0 to 100. */
    val confidence: Int,
    /** In plain words, why this category is suggested. */
    val reason: String,
    val period: RecurrencePeriod,
    /** How many earlier entries were compared. */
    val earlierEntries: Int
)

/**
 * Decides whether a new entry looks like the next one in a series the user already categorised,
 * and if so which category to suggest. It never rests on the amount alone: who it is with, how
 * close the amount is, whether the dates fall into a regular rhythm, and how alike the wording is
 * all count, and an entry with no regular rhythm gets no suggestion.
 */
object RecurringPatternDetector {
    /** Earlier entries needed before a series exists. */
    const val MIN_EARLIER = 2

    /** Score (0 to 100) needed to suggest anything; below it the category is left alone. */
    const val THRESHOLD = 70

    /** Amounts may differ by this share of the earlier amount; stored in tenths of a percent. */
    const val DEFAULT_TOLERANCE_TENTHS = 100

    val TOLERANCE_CHOICES_TENTHS = listOf(50, 75, 100)

    private const val ENTRIES_COMPARED = 5
    private const val STRONG_IDENTITY = 40
    private const val WEAK_IDENTITY = 25
    private const val AMOUNT_POINTS = 25
    private const val RHYTHM_POINTS = 15
    private const val STEADY_RHYTHM_POINTS = 10
    private const val WORDING_POINTS = 10

    /**
     * @param earlier entries with the same counterparty and type, in any order; only those before [current] count
     * @param strongIdentity true when [pattern] is keyed on a specific counterparty (a saved customer, UPI id, account or phone) rather than merchant text
     * @param identityLabel what the key stands for, for the reason ("account", "payee", ...)
     * @param toleranceTenths allowed difference in amount, in tenths of a percent (100 is 10%)
     */
    fun suggest(
        current: RecurrenceEntry,
        pattern: CategoryPatternEntity,
        earlier: List<RecurrenceEntry>,
        strongIdentity: Boolean,
        identityLabel: String,
        toleranceTenths: Int = DEFAULT_TOLERANCE_TENTHS
    ): RecurringSuggestion? {
        val compared = earlier
            .filter { it.dateTime.isBefore(current.dateTime) }
            .sortedByDescending { it.dateTime }
            .take(ENTRIES_COMPARED)
        if (compared.size < MIN_EARLIER) return null

        var score = if (strongIdentity) STRONG_IDENTITY else WEAK_IDENTITY

        val tolerance = BigDecimal(toleranceTenths).movePointLeft(3)
        val close = compared.filter { withinTolerance(current.amount, it.amount, tolerance) }
        // An amount far from every earlier one is a different kind of entry, whatever else matches
        if (close.isEmpty()) return null
        score += AMOUNT_POINTS * close.size / compared.size

        // gaps[0] is the time since the latest earlier entry; the rest are the gaps within the series
        val gaps = (listOf(current) + compared).zipWithNext { later, sooner ->
            Duration.between(sooner.dateTime, later.dateTime).toDays()
        }
        val period = RecurrencePeriod.of(gaps.first()) ?: return null
        score += RHYTHM_POINTS
        val earlierGaps = gaps.drop(1)
        if (earlierGaps.isNotEmpty() && earlierGaps.all { period.matches(it) }) score += STEADY_RHYTHM_POINTS

        score += (WORDING_POINTS * compared.map { similarity(current.text, it.text) }.average()).roundToInt()

        if (score < THRESHOLD) return null

        val reason = buildString {
            append("${compared.size} earlier ${period.label} entries with the same $identityLabel")
            maxDeviationPercent(current.amount, close)?.let { percent ->
                append(if (percent == 0) ", same amount" else ", amounts within $percent%")
            }
            append(", filed under ${pattern.category}")
        }
        return RecurringSuggestion(
            category = pattern.category,
            subcategory = pattern.subcategory,
            confidence = score.coerceAtMost(100),
            reason = reason,
            period = period,
            earlierEntries = compared.size
        )
    }

    private fun withinTolerance(now: BigDecimal, before: BigDecimal, tolerance: BigDecimal): Boolean {
        if (before.signum() == 0) return now.signum() == 0
        return (now - before).abs() <= before.abs() * tolerance
    }

    /** The largest difference, as a whole percent rounded up, among the entries that were close; null when none were. */
    private fun maxDeviationPercent(now: BigDecimal, close: List<RecurrenceEntry>): Int? {
        val worst = close.filter { it.amount.signum() != 0 }.maxOfOrNull { ((now - it.amount).abs()).divide(it.amount.abs(), 4, RoundingMode.HALF_UP) }
            ?: return null
        return worst.movePointRight(2).setScale(0, RoundingMode.CEILING).toInt()
    }

    private fun tokens(text: String): Set<String> =
        text.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
            .split(' ').filter { it.length > 1 }.toSet()

    /** Share of words two texts have in common, 0 to 1. Texts with no words say nothing either way. */
    private fun similarity(a: String, b: String): Double {
        val x = tokens(a)
        val y = tokens(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return x.intersect(y).size.toDouble() / x.union(y).size
    }
}
