package com.ritesh.cashiro.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ritesh.cashiro.data.database.entity.NetWorthItemEntity
import com.ritesh.cashiro.data.database.entity.NetWorthItemValueEntity
import com.ritesh.cashiro.data.database.entity.NetWorthSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetWorthDao {

    // Items
    @Query("SELECT * FROM net_worth_items WHERE is_active = 1 ORDER BY name ASC")
    fun observeItems(): Flow<List<NetWorthItemEntity>>

    @Query("SELECT * FROM net_worth_items ORDER BY id ASC")
    suspend fun getAllItems(): List<NetWorthItemEntity>

    @Query("SELECT * FROM net_worth_items WHERE id = :id")
    suspend fun getItem(id: Long): NetWorthItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: NetWorthItemEntity): Long

    @Update
    suspend fun updateItem(item: NetWorthItemEntity)

    @Query("DELETE FROM net_worth_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM net_worth_items")
    suspend fun deleteAllItems()

    // Value history
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertValue(value: NetWorthItemValueEntity): Long

    @Query("SELECT * FROM net_worth_item_values ORDER BY item_id ASC, date ASC")
    suspend fun getAllValues(): List<NetWorthItemValueEntity>

    @Query("SELECT * FROM net_worth_item_values WHERE item_id = :itemId ORDER BY date DESC")
    fun observeValues(itemId: Long): Flow<List<NetWorthItemValueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValues(values: List<NetWorthItemValueEntity>)

    @Query("DELETE FROM net_worth_item_values")
    suspend fun deleteAllValues()

    // Snapshots
    @Query("SELECT * FROM net_worth_snapshots ORDER BY year_month ASC")
    fun observeSnapshots(): Flow<List<NetWorthSnapshotEntity>>

    @Query("SELECT * FROM net_worth_snapshots ORDER BY year_month ASC")
    suspend fun getAllSnapshots(): List<NetWorthSnapshotEntity>

    @Query("SELECT * FROM net_worth_snapshots WHERE year_month = :yearMonth")
    suspend fun getSnapshot(yearMonth: String): NetWorthSnapshotEntity?

    /** Replaces the month's row (unique on the month), so writing a month twice is harmless. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: NetWorthSnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshots(snapshots: List<NetWorthSnapshotEntity>)

    @Query("DELETE FROM net_worth_snapshots")
    suspend fun deleteAllSnapshots()
}
