package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // --- Payers ---
    @Query("SELECT * FROM payers ORDER BY name ASC")
    fun getAllPayersFlow(): Flow<List<Payer>>

    @Query("SELECT * FROM payers ORDER BY name ASC")
    suspend fun getAllPayers(): List<Payer>

    @Query("SELECT * FROM payers WHERE id = :id")
    suspend fun getPayerById(id: String): Payer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayer(payer: Payer)

    @Query("DELETE FROM payers WHERE id = :id")
    suspend fun deletePayerById(id: String)

    // --- Collection Items ---
    @Query("SELECT * FROM collection_items")
    fun getAllCollectionItemsFlow(): Flow<List<CollectionItem>>

    @Query("SELECT * FROM collection_items")
    suspend fun getAllCollectionItems(): List<CollectionItem>

    @Query("SELECT * FROM collection_items WHERE payerId = :payerId")
    fun getCollectionItemsForPayerFlow(payerId: String): Flow<List<CollectionItem>>

    @Query("SELECT * FROM collection_items WHERE payerId = :payerId")
    suspend fun getCollectionItemsForPayer(payerId: String): List<CollectionItem>

    @Query("SELECT * FROM collection_items WHERE id = :id")
    suspend fun getCollectionItemById(id: String): CollectionItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollectionItem(item: CollectionItem)

    @Query("DELETE FROM collection_items WHERE id = :id")
    suspend fun deleteCollectionItemById(id: String)

    // --- Payment Records ---
    @Query("SELECT * FROM payment_records")
    fun getAllPaymentRecordsFlow(): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payment_records")
    suspend fun getAllPaymentRecords(): List<PaymentRecord>

    @Query("SELECT * FROM payment_records WHERE collectionItemId = :itemId")
    fun getPaymentRecordsForItemFlow(itemId: String): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payment_records WHERE collectionItemId = :itemId")
    suspend fun getPaymentRecordsForItem(itemId: String): List<PaymentRecord>

    @Query("SELECT * FROM payment_records WHERE id = :id")
    suspend fun getPaymentRecordById(id: String): PaymentRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentRecord(record: PaymentRecord)

    @Query("DELETE FROM payment_records WHERE id = :id")
    suspend fun deletePaymentRecordById(id: String)

    // --- Synchronization Queue ---
    @Query("SELECT * FROM sync_queue ORDER BY timestamp ASC")
    fun getSyncQueueFlow(): Flow<List<SyncQueueItem>>

    @Query("SELECT * FROM sync_queue ORDER BY timestamp ASC")
    suspend fun getSyncQueueList(): List<SyncQueueItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncQueueItem(item: SyncQueueItem)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteSyncQueueItemById(id: Long)

    @Query("DELETE FROM sync_queue WHERE id IN (:ids)")
    suspend fun deleteSyncQueueItemsByIds(ids: List<Long>)

    @Query("DELETE FROM sync_queue")
    suspend fun clearSyncQueue()

    // --- Cash Box Transactions ---
    @Query("SELECT * FROM cash_transactions ORDER BY date DESC, createdAt DESC")
    fun getAllCashTransactionsFlow(): Flow<List<CashTransaction>>

    @Query("SELECT * FROM cash_transactions ORDER BY date DESC, createdAt DESC")
    suspend fun getAllCashTransactions(): List<CashTransaction>

    @Query("SELECT * FROM cash_transactions WHERE id = :id")
    suspend fun getCashTransactionById(id: String): CashTransaction?

    @Query("SELECT * FROM cash_transactions WHERE relatedPaymentId = :paymentId")
    suspend fun getCashTransactionByPaymentId(paymentId: String): CashTransaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashTransaction(transaction: CashTransaction)

    @Query("DELETE FROM cash_transactions WHERE id = :id")
    suspend fun deleteCashTransactionById(id: String)

    @Query("DELETE FROM cash_transactions WHERE relatedPaymentId = :paymentId")
    suspend fun deleteCashTransactionByPaymentId(paymentId: String)
}
