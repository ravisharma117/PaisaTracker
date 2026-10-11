package com.naxits.paisatracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/** What a manually tracked holding is. Assets add to net worth, liabilities take away. */
enum class NetWorthItemType(val isLiability: Boolean, val isLoan: Boolean = false) {
    MUTUAL_FUND(false),
    STOCKS(false),
    FIXED_DEPOSIT(false),
    GOLD(false),
    PROPERTY(false),
    VEHICLE(false),
    OTHER_ASSET(false),
    HOME_LOAN(true, isLoan = true),
    VEHICLE_LOAN(true, isLoan = true),
    PERSONAL_LOAN(true, isLoan = true),
    EDUCATION_LOAN(true, isLoan = true),
    OTHER_LIABILITY(true);

    /** Funds, shares and deposits are shown together as investments. */
    val isInvestment: Boolean get() = this == MUTUAL_FUND || this == STOCKS || this == FIXED_DEPOSIT
}

/**
 * A holding or a loan that is not a bank account. [currentValue] is the value as of [asOfDate]:
 * for an asset its worth, for a loan the balance still owed on that date. A loan with a rate,
 * tenure and EMI is carried forward from that point by the repayment schedule, so the balance is
 * an estimate until the user types in the real one.
 */
@Entity(tableName = "net_worth_items", indices = [Index(value = ["type"])])
data class NetWorthItemEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: NetWorthItemType,

    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "INR",

    @ColumnInfo(name = "current_value")
    val currentValue: BigDecimal,

    @ColumnInfo(name = "as_of_date")
    val asOfDate: LocalDate,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    // Loan terms. All null for assets and for loans tracked by balance alone.
    @ColumnInfo(name = "annual_rate_percent")
    val annualRatePercent: BigDecimal? = null,

    @ColumnInfo(name = "emi_amount")
    val emiAmount: BigDecimal? = null,

    /** Day of the month the EMI is paid; the last day in shorter months. */
    @ColumnInfo(name = "emi_day")
    val emiDay: Int? = null,

    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

/** Every value an item has had, so updating a value keeps the old one for the trend. */
@Entity(
    tableName = "net_worth_item_values",
    foreignKeys = [
        ForeignKey(
            entity = NetWorthItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["item_id", "date"], unique = true)]
)
data class NetWorthItemValueEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "item_id")
    val itemId: Long,

    @ColumnInfo(name = "value")
    val value: BigDecimal,

    @ColumnInfo(name = "date")
    val date: LocalDate
)

/** Net worth at the end of a month, kept so the trend does not depend on rebuilding the past. */
@Entity(tableName = "net_worth_snapshots", indices = [Index(value = ["year_month"], unique = true)])
data class NetWorthSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** "2026-10". */
    @ColumnInfo(name = "year_month")
    val yearMonth: String,

    @ColumnInfo(name = "assets")
    val assets: BigDecimal,

    @ColumnInfo(name = "liabilities")
    val liabilities: BigDecimal,

    @ColumnInfo(name = "net_worth")
    val netWorth: BigDecimal,

    @ColumnInfo(name = "currency", defaultValue = "INR")
    val currency: String = "INR",

    /** True for months rebuilt from older records rather than captured at the time. */
    @ColumnInfo(name = "is_estimate", defaultValue = "0")
    val isEstimate: Boolean = false,

    @ColumnInfo(name = "captured_at")
    val capturedAt: LocalDateTime = LocalDateTime.now()
)
