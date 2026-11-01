package com.naxits.paisatracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import java.time.LocalDateTime

/**
 * What the user says about an account, kept apart from its balance history.
 *
 * An account is the pair bank name + last 4 digits over `account_balances` rows, so this table is
 * keyed the same way. A row exists only once the user sets something on the account.
 */
@Entity(tableName = "account_meta", primaryKeys = ["bank_name", "account_last4"])
data class AccountMetaEntity(
    @ColumnInfo(name = "bank_name") val bankName: String,
    @ColumnInfo(name = "account_last4") val accountLast4: String,
    /** Icon the user picked. Wins over the logo found from the bank name. Null means no override. */
    @ColumnInfo(name = "custom_icon_name") val customIconName: String? = null,
    /** Comma-joined tags, see [com.naxits.paisatracker.domain.model.AccountTags]. */
    @ColumnInfo(name = "tags", defaultValue = "") val tags: String = "",
    @ColumnInfo(name = "description", defaultValue = "") val description: String = "",
    @ColumnInfo(name = "updated_at") val updatedAt: LocalDateTime = LocalDateTime.now()
)
