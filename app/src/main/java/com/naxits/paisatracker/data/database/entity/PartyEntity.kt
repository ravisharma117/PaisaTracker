package com.naxits.paisatracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

object PartyKind {
    const val CUSTOMER = "CUSTOMER"
    const val VENDOR = "VENDOR"
}

/**
 * A customer or vendor the user does business with. Kept apart from the Khata people in
 * `lend_borrow_persons`, which track money lent and borrowed.
 */
@Entity(tableName = "parties", indices = [Index(value = ["name"])])
data class PartyEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    /** [PartyKind.CUSTOMER] or [PartyKind.VENDOR]. */
    @ColumnInfo(name = "kind", defaultValue = "CUSTOMER")
    val kind: String = PartyKind.CUSTOMER,

    @ColumnInfo(name = "phone")
    val phone: String? = null,

    /** UPI ids and account numbers this party is known by, one per line, see [com.naxits.paisatracker.domain.model.PartyIdentifier]. */
    @ColumnInfo(name = "identifiers", defaultValue = "")
    val identifiers: String = "",

    /** Other names the party appears under in bank text, one per line. */
    @ColumnInfo(name = "aliases", defaultValue = "")
    val aliases: String = "",

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "is_archived", defaultValue = "0")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
