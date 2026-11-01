package com.naxits.paisatracker.data.export

import com.naxits.paisatracker.data.database.dao.AccountMetaDao
import com.naxits.paisatracker.data.database.dao.ArchiveDao
import com.naxits.paisatracker.data.database.dao.CardDao
import com.naxits.paisatracker.data.database.dao.LendBorrowDao
import com.naxits.paisatracker.data.database.dao.PartyDao
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Writes every transaction and lending entry the app holds to a CSV, as a long-term record. */
@Singleton
class ArchiveExporter @Inject constructor(
    private val archiveDao: ArchiveDao,
    private val lendBorrowDao: LendBorrowDao,
    private val cardDao: CardDao,
    private val accountMetaDao: AccountMetaDao,
    private val partyDao: PartyDao
) {
    /** Returns how many rows were written, not counting the header. */
    suspend fun export(out: OutputStream): Int = withContext(Dispatchers.IO) {
        val rows = ArchiveCsv.rows(
            ArchiveCsv.Input(
                transactions = archiveDao.allTransactions(),
                lendingEntries = lendBorrowDao.getAllTransactionsList(),
                persons = lendBorrowDao.getAllPersonsList(),
                balances = archiveDao.allBalances(),
                cards = cardDao.getAllCards().first(),
                accountMeta = accountMetaDao.getAll(),
                parties = partyDao.getAll()
            )
        )
        ArchiveCsv.write(rows, out)
        rows.size
    }
}
