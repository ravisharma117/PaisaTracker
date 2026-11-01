package com.naxits.paisatracker.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.naxits.paisatracker.data.database.entity.AccountMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountMetaDao {

    @Query("SELECT * FROM account_meta")
    fun observeAll(): Flow<List<AccountMetaEntity>>

    @Query("SELECT * FROM account_meta")
    suspend fun getAll(): List<AccountMetaEntity>

    @Query("SELECT * FROM account_meta WHERE bank_name = :bankName AND account_last4 = :accountLast4")
    suspend fun get(bankName: String, accountLast4: String): AccountMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: AccountMetaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(metas: List<AccountMetaEntity>)

    @Query("DELETE FROM account_meta WHERE bank_name = :bankName AND account_last4 = :accountLast4")
    suspend fun delete(bankName: String, accountLast4: String)

    @Query("DELETE FROM account_meta")
    suspend fun deleteAll()
}
