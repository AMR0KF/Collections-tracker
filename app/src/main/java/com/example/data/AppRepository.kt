package com.example.data

import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

object EntitySerializer {
    fun serializePayer(p: Payer): String {
        return JSONObject().apply {
            put("id", p.id)
            put("name", p.name)
            put("phone", p.phone ?: JSONObject.NULL)
            put("notes", p.notes ?: JSONObject.NULL)
            put("createdAt", p.createdAt)
            put("updatedAt", p.updatedAt)
        }.toString()
    }

    fun deserializePayer(json: String): Payer {
        val obj = JSONObject(json)
        return Payer(
            id = obj.getString("id"),
            name = obj.getString("name"),
            phone = if (obj.isNull("phone")) null else obj.getString("phone"),
            notes = if (obj.isNull("notes")) null else obj.getString("notes"),
            createdAt = obj.getLong("createdAt"),
            updatedAt = obj.getLong("updatedAt")
        )
    }

    fun serializeCollectionItem(item: CollectionItem): String {
        return JSONObject().apply {
            put("id", item.id)
            put("payerId", item.payerId)
            put("type", item.type)
            put("customTypeLabel", item.customTypeLabel ?: JSONObject.NULL)
            put("amount", item.amount)
            put("frequency", item.frequency)
            put("intervalDays", item.intervalDays ?: JSONObject.NULL)
            put("startDate", item.startDate)
            put("endDate", item.endDate ?: JSONObject.NULL)
            put("createdAt", item.createdAt)
            put("updatedAt", item.updatedAt)
        }.toString()
    }

    fun deserializeCollectionItem(json: String): CollectionItem {
        val obj = JSONObject(json)
        return CollectionItem(
            id = obj.getString("id"),
            payerId = obj.getString("payerId"),
            type = obj.getString("type"),
            customTypeLabel = if (obj.isNull("customTypeLabel")) null else obj.getString("customTypeLabel"),
            amount = obj.getDouble("amount"),
            frequency = obj.getString("frequency"),
            intervalDays = if (obj.isNull("intervalDays")) null else obj.getInt("intervalDays"),
            startDate = obj.getLong("startDate"),
            endDate = if (obj.isNull("endDate")) null else obj.getLong("endDate"),
            createdAt = obj.getLong("createdAt"),
            updatedAt = obj.getLong("updatedAt")
        )
    }

    fun serializePaymentRecord(r: PaymentRecord): String {
        return JSONObject().apply {
            put("id", r.id)
            put("collectionItemId", r.collectionItemId)
            put("cycleIndex", r.cycleIndex)
            put("amount", r.amount)
            put("date", r.date)
            put("method", r.method)
            put("notes", r.notes ?: JSONObject.NULL)
            put("createdAt", r.createdAt)
            put("updatedAt", r.updatedAt)
        }.toString()
    }

    fun deserializePaymentRecord(json: String): PaymentRecord {
        val obj = JSONObject(json)
        return PaymentRecord(
            id = obj.getString("id"),
            collectionItemId = obj.getString("collectionItemId"),
            cycleIndex = obj.getInt("cycleIndex"),
            amount = obj.getDouble("amount"),
            date = obj.getLong("date"),
            method = obj.getString("method"),
            notes = if (obj.isNull("notes")) null else obj.getString("notes"),
            createdAt = obj.getLong("createdAt"),
            updatedAt = obj.getLong("updatedAt")
        )
    }
}

class AppRepository(private val appDao: AppDao) {

    val allPayers: Flow<List<Payer>> = appDao.getAllPayersFlow()
    val allCollectionItems: Flow<List<CollectionItem>> = appDao.getAllCollectionItemsFlow()
    val allPaymentRecords: Flow<List<PaymentRecord>> = appDao.getAllPaymentRecordsFlow()
    val syncQueue: Flow<List<SyncQueueItem>> = appDao.getSyncQueueFlow()

    suspend fun getAllPayersDirect(): List<Payer> = appDao.getAllPayers()
    suspend fun getAllCollectionItemsDirect(): List<CollectionItem> = appDao.getAllCollectionItems()
    suspend fun getAllPaymentRecordsDirect(): List<PaymentRecord> = appDao.getAllPaymentRecords()
    suspend fun getSyncQueueDirect(): List<SyncQueueItem> = appDao.getSyncQueueList()

    // --- Payer Actions ---
    suspend fun insertPayer(payer: Payer, enqueue: Boolean = true) {
        appDao.insertPayer(payer)
        if (enqueue) {
            val payload = EntitySerializer.serializePayer(payer)
            appDao.insertSyncQueueItem(
                SyncQueueItem(
                    action = "insert",
                    entityType = "payer",
                    entityId = payer.id,
                    payload = payload,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deletePayer(payerId: String, enqueue: Boolean = true) {
        val payer = appDao.getPayerById(payerId)
        appDao.deletePayerById(payerId)
        if (enqueue && payer != null) {
            val payload = EntitySerializer.serializePayer(payer)
            appDao.insertSyncQueueItem(
                SyncQueueItem(
                    action = "delete",
                    entityType = "payer",
                    entityId = payerId,
                    payload = payload,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    // --- Collection Item Actions ---
    suspend fun insertCollectionItem(item: CollectionItem, enqueue: Boolean = true) {
        appDao.insertCollectionItem(item)
        if (enqueue) {
            val payload = EntitySerializer.serializeCollectionItem(item)
            appDao.insertSyncQueueItem(
                SyncQueueItem(
                    action = "insert",
                    entityType = "collection_item",
                    entityId = item.id,
                    payload = payload,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteCollectionItem(itemId: String, enqueue: Boolean = true) {
        val item = appDao.getCollectionItemById(itemId)
        appDao.deleteCollectionItemById(itemId)
        if (enqueue && item != null) {
            val payload = EntitySerializer.serializeCollectionItem(item)
            appDao.insertSyncQueueItem(
                SyncQueueItem(
                    action = "delete",
                    entityType = "collection_item",
                    entityId = itemId,
                    payload = payload,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    // --- Payment Record Actions ---
    suspend fun insertPaymentRecord(record: PaymentRecord, enqueue: Boolean = true) {
        appDao.insertPaymentRecord(record)
        if (record.method.lowercase() == "cash") {
            val description = "Automatic Collection - Cycle ${record.cycleIndex + 1}"
            appDao.insertCashTransaction(
                CashTransaction(
                    id = java.util.UUID.randomUUID().toString(),
                    type = "in",
                    amount = record.amount,
                    category = "payment_collection",
                    description = description,
                    date = record.date,
                    relatedPaymentId = record.id,
                    createdAt = record.createdAt,
                    updatedAt = record.updatedAt
                )
            )
        }
        if (enqueue) {
            val payload = EntitySerializer.serializePaymentRecord(record)
            appDao.insertSyncQueueItem(
                SyncQueueItem(
                    action = "insert",
                    entityType = "payment",
                    entityId = record.id,
                    payload = payload,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deletePaymentRecord(recordId: String, enqueue: Boolean = true) {
        val record = appDao.getPaymentRecordById(recordId)
        appDao.deletePaymentRecordById(recordId)
        if (record != null && record.method.lowercase() == "cash") {
            appDao.deleteCashTransactionByPaymentId(recordId)
        }
        if (enqueue && record != null) {
            val payload = EntitySerializer.serializePaymentRecord(record)
            appDao.insertSyncQueueItem(
                SyncQueueItem(
                    action = "delete",
                    entityType = "payment",
                    entityId = recordId,
                    payload = payload,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    // --- Sync Queue Maintenance ---
    suspend fun deleteQueueItems(ids: List<Long>) {
        appDao.deleteSyncQueueItemsByIds(ids)
    }

    suspend fun clearQueue() {
        appDao.clearSyncQueue()
    }

    // --- Cash Box Transactions ---
    val allCashTransactions: Flow<List<CashTransaction>> = appDao.getAllCashTransactionsFlow()

    suspend fun getAllCashTransactionsDirect(): List<CashTransaction> = appDao.getAllCashTransactions()

    suspend fun getCashTransactionById(id: String): CashTransaction? = appDao.getCashTransactionById(id)

    suspend fun getCashTransactionByPaymentId(paymentId: String): CashTransaction? = appDao.getCashTransactionByPaymentId(paymentId)

    suspend fun insertCashTransaction(transaction: CashTransaction) {
        appDao.insertCashTransaction(transaction)
    }

    suspend fun deleteCashTransaction(id: String) {
        appDao.deleteCashTransactionById(id)
    }

    suspend fun deleteCashTransactionByPaymentId(paymentId: String) {
        appDao.deleteCashTransactionByPaymentId(paymentId)
    }
}
