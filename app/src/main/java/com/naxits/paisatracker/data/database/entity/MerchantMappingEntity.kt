package com.naxits.paisatracker.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "merchant_mappings")
data class MerchantMappingEntity(
    @PrimaryKey
    @ColumnInfo(name = "merchant_name")
    val merchantName: String,
    
    @ColumnInfo(name = "category")
    val category: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    /** The sub-category that goes with [category]. */
    @ColumnInfo(name = "subcategory")
    val subcategory: String? = null,

    /** The vendor's name in a spelling-independent form, so "Shakti Fuel Station" finds "Shaktifuelstation". */
    @ColumnInfo(name = "match_key")
    val matchKey: String? = null
)