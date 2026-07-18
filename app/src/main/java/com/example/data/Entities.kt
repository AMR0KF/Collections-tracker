package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "payers")
data class Payer(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "collection_items",
    foreignKeys = [
        ForeignKey(
            entity = Payer::class,
            parentColumns = ["id"],
            childColumns = ["payerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["payerId"])]
)
data class CollectionItem(
    @PrimaryKey val id: String,
    val payerId: String,
    val type: String, // "rent", "loan", "membership", "custom"
    val customTypeLabel: String?,
    val amount: Double,
    val frequency: String, // "weekly", "monthly", "custom"
    val intervalDays: Int?,
    val startDate: Long,
    val endDate: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "payment_records",
    foreignKeys = [
        ForeignKey(
            entity = CollectionItem::class,
            parentColumns = ["id"],
            childColumns = ["collectionItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["collectionItemId"])]
)
data class PaymentRecord(
    @PrimaryKey val id: String,
    val collectionItemId: String,
    val cycleIndex: Int,
    val amount: Double,
    val date: Long,
    val method: String, // "cash", "transfer", "other"
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "sync_queue")
data class SyncQueueItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String, // "insert", "update", "delete"
    val entityType: String, // "payer", "collection_item", "payment"
    val entityId: String,
    val payload: String, // Serialized JSON containing the entity's properties
    val timestamp: Long
)

@Entity(tableName = "cash_transactions")
data class CashTransaction(
    @PrimaryKey val id: String,
    val type: String, // "in" (Cash In) or "out" (Paid Out)
    val amount: Double,
    val category: String, // "payout", "deposit", "expense", "wage", "refund", "payment_collection", "other"
    val description: String?,
    val date: Long,
    val relatedPaymentId: String?, // Linked payment ID if from a cash collection
    val createdAt: Long,
    val updatedAt: Long
)
