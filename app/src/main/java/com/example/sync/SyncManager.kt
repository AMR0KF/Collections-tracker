package com.example.sync

import android.content.Context
import android.util.Log
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

// Simulated Server Database
object MockServer {
    val payers = ConcurrentHashMap<String, Payer>()
    val collectionItems = ConcurrentHashMap<String, CollectionItem>()
    val paymentRecords = ConcurrentHashMap<String, PaymentRecord>()

    init {
        seedInitialServerData()
    }

    fun seedInitialServerData() {
        val now = System.currentTimeMillis()
        // Payer 1: Acme Corp (All Clear / Fully Paid)
        val acmeId = "payer_acme_corp"
        val acme = Payer(
            id = acmeId,
            name = "Acme Corp",
            phone = "555-0199",
            notes = "Wholesale distributor. Rent is due on the 1st of each month.",
            createdAt = now - 30 * 24 * 60 * 60 * 1000L, // 30 days ago
            updatedAt = now - 30 * 24 * 60 * 60 * 1000L
        )
        payers[acmeId] = acme

        val rentItemId = "item_acme_rent"
        val acmeRent = CollectionItem(
            id = rentItemId,
            payerId = acmeId,
            type = "rent",
            customTypeLabel = null,
            amount = 1200.0,
            frequency = "monthly",
            intervalDays = null,
            startDate = now - 15 * 24 * 60 * 60 * 1000L, // 15 days ago
            endDate = null,
            createdAt = now - 30 * 24 * 60 * 60 * 1000L,
            updatedAt = now - 30 * 24 * 60 * 60 * 1000L
        )
        collectionItems[rentItemId] = acmeRent

        // Payment for Acme: Cycle index 0, fully paid $1200
        val paymentAcmeId = "payment_acme_1"
        val paymentAcme = PaymentRecord(
            id = paymentAcmeId,
            collectionItemId = rentItemId,
            cycleIndex = 0,
            amount = 1200.0,
            date = now - 14 * 24 * 60 * 60 * 1000L,
            method = "transfer",
            notes = "First rent cycle paid in full",
            createdAt = now - 14 * 24 * 60 * 60 * 1000L,
            updatedAt = now - 14 * 24 * 60 * 60 * 1000L
        )
        paymentRecords[paymentAcmeId] = paymentAcme

        // Payer 2: John Doe (Overdue Loan)
        val johnId = "payer_john_doe"
        val john = Payer(
            id = johnId,
            name = "John Doe",
            phone = "555-4422",
            notes = "Personal friend. Weekly loan repayment.",
            createdAt = now - 45 * 24 * 60 * 60 * 1000L,
            updatedAt = now - 45 * 24 * 60 * 60 * 1000L
        )
        payers[johnId] = john

        val loanItemId = "item_john_loan"
        val johnLoan = CollectionItem(
            id = loanItemId,
            payerId = johnId,
            type = "loan",
            customTypeLabel = null,
            amount = 150.0,
            frequency = "weekly",
            intervalDays = null,
            startDate = now - 20 * 24 * 60 * 60 * 1000L, // 20 days ago -> expects 3 cycles (cycle 0, cycle 1, cycle 2)
            endDate = null,
            createdAt = now - 45 * 24 * 60 * 60 * 1000L,
            updatedAt = now - 45 * 24 * 60 * 60 * 1000L
        )
        collectionItems[loanItemId] = johnLoan

        // Payment for John Doe: Paid only Cycle 0, Cycle 1 & 2 are overdue/due
        val paymentJohnId = "payment_john_1"
        val paymentJohn = PaymentRecord(
            id = paymentJohnId,
            collectionItemId = loanItemId,
            cycleIndex = 0,
            amount = 150.0,
            date = now - 18 * 24 * 60 * 60 * 1000L,
            method = "cash",
            notes = "Week 1 installment",
            createdAt = now - 18 * 24 * 60 * 60 * 1000L,
            updatedAt = now - 18 * 24 * 60 * 60 * 1000L
        )
        paymentRecords[paymentJohnId] = paymentJohn
    }

    fun clearServerState() {
        payers.clear()
        collectionItems.clear()
        paymentRecords.clear()
        seedInitialServerData()
    }
}

class SyncManager(
    private val context: Context,
    private val repository: AppRepository
) {
    private val sharedPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline

    private val _syncingState = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncingState: StateFlow<SyncStatus> = _syncingState

    sealed class SyncStatus {
        object Idle : SyncStatus()
        object Syncing : SyncStatus()
        data class Success(val pushed: Int, val pulled: Int) : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }

    var lastSyncTimestamp: Long
        get() = sharedPrefs.getLong("last_sync_timestamp", 0L)
        set(value) = sharedPrefs.edit().putLong("last_sync_timestamp", value).apply()

    fun toggleConnection() {
        _isOnline.value = !_isOnline.value
        Log.d("SyncManager", "Connection toggled. Online: ${_isOnline.value}")
    }

    fun setConnection(online: Boolean) {
        _isOnline.value = online
    }

    suspend fun performSync(): Boolean = withContext(Dispatchers.IO) {
        if (!_isOnline.value) {
            _syncingState.value = SyncStatus.Error("Cannot sync while offline")
            return@withContext false
        }

        _syncingState.value = SyncStatus.Syncing
        val now = System.currentTimeMillis()

        try {
            // Simulate short network delay
            kotlinx.coroutines.delay(1200)

            // 1. Outbound push (chronological order)
            val queue = repository.getSyncQueueDirect()
            var pushedCount = 0

            for (qItem in queue) {
                when (qItem.entityType) {
                    "payer" -> {
                        val clientPayer = EntitySerializer.deserializePayer(qItem.payload)
                        val serverPayer = MockServer.payers[clientPayer.id]
                        if (qItem.action == "delete") {
                            MockServer.payers.remove(clientPayer.id)
                        } else {
                            // Last-Write-Wins: if local is newer or server doesn't exist
                            if (serverPayer == null || clientPayer.updatedAt > serverPayer.updatedAt) {
                                MockServer.payers[clientPayer.id] = clientPayer
                            }
                        }
                    }
                    "collection_item" -> {
                        val clientItem = EntitySerializer.deserializeCollectionItem(qItem.payload)
                        val serverItem = MockServer.collectionItems[clientItem.id]
                        if (qItem.action == "delete") {
                            MockServer.collectionItems.remove(clientItem.id)
                        } else {
                            if (serverItem == null || clientItem.updatedAt > serverItem.updatedAt) {
                                MockServer.collectionItems[clientItem.id] = clientItem
                            }
                        }
                    }
                    "payment" -> {
                        val clientRecord = EntitySerializer.deserializePaymentRecord(qItem.payload)
                        val serverRecord = MockServer.paymentRecords[clientRecord.id]
                        if (qItem.action == "delete") {
                            MockServer.paymentRecords.remove(clientRecord.id)
                        } else {
                            if (serverRecord == null || clientRecord.updatedAt > serverRecord.updatedAt) {
                                MockServer.paymentRecords[clientRecord.id] = clientRecord
                            }
                        }
                    }
                }
                pushedCount++
            }

            // Purge outbound queue from local DB upon successful processing
            if (queue.isNotEmpty()) {
                repository.deleteQueueItems(queue.map { it.id })
            }

            // 2. Inbound pull: pull server changes with updatedAt > lastSyncTimestamp
            var pulledCount = 0
            val syncLimitTime = lastSyncTimestamp

            // Pull Payers
            for ((_, serverPayer) in MockServer.payers) {
                if (serverPayer.updatedAt > syncLimitTime) {
                    val localPayer = repository.getAllPayersDirect().find { it.id == serverPayer.id }
                    if (localPayer == null || serverPayer.updatedAt > localPayer.updatedAt) {
                        repository.insertPayer(serverPayer, enqueue = false)
                        pulledCount++
                    }
                }
            }

            // Pull Collection Items
            for ((_, serverItem) in MockServer.collectionItems) {
                if (serverItem.updatedAt > syncLimitTime) {
                    val localItem = repository.getAllCollectionItemsDirect().find { it.id == serverItem.id }
                    if (localItem == null || serverItem.updatedAt > localItem.updatedAt) {
                        // Check if the associated payer exists locally first to prevent FK crashes
                        val associatedPayerExists = repository.getAllPayersDirect().any { it.id == serverItem.payerId }
                        if (associatedPayerExists) {
                            repository.insertCollectionItem(serverItem, enqueue = false)
                            pulledCount++
                        }
                    }
                }
            }

            // Pull Payments
            for ((_, serverRecord) in MockServer.paymentRecords) {
                if (serverRecord.updatedAt > syncLimitTime) {
                    val localRecord = repository.getAllPaymentRecordsDirect().find { it.id == serverRecord.id }
                    if (localRecord == null || serverRecord.updatedAt > localRecord.updatedAt) {
                        // Check if associated collection item exists locally first
                        val associatedItemExists = repository.getAllCollectionItemsDirect().any { it.id == serverRecord.collectionItemId }
                        if (associatedItemExists) {
                            repository.insertPaymentRecord(serverRecord, enqueue = false)
                            pulledCount++
                        }
                    }
                }
            }

            // Also check for deletions from server:
            // For example, if an item is deleted on the server, we can remove it locally.
            // (For simplicity in this simulated model, standard entities handle addition/edits brilliantly.)

            // Update Last Sync Timestamp
            lastSyncTimestamp = now
            _syncingState.value = SyncStatus.Success(pushedCount, pulledCount)
            return@withContext true
        } catch (e: Exception) {
            Log.e("SyncManager", "Sync failed: ${e.message}", e)
            _syncingState.value = SyncStatus.Error(e.message ?: "Unknown sync error")
            return@withContext false
        }
    }

    // Helper extension to get current value from flows if available
    private fun <T> kotlinx.coroutines.flow.StateFlow<T>.valueAndNull(): T? {
        return try {
            this.value
        } catch (e: Exception) {
            null
        }
    }
}
