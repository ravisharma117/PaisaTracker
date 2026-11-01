package com.naxits.paisatracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

object ReviewKind {
    /** Who the transaction was with (customer or vendor). */
    const val PARTY = "PARTY"

    /** The category the app suggested from earlier, similar transactions. */
    const val CATEGORY = "CATEGORY"
}

object ReviewStatus {
    const val PENDING = "PENDING"

    /** The user confirmed (or corrected and confirmed) it. */
    const val CONFIRMED = "CONFIRMED"

    /** Nobody reviewed it for three months, so the suggestion was kept as it was. */
    const val AUTO_CONFIRMED = "AUTO_CONFIRMED"

    const val DISMISSED = "DISMISSED"
}

object ReviewResolvedBy {
    const val MANUAL = "MANUAL"
    const val AUTO_THREE_MONTHS = "AUTO_3M"
}

/**
 * Something the app worked out about a transaction that the user should look at. Rows are never
 * deleted, so what was suggested, why, and how it was settled stay available for checking later.
 * One row per transaction and kind.
 */
@Entity(
    tableName = "transaction_reviews",
    indices = [
        Index(value = ["transaction_id", "kind"], unique = true),
        Index(value = ["status"])
    ]
)
data class TransactionReviewEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,

    /** [ReviewKind.PARTY] or [ReviewKind.CATEGORY]. */
    @ColumnInfo(name = "kind")
    val kind: String,

    /** The party name or category suggested; "Unknown" when no party matched. */
    @ColumnInfo(name = "suggested_value")
    val suggestedValue: String,

    @ColumnInfo(name = "suggested_subcategory")
    val suggestedSubcategory: String? = null,

    /** For a party review: the matched party, null when it is Unknown. */
    @ColumnInfo(name = "suggested_party_id")
    val suggestedPartyId: Long? = null,

    /** In plain words, how this was worked out. */
    @ColumnInfo(name = "reason")
    val reason: String,

    /** 0 to 100. */
    @ColumnInfo(name = "confidence", defaultValue = "0")
    val confidence: Int = 0,

    @ColumnInfo(name = "status", defaultValue = "PENDING")
    val status: String = ReviewStatus.PENDING,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "resolved_at")
    val resolvedAt: LocalDateTime? = null,

    /** [ReviewResolvedBy.MANUAL] or [ReviewResolvedBy.AUTO_THREE_MONTHS]. */
    @ColumnInfo(name = "resolved_by")
    val resolvedBy: String? = null,

    /** What the review ended up as: the confirmed or corrected value. */
    @ColumnInfo(name = "resolved_value")
    val resolvedValue: String? = null
) {
    val isPending: Boolean get() = status == ReviewStatus.PENDING
}
