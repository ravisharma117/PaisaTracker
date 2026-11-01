package com.naxits.paisatracker.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.naxits.paisatracker.data.database.entity.AccountBalanceEntity
import com.naxits.paisatracker.data.database.entity.TransactionEntity

/** What the long-term CSV record reads. Unlike the app's own lists, it includes deleted transactions. */
@Dao
interface ArchiveDao {

    @Query("SELECT * FROM transactions ORDER BY date_time ASC, id ASC")
    suspend fun allTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM account_balances WHERE is_sample = 0")
    suspend fun allBalances(): List<AccountBalanceEntity>
}
