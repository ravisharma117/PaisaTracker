package com.naxits.paisatracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * "When this counterparty shows up for this kind of entry, the user files it under this category."
 * Written when the user categorises something or confirms a suggested category, and read when a
 * new entry might be the next one in a series.
 *
 * [patternKey] says who it is about, strongest first: `party:<id>`, `upi:<address>`,
 * `acct:<digits>`, `phone:<number>`, `merchant:<name>`. One row per key and entry type.
 */
@Entity(tableName = "category_patterns", primaryKeys = ["pattern_key", "type"])
data class CategoryPatternEntity(
    @ColumnInfo(name = "pattern_key") val patternKey: String,
    /** The [TransactionType] name, so income and expense to the same party are remembered apart. */
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "subcategory") val subcategory: String? = null,
    /** How many times in a row the user chose this category for this key. */
    @ColumnInfo(name = "count", defaultValue = "1") val count: Int = 1,
    @ColumnInfo(name = "last_amount") val lastAmount: BigDecimal,
    @ColumnInfo(name = "last_date") val lastDate: LocalDateTime,
    @ColumnInfo(name = "updated_at") val updatedAt: LocalDateTime = LocalDateTime.now()
)
